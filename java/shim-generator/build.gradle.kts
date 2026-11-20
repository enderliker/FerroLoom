plugins { `java-library` }

dependencies {
    implementation(files(rootProject.file(".runtime/fabric/asm.jar"), rootProject.file(".runtime/fabric/asm-tree.jar")))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> { options.release.set(25) }
tasks.test { useJUnitPlatform() }
