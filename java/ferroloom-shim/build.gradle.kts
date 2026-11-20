plugins { `java-library` }

dependencies {
    implementation(project(":ferroloom-runtime"))
}

tasks.withType<JavaCompile> { options.release.set(25) }
