<!--
The format is based on Keep a Changelog: https://keepachangelog.com/en/1.1.0/
-->

## [Unreleased]

*No changes yet*

## [3.0.1] - 2024-10-30

> [!WARNING]
> The minimal supported Gradle version now is 8.3 as it is required for the Shadow plugin

- Update Shadow plugin to v8.3.4 ([KTOR-7228](https://youtrack.jetbrains.com/issue/KTOR-7228), [KTOR-6107](https://youtrack.jetbrains.com/issue/KTOR-6107))
- Bump minimal required JVM to run Gradle from 8 to 11 (as it is required for jib plugin)
- Bump default JRE in Docker to 21 (current LTS version)
- Set Kotlin API and language level to 1.8 for compatibility with Gradle 8.0+

[unreleased]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.1...main
[3.0.1]: https://github.com/ktorio/ktor-build-plugins/compare/v3.0.0...v3.0.1