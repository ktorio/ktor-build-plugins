package io.ktor.plugin.extension

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer

abstract class KtorExtension

private val Project.ktorExtension: KtorExtension
    get() = extensions.findByType(KtorExtension::class.java)
        ?: project.extensions.create("ktor", KtorExtension::class.java)

val Project.ktorExtensions: ExtensionContainer
    get() = (ktorExtension as ExtensionAware).extensions

inline fun <reified T : Any> Project.createKtorExtension(name: String): T =
    ktorExtensions.create(name, T::class.java)

inline fun <reified T : Any> Project.getKtorExtension(): T =
    ktorExtensions.getByType(T::class.java)