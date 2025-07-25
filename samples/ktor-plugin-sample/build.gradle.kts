import io.ktor.plugin.features.*

plugins {
    id(libs.plugins.ktor.get().pluginId)
}

repositories {
    mavenLocal()
    mavenCentral()
}

application.mainClass.set("io.ktor.samples.plugin.ApplicationKt")

ktor {
    plugin {
        id = "ktor-gradle-plugin-sample"
        name = "Sample Plugin"
        description = "Shows how to publish your Ktor plugin"
        vcsLink = "https://test-123.abc"
        copyright = StandardLicenses.AGPL
        category = PluginCategory.SERIALIZATION
        prerequisites = listOf("websockets")
        documentation {
            description = """
                This is a longer description you can use for your plugin. It will
                appear in the right panel of the project generator.  You may use
                markdown here.
            """.trimIndent()
        }
    }
}

dependencies {
    implementation("io.ktor:ktor-plugin:3.0.0-SNAPSHOT")
    implementation("io.ktor:ktor-server-core:3.0.0-SNAPSHOT")
    implementation("io.ktor:ktor-server-websockets-jvm:3.0.0-SNAPSHOT")
}
