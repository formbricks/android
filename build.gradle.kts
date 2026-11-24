// Top-level build file where you can add configuration options common to all sub-projects/modules.

// The Android Gradle Plugin drags its own tooling onto this build script's classpath: gRPC and
// Netty (io.grpc:grpc-netty), Bouncy Castle (com.android.tools:sdk-common), commons-compress
// (:repository), jose4j (bundletool), JDOM (jetifier) and the Kotlin Gradle plugin. The floors in
// the `subprojects` block below cannot reach any of it - that block configures each project's own
// configurations, which are resolved separately, so Netty was still landing at 4.1.93.Final
// there while the floor read 4.1.138.Final.
//
// Platforms and constraints only ever raise a version, so a newer one shipped by a future AGP
// still wins. AGP 9 drops gRPC outright, which retires the Netty half of this entirely.
buildscript {
    dependencies {
        classpath(platform("io.netty:netty-bom:${libs.versions.netty.get()}"))
        classpath(platform("com.google.protobuf:protobuf-bom:${libs.versions.protobuf.get()}"))
        constraints {
            add("classpath", "org.bouncycastle:bcprov-jdk18on:${libs.versions.bouncycastle.get()}")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:${libs.versions.bouncycastle.get()}")
            add("classpath", "org.bouncycastle:bcutil-jdk18on:${libs.versions.bouncycastle.get()}")
            add("classpath", "org.bitbucket.b_c:jose4j:${libs.versions.jose4j.get()}")
            add("classpath", "org.jdom:jdom2:${libs.versions.jdom2.get()}")
            add("classpath", "org.apache.commons:commons-compress:${libs.versions.commonsCompress.get()}")
            // Not from AGP directly: commons-compress 1.26+ depends on it, so the floor above
            // brings it onto this classpath.
            add("classpath", "org.apache.commons:commons-lang3:${libs.versions.commonsLang3.get()}")
            // AGP 9.4.1 still resolves 2.2.10, which has an unsafe-deserialization flaw in the
            // Kotlin build cache (fixed in 2.4.20-Beta1). It is already 2.4.20 in :android.
            add("classpath", "org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.legacy.kapt) apply false
}

// Raise known-vulnerable transitive dependencies of the build toolchain to patched versions.
// None of these is a dependency of the SDK itself - they are pulled in by the Android Gradle
// Plugin's Unified Test Platform (netty, protobuf), by Dokka's engine (jackson, jsoup, freemarker),
// by the Kotlin plugin's signing helpers (Bouncy Castle) and by the Android lint tool (httpclient,
// commons-lang3, Bouncy Castle again), so the published AAR and its POM are unaffected.
//
// Every project gets these, because both modules resolve AGP's tooling for themselves: while this
// block lived in android/build.gradle.kts, :playground kept its own unfloored copy of the lint
// toolchain and Dependabot kept reporting the alerts (bcprov 1.80.2, commons-lang3 3.16.0,
// httpclient 4.5.6) even though the floors were "already there". A module added later must not
// have to remember to repeat this.
//
// These are floors, not overrides: `useVersion` on its own would also drag a *newer* version back
// down, so anything at or above the floor is left alone and only older versions are raised. That
// matters now that Dependabot bumps `agp` weekly and each bump can ship newer transitives of its own.
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
    "org.freemarker" to libs.versions.freemarker.get(),
    // Kotlin 2.4's `kotlinBouncyCastleConfiguration` backs its PGP key and signing check
    // tasks. This build never runs them, but the dependency graph still resolves it.
    "org.bouncycastle" to libs.versions.bouncycastle.get()
)

// Floors for single artifacts whose group also holds artifacts that must not be moved.
val moduleFloors = mapOf(
    // The Android lint tool pulls httpclient 4.5.6 (via httpmime) and commons-lang3 3.16.0
    // (via commons-compress). commons-compress and commons-codec share the
    // org.apache.commons group, hence the per-module floor.
    "org.apache.httpcomponents:httpclient" to libs.versions.httpclient.get(),
    "org.apache.commons:commons-lang3" to libs.versions.commonsLang3.get()
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

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            // jackson-annotations is released per minor version only (2.22, not 2.22.2); the
            // pinned jackson-bom already pulls it to the matching line, so leave it alone.
            if (requested.name == "jackson-annotations") return@eachDependency

            val floor = moduleFloors["${requested.group}:${requested.name}"]
                ?: securityFloors[requested.group]
                ?: return@eachDependency
            if (isBelowFloor(requested.version, floor)) {
                useVersion(floor)
                because("security floor - see gradle/libs.versions.toml")
            }
        }
    }
}
