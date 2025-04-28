plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.dokka") version "1.9.10"
    id("jacoco")
    id("maven-publish")      
    signing
}

version = "0.1.0"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf(
            "jdk.internal.*",
        )
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
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.databinding.common)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId    = "com.formbricks"
            artifactId = "android"
            version    = version.toString()

            afterEvaluate { from(components["release"]) }

            pom {
                name.set("Formbricks Android SDK")
                description.set("Formbricks anroid SDK")
                url.set("https://github.com/formbricks/android")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("formbricks")
                        name.set("Formbricks")
                        email.set("hola@formbricks.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/formbricks/android.git")
                    developerConnection.set("scm:git:ssh://github.com:formbricks/android.git")
                    url.set("https://github.com/formbricks/android")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH-release"
            url  = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = findProperty("ossrhUsername") as String?
                password = findProperty("ossrhPassword") as String?
             }
        }
    }

    signing {
        sign(publishing.publications["release"])
    }
}
