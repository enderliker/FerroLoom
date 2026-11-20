plugins { `java-library` }

val loaderJar = rootProject.file(".runtime/fabric/fabric-loader-0.20.1.jar")
val shimJar = project(":ferroloom-shim").layout.buildDirectory.file("generated/shim.jar")
val shimModuleJar = project(":ferroloom-shim").layout.buildDirectory.file("libs/ferroloom-shim.jar")

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
}

tasks.withType<JavaCompile> { options.release.set(25) }
tasks.compileJava { dependsOn(":ferroloom-shim:generateShim") }
tasks.compileTestJava { dependsOn(":ferroloom-shim:jar") }
tasks.test {
    useJUnitPlatform()
    forkEvery = 1
    dependsOn(":ferroloom-shim:jar")
    systemProperty("ferro.shim.jar", shimModuleJar.get().asFile.absolutePath)
}
