import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

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
    // AGP 9 has Kotlin support built in, so org.jetbrains.kotlin.android must not be applied.
    // legacy-kapt replaces org.jetbrains.kotlin.kapt, which is unsupported with built-in Kotlin;
    // it is what runs the data binding annotation processor.
    id("com.android.legacy-kapt")
    kotlin("plugin.serialization") version "2.4.20"
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    id("jacoco")
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("org.sonarqube") version "7.5.0.8588"
}

// main only holds the placeholder. The release workflow passes the version from the release tag
// (scripts/release/version.py) as the `releaseVersion` Gradle property, so main never carries a
// real version. See RELEASING.md.
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-dev")
val groupId = "com.formbricks"
val artifactId = "android"

// format: `./gradlew ktlintCheck`; `./gradlew ktlintFormat` fixes most findings in place.
ktlint {
    version.set(libs.versions.ktlint)
}

// lint: `./gradlew detekt`. The rule set is detekt's defaults plus config/detekt/detekt.yml.
detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/androidTest/java")
}

// Configure JaCoCo version
jacoco {
    toolVersion = "0.8.11"
}

// The toolchain security floors live in the root build script's `subprojects` block, so that
// every module gets them. See build.gradle.kts.

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
    // lint: `./gradlew lint`. Warnings fail the build like errors do.
    lint {
        warningsAsErrors = true
        abortOnError = true
        // These report that something newer was released upstream, not a defect in this code, so
        // they would turn CI red on any day a dependency ships. Versions are pinned on purpose
        // (see gradle/libs.versions.toml) and raised through Dependabot.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// The compiler here is newer than the Kotlin our users run (AGP 9 needs it, and the root build
// raises it past a security advisory). A compiler stamps its own version into every class it
// emits, and an app's compiler rejects anything more than one minor version above itself. So the
// output is held at Kotlin 2.1: language and API level, and the stdlib the POM declares.
// scripts/check-kotlin-baseline.py fails CI if any of this drifts.
//
// `kotlinOptions { jvmTarget = "11" }` became a script compilation error with Kotlin 2.4, hence
// `compilerOptions`.
kotlin {
    coreLibrariesVersion = "2.1.0"

    // Every public declaration states its visibility, so nothing joins the API by accident.
    explicitApi()

    // api-check: `checkKotlinAbi` diffs the public API against android/api/android.api.
    // After an intended API change, run `./gradlew updateKotlinAbi` and commit the new dump.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // AGP's built-in Kotlin leaves this convention unset, so the update task fails without it.
        referenceDumpDir.set(layout.projectDirectory.dir("api"))
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        allWarningsAsErrors.set(true)
        // Holding the language at 2.1 is deliberate (see above), so the compiler's "language
        // version 2.1 is deprecated" notice is silenced rather than failing the build.
        freeCompilerArgs.add("-Xsuppress-version-warnings")
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

val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    group = "documentation"
    description =
        "Packages the Dokka Javadoc output into the -javadoc jar published to Maven Central."
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
    archiveClassifier.set("javadoc")
}

mavenPublishing {
    publishToMavenCentral()

    // AGP's own withJavadocJar() runs a bundled Dokka 1.4.32 engine on a detached
    // configuration that neither the dependency report nor the floors above can reach (old
    // Jackson included), so the javadoc jar is built from Dokka 2.x above and attached to
    // the publication instead.
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = SourcesJar.Sources(),
            javadocJar = JavadocJar.None()
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

    val debugTree = fileTree(
        mapOf(
            "dir" to
                layout.buildDirectory.dir(
                    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
                ).get().asFile,
            "excludes" to fileFilter
        )
    )

    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(
            mapOf(
                "dir" to layout.buildDirectory.get().asFile,
                "includes" to listOf(
                    "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
                    "outputs/code_coverage/debugAndroidTest/connected/**/*.exec"
                )
            )
        )
    )
}

// Configure Sonar
sonar {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file(
                "reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml"
            ).get().asFile.path
        )
    }
}

tasks.sonar {
    dependsOn("jacocoAndroidTestReport")
}

// KGP 2.4 registers the ABI tasks for AGP's built-in Kotlin but never hands the dump task any
// classes: its Android wiring only runs for the separate kotlin-android plugin, which AGP 9 no
// longer applies. Left alone, the dump is empty and the check passes vacuously. Feed it the
// release classes the same way KGP does for a single JVM target. The dump task is internal to
// KGP, hence reflection; if a KGP upgrade changes it, this fails the build instead of silently
// checking nothing (and an empty dump would also fail against the committed one).
tasks.named("internalDumpKotlinAbi") {
    val releaseClasses = files(
        tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileReleaseKotlin")
            .flatMap { it.destinationDirectory }
    )
    inputs.files(releaseClasses).withPropertyName("releaseClasses")
    val targetInfo = Class.forName(
        "org.jetbrains.kotlin.gradle.tasks.abi.KotlinAbiDumpTaskImpl\$JvmTargetInfo",
        true,
        javaClass.classLoader
    ).getConstructor(String::class.java, FileCollection::class.java)

    @Suppress("UNCHECKED_CAST")
    val jvm = javaClass.getMethod("getJvm").invoke(this) as ListProperty<Any>
    jvm.add(targetInfo.newInstance("", releaseClasses))
}
