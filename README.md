# Ktor Gradle plugin

This plugin simplifies the [deployment](https://ktor.io/docs/deploy.html) process of server Ktor applications and
provides the following capabilities:

- Building fat JARs.
- Dockerizing your applications.
- Building GraalVM native images.

### Install the plugin

To install the plugin, add it to the `plugins` block of your `build.gradle.kts`:

```kotlin
plugins {
    id("io.ktor.plugin") version "2.1.0-eap-1"
}
```

or `build.gradle` file:

```groovy
plugins {
    id "io.ktor.plugin" version "2.1.0-eap-1"
}
```

### EAP builds

You can also use EAP versions of the plugin
published on [Space Packages](https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/plugin/plugin/).
To do this, consider adding <https://maven.pkg.jetbrains.space/public/p/ktor/eap> to the list of plugin repositories
in the `settings.gradle` file, which may look like this:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}
```

### Build a fat JAR

To build and run a fat JAR, use the `buildFatJar`/`runFatJat` tasks.
Note that a [main class](https://ktor.io/docs/server-dependencies.html#create-entry-point) should be configured for your
application, for example:

```kotlin
// build.gradle.kts
application {
    mainClass.set("com.example.ApplicationKt")
}
```

After the task is executed, you should see the `***-all.jar` file in the `build/libs` directory.
You can optionally configure a name of fat JAR to be generated:

```kotlin
// build.gradle.kts
ktor {
    fatJar {
        archiveFileName = "fat.jar"
    }
}
```

You can find a sample build script
here: [ktor-fatjar-sample/build.gradle.kts](samples/ktor-fatjar-sample/build.gradle.kts).

### Dockerize your application

The following tasks are available for packaging, running, and deploying your application using Docker:

- `buildImage`: builds a project's Docker image to a tarball.
  This task generates a `***.tar` file in the `build` directory.
  You can load this image to a Docker daemon using
  the [docker load](https://docs.docker.com/engine/reference/commandline/load/) command.
- `runDocker`: builds a project's image to a Docker daemon and runs it.
- `publishImageToLocalRegistry`: builds and publishes a project's Docker image to a local registry.
- `publishImage`: builds and publishes a project's Docker image to an external registry.
  Note that you need to configure the external registry using the `ktor.docker.externalRegistry` property for this task.

A sample configuration for Docker-related tasks might look as follows:

```kotlin
// build.gradle.kts
ktor {
    docker {
        jreVersion = JreVersion.JRE_17
        localImageName = "sample-docker-image"
        imageTag = "0.0.1-preview"

        externalRegistry = DockerImageRegistry.dockerHub(
            appName = "ktor-app",
            username = System.getenv("DOCKER_HUB_USERNAME"),
            password = System.getenv("DOCKER_HUB_PASSWORD")
        )
    }
}
```

You can find a sample build script
here: [ktor-docker-sample/build.gradle.kts](samples/ktor-docker-sample/build.gradle.kts).

### Build a GraalVM native image

To build a project's GraalVM native image, use the `buildNativeImage` task.
Before running this task, ensure [GraalVM](https://www.graalvm.org/docs/getting-started/)
and [Native Image](https://www.graalvm.org/reference-manual/native-image/) are installed.

> Note that working with Native Image requires setting the `GRAALVM_HOME` and `JAVA_HOME` environment variables.

The `buildNativeImage` task generates a native executable with your application in the `build/native/nativeCompile`
directory.
You can optionally specify the executable name:

```kotlin
// build.gradle.kts
ktor {
    nativeImage {
        imageName = "native-image-sample"
    }
}
```

You can find a sample build script
here: [ktor-native-image-sample/build.gradle.kts](samples/ktor-native-image-sample/build.gradle.kts).
