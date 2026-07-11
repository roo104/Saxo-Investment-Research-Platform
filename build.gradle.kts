plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
}

group = "jp"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.3")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.mysql:mysql-connector-j")
    // Netty's native macOS DNS resolver for Apple Silicon; without the matching classifier Netty
    // logs an UnsatisfiedLinkError and falls back to JVM DNS. Version is supplied by the Boot BOM.
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mysql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Netty loads native libraries (transport, DNS resolver, etc.) via System.loadLibrary. On JDK 25
// this triggers "restricted method has been called" warnings unless native access is granted
// explicitly; the flag opts the unnamed module (Netty is not modularized) in and silences them.
tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// For the packaged executable jar there is no command line to pass flags on, so bake the grant
// into the manifest instead: `java -jar` honors Enable-Native-Access exactly like the CLI flag.
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    manifest {
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}
