// A throwaway app that depends on the SDK the way a customer's app does: by Maven coordinates,
// resolved from mavenLocal() after `publishToMavenLocal`. It is its own Gradle build, not part
// of the SDK's, so nothing can reach the SDK's sources. Run it through scripts/package-check.sh.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The SDK comes from mavenLocal only, so a stale copy on Maven Central cannot stand in
        // for the one just built.
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroup("com.formbricks") }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "formbricks-package-check-consumer"
