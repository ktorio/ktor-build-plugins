package io.ktor.compiler.services

import java.io.File

object KtorTestEnvironmentProperties {
    val testSamplesClasspath by lazy {
        System.getProperty("testSamples.classpath")
            ?.split(File.pathSeparator)?.map(::File)
            ?: error("Unable to get a valid classpath from the 'testSamples.classpath' property")
    }
    val testSamplesLocation by lazy {
        System.getProperty("testSamples.location")
            ?: error("'testSamples.location' is not set")
    }
}