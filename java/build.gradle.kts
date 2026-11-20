subprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

tasks.register<Exec>("fetchRuntime") {
    commandLine("bash", rootProject.file("../tools/fetch-runtime.sh").absolutePath)
}
