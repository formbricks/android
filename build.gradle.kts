// Top-level build file where you can add configuration options common to all sub-projects/modules.

// The Android Gradle Plugin drags its own tooling onto this build script's classpath: gRPC and
// Netty (io.grpc:grpc-netty), Bouncy Castle (com.android.tools:sdk-common), commons-compress
// (:repository), jose4j (bundletool) and JDOM (jetifier). The resolutionStrategy in
// android/build.gradle.kts cannot reach any of it - that block configures the :android project's
// own configurations, which are resolved separately, so Netty was still landing at 4.1.93.Final
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
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
}
