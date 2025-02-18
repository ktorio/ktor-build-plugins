package io.ktor.plugin.internal

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal fun <T> Property<T>.finalizedOnRead(): Property<T> = apply { finalizeValueOnRead() }

internal fun Provider<String>.toBoolean(): Provider<Boolean> = map { it.toBoolean() }.orElse(false)
