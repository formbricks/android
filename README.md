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
    implementation("com.formbricks.android:android:1.0.0") // replace with latest version
}
```

Enable DataBinding in your app’s module build.gradle.kts:

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

## License

This SDK is released under the MIT License.
