plugins {
    id("java-library")
}

repositories {
    maven("https://maven.enginehub.org/repo/")
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
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
}
