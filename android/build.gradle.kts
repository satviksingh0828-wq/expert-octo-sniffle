plugins {
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

// Dependencies are pinned in gradle/libs.versions.toml and build.gradle.kts.
// Generate fresh dependency locks after the new ML Kit libraries pass GitHub CI.
