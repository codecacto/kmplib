# Repository Guidelines

## Project Structure and Module Organization
This is a Kotlin Multiplatform library. The main module is `library/`.
- `library/src/commonMain/`: shared Kotlin code (core utilities, Firebase wrappers, masks, validators).
- `library/src/androidMain/`: Android-specific implementations.
- `library/src/iosMain/`: iOS-specific implementations.
- `library/src/commonTest/`: shared unit tests.
- `images/`: documentation assets referenced in docs.
- `build/` and `library/build/`: generated build outputs (do not edit).

## Build, Test, and Development Commands
Use the Gradle wrapper in repo root.
- `./gradlew :library:build` builds the KMP library artifacts.
- `./gradlew jvmTest` runs common tests on the JVM target.
- `./gradlew iosSimulatorArm64Test` runs common tests on iOS simulator (macOS only).
- `./gradlew testAndroidHostTest` runs Android host unit tests.
- `./gradlew publishToMavenCentral` publishes release artifacts (used in release workflow).

## Coding Style and Naming Conventions
- Kotlin style: `kotlin.code.style=official` (see `gradle.properties`).
- Indentation: 4 spaces; trailing whitespace avoided.
- Naming: packages are lowercase (`br.com.codecacto.kmplib`), classes are `UpperCamelCase`, functions and properties are `lowerCamelCase`.
- Target JVM: 17 for Android compilations (see `library/build.gradle.kts`).

## Testing Guidelines
- Framework: `kotlin.test` in `library/src/commonTest/`.
- Naming: `*Test.kt` for test files, `*Test` for classes.
- Preferred command: `./gradlew jvmTest` for fast feedback; run target-specific tests before release.

## Commit and Pull Request Guidelines
- Git history is not present in this checkout, so no commit message convention is detectable.
- PRs should describe the change, include rationale, and mention affected targets (common/android/ios).
- If changes affect public APIs or publishing, note version impact and update documentation.

## Configuration Notes
- JDK 21 is expected by local Gradle settings (`gradle.properties`), while CI builds run JDK 17/21 depending on workflow.
