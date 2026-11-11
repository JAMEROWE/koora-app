pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central first: unblocks environments
        // where repo.maven.apache.org rate-limits the shared IP.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "Koora"
include(":app")
