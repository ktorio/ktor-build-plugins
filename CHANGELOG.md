<!--
The format is based on Keep a Changelog: https://keepachangelog.com/en/1.1.0/
-->

## [Unreleased]

_No changes yet_

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

[unreleased]: https://github.com/ktorio/ktor-build-plugins/compare/v3.1.1...main
[3.1.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.1.0...v3.1.1
[3.1.0]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.3...v3.1.0
[3.0.3]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.2...v3.0.3
[3.0.2]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.1...v3.0.2
[3.0.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.0...v3.0.1