package io.ktor.plugin.features

import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowScope
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.process.CommandLineArgumentProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.concurrent.thread

internal const val RUN_HOT_TASK_NAME = "runHot"

/**
 * Gradle task that produces the compiled classes HotSwapAgent watches. `runHot` forks a background
 * continuous build of this task (`gradlew -t <path>:classes`) so that saving a source file triggers
 * a recompile, which the agent then hot-swaps into the running server — no second terminal needed.
 */
private const val RECOMPILE_TASK_NAME = "classes"

/**
 * Gradle property (`-Pio.ktor.hotReload.autoRecompile=false`) to opt out of the forked continuous
 * recompile and drive compilation yourself (IDE auto-make, or your own `gradle -t classes`).
 */
private const val AUTO_RECOMPILE_PROPERTY = "io.ktor.hotReload.autoRecompile"

/**
 * Gradle property (`-Pio.ktor.hotReload.verbose=true`) for debugging the recompile/redefine/reload
 * pipeline end to end. Off by default: this output is mostly noise on the happy path. Turns on two
 * things at once:
 * - The forked continuous build's raw output (every `[recompile]`-prefixed line: task graph,
 *   up-to-date checks, `BUILD SUCCESSFUL` banners) instead of just a concise "change detected" /
 *   failure summary — see [HotRecompiler].
 * - HotSwapAgent's own internal logging, raised from its default level to `DEBUG` via a `LOGGER=`
 *   `-javaagent:` option (confirmed via decompiling `HotswapAgent.parseArgs`, which recognizes this
 *   key alongside `autoHotswap`/`disablePlugin` and calls `AgentLogger.setLevel(...)`). At the
 *   default level the agent only logs the classes it redefined; at `DEBUG` it also logs what it's
 *   watching, why a class was or wasn't picked up, and plugin-level activity — the detail needed to
 *   tell "nothing changed" apart from "changed but didn't reload". Since [handleForkedJvmLogLine]
 *   already forwards every line of the forked JVM's output unfiltered, this is the only lever needed
 *   to surface it.
 */
private const val VERBOSE_PROPERTY = "io.ktor.hotReload.verbose"

/**
 * HotSwapAgent is a JVM agent that watches the compiled-output directories on the classpath and, on
 * change, pushes the recompiled bytecode into the live JVM via `Instrumentation.redefineClasses`.
 *
 * The [HOT_RELOAD_JVM_ARGS] flag only *permits* enhanced redefinition — it is passive and never
 * triggers a swap on its own. This agent is what actually triggers it, so a background recompile
 * (`gradle -t classes`, or the IDE's auto-make) is applied to the running server with no restart.
 *
 * It is a single self-contained jar with no transitive dependencies, resolved from the consuming
 * build's repositories (Maven Central by default).
 */
private const val HOTSWAP_AGENT_DEPENDENCY = "org.hotswapagent:hotswap-agent:2.0.3"
private const val HOTSWAP_AGENT_CONFIGURATION_NAME = "ktorHotswapAgent"

/**
 * Agent options appended after `=` in `-javaagent:<jar>=<options>` (comma-separated `key=value`).
 *
 * `autoHotswap=true` makes the agent watch the classpath's compiled-output directories and redefine
 * changed classes via the Instrumentation API, with no debugger attached (its default is `false`,
 * which only reacts to IDE/JPDA-driven redefinitions). The application's own classes live in
 * directories on the classpath and are discovered and watched automatically — but only because
 * `-XX:HotswapAgent=external` (see [HOT_RELOAD_JVM_ARGS]) opens the JDK internals the agent needs to
 * read that classpath; without it the watch list comes up empty and nothing reloads.
 *
 * `disablePlugin=AnonymousClassPatch` turns off HotSwapAgent's plugin for renumbering *Java*
 * anonymous classes across redefinitions. It does not understand Kotlin's synthetic lambda classes
 * (e.g. a route handler compiles to `...$configureRouting$1$1`): its transform throws an
 * `InvocationTargetException` and can wedge that class so later edits stop reloading. DCEVM redefines
 * those classes correctly on its own, so disabling the plugin is strictly a fix here. Note only a
 * single plugin can be disabled via the command line — multiple values would collide with the
 * comma that separates agent options; disabling more would require a `hotswap-agent.properties` file.
 */
private const val HOTSWAP_AGENT_OPTIONS = "autoHotswap=true,disablePlugin=AnonymousClassPatch"

/**
 * Gradle property (`-Pio.ktor.hotReload.embeddedServerAccessor=<FQN>#<staticMethodName>`) naming a
 * no-arg static method that returns a `Collection<EmbeddedServer<*, *>>` of every running server,
 * e.g. `io.ktor.samples.fatjar.ApplicationKt#getEmbeddedServers` for a top-level
 * `val embeddedServers: MutableList<EmbeddedServer<*, *>>` (Kotlin compiles that to exactly this
 * static accessor shape). A collection rather than a single instance because a process can host more
 * than one `EmbeddedServer` — and so this accessor's shape doesn't need to change once Ktor itself
 * maintains such a collection automatically instead of consumer code doing it by hand. Forwarded as a
 * system property to the forked JVM, where a bundled custom HotSwapAgent plugin (see
 * [HOTSWAP_AGENT_PLUGIN_RESOURCE]) reads it reflectively to call `EmbeddedServer.reload()` on each
 * instance after every class redefinition, re-running the application's `modules` — the only way
 * structural changes (new routes, application-scoped setup) take effect without restarting the
 * process.
 *
 * Unset by default: without it, HotSwapAgent still redefines classes in place (edits inside existing
 * handler bodies apply immediately) but structural changes require a restart, exactly as before this
 * property existed.
 *
 * HotSwapAgent's redefinition event can't distinguish a trivial method-body edit from a structural
 * one, so enabling this means *every* redefinition triggers a full module reload, not just structural
 * ones — trading some of the seamless, state-preserving in-place swap for correctness on structural
 * changes.
 */
private const val EMBEDDED_SERVER_ACCESSOR_PROPERTY = "io.ktor.hotReload.embeddedServerAccessor"

/**
 * Classpath-relative path to the custom `KtorReload` HotSwapAgent plugin jar, bundled as a resource
 * by `plugin/build.gradle.kts` (via the `hotswapPluginArtifact` configuration) from the sibling
 * `hotswap-agent-plugin` module's build output. See that module's `KtorReloadPlugin`.
 */
private const val HOTSWAP_AGENT_PLUGIN_RESOURCE = "io/ktor/plugin/hotreload/hotswap-agent-plugin.jar"

/**
 * The JetBrains Runtime bundles a DCEVM-enabled JVM. Running the application on it with the flag
 * below lifts the standard HotSwap restriction (method-body-only changes) and allows classes to be
 * redefined in place on recompile — so edits to handler bodies take effect in the running server
 * without a restart, keeping process state (connections, caches, warm JIT) alive.
 */
private val HOT_RELOAD_JVM_ARGS = listOf(
    // Keep the command usable if it is ever launched on a non-JBR VM that lacks the flags below.
    "-XX:+IgnoreUnrecognizedVMOptions",
    // Enable DCEVM enhanced class redefinition (structural changes, not just method bodies).
    "-XX:+AllowEnhancedClassRedefinition",
    // Tell the JBR that an external `-javaagent` (HotSwapAgent) will drive redefinition, and open the
    // internal JDK modules it needs (notably `jdk.internal.loader`). Without this the agent cannot
    // read the app classloader's classpath: auto-discovery of watched dirs silently no-ops, and any
    // `extraClasspath` use crashes the VM at premain with an IllegalAccessError. This is the flag that
    // makes `autoHotswap` actually watch the compiled output.
    "-XX:HotswapAgent=external",
)

/** JBR is compatible up to Java 21; targeting a later version breaks enhanced redefinition. */
private const val JBR_JAVA_VERSION = 21

/**
 * Registers the [RUN_HOT_TASK_NAME] task: launches the Ktor server on the JetBrains Runtime with
 * enhanced class redefinition enabled and the HotSwapAgent attached, so recompiling while it runs
 * hot-swaps the changed classes into the live JVM.
 *
 * Iteration loop: run this task, edit code, save. The task forks a background continuous build
 * (`gradlew -t <path>:classes`) so edits are recompiled automatically; HotSwapAgent then detects the
 * changed `.class` files and the JBR redefines them in place. Edits inside existing handler bodies
 * apply immediately. Structural changes (new routes, application-scoped setup) additionally require
 * re-running the modules — set `-P$EMBEDDED_SERVER_ACCESSOR_PROPERTY` to enable that automatically via
 * a bundled custom HotSwapAgent plugin. Opt out of the forked recompile with `-P$AUTO_RECOMPILE_PROPERTY=false`.
 * If a change doesn't seem to reload, pass `-P$VERBOSE_PROPERTY=true` to see the full recompile
 * output and HotSwapAgent's own debug logging.
 */
internal fun Project.configureHotRun(extension: KtorExtension) {
    // The Application plugin and its `application { mainClass }` DSL are only set up for Kotlin/JVM
    // projects (see [configureApplication]); the hot-run task reuses that same configuration.
    whenKotlinJvmApplied {
        val mainClassProvider = application.mainClass
        val runtimeClasspath = extensions.getByType(SourceSetContainer::class.java)
            .named(SourceSet.MAIN_SOURCE_SET_NAME)
            .map { it.runtimeClasspath }
        val jbrLauncher = jetBrainsRuntimeLauncher()
        // FlowScope is only injectable, so obtain it through a tiny injected holder rather than
        // changing the plugin's public constructor.
        val flowScope = objects.newInstance(HotReloadServices::class.java).flowScope

        // Capture plain, serializable values at configuration time so the execution-time actions below
        // never touch `project` (keeps the task configuration-cache compatible).
        val autoRecompile = providers.gradleProperty(AUTO_RECOMPILE_PROPERTY)
            .map { it.toBoolean() }.getOrElse(true)
        val verbose = providers.gradleProperty(VERBOSE_PROPERTY)
            .map { it.toBoolean() }.getOrElse(false)
        val gradleRootDir = rootDir
        // Continuous build selects tasks from the root, so use the fully-qualified task path.
        val classesTaskPath = if (path == ":") ":$RECOMPILE_TASK_NAME" else "$path:$RECOMPILE_TASK_NAME"
        // Deliberately not under `build/ktor` (`ProjectLayout.ktorOutputDir`): `KtorGradlePlugin`
        // registers that directory as an extra `main` resources source dir for other features (e.g.
        // OpenAPI) to stage generated output for bundling. These files are `runHot`'s own private,
        // disposable scratch state (merged agent jar, recompiler pidfile) — bundling them into the
        // built jar/fatJar would be pure accidental bloat.
        val recompilerPidFile = layout.buildDirectory.file("$RUN_HOT_TASK_NAME/$RUN_HOT_TASK_NAME.recompiler.pid").get().asFile
        val hotswapWorkDir = layout.buildDirectory.dir(RUN_HOT_TASK_NAME).get().asFile
        val embeddedServerAccessor = providers.gradleProperty(EMBEDDED_SERVER_ACCESSOR_PROPERTY).orNull

        val runHot = tasks.registerKtorTask<JavaExec>(
            RUN_HOT_TASK_NAME,
            "Runs the Ktor server on the JetBrains Runtime with hot class redefinition for fast iteration.",
        ) {
            classpath(runtimeClasspath)
            mainClass.set(mainClassProvider)
            javaLauncher.set(jbrLauncher)
            jvmArgs(HOT_RELOAD_JVM_ARGS)
            // Created here (inside the task's own lazy configuration), not in the outer
            // `whenKotlinJvmApplied` scope, so the dependency/configuration it creates is only added to
            // the project when `runHot` is actually configured — never as a side effect of merely
            // applying `kotlin("jvm")`.
            val hotswapAgentJar = hotswapAgentClasspath()
            // Resolve the agent jar lazily (at execution time) so applying the plugin never forces
            // dependency resolution during configuration. `autoHotswap=true` is essential: without it
            // HotSwapAgent only reacts to redefinitions pushed by an attached debugger and never
            // watches the compiled output itself — the whole point of a debugger-free hot run.
            jvmArgumentProviders.add(CommandLineArgumentProvider {
                val agentJar = HotswapAgentJarMerger.merge(hotswapAgentJar.singleFile, hotswapWorkDir)
                val agentOptions = if (verbose) "$HOTSWAP_AGENT_OPTIONS,LOGGER=debug" else HOTSWAP_AGENT_OPTIONS
                listOf("-javaagent:${agentJar.absolutePath}=$agentOptions")
            })
            if (embeddedServerAccessor != null) {
                systemProperty(EMBEDDED_SERVER_ACCESSOR_PROPERTY, embeddedServerAccessor)
            }

            // Route the forked server JVM's own output through a line filter: strips HotSwapAgent's
            // hardcoded "HOTSWAP AGENT:" prefix (confirmed via decompiling `AgentLoggerHandler` —
            // there's no configuration hook for it, it's a literal `StringBuilder.append`) so its log
            // lines read like normal output, and watches for the KtorReload plugin's own "reload
            // finished" line to report how long the redefinition-to-reload round trip took. Wired via
            // `doFirst` (constructing everything fresh inside the closure, capturing nothing from the
            // outer scope) to stay configuration-cache compatible.
            doFirst {
                val forkedJvmLogger = Logging.getLogger(RUN_HOT_TASK_NAME)
                // Separate instances (not one shared stream) since Gradle pumps stdout/stderr on
                // different threads; each keeps its own line buffer so bytes from one never corrupt
                // a line being assembled from the other.
                standardOutput = LineSplittingOutputStream { line -> handleForkedJvmLogLine(line, forkedJvmLogger) }
                errorOutput = LineSplittingOutputStream { line -> handleForkedJvmLogLine(line, forkedJvmLogger) }
            }

            if (autoRecompile) {
                // Fork the continuous recompile just before the server starts.
                doFirst { HotRecompiler.start(gradleRootDir, classesTaskPath, recompilerPidFile, verbose) }
                // Stop it at build completion — crucially including Ctrl+C. `runHot` never *completes*
                // (the server blocks forever), so a `doLast` would rarely fire; a JVM shutdown hook only
                // fires under `--no-daemon`. A `FlowScope.always` action runs when the build finishes for
                // any reason, including graceful cancellation, so it's the one hook that reliably reaps
                // the recompiler. Registered inside the task config (not at plugin apply) so it only
                // arms when `runHot` is actually requested — otherwise an unrelated build's completion
                // would kill a recompiler owned by a `runHot` running in another terminal (shared pidfile).
                flowScope.always(StopHotRecompilerFlowAction::class.java) { spec ->
                    spec.parameters.pidFilePath.set(recompilerPidFile.absolutePath)
                }
            }
        }

    }
}

/**
 * Creates (once) a resolvable, non-transitive configuration holding the [HOTSWAP_AGENT_DEPENDENCY]
 * and returns its files as a lazy [FileCollection] — the single agent jar to pass to `-javaagent:`.
 */
private fun Project.hotswapAgentClasspath(): FileCollection {
    val agentDependency = dependencies.create(HOTSWAP_AGENT_DEPENDENCY)
    val configuration = configurations.maybeCreate(HOTSWAP_AGENT_CONFIGURATION_NAME).apply {
        isCanBeConsumed = false
        isCanBeResolved = true
        // The agent jar is fully shaded; keeping it non-transitive guarantees a single file to attach.
        isTransitive = false
        dependencies.add(agentDependency)
    }
    return configuration.incoming.files
}

/**
 * HotSwapAgent only scans for plugins under `org.hotswap.agent.plugin.*` on the classloader that
 * loaded the `-javaagent:` jar itself, so [HOTSWAP_AGENT_PLUGIN_RESOURCE]'s classes must physically
 * ship inside that same jar as the `hotswap-agent` dependency — they can't be passed separately.
 * [merge] builds that combined jar execution-time only, by copying the resolved agent jar
 * byte-for-byte (preserving its `META-INF/MANIFEST.MF`, which carries the `Premain-Class` attribute
 * the JVM needs at `-javaagent:` load time) and appending only the bundled plugin's own class entries.
 *
 * Always rewrites the output rather than reusing a previously merged jar found on disk: this bundled
 * plugin resource comes from a sibling module still under active development, so a merged jar left
 * over in the consuming project's build directory from before a `hotswap-agent-plugin` change would
 * otherwise be silently reused forever — a stale plugin loaded with no error, indistinguishable from
 * hot-swapping simply not working. The rewrite is a single in-process zip copy (a few MB), cheap
 * enough to redo on every `runHot` launch.
 */
internal object HotswapAgentJarMerger {
    private val logger = Logging.getLogger(HotswapAgentJarMerger::class.java)

    fun merge(agentJar: File, outputDir: File): File {
        val mergedJar = File(outputDir, "$RUN_HOT_TASK_NAME-agent.jar")
        outputDir.mkdirs()
        val pluginJar = javaClass.classLoader.getResourceAsStream(HOTSWAP_AGENT_PLUGIN_RESOURCE)
            ?: error("Bundled resource '$HOTSWAP_AGENT_PLUGIN_RESOURCE' not found on the classpath")

        ZipOutputStream(mergedJar.outputStream()).use { out ->
            agentJar.inputStream().use { copyEntries(it, out) { true } }
            pluginJar.use { copyEntries(it, out) { name -> name.startsWith("org/hotswap/agent/plugin/ktor/") } }
        }
        logger.info(ktorInfoLine("Merged HotSwapAgent and the KtorReload plugin into $mergedJar"))
        return mergedJar
    }

    /**
     * Copies directory entries too, not just class files: HotSwapAgent's plugin scanner resolves
     * `org/hotswap/agent/plugin` via `ClassLoader.getResources(...)`, which for a jar classpath
     * entry only succeeds if that path has an explicit directory entry in the zip's central
     * directory. Dropping directory entries silently breaks discovery of *every* plugin, built-in
     * ones included, not just the bundled one.
     */
    private fun copyEntries(input: java.io.InputStream, out: ZipOutputStream, include: (String) -> Boolean) {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (include(entry.name)) {
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.copyTo(out)
                    out.closeEntry()
                }
                entry = zip.nextEntry
            }
        }
    }
}

/**
 * Times the round trip the user actually cares about: from the continuous build detecting a saved
 * change to the KtorReload plugin (running inside the forked server JVM, see
 * `hotswap-agent-plugin`'s `ReloadCommand`) finishing the resulting reload. The two ends of this
 * span are observed in different OS processes — the recompiler subprocess and the forked server JVM
 * — so a shared, thread-safe timestamp is the simplest way to bridge them without IPC.
 */
private object ReloadTimer {
    private val changeDetectedAtNanos = AtomicLong(-1)

    fun markChangeDetected() {
        changeDetectedAtNanos.set(System.nanoTime())
    }

    /** Returns the elapsed time since the last [markChangeDetected], consuming it, or `null` if none is pending. */
    fun elapsedSinceChangeDetected(): Duration? {
        val start = changeDetectedAtNanos.getAndSet(-1)
        if (start < 0) return null
        return Duration.ofNanos(System.nanoTime() - start)
    }
}

private val KTOR_LOG_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/**
 * Formats a line we emit ourselves like the app's own default logback pattern
 * (`%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg`), so it reads consistently
 * alongside the forked JVM's own log output on the console instead of standing out as bare text.
 */
private fun ktorLogLine(level: String, message: String): String {
    val timestamp = LocalDateTime.now().format(KTOR_LOG_TIMESTAMP_FORMAT)
    return "$timestamp [${Thread.currentThread().name}] ${level.padEnd(5)} ktor - $message"
}

private fun ktorInfoLine(message: String) = ktorLogLine("INFO", message)
private fun ktorWarnLine(message: String) = ktorLogLine("WARN", message)

/** Matches HotSwapAgent's hardcoded `"HOTSWAP AGENT:" + timestamp + " " + message` line format. */
private val HOTSWAP_AGENT_PREFIX = Regex("""^HOTSWAP AGENT:\s*""")

/** The KtorReload plugin's own `ReloadCommand` success line (see `hotswap-agent-plugin`). */
private val RELOAD_FINISHED = Regex("""Reloaded \d+ Ktor EmbeddedServer instance\(s\) after class redefinition""")

/**
 * Processes one line of the forked server JVM's stdout/stderr: strips the "HOTSWAP AGENT:" prefix
 * so its log lines read like any other log output, then forwards it. When the line is the
 * KtorReload plugin's "reload finished" line, also reports how long that took since the matching
 * change was detected (see [ReloadTimer]).
 */
private fun handleForkedJvmLogLine(line: String, logger: Logger) {
    val stripped = line.replaceFirst(HOTSWAP_AGENT_PREFIX, "")
    logger.lifecycle(stripped)
    if (RELOAD_FINISHED.containsMatchIn(stripped)) {
        ReloadTimer.elapsedSinceChangeDetected()?.let { elapsed ->
            logger.lifecycle(ktorInfoLine("Reloaded in ${elapsed.toMillis()}ms"))
        }
    }
}

/**
 * Buffers written bytes into lines (splitting on `\n`, tolerating a preceding `\r`) and hands each
 * complete line to [onLine]. Not thread-safe across concurrent writers — callers piping both stdout
 * and stderr through this must use one instance per stream.
 */
private class LineSplittingOutputStream(private val onLine: (String) -> Unit) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    override fun write(b: Int) {
        if (b == '\n'.code) {
            onLine(buffer.toString(Charsets.UTF_8))
            buffer.reset()
        } else if (b != '\r'.code) {
            buffer.write(b)
        }
    }
}

/**
 * Manages the forked `gradlew -t <path>:classes` continuous build that keeps the compiled output
 * fresh while `runHot` is running. Stateless by design: the child process is tracked purely through a
 * pidfile so the task's execution-time actions stay configuration-cache compatible.
 *
 * Lifecycle:
 * - [start] kills any recompiler left over from a previous run (via the pidfile), launches a new one,
 *   records its pid, forwards its output to the run console, and registers a JVM shutdown hook.
 * - [stop] runs from [StopHotRecompilerFlowAction] when the build finishes — including Ctrl+C.
 * - Belt-and-suspenders: the shutdown hook covers `--no-daemon`, and [start]'s stale-kill reclaims any
 *   recompiler that somehow outlived its run, so it never accumulates beyond one stray process.
 */
private object HotRecompiler {
    private val logger = Logging.getLogger(HotRecompiler::class.java)

    fun start(rootDir: File, classesTaskPath: String, pidFile: File, verbose: Boolean) {
        stopStale(pidFile)

        val wrapper = File(rootDir, if (isWindows) "gradlew.bat" else "gradlew")
        if (!wrapper.isFile) {
            logger.warn(
                ktorWarnLine(
                    "Gradle wrapper not found at $wrapper; auto-recompile is off. " +
                        "Run `gradle -t $classesTaskPath` yourself, or pass -P$AUTO_RECOMPILE_PROPERTY=false."
                )
            )
            return
        }

        logger.lifecycle(ktorInfoLine("Auto-recompile: ${wrapper.name} -t $classesTaskPath"))
        val process = try {
            // `--no-configuration-cache` is required, not just an optimization: with the configuration
            // cache on, a continuous build reuses the cached input fingerprints across watch iterations,
            // so `compileKotlin` reports UP-TO-DATE after an edit ("Change detected" fires but nothing
            // recompiles) and the hot-swap never happens.
            //
            // `--no-build-cache` guards against a distinct but same-shaped failure: if an edit reverts
            // source back to something byte-identical to a previously-compiled state (e.g. undoing a
            // change), `compileKotlin` can be satisfied FROM-CACHE instead of actually invoking the
            // compiler. HotSwapAgent's watcher never sees the resulting no-op write to the `.class` file
            // as a change, so it never redefines anything — the build reports success, but the hot-swap
            // silently never happens.
            ProcessBuilder(
                wrapper.absolutePath, "-t", classesTaskPath,
                "--no-configuration-cache", "--no-build-cache", "--console=plain",
            )
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            logger.warn(ktorWarnLine("Failed to start continuous recompile: ${e.message}"))
            return
        }

        pidFile.parentFile?.mkdirs()
        pidFile.writeText(process.pid().toString())

        // A failed compile means no hot-swap, so its output must always surface. On the happy path
        // though, the raw continuous-build output (task graph, up-to-date checks, BUILD SUCCESSFUL
        // banners) is just noise, so by default only a concise "change detected" line is shown and
        // everything else is buffered and discarded unless the build actually fails.
        thread(isDaemon = true, name = "ktor-recompiler-output") {
            val pending = StringBuilder()
            process.inputStream.bufferedReader().forEachLine { line ->
                if (line.contains("Change detected, executing build")) {
                    ReloadTimer.markChangeDetected()
                }
                when {
                    verbose -> logger.lifecycle("[recompile] $line")
                    line.contains("Change detected, executing build") -> {
                        pending.setLength(0)
                        logger.lifecycle(ktorInfoLine("Change detected, recompiling..."))
                    }
                    line.contains("BUILD FAILED") -> {
                        pending.appendLine(line)
                        logger.lifecycle(pending.toString().trimEnd())
                        pending.setLength(0)
                    }
                    line.contains("BUILD SUCCESSFUL") -> pending.setLength(0)
                    else -> pending.appendLine(line)
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { destroyTree(process.toHandle()) })
    }

    fun stop(pidFile: File) {
        readPid(pidFile)?.let { pid -> ProcessHandle.of(pid).ifPresent(::destroyTree) }
        pidFile.delete()
    }

    private fun stopStale(pidFile: File) {
        val pid = readPid(pidFile) ?: return
        ProcessHandle.of(pid).ifPresent { handle ->
            logger.info(ktorInfoLine("Stopping stale recompiler (pid $pid) from a previous run"))
            destroyTree(handle)
        }
        pidFile.delete()
    }

    private fun readPid(pidFile: File): Long? =
        pidFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()

    /** Kill children first (the Gradle daemon client), then the wrapper process itself. */
    private fun destroyTree(handle: ProcessHandle) {
        handle.descendants().forEach { it.destroy() }
        handle.destroy()
    }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}

/** Injectable holder used solely to obtain the build's [FlowScope] without a public API change. */
internal abstract class HotReloadServices @Inject constructor(val flowScope: FlowScope)

/**
 * Dataflow action that stops the forked continuous recompiler when the `runHot` build finishes,
 * however it finishes (success, failure, or Ctrl+C cancellation). Runs outside task execution with no
 * access to `Project`, so it works purely from the pidfile path handed in via [Parameters].
 */
internal abstract class StopHotRecompilerFlowAction : FlowAction<StopHotRecompilerFlowAction.Parameters> {
    interface Parameters : FlowParameters {
        @get:Input
        val pidFilePath: Property<String>
    }

    override fun execute(parameters: Parameters) {
        HotRecompiler.stop(File(parameters.pidFilePath.get()))
    }
}

/**
 * Resolves a JetBrains Runtime launcher through Gradle's toolchain service.
 *
 * Gradle auto-detects JBR installations already on the machine. To let Gradle download one on
 * demand, apply the foojay toolchain resolver in the consuming build's `settings.gradle.kts`.
 */
private fun Project.jetBrainsRuntimeLauncher(): Provider<JavaLauncher> {
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    return toolchains.launcherFor {
        it.languageVersion.set(JavaLanguageVersion.of(JBR_JAVA_VERSION))
        it.vendor.set(JvmVendorSpec.JETBRAINS)
    }
}
