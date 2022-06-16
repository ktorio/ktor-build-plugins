package io.ktor.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun createGradleRunner(projectDir: File): GradleRunner =
    GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()

fun assertZipFilesEqual(expected: ZipFile, actual: ZipFile) {
    val expectedEntries = expected.entries().toList().sortedBy { it.name }
    val actualEntries = actual.entries().toList().sortedBy { it.name }
    assertEquals(expectedEntries.size, actualEntries.size, "Zip files have different number of entries")

    val expectedNames = expectedEntries.map { it.name }
    val actualNames = actualEntries.map { it.name }
    assertEquals(expectedNames, actualNames)

    for ((expectedEntry, actualEntry) in expectedEntries.zip(actualEntries)) {
        if (expectedEntry.isDirectory) {
            assertTrue(actualEntry.isDirectory, "Entry ${actualEntry.name} is not a directory")
        } else {
            assertFalse(actualEntry.isDirectory, "Entry ${actualEntry.name} is a directory")

            assertEquals(
                expectedEntry.size,
                actualEntry.size,
                "Size of entry ${expectedEntry.name} is different (expected: ${expectedEntry.size}, actual: ${actualEntry.size})"
            )

            val expectedContent = expected.getInputStream(expectedEntry).readBytes()
            val actualContent = actual.getInputStream(actualEntry).readBytes()
            assertContentEquals(expectedContent, actualContent, "Content of entry ${actualEntry.name} is different")
        }
    }
}

fun buildProject(
    projectDir: File,
    buildGradleKtsContent: String,
    settingsGradleKtsContent: String,
    mainKtContent: String,
    buildCommand: String,
    expectSuccess: Boolean = true
): BuildResult {
    projectDir.resolve("build.gradle.kts").writeText(buildGradleKtsContent)
    projectDir.resolve("settings.gradle.kts").writeText(settingsGradleKtsContent)
    projectDir
        .resolve("src/main/kotlin/my/org")
        .also { it.mkdirs() }
        .resolve("Main.kt")
        .writeText(mainKtContent)

    return createGradleRunner(projectDir).withArguments(buildCommand).run {
        if (expectSuccess) {
            build()
        } else {
            buildAndFail()
        }
    }
}
