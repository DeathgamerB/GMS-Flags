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
        maven {
            url = uri("https://jitpack.io")
        }
    }
}
rootProject.name = "GMS Flags"
include(":app")
include(":core:platform")
include(":core:ui")
include(":data:repository")
include(":data:network")
include(":data:preferences")
include(":features:onboarding")
include(":features:suggestions")
include(":features:search")
include(":features:saved")
include(":features:updates")
include(":features:flagsChange")
include(":features:settings")
include(":data:repository:impl")
include(":data:network:impl")
include(":data:databases:local")
include(":data:databases:local:impl")
include(":data:databases:gms")
include(":core:common")
include(":data:databases:gms:impl")
include(":data:preferences:impl")
include(":features:flagsFile")
include(":domain")
include(":core:byteUtils")
