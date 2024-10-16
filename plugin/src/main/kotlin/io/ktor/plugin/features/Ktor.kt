package io.ktor.plugin.features

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

const val KTOR_TASK_GROUP_NAME = "Ktor"

inline fun TaskContainer.registerKtorTask(
    name: String,
    description: String,
    crossinline configure: Task.() -> Unit = {}
): TaskProvider<DefaultTask> = registerKtorTask<DefaultTask>(
    name = name,
    description = description,
    configure = configure
)

inline fun <reified T : Task> TaskContainer.registerKtorTask(
    name: String,
    description: String,
    vararg constructorArgs: Any?,
    crossinline configure: T.() -> Unit = {}
): TaskProvider<T> = register(name, T::class.java, *constructorArgs).also { taskProvider ->
    taskProvider.configure { task ->
        task.group = KTOR_TASK_GROUP_NAME
        task.description = description
        task.configure()
    }
}

abstract class KtorExtension

private val Project.ktorExtension: KtorExtension
    get() = extensions.findByType(KtorExtension::class.java)
        ?: project.extensions.create("ktor", KtorExtension::class.java)

inline fun <reified T> Any.getExtension(): T =
    (this as ExtensionAware).extensions.getByType(T::class.java)

val Project.ktorExtensions: ExtensionContainer
    get() = (ktorExtension as ExtensionAware).extensions

inline fun <reified T> Project.createKtorExtension(name: String): T =
    ktorExtensions.create(name, T::class.java, project)

inline fun <reified T> Project.getKtorExtension(): T =
    ktorExtensions.getByType(T::class.java)

inline fun <reified T> Project.property(defaultValue: T?): Property<T> =
    objects.property(T::class.java).convention(defaultValue)

val Project.javaVersion: JavaVersion get() = extensions.getByType(JavaPluginExtension::class.java).targetCompatibility
