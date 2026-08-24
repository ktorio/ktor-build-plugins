package org.hotswap.agent.plugin.ktor

import org.hotswap.agent.command.Command
import org.hotswap.agent.logging.AgentLogger

/**
 * Reflectively resolves every running `EmbeddedServer` in [appClassLoader] and calls `reload()` on
 * each — re-running each server's configured modules. Purely reflective, with zero compile-time
 * dependency on `ktor-server-core`, so this plugin has no coupling to whatever Ktor version the
 * consuming project uses.
 *
 * The accessor is named via the `io.ktor.hotReload.embeddedServerAccessor` system property (format
 * `<fully.qualified.ClassName>#<staticAccessorMethodName>`), forwarded into the forked JVM by
 * ktor-build-plugins' `HotRun.kt`. It must name a no-arg static method returning a
 * `Collection<EmbeddedServer<*, *>>`, not a single instance: a process can host more than one
 * `EmbeddedServer`, and the eventual goal is for Ktor itself to maintain that collection
 * automatically (registering each instance on construction) rather than consumer code doing it by
 * hand — this plugin only needs *a* no-arg static accessor returning something iterable, so it
 * doesn't need to change again once that lands.
 *
 * A `data class`, not an incidental style choice: HotSwapAgent's [Scheduler] keys its pending-work
 * map on `Command.equals()`/`hashCode()` (confirmed via decompiling `SchedulerImpl`), so scheduling
 * several equal commands within the debounce window collapses them into a single map entry — one
 * execution — instead of one per redefined class in a batch. A plain class here (identity equality)
 * would make every `ReloadCommand` instance distinct and defeat `SKIP` entirely.
 */
internal data class ReloadCommand(private val appClassLoader: ClassLoader) : Command {

    override fun executeCommand() {
        val accessor = System.getProperty(EMBEDDED_SERVER_ACCESSOR_PROPERTY)
            ?: "io.ktor.server.engine.EmbeddedServerKt#embeddedServerInstances"

        val separatorIndex = accessor.indexOf('#')
        if (separatorIndex < 0) {
            LOG.error("Invalid -D$EMBEDDED_SERVER_ACCESSOR_PROPERTY value '$accessor': expected '<FQN>#<methodName>'")
            return
        }
        val className = accessor.substring(0, separatorIndex)
        val methodName = accessor.substring(separatorIndex + 1)

        val embeddedServers = try {
            appClassLoader.loadClass(className).getMethod(methodName).invoke(null)
        } catch (e: Exception) {
            LOG.error("Failed to invoke $accessor to look up running Ktor EmbeddedServer instances", e)
            return
        }

        if (embeddedServers !is Iterable<*>) {
            val actualType = embeddedServers?.javaClass?.name ?: "null"
            LOG.error("$accessor returned $actualType; expected a Collection<EmbeddedServer<*, *>>")
            return
        }

        var reloaded = 0
        for (embeddedServer in embeddedServers) {
            if (embeddedServer == null) continue
            try {
                embeddedServer.javaClass.getMethod("reload").invoke(embeddedServer)
                reloaded++
            } catch (e: Exception) {
                LOG.error("Failed to reload a Ktor EmbeddedServer via $accessor", e)
            }
        }
        LOG.info("Reloaded $reloaded Ktor EmbeddedServer instance(s) after class redefinition")
    }

    companion object {
        const val EMBEDDED_SERVER_ACCESSOR_PROPERTY = "io.ktor.hotReload.embeddedServerAccessor"
        private val LOG = AgentLogger.getLogger(ReloadCommand::class.java)
    }
}
