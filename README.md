# Formbricks Android SDK

**Formbricks Android SDK** provides an easy way to embed Formbricks surveys and feedback forms in your Android applications via a WebView or Fragment. It handles survey loading, analytics tracking, and secure communication with your Formbricks server.

---

## Features

- Full-screen survey support via a dedicated `Activity`.
- Embedded survey support via a `Fragment` with `ViewBinding`.
- Automatic JavaScript configuration and WebViewClient handling.
- Optional analytics tracking integration.
- Easy setup with Gradle and Maven Central.

---

## Installation

Add the Maven Central repository and the Formbricks SDK dependency to your application's `build.gradle.kts`:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.formbricks.android:android:0.1.0") // replace with latest version
}
```

Enable ViewBinding (and DataBinding if needed) in your app’s module build.gradle.kts:

```kotlin
android {
  buildFeatures {
    viewBinding = true
    // dataBinding = true  // only if your own layouts use DataBinding
  }
}
```

## Usage

1. Initialize the SDK
   In your Activity’s onCreate, configure and initialize Formbricks. You must supply the host’s supportFragmentManager:

```kotlin
val config = FormbricksConfig.Builder(
    "https://your-formbricks-server.com",
    "YOUR_ENVIRONMENT_ID"
)
  .setLoggingEnabled(true)
  .setFragmentManager(supportFragmentManager)
  .build()

Formbricks.setup(this, config, true)
Formbricks.setUserId("user-12345")
```

2. Open (trigger) a survey
   To display a survey, simply call Formbricks.track(...) with your survey URL. The SDK will handle launching its WebView internally:

```kotlin
Formbricks.track(
    event = "survey",
    properties = mapOf("url" to "https://your-formbricks-server.com/survey/abc123")
)
```

This will open the survey in the SDK’s built-in WebView.

3. Track Custom Events
   You can also log arbitrary analytics events without UI:

```kotlin
Formbricks.track("button_clicked")
```

## Contributing

We welcome issues and pull requests on our GitHub repository.

## License

This SDK is released under the MIT License.
