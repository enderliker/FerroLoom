plugins { `java-library` }

val loaderJar = rootProject.file(".runtime/fabric/fabric-loader-0.20.1.jar")

dependencies {
    implementation(project(":ferroloom-runtime"))
    implementation(project(":ferroloom-shim"))
    compileOnly(files(loaderJar))
    implementation(files(rootProject.file(".runtime/fabric/asm.jar")))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(files(loaderJar))
    testImplementation(files(rootProject.file(".runtime/fabric/asm.jar")))
    testImplementation(project(":ferroloom-shim"))
    testRuntimeOnly(fileTree(rootProject.file(".runtime/fabric")) { include("*.jar") })
}

tasks.withType<JavaCompile> { options.release.set(25) }
tasks.test { useJUnitPlatform() }
