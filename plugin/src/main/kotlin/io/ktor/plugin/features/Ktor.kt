package io.ktor.plugin.features

import io.ktor.plugin.*
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

@Deprecated(
    "Use KtorGradlePlugin.TASK_GROUP instead",
    ReplaceWith(
        "KtorGradlePlugin.TASK_GROUP",
        "io.ktor.plugin.KtorGradlePlugin",
    )
)
public const val KTOR_TASK_GROUP_NAME: String = KtorGradlePlugin.TASK_GROUP

internal inline fun TaskContainer.registerKtorTask(
    name: String,
    description: String,
    crossinline configure: Task.() -> Unit = {}
): TaskProvider<DefaultTask> = registerKtorTask<DefaultTask>(
    name = name,
    description = description,
    configure = configure
)

internal inline fun <reified T : Task> TaskContainer.registerKtorTask(
    name: String,
    description: String,
    vararg constructorArgs: Any,
    crossinline configure: T.() -> Unit = {}
): TaskProvider<T> = register(name, T::class.java, *constructorArgs).also { taskProvider ->
    taskProvider.configure { task ->
        task.group = KtorGradlePlugin.TASK_GROUP
        task.description = description
        task.configure()
    }
}

public abstract class KtorExtension @Inject internal constructor(
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
    public val development: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(providers.gradleProperty(DEVELOPMENT_MODE_PROPERTY).toBoolean())
        .convention(providers.systemProperty(DEVELOPMENT_MODE_PROPERTY).toBoolean())
        .finalizedOnRead()

    public companion object {
        public const val NAME: String = "ktor"

        internal const val DEVELOPMENT_MODE_PROPERTY = "io.ktor.development"
    }
}

private val Project.ktorExtension: KtorExtension
    get() = extensions.getByType(KtorExtension::class.java)

internal inline fun <reified T: Any> Any.getExtension(): T =
    (this as ExtensionAware).extensions.getByType(T::class.java)

internal val Project.ktorExtensions: ExtensionContainer
    get() = (ktorExtension as ExtensionAware).extensions

internal inline fun <reified T: Any> Project.createKtorExtension(name: String): T =
    ktorExtensions.create(name, T::class.java, project)

internal inline fun <reified T: Any> Project.getKtorExtension(): T =
    ktorExtensions.getByType(T::class.java)

internal inline fun <reified T : Any> Project.property(defaultValue: T?): Property<T> = objects.property<T>(defaultValue)

internal inline fun <reified T : Any> ObjectFactory.property(defaultValue: T?): Property<T> =
    property(T::class.java).convention(defaultValue)

internal val Project.javaVersion: JavaVersion get() = extensions.getByType(JavaPluginExtension::class.java).targetCompatibility
