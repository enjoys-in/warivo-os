// Warivo Companion — root build file.
// Its own Gradle project, not a module of launcher/: the two apps ship separately, target
// different devices and should never be able to break each other's build.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
