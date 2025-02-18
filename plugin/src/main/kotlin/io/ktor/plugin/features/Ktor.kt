package io.ktor.plugin.features

import io.ktor.plugin.internal.*
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

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

abstract class KtorExtension @Inject internal constructor(
    objects: ObjectFactory,
    providers: ProviderFactory,
) {

    /**
     * Enables a special mode targeted for development.
     *
     * By default, development mode is enabled if Gradle property or system property with the name "io.ktor.development"
     * is set to `true`.
     *
     * [Documentation](https://ktor.io/docs/server-development-mode.html)
     */
    val development: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(providers.gradleProperty(DEVELOPMENT_MODE_PROPERTY).toBoolean())
        .convention(providers.systemProperty(DEVELOPMENT_MODE_PROPERTY).toBoolean())
        .finalizedOnRead()

    companion object {
        const val NAME: String = "ktor"

        internal const val DEVELOPMENT_MODE_PROPERTY = "io.ktor.development"
    }
}

private val Project.ktorExtension: KtorExtension
    get() = extensions.getByType(KtorExtension::class.java)

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
