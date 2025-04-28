package io.ktor.plugin.internal

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.plugins.PluginContainer

internal inline fun <reified T : Plugin<*>> PluginContainer.apply(): T = apply(T::class.java)

internal inline fun <reified T> DomainObjectCollection<in T>.withType(): DomainObjectCollection<T> {
    return withType(T::class.java)
}

internal inline fun <reified T> DomainObjectCollection<in T>.configureEach(configure: Action<T>) {
    withType(T::class.java).configureEach(configure)
}
