# Formbricks Android SDK

**Formbricks Android SDK** provides an easy way to embed Formbricks surveys and feedback forms in your Android applications via a WebView.

## Installation

Add the Maven Central repository and the Formbricks SDK dependency to your application's `build.gradle.kts`:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.formbricks:android:2.2.0") // replace with latest version
}
```

Enable DataBinding in your app's module build.gradle.kts:

```kotlin
android {
  buildFeatures {
    dataBinding = true
  }
}
```

## Usage

```kotlin
// 1. Initialize the SDK
val config = FormbricksConfig.Builder(
    "https://your-formbricks-server.com",
    "YOUR_WORKSPACE_ID"
)
  .setLoggingEnabled(true)
  .setFragmentManager(supportFragmentManager)
  .build()

// Note: `environmentId` is deprecated and will be removed in a future version.
// Existing integrations using the legacy entry point still work:
//
//   FormbricksConfig.Builder.withEnvironmentId(
//       "https://your-formbricks-server.com",
//       "YOUR_ENVIRONMENT_ID"
//   )
//
// Migrate to `workspaceId` as soon as possible.

// 2. Setup Formbricks
Formbricks.setup(this, config)

// 3. Identify the user
Formbricks.setUserId("user‑123")

// 4. Track events
Formbricks.track("button_pressed")

// 5. Set or add user attributes
Formbricks.setAttribute("test@web.com", "email")
Formbricks.setAttributes(mapOf(Pair("attr1", "val1"), Pair("attr2", "val2")))

// 6. Change language (no userId required):
Formbricks.setLanguage("de")

// 7. Log out:
Formbricks.logout()
```

### Dark mode

Surveys render light by default. Call `setAppearance` to change it:

```kotlin
Formbricks.setAppearance(FormbricksAppearance.DARK) // LIGHT, DARK or SYSTEM
```

- Works before or after `setup`, or pass it in the config with `.setAppearance(FormbricksAppearance.DARK)`. A string overload (`"dark"`) is available too.
- An open survey switches in place; the typed answer and current question stay.
- `SYSTEM` follows **your app's** night mode (including `AppCompatDelegate.setDefaultNightMode`), not the phone's, and updates live.
- To keep an open survey's answers when the night mode changes, let the host Activity handle `uiMode` in `android:configChanges`. Otherwise Android recreates the Activity and the survey reloads, as it does on rotation.
- Kept across `logout()`, forgotten on app restart, never sent to the server. An unknown value is logged and falls back to light.
- Needs a Formbricks server that supports dark mode; an older server keeps surveys light.

Custom CSS configured in Formbricks needs no SDK call; it arrives with the workspace state.

## Contributing

We welcome issues and pull requests on our GitHub repository.

## Testing and Code Coverage

### Running Tests

To run the instrumented tests, make sure you have an Android emulator running or a physical device connected, then execute:

```bash
./gradlew connectedDebugAndroidTest
```

### Generating Coverage Reports

The SDK uses JaCoCo for code coverage reporting. To generate a coverage report for instrumented tests:

1. Make sure you have an Android emulator running or a physical device connected
2. Run the provided script:
   ```bash
   ./generate-instrumented-coverage.sh
   ```
   This will:
   - Run the instrumented tests
   - Generate a JaCoCo coverage report
   - Open the HTML report in your default browser

Alternatively, you can run the Gradle task directly:

```bash
./gradlew jacocoAndroidTestReport
```

The coverage report will be generated at:

```
android/build/reports/jacoco/jacocoAndroidTestReport/html/index.html
```

## License

This SDK is released under the MIT License.
