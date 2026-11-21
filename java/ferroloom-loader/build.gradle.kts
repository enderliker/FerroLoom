plugins { `java-library` }

val loaderJar = rootProject.file(".runtime/fabric/fabric-loader-0.20.1.jar")
val shimJar = project(":ferroloom-shim").layout.buildDirectory.file("generated/shim.jar")
val shimModuleJar = project(":ferroloom-shim").layout.buildDirectory.file("libs/ferroloom-shim.jar")
// The sibling API project is a development artefact; tests that need it skip when it is absent.
// -PferroApiJar (used by the API repo's CI) wins; otherwise take the newest binary jar, never the
// sources jar, so a version bump does not turn the integration test into a silent skip.
val apiDir = rootProject.file("../../FerroFabric-API/build/libs")
val discoveredApiJar = apiDir.listFiles { file ->
    file.isFile && file.name.startsWith("ferrofabric-api-") &&
        file.name.endsWith(".jar") && !file.name.contains("-sources")
}?.maxByOrNull { it.name }
val apiJar = providers.gradleProperty("ferroApiJar").map { file(it) }.orNull ?: discoveredApiJar

dependencies {
    implementation(project(":ferroloom-runtime"))
    compileOnly(files(loaderJar))
    implementation(files(rootProject.file(".runtime/fabric/asm.jar")))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(files(loaderJar))
    testImplementation(files(rootProject.file(".runtime/fabric/asm.jar")))
    testImplementation(project(":ferroloom-shim"))
    testImplementation(files(shimJar))
    testRuntimeOnly(files(shimJar))
    testRuntimeOnly(fileTree(rootProject.file(".runtime/fabric")) { include("*.jar") })
    if (apiJar != null && apiJar.isFile) testImplementation(files(apiJar))
}

tasks.withType<JavaCompile> { options.release.set(25) }
tasks.compileJava { dependsOn(":ferroloom-shim:generateShim") }
tasks.compileTestJava { dependsOn(":ferroloom-shim:jar") }
tasks.test {
    useJUnitPlatform()
    forkEvery = 1
    dependsOn(":ferroloom-shim:jar")
    systemProperty("ferro.shim.jar", shimModuleJar.get().asFile.absolutePath)
    // Tests read this only to decide whether the API integration test can run; they set
    // ferro.apiJar themselves, so the other tests see no API at all.
    systemProperty("ferro.testApiJar", apiJar?.absolutePath ?: "")
}
