plugins {
    alias(libs.plugins.android.application)
}

val sdkVersion = providers.gradleProperty("sdkVersion").orNull
    ?: error("Pass the SDK version under test: -PsdkVersion=<version>")

android {
    namespace = "com.formbricks.consumer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.formbricks.consumer"
        minSdk = 24
        targetSdk = 35
    }

    buildTypes {
        release {
            // R8 runs over the SDK with the consumer rules it ships, as it would in an app.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // The SDK's README requires apps to turn data binding on; without it the SDK's binding
    // classes reference a BR class that only the app's build generates.
    buildFeatures {
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.formbricks:android:$sdkVersion")
}
