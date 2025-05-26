import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.dokka") version "1.9.10"
    id("jacoco")
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.sonarqube") version "4.4.1.3373"
}

version = "1.0.2"
val groupId = "com.formbricks"
val artifactId = "android"

// Configure JaCoCo version
jacoco {
    toolVersion = "0.8.11"
}

android {
    namespace = "com.formbricks.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("debug") {
            enableAndroidTestCoverage = true
            isTestCoverageEnabled = true  // For backward compatibility
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "META-INF/library_release.kotlin_module"
            excludes += "classes.dex"
            excludes += "**.**"
            pickFirsts += "**/DataBinderMapperImpl.java"
            pickFirsts += "**/DataBinderMapperImpl.class"
            pickFirsts += "**/formbrickssdk/DataBinderMapperImpl.java"
            pickFirsts += "**/formbrickssdk/DataBinderMapperImpl.class"
        }
    }
    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)

    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp3.logging.interceptor)

    implementation(libs.material)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.databinding.common)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(groupId, artifactId, version.toString())

    pom {
        name = "Formbricks Android SDK"
        description = "Formbricks anroid SDK"
        url = "https://github.com/formbricks/android"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "formbricks"
                name = "Formbricks"
                email = "hola@formbricks.com"
            }
        }
        scm {
            connection = "scm:git:git://github.com/formbricks/android.git"
            developerConnection = "scm:git:ssh://github.com:formbricks/android.git"
            url = "https://github.com/formbricks/android"
        }
    }
}

// Add JaCoCo tasks
tasks.register<JacocoReport>("jacocoAndroidTestReport") {
    dependsOn("connectedDebugAndroidTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/databinding/**/*.class",
        "android/databinding/*Binding.*",
        "android/BuildConfig.*",
        "**/*\$*.*",
        "**/Lambda\$*.class",
        "**/Lambda.class",
        "**/*Lambda.class",
        "**/*Lambda*.class",
        "**/*_MembersInjector.class",
        "**/Dagger*Component.class",
        "**/Dagger*Component\$*.class",
        "**/*Module_*Factory.class"
    )

    val debugTree = fileTree(mapOf(
        "dir" to layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile,
        "excludes" to fileFilter
    ))

    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(mapOf(
        "dir" to layout.buildDirectory.get().asFile,
        "includes" to listOf(
            "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
            "outputs/code_coverage/debugAndroidTest/connected/**/*.exec"
        )
    )))
}

// Configure Sonar
sonar {
    properties {
        property("sonar.coverage.jacoco.xmlReportPaths", 
            layout.buildDirectory.file("reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml").get().asFile.path)
    }
}

tasks.sonar {
    dependsOn("jacocoAndroidTestReport")
}