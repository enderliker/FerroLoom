plugins { `java-library` }

repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Ruling: no JDK 21 is installed; target 21 bytecode with the installed JDK 27.
tasks.withType<JavaCompile> { options.release.set(21) }

tasks.test { useJUnitPlatform() }