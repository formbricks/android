# Repository Guidelines

## Project Structure & Module Organization

- This repository is a Gradle build with two modules (`settings.gradle.kts`).
- The published SDK (`com.formbricks:android`) lives in `android`. Its source is under `android/src/main/java/com/formbricks/android`: the public entry point `Formbricks.kt`, plus `manager/`, `network/`, `webview/`, `model/` and `helper/`.
- SDK tests are instrumented tests in `android/src/androidTest`; there are no JVM unit tests yet.
- The playground app lives in `playground`. It is a host app for manual testing and the E2E golden journey (`playground/src/androidTest`), and is never published.
- `android/api/android.api` is the public API baseline. Update it with the Gradle task below, never by hand.
- `e2e/` holds the E2E harness (`e2e/server` boots a Formbricks server, `e2e/android` runs the journey on an emulator). `scripts/` holds the package check and the release scripts (`scripts/release`). `config/detekt/detekt.yml` is the detekt config.
- Dependency versions live in `gradle/libs.versions.toml`. The root `build.gradle.kts` raises vulnerable build-toolchain dependencies to patched floors.
- Releasing is described in `RELEASING.md`.

## Build, Test, and Development Commands

Run commands from the repo root. These are the commands CI runs (`.github/workflows/ci.yml`, which lists them at the top).

- `./gradlew :android:detekt :android:lint :playground:detekt :playground:lint` runs detekt and Android Lint.
- `./gradlew :android:ktlintCheck :playground:ktlintCheck` checks formatting; `ktlintFormat` fixes it.
- `./gradlew :android:assemble :android:assembleDebugAndroidTest :playground:assembleDebug :playground:assembleDebugAndroidTest` builds every variant and the instrumented tests.
- `./gradlew :android:connectedDebugAndroidTest` runs the SDK's instrumented tests on a running emulator or device. CI runs them through `./gradlew :android:jacocoAndroidTestReport`, which also writes the coverage report. A bare `./gradlew connectedDebugAndroidTest` also runs the playground's golden journey, which needs the E2E server.
- `./gradlew :android:testDebugUnitTest` runs the JVM unit tests.
- `./gradlew :android:checkKotlinAbi` compares the public API with `android/api/android.api`. After an intended API change, run `./gradlew :android:updateKotlinAbi` and commit the updated baseline.
- `scripts/package-check.sh` publishes to `mavenLocal` with a throwaway signing key, checks the POM and AAR, and builds a minified consumer app against them.
- `python3 -m unittest discover -s scripts/release`, `.github/scripts/pr-check-summary.test.sh` and `e2e/*/test/*.sh` test the repo's scripts.
- E2E (`.github/workflows/e2e.yml`, job `e2e-tests`) runs the golden journey against a published Formbricks server image; it needs `ENTERPRISE_LICENSE_KEY`.
- `workflow-lint` runs actionlint and zizmor on the workflows and shellcheck on `.github/scripts/*.sh`, `scripts/*.sh` and every `e2e/**/*.sh`.

## Coding Style & Naming Conventions

- CI runs every check in `.github/workflows/ci.yml`; `PR Check Summary` is the one required check.
- ktlint (Android Studio code style) is the formatter and detekt the linter; `.editorconfig` sets 4-space indentation for Kotlin.
- Kotlin warnings are errors, and explicit API mode is on in `android`: every public declaration needs an explicit visibility.

## Commit & Pull Request Guidelines

- Commits and PR titles follow Conventional Commits, such as `fix(security): floor guava at 33.7.2 for the build toolchain` or `chore: bump to 2.3.1`. `.github/workflows/pr-title.yml` checks the PR title, because PR titles become the release notes.
- Put the Linear ticket at the end of the PR title when there is one: `fix: keep the host Activity alive when a backgrounded survey closes [ENG-3845]`.
- Don't commit directly to `main`; open a pull request.

## Agent skills

### Issue tracker

Linear, Engineering team (`ENG`); skills use the Linear MCP tools. See `docs/agents/issue-tracker.md`.

### Triage labels

No triage labels: the five roles map onto Linear workflow states. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` at the repo root and ADRs in `docs/adr/`. See `docs/agents/domain.md`.
