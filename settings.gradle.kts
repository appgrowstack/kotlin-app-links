pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-app-links"

// Kotlin/JVM だけ。Android の SDK を要らない（Play Install Referrer・App Link・製品ページを開くのはアプリが渡す口）
include(":links-core")
