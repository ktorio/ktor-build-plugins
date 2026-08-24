package org.hotswap.agent.plugin.ktor

import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.command.Scheduler
import org.hotswap.agent.config.PluginConfiguration
import org.hotswap.agent.util.PluginManagerInvoker

/**
 * Reloads a Ktor `EmbeddedServer`'s application modules after HotSwapAgent redefines classes, so
 * structural changes (new routes, application-scoped setup) take effect without a restart —
 * complementing HotSwapAgent's own in-place method-body redefinition.
 *
 * HotSwapAgent's plugin scanner only looks under the fixed `org.hotswap.agent.plugin` package on the
 * `-javaagent:` jar's own classloader, so this class must ship inside that same jar (see the runtime
 * jar merge in ktor-build-plugins' `HotRun.kt`, which combines this module's compiled output with the
 * `hotswap-agent` dependency jar before passing it to `-javaagent:`).
 *
 * Mirrors the structure of HotSwapAgent's own built-in `HotswapperPlugin` (the plugin behind
 * `autoHotswap=true`), which is the only confirmed-working template for a plugin — like this one —
 * that applies to every classloader rather than a specific framework's.
 */
@Plugin(
    name = "KtorReload",
    description = "Reloads a Ktor EmbeddedServer's application modules after a class redefinition.",
    testedVersions = ["2.0.3"],
)
class KtorReloadPlugin {

    @Init
    lateinit var scheduler: Scheduler

    /**
     * Fires once per redefined class, synchronously within the same `redefineClasses` call that
     * HotSwapAgent's own hotswapper plugin triggers. [Scheduler] runs on a single background thread
     * shared with that hotswapper command, so scheduling here is guaranteed to run strictly after the
     * enclosing redefinition batch completes — no debounce window is needed for correctness, only to
     * coalesce a burst of many classes into a single reload.
     *
     * [ReloadCommand] equality depends only on the (effectively constant) app classloader, so every
     * command scheduled here is "equal" to every other one. [Scheduler] treats an already-`SKIP`ped
     * duplicate as "identical work already queued/running, drop this one" — safe while the duplicate
     * is merely *pending* (still coalescing a burst), but `reload()` itself can take seconds (it
     * re-runs the app's modules), so a later edit's redefinition can land while the previous reload is
     * still *executing*. `SKIP` would then discard that reload permanently, with nothing to retry it,
     * leaving the live server on stale routes/modules even though the class bytes were redefined.
     * `WAIT_AND_RUN_AFTER` queues behind the in-flight reload instead of dropping the new one.
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
    fun onRedefine(appClassLoader: ClassLoader) {
        scheduler.scheduleCommand(
            ReloadCommand(appClassLoader),
            RELOAD_DEBOUNCE_MS,
            Scheduler.DuplicateSheduleBehaviour.WAIT_AND_RUN_AFTER,
        )
    }

    companion object {
        private const val RELOAD_DEBOUNCE_MS = 300

        @Init
        @JvmStatic
        fun init(pluginConfiguration: PluginConfiguration, appClassLoader: ClassLoader?) {
            // Because onRedefine's classNameRegexp is ".*", HotswapTransformer initializes this plugin
            // for every classloader that loads any class — including the bootstrap classloader (which
            // Java/HotSwapAgent represents as a null ClassLoader) as the very first one encountered.
            // PluginRegistry.initializePlugin rejects a null appClassLoader outright, and no Ktor
            // EmbeddedServer can be running on the bootstrap classloader anyway, so just skip it.
            if (appClassLoader == null) return
            PluginManagerInvoker.callInitializePlugin(KtorReloadPlugin::class.java, appClassLoader)
        }
    }
}
