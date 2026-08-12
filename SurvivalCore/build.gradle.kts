plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "pl.championsmp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // Oficjalne bezpieczne repozytorium PaperMC
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Podajemy konkretną wersję API dla Minecraft 26.2, aby Gradle pobrał ją bez błędu
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25) // Zgodność kompilacji dla Javy 25
    }
}
