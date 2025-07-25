package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorPluginTest {

    @Test
    fun works() {
//        val project = ProjectBuilder.builder().build()
//        project.applyPlugin {
//            getExtension<PluginManifestExtension>()
//        }
//
//        val task = project.tasks.named("buildKtorPluginManifest", BuildManifestTask::class.java).get()
//        val cause = assertFailsWith<GradleException> {
//            task.execute()
//        }
//
//        assertEquals(
//            "You're trying to build an image with JRE 11 while your project's JDK or " +
//                    "'java.targetCompatibility' is 17. Please use a higher version of an image JRE " +
//                    "through the 'ktor.docker.jreVersion' extension in the build file, or " +
//                    "set the 'java.targetCompatibility' property to a lower version.",
//            cause.message
//        )
    }

}