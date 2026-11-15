// The playground: a small host app for trying the SDK by hand and for the E2E golden journey
// (src/androidTest/.../GoldenJourneyTest.kt, run by e2e/android/run-journey.sh). It depends on the
// :android module directly and is never published.
//
// The server it talks to defaults to the E2E harness as the emulator sees it. Point it elsewhere with
//   ./gradlew :playground:installDebug -Pformbricks.appUrl=https://… -Pformbricks.workspaceId=…
// The SDK only accepts https:// URLs.
plugins {
    id("com.android.application")
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val defaultAppUrl = providers.gradleProperty("formbricks.appUrl")
    .getOrElse("https://10.0.2.2:3443")
val defaultWorkspaceId = providers.gradleProperty("formbricks.workspaceId")
    .getOrElse("clsdkqaws00000000000000001")

ktlint {
    version.set(libs.versions.ktlint)
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/androidTest/java")
}

android {
    namespace = "com.formbricks.playground"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.formbricks.playground"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FORMBRICKS_APP_URL", "\"$defaultAppUrl\"")
        buildConfigField("String", "FORMBRICKS_WORKSPACE_ID", "\"$defaultWorkspaceId\"")
    }

    buildFeatures {
        buildConfig = true
        // The SDK's README requires apps to turn data binding on: its survey view is a binding.
        dataBinding = true
        viewBinding = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Same reasoning as :android - these only report that something newer shipped upstream.
        // OldTargetApi too: targetSdk follows the SDK's compileSdk (35); raise them together.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "OldTargetApi"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":android"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.web)
}
