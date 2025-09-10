import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    if (shouldPublishToMavenCentral()) publishToMavenCentral(automaticRelease = true)
    configureSigning(this)

    pom {
        name = project.name
        description = checkNotNull(project.description) { "Project description missing" }
        url = "https://github.com/ktorio/ktor-build-plugins"
        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "JetBrains"
                name = "Jetbrains Team"
                organization = "JetBrains"
                organizationUrl = "https://www.jetbrains.com"
            }
        }
        scm {
            url = "https://github.com/ktorio/ktor-build-plugins.git"
        }
    }
}

publishing {
    repositories {
        maybeSpace()
        mavenLocal()
    }
}

private fun shouldPublishToMavenCentral(): Boolean =
    providers.gradleProperty("mavenCentralUsername").isPresent &&
            providers.gradleProperty("mavenCentralPassword").isPresent

private fun RepositoryHandler.maybeSpace() {
    if (hasProperty("space")) {
        val publishingUrl = System.getenv("PUBLISHING_URL")
        val publishingUser = System.getenv("PUBLISHING_USER")
        val publishingPassword = System.getenv("PUBLISHING_PASSWORD")
        if (publishingUrl == null || publishingUser == null || publishingPassword == null) {
            throw GradleException("Missing publishing credentials (PUBLISHING_URL / PUBLISHING_USER / PUBLISHING_PASSWORD)")
        }

        maven(url = publishingUrl) {
            name = "space"
            credentials {
                username = publishingUser
                password = publishingPassword
            }
        }
    }
}

private fun Project.configureSigning(mavenPublishing: MavenPublishBaseExtension) {
    extra["signing.gnupg.keyName"] = (System.getenv("SIGN_KEY_ID") ?: return)
    extra["signing.gnupg.passphrase"] = (System.getenv("SIGN_KEY_PASSPHRASE") ?: return)

    pluginManager.apply("signing")
    mavenPublishing.signAllPublications()
    the<SigningExtension>().useGpgCmd()
}
