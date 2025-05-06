package io.ktor.plugin

import com.google.cloud.tools.jib.gradle.JibExtension
import io.ktor.plugin.features.*
import io.ktor.plugin.features.DockerImageRegistry.Companion.externalRegistry
import io.mockk.mockkStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.internal.provider.Providers
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.util.GradleVersion
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.*

class DockerTest {
    private val project = createProject()

    @Test
    fun `cannot build an image when the target java version is greater than the image's jre version`() {
        project.applyKtorPlugin {
            getExtension<DockerExtension>().jreVersion.set(JavaVersion.VERSION_11)
        }
        project.extensions.configure(JavaPluginExtension::class.java) {
            it.targetCompatibility = JavaVersion.VERSION_17
        }

        val task = project.tasks.named("setupJibLocal", ConfigureJibLocalTask::class.java).get()
        val cause = assertFailsWith<GradleException> {
            task.execute()
        }

        assertEquals(
            "You're trying to build an image with JRE 11 while your project's JDK or " +
                "'java.targetCompatibility' is 17. Please use a higher version of an image JRE " +
                "through the 'ktor.docker.jreVersion' extension in the build file, or " +
                "set the 'java.targetCompatibility' property to a lower version.",
            cause.message
        )
    }

    @Test
    fun `name of the source image considers set image's jre version`() {
        project.applyKtorPlugin {
            getExtension<DockerExtension>().jreVersion.set(JavaVersion.VERSION_17)
        }

        val task = project.tasks.named("setupJibLocal", ConfigureJibLocalTask::class.java).get()
        task.execute()

        val jibException = project.extensions.getByType(JibExtension::class.java)
        assertEquals("eclipse-temurin:17-jre", jibException.from.image)
    }


    @Test
    fun `name of the target image has a default value`() {
        project.applyKtorPlugin()

        val task = project.tasks.named("setupJibLocal", ConfigureJibLocalTask::class.java).get()
        task.execute()
        val jibException = project.extensions.getByType(JibExtension::class.java)
        assertEquals("ktor-docker-image", jibException.to.image)
    }
    @Test
    fun `name of the target image is determined by the docker extension`() {
        project.applyKtorPlugin {
            getExtension<DockerExtension>().localImageName.set("target-image")
        }

        val task = project.tasks.named("setupJibLocal", ConfigureJibLocalTask::class.java).get()
        task.execute()
        val jibException = project.extensions.getByType(JibExtension::class.java)
        assertEquals("target-image", jibException.to.image)
    }

    @Test
    fun `docker extension configures target image name and registry auth`() {
        project.applyKtorPlugin {
            getExtension<DockerExtension>().apply {
                externalRegistry.set(
                    externalRegistry(
                        username = Providers.of("username"),
                        password = Providers.of("password"),
                        hostname = Providers.of("localhost"),
                        namespace = Providers.of("ns"),
                        project = Providers.of("project")
                    )
                )
            }
        }

        val task = project.tasks.named("setupJibExternal", ConfigureJibExternalTask::class.java).get()
        task.execute()
        val jibException = project.extensions.getByType(JibExtension::class.java)
        assertEquals("localhost/ns/project", jibException.to.image)
        assertEquals("username", jibException.to.auth.username)
        assertEquals("password", jibException.to.auth.password)
    }

    @Test
    fun `docker extension configures tag of the target image`() {
        project.applyKtorPlugin {
            getExtension<DockerExtension>().imageTag.set("1.2.3")
        }

        val task = project.tasks.named("setupJibLocal", ConfigureJibLocalTask::class.java).get()
        task.execute()
        val jibException = project.extensions.getByType(JibExtension::class.java)
        assertEquals(listOf("1.2.3"), jibException.to.tags.toList())
    }

    @Test
    fun `docker extension adds a tag to the target image tags`() {
        project.applyKtorPlugin {
            getExtension<DockerExtension>().imageTag.set("1.2.3")
        }

        project.extensions.configure(JibExtension::class.java) { ext ->
            ext.to.setTags(listOf("latest"))
        }

        val task = project.tasks.named("setupJibLocal", ConfigureJibLocalTask::class.java).get()
        task.execute()
        val jibException = project.extensions.getByType(JibExtension::class.java)
        assertEquals(listOf("latest", "1.2.3"), jibException.to.tags.toList())
    }

    @Test
    fun `jib tasks are marked as not compatible with configuration cache`() {
        mockkStatic(GradleVersion::class) {
            project.applyKtorPlugin()

            val jibTask = project.tasks.named("jib", DefaultTask::class.java).get()
            assertFalse { jibTask.isCompatibleWithConfigurationCache }
            assertEquals(
                "JIB plugin is not compatible with the configuration cache. See https://github.com/GoogleContainerTools/jib/issues/3132",
                jibTask.reasonTaskIsIncompatibleWithConfigurationCache.get()
            )
        }
    }

    @ParameterizedTest
    @MethodSource("dataForTestingTasks")
    fun `has a task that depends on jib tasks`(data: Pair<String, List<String>>) {
        val (taskName, dependentTasks) = data
        project.applyKtorPlugin()

        val task = project.tasks.named(taskName).get()
        assertEquals("Ktor", task.group)
        assertFalse { task.description.isNullOrEmpty() }

        val dependencies = task.taskDependencies.getDependencies(task)
        assertEquals(2, dependencies.size)
        assertTrue { dependencies.contains(project.tasks.named(dependentTasks[0]).get()) }
        assertTrue { dependencies.contains(project.tasks.named(dependentTasks[1]).get()) }
    }

    companion object {
        @JvmStatic
        fun dataForTestingTasks() = listOf (
            "runDocker" to listOf("jibDockerBuild", "setupJibLocal"),
            "publishImageToLocalRegistry" to listOf("jibDockerBuild", "setupJibLocal"),
            "publishImage" to listOf("setupJibExternal", "jib"),
            "buildImage" to listOf("setupJibLocal", "jibBuildTar"),
        )
    }
}
