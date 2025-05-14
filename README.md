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
    implementation("com.formbricks:android:1.0.0") // replace with latest version
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
    "YOUR_ENVIRONMENT_ID"
)
  .setLoggingEnabled(true)
  .setFragmentManager(supportFragmentManager)
  .build()

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
