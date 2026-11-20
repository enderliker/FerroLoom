plugins { `java-library` }

val generatedJar = layout.buildDirectory.file("generated/shim.jar")

val generateShim by tasks.registering(JavaExec::class) {
    dependsOn(":shim-generator:classes")
    classpath = project(":shim-generator").sourceSets["main"].runtimeClasspath
    mainClass.set("dev.ferroloom.shimgen.ShimGeneratorMain")
    args(
        rootProject.file(".runtime/minecraft/client.jar").absolutePath,
        rootProject.file("shim/impl").absolutePath,
        generatedJar.get().asFile.absolutePath,
    )
    inputs.file(rootProject.file(".runtime/minecraft/client.jar"))
    inputs.dir(rootProject.file("shim/impl"))
    outputs.file(generatedJar)
}

sourceSets {
    main {
        compileClasspath += files(generatedJar)
        runtimeClasspath += files(generatedJar)
    }
}

tasks.compileJava { dependsOn(generateShim) }
tasks.jar {
    archiveBaseName.set("ferroloom-shim")
    archiveVersion.set("")
    from(zipTree(generatedJar))
}

dependencies {
    implementation(project(":ferroloom-runtime"))
    compileOnly(files(rootProject.file(".runtime/fabric/fabric-loader-0.20.1.jar")))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(files(generatedJar))
}

tasks.withType<JavaCompile> { options.release.set(25) }
tasks.test {
    useJUnitPlatform()
    dependsOn(generateShim)
    systemProperty("ferro.shim.jar", generatedJar.get().asFile.absolutePath)
}
