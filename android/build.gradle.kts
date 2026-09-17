import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

// The Dokka Gradle plugin puts Dokka's engine, and with it Jackson, on this build script's
// own classpath, which the project-level resolutionStrategy below cannot reach. Importing the
// Jackson BOM as a platform here adds version constraints to that classpath before the
// plugins block is resolved. Like the floors below, this only ever raises: a newer version
// requested by a future Dokka release still wins.
buildscript {
    dependencies {
        classpath(platform("com.fasterxml.jackson:jackson-bom:${libs.versions.jackson.get()}"))
    }
}

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization") version "2.1.0"
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    id("jacoco")
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.sonarqube") version "7.5.0.8588"
}

version = "2.2.0"
val groupId = "com.formbricks"
val artifactId = "android"

// Configure JaCoCo version
jacoco {
    toolVersion = "0.8.11"
}

// Raise known-vulnerable transitive dependencies of the build toolchain to patched
// versions. None of these is a dependency of the SDK itself - they are pulled in by the
// Android Gradle Plugin's Unified Test Platform (netty, protobuf) and by Dokka's engine
// (jackson, jsoup), so the published AAR and its POM are unaffected.
//
// These are floors, not overrides: `useVersion` on its own would also drag a *newer*
// version back down, so anything at or above the floor is left alone and only older
// versions are raised. That matters now that Dependabot bumps `agp` weekly and each bump
// can ship newer transitives of its own.
run {
    val securityFloors = mapOf(
        "io.netty" to libs.versions.netty.get(),
        "com.google.protobuf" to libs.versions.protobuf.get(),
        // Dokka 2.2.0 still declares Jackson 2.15.x and jsoup 1.16.x for its engine. It only
        // uses their stable APIs, so it runs fine on the latest patched releases.
        "com.fasterxml.jackson" to libs.versions.jackson.get(),
        "com.fasterxml.jackson.core" to libs.versions.jackson.get(),
        "com.fasterxml.jackson.dataformat" to libs.versions.jackson.get(),
        "com.fasterxml.jackson.module" to libs.versions.jackson.get(),
        "org.jsoup" to libs.versions.jsoup.get(),
    )

    fun isBelowFloor(current: String?, floor: String): Boolean {
        if (current.isNullOrBlank()) return true
        fun numericParts(v: String) = v.split('.', '-', '_').mapNotNull(String::toIntOrNull)
        val actual = numericParts(current)
        val wanted = numericParts(floor)
        for (i in 0 until maxOf(actual.size, wanted.size)) {
            val a = actual.getOrElse(i) { 0 }
            val b = wanted.getOrElse(i) { 0 }
            if (a != b) return a < b
        }
        return false
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            // jackson-annotations is released per minor version only (2.22, not 2.22.2); the
            // pinned jackson-bom already pulls it to the matching line, so leave it alone.
            if (requested.name == "jackson-annotations") return@eachDependency

            val floor = securityFloors[requested.group] ?: return@eachDependency
            if (isBelowFloor(requested.version, floor)) {
                useVersion(floor)
                because("security floor - see gradle/libs.versions.toml")
            }
        }
    }
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

// API documentation. Dokka 2.x generates it; the Javadoc-format output is what ends up in
// the -javadoc jar on Maven Central (see the publishing block below).
dokka {
    moduleName.set("Formbricks Android SDK")
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl("https://github.com/formbricks/android/tree/main/android/src/main/java")
            remoteLineSuffix.set("#L")
        }
    }
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    group = "documentation"
    description = "Packages the Dokka Javadoc output into the -javadoc jar published to Maven Central."
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
    archiveClassifier.set("javadoc")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // AGP's own withJavadocJar() runs a bundled Dokka 1.4.32 engine on a detached
    // configuration that neither the dependency report nor the floors above can reach (old
    // Jackson included), so the javadoc jar is built from Dokka 2.x above and attached to
    // the publication instead.
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false,
        )
    )

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

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(dokkaJavadocJar)
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
