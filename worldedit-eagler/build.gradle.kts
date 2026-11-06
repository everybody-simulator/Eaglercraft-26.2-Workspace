plugins {
    id("java-library")
}

repositories {
    maven("https://maven.enginehub.org/repo/") {
        // Ignore Gradle module metadata's strict Minecraft-library constraints.
        // The workspace already supplies newer compatible Gson/Guava versions.
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

dependencies {
    implementation(project(":game"))

    // Compile against WorldEdit's platform-independent API only.
    // The server target will own the runtime dependency after the TeaVM
    // compatibility probe has passed.
    implementation("com.sk89q.worldedit:worldedit-core:7.4.0")

    // Minecraft's public signatures expose these types; game keeps them as
    // implementation dependencies, so make them visible to this adapter at compile time.
    compileOnly("com.mojang:datafixerupper:10.0.21")
    compileOnly("com.mojang:brigadier:1.3.10")
    compileOnly("com.mojang:authlib:9.0.75")
    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
}
