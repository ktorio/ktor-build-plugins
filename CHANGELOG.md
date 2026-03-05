<!--
The format is based on Keep a Changelog: https://keepachangelog.com/en/1.1.0/
---
Template for new releases:

## [x.y.z] - YYYY-MM-DD

> [Ktor x.y.z Changelog](https://github.com/ktorio/ktor/releases/tag/x.y.z)

### Features
-

### Improvements
-

### Bugfixes
-

### Dependencies
-
-->

## [Unreleased]

*No changes yet*

## [3.4.1] - 2026-03-03

> [Ktor 3.4.1 Changelog](https://github.com/ktorio/ktor/releases/tag/3.4.1)

### Bugfixes

OpenAPI generation:
- [KTOR-9305] Fix for local parameter key variables causing errors
- [KTOR-9279] Account for reified type parameters in call stack
- [KTOR-9289] Fix resources endpoints
- [KTOR-9291] More general support for routing functions
- [KTOR-9281] Read lambda argument bodies for code inference

[KTOR-9305]: https://youtrack.jetbrains.com/issue/KTOR-9305
[KTOR-9279]: https://youtrack.jetbrains.com/issue/KTOR-9279
[KTOR-9289]: https://youtrack.jetbrains.com/issue/KTOR-9289
[KTOR-9291]: https://youtrack.jetbrains.com/issue/KTOR-9291
[KTOR-9281]: https://youtrack.jetbrains.com/issue/KTOR-9281

## [3.4.0] - 2026-01-22

> [Ktor 3.4.0 Changelog](https://github.com/ktorio/ktor/releases/tag/3.4.0)

### Features
- [KTOR-8859] OpenAPI generation: Routing documentation API code generation

### Dependencies
- Kotlin 2.2.21 → 2.3.0
- Gradle 9.0.0 → 9.3.0
- Jib plugin 3.4.5 → 3.5.2

[KTOR-8859]: https://youtrack.jetbrains.com/issue/KTOR-8859

## [3.3.3] - 2025-11-27

> [Ktor 3.3.3 Changelog](https://github.com/ktorio/ktor/releases/tag/3.3.3)

### Features
- [KTOR-9120] OpenAPI generation: Add operationId parameter

### Bugfixes
- [KTOR-8878] OpenAPI generation: Fix for stack overflow on resolveType

[KTOR-9120]: https://youtrack.jetbrains.com/issue/KTOR-9120

## [3.3.2] - 2025-11-05

> [Ktor 3.3.2 Changelog](https://github.com/ktorio/ktor/releases/tag/3.3.2)

### Improvements
- [KTOR-8878] OpenAPI generation: Expanded support for common contextual types

### Bugfixes
- [KTOR-9021] OpenAPI generation: Fix type parameters inference and include missing KDoc parameters

### Dependencies
- Kotlin 2.2.20 → 2.2.21

[KTOR-8878]: https://youtrack.jetbrains.com/issue/KTOR-8878
[KTOR-9021]: https://youtrack.jetbrains.com/issue/KTOR-9021

## [3.3.1] - 2025-10-08

> [Ktor 3.3.1 Changelog](https://github.com/ktorio/ktor/releases/tag/3.3.1)

## [3.3.0] - 2025-09-12

> [!WARNING]
> The minimum supported Gradle version now is 8.11 as it is required for the Shadow plugin

- Update Ktor to [v3.3.0](https://github.com/ktorio/ktor/releases/tag/3.3.0)
- Update Gradle to 9.0.0
- Update Shadow plugin to v9.1.0
- [KTOR-8721] OpenAPI generation build extension preview

[KTOR-8721]: https://youtrack.jetbrains.com/issue/KTOR-8721

## [3.2.3] - 2025-07-29

- Update Ktor to [v3.2.3](https://github.com/ktorio/ktor/releases/tag/3.2.3)
- [KTOR-8678] Enforce Commons Lang v3.18.0+

[KTOR-8678]: https://youtrack.jetbrains.com/issue/KTOR-8678

## [3.2.2] - 2025-07-14

- Update Ktor to [v3.2.2](https://github.com/ktorio/ktor/releases/tag/3.2.2)
- Update Gradle to v8.14.3

## [3.2.1] - 2025-07-04

- Update Ktor to [v3.2.1](https://github.com/ktorio/ktor/releases/tag/3.2.1)
- Update Shadow plugin to v8.3.8
- Update Gradle to v8.14.2

## [3.2.0] - 2025-06-13

- Update Ktor to [v3.2.0](https://github.com/ktorio/ktor/releases/tag/3.2.0)
- [KTOR-8444] Basic compatibility with the Kotlin Multiplatform Gradle plugin
- [KTOR-8419] Do not apply Gradle's Application plugin for KMP projects
- Make the plugin Groovy-friendly (#178)
- Add `internal` to all internal APIs

[KTOR-8444]: https://youtrack.jetbrains.com/issue/KTOR-8444/
[KTOR-8419]: https://youtrack.jetbrains.com/issue/KTOR-8419/

## [3.1.3] - 2025-05-05

- Update Ktor to [v3.1.3](https://github.com/ktorio/ktor/releases/tag/3.1.3)
- Update Gradle to v8.14

## [3.1.2] - 2025-03-27

- Update Ktor to [v3.1.2](https://github.com/ktorio/ktor/releases/tag/3.1.2)
- Update Jib plugin to v3.4.5
- Update Gradle plugin for GraalVM Native Image to v0.10.6
- Update Gradle to v8.13

## [3.1.1] - 2025-02-25

- Update Ktor to [v3.1.1](https://github.com/ktorio/ktor/releases/tag/3.1.1)
- [KTOR-8173] Add property `ktor.development` to simplify development mode enabling

[KTOR-8173]: https://youtrack.jetbrains.com/issue/KTOR-8173/

## [3.1.0] - 2025-02-12

- Update Ktor to [v3.1.0](https://github.com/ktorio/ktor/releases/tag/3.1.0)
- Update Shadow plugin to v8.3.6 
- Update Gradle plugin for GraalVM Native Image to v0.10.5
- Update Gradle to v8.12.1

## [3.0.3] - 2024-12-19

- Update Ktor to [v3.0.3](https://github.com/ktorio/ktor/releases/tag/3.0.3)

## [3.0.2] - 2024-12-04

- Update Ktor to [v3.0.2](https://github.com/ktorio/ktor/releases/tag/3.0.2)
- Update Shadow plugin to v8.3.5

## [3.0.1] - 2024-10-30

> [!WARNING]
> The minimal supported Gradle version now is 8.3 as it is required for the Shadow plugin

- Update Ktor to [v3.0.1](https://github.com/ktorio/ktor/releases/tag/3.0.1)
- Update Shadow plugin to v8.3.4 ([KTOR-7228](https://youtrack.jetbrains.com/issue/KTOR-7228), [KTOR-6107](https://youtrack.jetbrains.com/issue/KTOR-6107))
- Bump minimal required JVM to run Gradle from 8 to 11 (as it is required for jib plugin)
- Bump default JRE in Docker to 21 (current LTS version)
- Set Kotlin API and language level to 1.8 for compatibility with Gradle 8.0+

[unreleased]: https://github.com/ktorio/ktor-build-plugins/compare/v3.4.1...main
[3.4.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.4.0...v3.4.1
[3.4.0]: https://github.com/ktorio/ktor-build-plugins/compare/v3.3.3...v3.4.0
[3.3.3]: https://github.com/ktorio/ktor-build-plugins/compare/v3.3.2...v3.3.3
[3.3.2]: https://github.com/ktorio/ktor-build-plugins/compare/v3.3.1...v3.3.2
[3.3.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.3.0...v3.3.1
[3.3.0]: https://github.com/ktorio/ktor-build-plugins/compare/v3.2.3...v3.3.0
[3.2.3]: https://github.com/ktorio/ktor-build-plugins/compare/v3.2.2...v3.2.3
[3.2.2]: https://github.com/ktorio/ktor-build-plugins/compare/v3.2.1...v3.2.2
[3.2.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.2.0...v3.2.1
[3.2.0]: https://github.com/ktorio/ktor-build-plugins/compare/v3.1.3...v3.2.0
[3.1.3]: https://github.com/ktorio/ktor-build-plugins/compare/v3.1.2...v3.1.3
[3.1.2]: https://github.com/ktorio/ktor-build-plugins/compare/v3.1.1...v3.1.2
[3.1.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.1.0...v3.1.1
[3.1.0]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.3...v3.1.0
[3.0.3]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.2...v3.0.3
[3.0.2]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.1...v3.0.2
[3.0.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.0...v3.0.1