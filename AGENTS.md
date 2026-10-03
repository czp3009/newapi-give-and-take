# Repository Guidelines

## Project Structure & Module Organization

Kotlin Multiplatform application for rewarding contributed new-api channels; packages use
`com.hiczp.newapi.giveandtake`.

- `src/commonMain/kotlin/`: entry point, shared logic, Ktorfit interfaces and serializable API models.
- Configuration/state models and file I/O live in the `file` package; configuration substructures are nested types.
- `src/jvmMain/kotlin/`: CIO HTTP engine; `src/nativeMain/kotlin/`: Curl engine.
- Environment variables use the Unicode Windows API in `mingwX64Main` and POSIX `getenv` with UTF-8 decoding in
  `linuxMain`/`macosMain`.
- `src/commonTest/kotlin/`: application, matching, file and API tests, with shared test helpers.
- `src/mingwX64Test/kotlin/`: Windows environment-variable tests.
- `gradle/libs.versions.toml`: dependencies/plugins; `build.gradle.kts`: targets/build configuration.

Keep generated outputs in `build/`; never edit or commit them. Preserve the default KMP hierarchy and standard
target/task wiring. Keep platform dependencies outside common source sets.

Do not modify hook scripts, including files under `.codex/hooks/`, even during formatting or documentation cleanup.

## Application Scope & Architecture

This project is an application, not a library. One invocation settles one new-api instance using one tracking rule and a
fixed 1:1 quota ratio; scheduling is external.

Keep the new-api integration isolated from the application. Within the application, use direct calls for fixed behavior
rather than injectable factories or callbacks. `api.createHttpClient` encapsulates HTTP initialization and new-api
protocol handling; tests use the same factory with a mock engine. `NewApiClient` borrows its HTTP client without
configuring or closing it; the application owns the client and closes it.

Use `Long` for channel IDs and user IDs throughout models, matching, state keys and API requests.

## Language & Documentation

Communicate in the user's language; write documentation and comments in English unless translating. Use ASCII
punctuation in Chinese. README is for users: explain what the project is, what it does, how to use it and how to build
it from source. Include only limitations that affect user actions or reward results; keep internal implementation
details in KDoc or these guidelines. Update README alongside behavior changes. Consolidate lasting conventions here
without duplication.

## Build, Test, and Development Commands

Use the Gradle wrapper with JDK 25:

- `./gradlew jvmTest` / `.\gradlew.bat jvmTest`: run shared tests on JVM.
- `.\gradlew.bat mingwX64Test`: run shared and Windows-specific tests on Windows x64.
- `./gradlew build` / `.\gradlew.bat build`: assemble and test.
- `.\gradlew.bat linkDebugExecutableMingwX64`: build Windows executable.
- `.\gradlew.bat runDebugExecutableMingwX64`: run it locally.

Targets cover Windows x64, Linux x64 and macOS ARM64; final link tasks run only when both the target OS and
architecture match the host. The JVM target primarily supports shared tests. Running the executable uses its configured
new-api instance; use mock tests for offline validation.

If a build fails because of stale caches or generated outputs, try `gradle clean` through the wrapper before rebuilding.

## Coding Style & Naming Conventions

- Use four-space indentation, PascalCase types/files, camelCase functions/properties and UPPER_SNAKE_CASE constants. Use
  multiline trailing commas. Keep existing comments and KDoc accurate and concise; remove redundant explanations. Add
  documentation to previously undocumented code only when it explains an important contract or non-obvious behavior.
- Separate functions with blank lines; place annotations and statements on separate lines. Prefer imports, using aliases
  for conflicts. Use raw strings for multiline text.
- Use string templates instead of `+` to concatenate strings.
- Prefer variable names matching the type name in lower camel case, unless a distinct semantic or business role
  justifies another name.
- Prefer Kotlin-style `forEach` iteration over index-based (`fori`-style) loops whenever practical.
- When wrapping a caught exception in a new exception, preserve the original exception as its cause.
- Keep conceptually single-line content on one line; do not force it across multiple lines just to limit line length.
- Prefer Kotlin/kotlinx and existing libraries. Keep portable logic in `commonMain`; use `expect`/`actual` only for
  genuine platform gaps. Name equivalent platform implementations by responsibility.
- Serialize typed models with kotlinx.serialization; never concatenate JSON. Use `@SerialName` for wire names. Follow
  new-api field types/nullability; missing required fields must fail rather than receive invented defaults. Preserve
  server messages.
- Use file-level kotlin-logging variables with explicit package names; do not infer logger names on Native.

No formatter/linter is configured.

## Testing Guidelines

Use `kotlin.test`, `MockEngine` and `runTest` (never `runBlocking`). Keep coroutine tests deterministic: do not
synchronize with `delay`/sleep or other timing-based waits; use coroutine primitives (`Channel`, `CompletableDeferred`,
`TestDispatcher` control such as `advanceUntilIdle`) to pin the execution order of key steps, and assert exact call
counts instead of accepting races. Name classes `*Test` and methods `testMeaningfulBehavior`. Cover serialization,
pagination and HTTP/business failures. Default tests must not contact live services; real-service acceptance requires
explicit selection of a disposable new-api instance. Report mock, native and live-service validation separately. No
coverage threshold exists.

## Configuration & File Contracts

Keep access tokens out of commits/logs; use placeholders in tests. Keep configuration separate from state.

- Resolve each setting as command-line argument > environment variable > configuration file. Only missing values and
  empty strings fall through; preserve whitespace. Report a missing setting only after all sources have been checked.
- Create missing config/state files from their templates, then continue normally. Template creation does not bypass
  overrides or settlement. Omitted config settings are unconfigured, not implicit template defaults.
- Do not add application-level URL/token format checks or state value validation. Deserialization still enforces
  required fields and types.
- Tracking matches complete, untrimmed lines in `remark` or `tag` using the named `userId` group. The first matching
  line wins; an invalid captured ID is an error, not a reason to try later lines.
- Use kotlinx-io for file operations and kotlinx-serialization-json-io to stream JSON through sources/sinks; avoid
  full-file strings and duplicate state maps.
- Write and close a temporary state file on the same filesystem before atomic replacement; never fall back to truncating
  the state file in place. Leftover temporary files do not replace state when reading.

## Settlement Contracts & Accepted Limits

`State.channels` maps channel IDs to user IDs and quota baselines. New associations and changed owners start at current
consumption without awarding historical rewards. Unchanged owners receive the signed consumption difference; update
their baseline only after a successful adjustment. Do not retry quota adjustments within a run.

Collect and match channel pages before changing state. Before removing a channel absent from the filtered list, query it
by ID again; settle recovered matches and retain baselines on query failures. Only the exact `record not found` business
failure confirms deletion. A confirmed missing channel or absent/invalid user match removes the entry; a later
association starts fresh.

Update state in memory during channel processing. Once the HTTP client is created, close it and save state once in
`finally`, including when processing is cancelled. Log ordinary channel failures and continue; propagate cancellation.
Final save failures propagate and may replace an earlier processing exception.

Remote quota adjustments and local state writes are intentionally not reconciled or made atomic together. Pagination is
not a snapshot; individual lookups recover previously tracked channels omitted by pagination but cannot discover omitted
new associations. Atomic file replacement protects against interrupted process writes, without guaranteeing persistence
across OS crashes or power loss.

Windows environment values use the Unicode API; POSIX environment values are decoded as UTF-8. Kotlin/Native
command-line conversion and kotlinx-io Windows path encoding limitations are accepted upstream constraints; do not add
workarounds. Assume a single program instance; do not introduce lock files or multi-instance coordination.
