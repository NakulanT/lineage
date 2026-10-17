import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ui"))
}

application {
    // Opens the window on Mac, Windows and Linux: ./gradlew :desktop:run
    mainClass.set("lineage.desktop.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Xmx1g", "-Dsun.java2d.opengl=false",
        // lets the Mac trackpad pinch gesture reach the app (ignored on Windows and Linux)
        "--add-exports", "java.desktop/com.apple.eawt.event=ALL-UNNAMED",
    )
}
