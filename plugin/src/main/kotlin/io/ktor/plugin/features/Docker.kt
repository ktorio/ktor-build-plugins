package io.ktor.plugin.features

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import com.google.cloud.tools.jib.gradle.JibTask
import com.google.cloud.tools.jib.gradle.TargetImageParameters
import io.ktor.plugin.internal.*
import org.gradle.api.*
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

public enum class DockerPortMappingProtocol {
    TCP, UDP
}

public data class DockerPortMapping(
    val outsideDocker: Int,
    val insideDocker: Int = outsideDocker,
    val protocol: DockerPortMappingProtocol = DockerPortMappingProtocol.TCP
)

public data class DockerEnvironmentVariable(
    val variable: String,
    val value: String? = null
)

public abstract class DockerExtension internal constructor(project: Project) {

    /**
     * Specifies the JRE version to use in the image. Defaults to [JavaVersion.VERSION_21].
     */
    public val jreVersion: Property<JavaVersion> = project.property(defaultValue = JavaVersion.VERSION_21)

    /**
     * Specifies a tag to use in the image. Defaults to `"latest"`.
     */
    public val imageTag: Property<String> = project.property(defaultValue = "latest")

    /**
     * Specifies an image name for local builds. Defaults to `"ktor-docker-image"`.
     */
    public val localImageName: Property<String> = project.property(defaultValue = "ktor-docker-image")

    /**
     * Specifies a custom base image to use in the image. Defaults to `null`.
     */
    public val customBaseImage: Property<String> = project.property<String>(defaultValue = null)

    /**
     * Specifies an external registry to push the image into. Default is not set.
     */
    public val externalRegistry: Property<DockerImageRegistry> =
        project.property<DockerImageRegistry>(defaultValue = null)

    /**
     * Specifies an image name in form `"imageName:tag"` for a local registry.
     */
    public val fullLocalImageName: Provider<String> = localImageName.zipWithTag(imageTag)

    /**
     * Specifies an image name in form `"imageName:tag"` for an external registry.
     */
    public val fullExternalImageName: Provider<String> = externalRegistry.flatMap { it.toImage }.zipWithTag(imageTag)

    /**
     * Specifies port mappings for a `runDocker` command.
     */
    public val portMappings: ListProperty<DockerPortMapping> =
        project.objects.listProperty(DockerPortMapping::class.java)
            .convention(listOf(DockerPortMapping(8080, 8080, DockerPortMappingProtocol.TCP)))

    /**
     * Specifies environment variable mappings for a `runDocker` command.
     */
    public val environmentVariables: ListProperty<DockerEnvironmentVariable> =
        project.objects.listProperty(DockerEnvironmentVariable::class.java).convention(emptyList())

    public fun environmentVariable(name: String, value: String) {
        environmentVariables.add(DockerEnvironmentVariable(name, value))
    }

    public companion object {
        public const val NAME: String = "docker"

        private fun Provider<String>.zipWithTag(tag: Provider<String>): Provider<String> =
            zip(tag) { imageName, imageTag ->
                "$imageName:$imageTag"
            }
    }
}

public interface DockerImageRegistry {
    /**
     * Specifies a link for [JibExtension.to.image][TargetImageParameters.image].
     */
    public val toImage: Provider<String>

    /**
     * Specifies a username for a given registry.
     */
    public val username: Provider<String>

    /**
     * Specifies a password for a given registry.
     */
    public val password: Provider<String>

    public companion object {
        /**
         * Creates a [DockerImageRegistry] for [DockerHub](https://hub.docker.com/)
         * from a given [appName], [username] and [password].
         */
        @JvmStatic
        public fun dockerHub(
            appName: Provider<String>,
            username: Provider<String>,
            password: Provider<String>
        ): DockerImageRegistry = DockerHubRegistry(appName, username, password)

        /**
         * Creates a [DockerImageRegistry] from a given [project], [username] and [password],
         * and an optional [hostname] and [namespace].
         *
         * The [hostname], [namespace], and [project] are combined in order to generate
         * the full image name, e.g.:
         * - hostname/namespace/project
         * - hostname/project
         * - project
         */
        @JvmStatic
        public fun externalRegistry(
            username: Provider<String>,
            password: Provider<String>,
            project: Provider<String>,
            hostname: Provider<String>? = null,
            namespace: Provider<String>? = null,
        ): DockerImageRegistry = ExternalRegistry(username, password, project, hostname, namespace)

        /**
         * Creates a [DockerImageRegistry] for [Google Container Registry](https://cloud.google.com/container-registry)
         * from a given [appName] and [username].
         */
        @JvmStatic
        public fun googleContainerRegistry(
            projectName: Provider<String>,
            appName: Provider<String>,
            username: Provider<String>,
            password: Provider<String>
        ): DockerImageRegistry = GoogleContainerRegistry(projectName, appName, username, password)
    }
}

private class DockerHubRegistry(
    appName: Provider<String>,
    override val username: Provider<String>,
    override val password: Provider<String>
) : DockerImageRegistry {
    override val toImage: Provider<String> = username.zip(appName) { user, app -> "$user/$app" }
}

private class ExternalRegistry(
    override val username: Provider<String>,
    override val password: Provider<String>,
    project: Provider<String>,
    hostname: Provider<String>? = null,
    namespace: Provider<String>? = null,
) : DockerImageRegistry {
    override val toImage: Provider<String> = project.map { project ->
        hostname?.let { "${it.get()}/" }.orEmpty()
            .plus(namespace?.let { "${it.get()}/" }.orEmpty())
            .plus(project)
    }
}

private class GoogleContainerRegistry(
    projectName: Provider<String>,
    appName: Provider<String>,
    override val username: Provider<String>,
    override val password: Provider<String>
) : DockerImageRegistry {
    override val toImage: Provider<String> = projectName.zip(appName) { project, app -> "gcr.io/$project/$app" }
}

// JIB related tasks
private const val JIB_BUILD_INTO_LOCAL_DOCKER_TASK_NAME = JibPlugin.BUILD_DOCKER_TASK_NAME
private const val JIB_BUILD_INTO_TAR_TASK_NAME = JibPlugin.BUILD_TAR_TASK_NAME
private const val JIB_BUILD_IMAGE_AND_PUBLISH_TASK_NAME = JibPlugin.BUILD_IMAGE_TASK_NAME

// Ktor related tasks
public const val PUBLISH_IMAGE_TO_LOCAL_REGISTRY_TASK_NAME: String = "publishImageToLocalRegistry"
public const val PUBLISH_IMAGE_TO_EXTERNAL_REGISTRY_TASK_NAME: String = "publishImage"
public const val BUILD_IMAGE_TASK_NAME: String = "buildImage"
public const val RUN_DOCKER_TASK_NAME: String = "runDocker"

// Ktor configuration tasks
private const val SETUP_JIB_LOCAL_TASK_NAME = "setupJibLocal"
private const val SETUP_JIB_EXTERNAL_TASK_NAME = "setupJibExternal"

private fun markJibTaskNotCompatible(task: Task) {
    task.notCompatibleWithConfigurationCache(
        "JIB plugin is not compatible with the configuration cache. " +
                "See https://github.com/GoogleContainerTools/jib/issues/3132"
    )
}

internal abstract class ConfigureJibTaskBase(@get:Input val isExternal: Boolean) : DefaultTask() {
    init {
        @Suppress("LeakingThis")
        markJibTaskNotCompatible(this)
    }

    @TaskAction
    fun execute() {
        val jibExtension = project.extensions.getByType(JibExtension::class.java)
        val dockerExtension = project.getKtorExtension<DockerExtension>()

        val baseImage = dockerExtension.customBaseImage
            .orElse(dockerExtension.jreVersion.map { "eclipse-temurin:${it.majorVersion}-jre" })
        jibExtension.from.setImage(baseImage)

        if (isExternal) {
            val externalRegistry = dockerExtension.externalRegistry
            jibExtension.to.setImage(dockerExtension.externalRegistry.get().toImage)
            jibExtension.to.auth.setUsername(externalRegistry.flatMap { it.username })
            jibExtension.to.auth.setPassword(externalRegistry.flatMap { it.password })
        } else {
            jibExtension.to.setImage(dockerExtension.localImageName)
        }

        val tag: String = dockerExtension.imageTag.get()
        jibExtension.to.tags = jibExtension.to.tags + tag

        val projectJava = project.javaVersion
        val imageJava = dockerExtension.jreVersion.get()
        if (imageJava < projectJava) {
            throw GradleException(
                "You're trying to build an image with JRE $imageJava while your project's JDK or 'java.targetCompatibility' is $projectJava. " +
                        "Please use a higher version of an image JRE through the 'ktor.docker.jreVersion' extension in the build file, " +
                        "or set the 'java.targetCompatibility' property to a lower version."
            )
        }
    }
}

internal abstract class ConfigureJibLocalTask : ConfigureJibTaskBase(isExternal = false)

internal abstract class ConfigureJibExternalTask : ConfigureJibTaskBase(isExternal = true)

private abstract class RunDockerTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val fullImageName: Property<String>

    @TaskAction
    fun execute() {
        val dockerExtension = project.getKtorExtension<DockerExtension>()
        execOperations.exec {
            it.commandLine(buildList {
                add("docker")
                add("run")
                for (portMapping in dockerExtension.portMappings.get()) {
                    add("-p")
                    with(portMapping) {
                        add("${outsideDocker}:${insideDocker}/${protocol.name.lowercase()}")
                    }
                }
                for (environmentVariable in dockerExtension.environmentVariables.get()) {
                    add("-e")
                    with(environmentVariable) {
                        if (value != null) {
                            add("${environmentVariable.variable}=${environmentVariable.value}")
                        } else {
                            add(environmentVariable.variable)
                        }
                    }
                }
                add(fullImageName.get())
            })
        }
    }
}

internal fun Project.configureDocker() {
    val dockerExtension = createKtorExtension<DockerExtension>(DockerExtension.NAME)

    // Apply JIB plugin only when the Kotlin JVM plugin is applied.
    // TODO: JIB uses hardcoded "main" source set, it makes it incompatible with KMP
    //   https://github.com/GoogleContainerTools/jib/issues/4316
    whenKotlinJvmApplied {
        apply<JibPlugin>()
    }

    // By using `withPlugin` we handle the case when a user explicitly applies JIB plugin in a KMP project.
    pluginManager.withPlugin(JIB_PLUGIN_ID) {
        configureJibPlugin(dockerExtension)
    }
}

private fun Project.configureJibPlugin(dockerExtension: DockerExtension) {
    tasks.configureEach<JibTask> { markJibTaskNotCompatible(it) }

    val configureJibLocalTask = tasks.register(SETUP_JIB_LOCAL_TASK_NAME, ConfigureJibLocalTask::class.java)
    val configureJibExternalTask = tasks.register(SETUP_JIB_EXTERNAL_TASK_NAME, ConfigureJibExternalTask::class.java)
    val configureJibTasks = arrayOf(configureJibLocalTask, configureJibExternalTask)

    val jibBuildIntoLocalDockerTask = tasks.named(JIB_BUILD_INTO_LOCAL_DOCKER_TASK_NAME)
    val jibBuildIntoTarTask = tasks.named(JIB_BUILD_INTO_TAR_TASK_NAME)
    val jibBuildImageAndPublishTask = tasks.named(JIB_BUILD_IMAGE_AND_PUBLISH_TASK_NAME)
    val jibTasks = arrayOf(jibBuildIntoLocalDockerTask, jibBuildIntoTarTask, jibBuildImageAndPublishTask)

    for (jibTask in jibTasks) {
        jibTask.configure {
            it.mustRunAfter(*configureJibTasks)
        }
    }

    tasks.registerKtorTask<RunDockerTask>(
        RUN_DOCKER_TASK_NAME,
        "Builds a project's image to a Docker daemon and runs it."
    ) {
        fullImageName.set(dockerExtension.fullLocalImageName)
        dependsOn(
            configureJibLocalTask,
            jibBuildIntoLocalDockerTask
        )
    }

    tasks.registerKtorTask(
        PUBLISH_IMAGE_TO_LOCAL_REGISTRY_TASK_NAME,
        "Builds and publishes a project's Docker image to a local registry."
    ) {
        dependsOn(
            configureJibLocalTask,
            jibBuildIntoLocalDockerTask
        )
    }

    tasks.registerKtorTask(
        PUBLISH_IMAGE_TO_EXTERNAL_REGISTRY_TASK_NAME,
        "Builds and publishes a project's Docker image to an external registry."
    ) {
        dependsOn(
            configureJibExternalTask,
            jibBuildImageAndPublishTask
        )
    }

    tasks.registerKtorTask(
        BUILD_IMAGE_TASK_NAME,
        "Builds a project's Docker image to a tarball."
    ) {
        dependsOn(
            configureJibLocalTask,
            jibBuildIntoTarTask
        )
    }
}
