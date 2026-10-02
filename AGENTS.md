# Repository Guidelines

## Project Structure & Module Organization

Kotlin Multiplatform client for new-api channel listing and user quota management; packages use
`com.hiczp.newapi.giveandtake`. Reward processing is not implemented.

- `src/commonMain/kotlin/`: entry point, shared logic, Ktorfit interfaces and serializable API models.
- `src/jvmMain/kotlin/`: CIO HTTP engine; `src/nativeMain/kotlin/`: Curl engine.
- `src/commonTest/kotlin/`: API tests and `TestUtils.kt` helpers.
- `gradle/libs.versions.toml`: dependencies/plugins; `build.gradle.kts`: targets/build configuration.

Keep generated outputs in `build/`; never edit or commit them. Preserve the default KMP hierarchy and standard
target/task wiring. Keep platform dependencies outside common source sets.

Do not modify hook scripts, including files under `.codex/hooks/`, even during formatting or documentation cleanup.

## Language & Documentation

Communicate in the user's language; write documentation and comments in English unless translating. Use ASCII
punctuation in Chinese. Keep README focused on implemented setup, usage and limitations; update it alongside behavior
changes. Consolidate lasting conventions here without duplication.

## Build, Test, and Development Commands

Use the Gradle wrapper with JDK 25:

- `./gradlew jvmTest` / `.\gradlew.bat jvmTest`: run shared tests on JVM.
- `./gradlew build` / `.\gradlew.bat build`: assemble and test.
- `.\gradlew.bat linkDebugExecutableMingwX64`: build Windows executable.
- `.\gradlew.bat runDebugExecutableMingwX64`: run it locally.

Targets cover Windows x64, Linux x64/ARM64 and macOS ARM64; final link tasks run only when both the target OS and
architecture match the host. `Main.kt` is an empty placeholder.

## Coding Style & Naming Conventions

- Use four-space indentation, PascalCase types/files, camelCase functions/properties and UPPER_SNAKE_CASE constants. Use
  multiline trailing commas. Keep existing comments and KDoc accurate and concise; remove redundant explanations. Add
  documentation to previously undocumented code only when it explains an important contract or non-obvious behavior.
- Separate functions with blank lines; place annotations and statements on separate lines. Prefer imports, using aliases
  for conflicts. Use raw strings for multiline text.
- Prefer Kotlin/kotlinx and existing libraries. Keep portable logic in `commonMain`; use `expect`/`actual` only for
  genuine platform gaps. Name equivalent platform implementations by responsibility.
- Serialize typed models with kotlinx.serialization; never concatenate JSON. Use `@SerialName` for wire names. Follow
  new-api field types/nullability; missing required fields must fail rather than receive invented defaults. Preserve
  server messages.

No formatter/linter is configured.

## Testing Guidelines

Use `kotlin.test`, `MockEngine` and `runTest` (never `runBlocking`). Keep coroutine tests deterministic: do not
synchronize with `delay`/sleep or other timing-based waits; use coroutine primitives (`Channel`, `CompletableDeferred`,
`TestDispatcher` control such as `advanceUntilIdle`) to pin the execution order of key steps, and assert exact call
counts instead of accepting races. Name classes `*Test` and methods `testMeaningfulBehavior`. Cover serialization,
pagination and HTTP/business failures. Default tests must not contact live services; real-service acceptance requires
explicit selection of a disposable new-api instance. Report mock, native and live-service validation separately. No
coverage threshold exists.

## Security & Configuration

Keep `NewApiConfig` tokens out of commits/logs; use placeholders in tests. Quota adjustments must not be retried blindly
after an uncertain response.
