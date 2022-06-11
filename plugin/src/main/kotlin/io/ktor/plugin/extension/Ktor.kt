package io.ktor.plugin.extension

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import kotlin.reflect.KClass

const val KTOR_TASK_GROUP_NAME = "Ktor"

fun TaskContainer.registerKtorTask(
    name: String,
    description: String,
    configure: Task.() -> Unit = {}
): TaskProvider<Task> {
    return register(name) {
        it.configureKtorTask(description, configure)
    }
}

fun <T : Task> TaskContainer.registerKtorTask(
    name: String,
    description: String,
    clazz: KClass<T>,
    configure: T.() -> Unit = {}
): TaskProvider<T> {
    return register(name, clazz.java) {
        it.configureKtorTask(description, configure)
    }
}

private fun <T : Task> T.configureKtorTask(
    description: String,
    configure: T.() -> Unit
) {
    this.group = KTOR_TASK_GROUP_NAME
    this.description = description
    this.configure()
}

abstract class KtorExtension

private val Project.ktorExtension: KtorExtension
    get() = extensions.findByType(KtorExtension::class.java)
        ?: project.extensions.create("ktor", KtorExtension::class.java)

val Project.ktorExtensions: ExtensionContainer
    get() = (ktorExtension as ExtensionAware).extensions

inline fun <reified T : Any> Project.createKtorExtension(name: String, vararg constructorArguments: Any?): T =
    ktorExtensions.create(name, T::class.java, *constructorArguments)

inline fun <reified T : Any> Project.getKtorExtension(): T =
    ktorExtensions.getByType(T::class.java)