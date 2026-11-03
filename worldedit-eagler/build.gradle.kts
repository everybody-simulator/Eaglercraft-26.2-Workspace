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
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.0")
}
