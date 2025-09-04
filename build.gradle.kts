plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

application {
    mainClass.set("app.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Koog core + helpers
    implementation("ai.koog:agents-core-jvm:0.3.0")
    implementation("ai.koog:agents-tools-jvm:0.3.0")
    implementation("ai.koog:prompt-executor-ollama-client:0.3.0")
    implementation("ai.koog:prompt-llm-jvm:0.3.0")
    implementation("ai.koog:prompt-executor-llms-all-jvm:0.3.0")

    // Keep all Ktor modules aligned to the same version
    implementation(platform("io.ktor:ktor-bom:2.3.12"))  // ← aligns versions

    // Ktor client (JVM)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")                  // your engine
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // (Optional but safe) include the general plugins bundle — contains HttpTimeout on 2.3.x
    implementation("io.ktor:ktor-client-plugins")

    // Core coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Nice-to-have for date handling
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    //Loading .env into the code
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // (Optional) Logging backend so SLF4J stops warning
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

kotlin {
    jvmToolchain(17)
}
