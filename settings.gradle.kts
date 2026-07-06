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
        google()
        mavenCentral()
    }
}

rootProject.name = "note-assistant-android"

include(":app")
include(":core-common")
include(":notes-domain")
include(":notes-data")
include(":notes-ui")
include(":assistant-mcp-base")
include(":assistant-bridge")
include(":assistant-runtime")
include(":assistant-tools")
include(":assistant-wakeword")
include(":app-settings")
