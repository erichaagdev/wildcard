plugins {
    kotlin("jvm") version "1.5.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.5")
    implementation("com.beust:jcommander:1.81")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.12.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.5")
    implementation("com.google.cloud:google-cloud-storage:2.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("org.shredzone.acme4j:acme4j-client:2.12")
    implementation("org.shredzone.acme4j:acme4j-utils:2.12")
    implementation(kotlin("stdlib-jdk8"))
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes("Main-Class" to "com.gorlah.cert.MainKt") }
        from(configurations.runtimeClasspath.get().files.map { if (it.isDirectory) it else zipTree(it) })
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }

    register<JavaExec>("run") {
        classpath = files(jar)
    }
}