package io.ktor.plugin.internal

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project

internal inline fun <reified T : Plugin<*>> Project.apply() = pluginManager.apply(T::class.java)

internal inline fun <reified T : Any> DomainObjectCollection<in T>.withType(): DomainObjectCollection<T> {
    return withType(T::class.java)
}

internal inline fun <reified T : Any> DomainObjectCollection<in T>.configureEach(configure: Action<T>) {
    withType(T::class.java).configureEach(configure)
}
