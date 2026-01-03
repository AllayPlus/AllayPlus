pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        gradlePluginPortal()
    }
}

rootProject.name = "AllayPlus"

// include multi modules
include(":api")
include(":server")
include(":codegen")
include(":data")
