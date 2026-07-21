import java.io.File

// Constante centralizada para a versão da aplicação
val appVersion = "1.5"

// Versões centralizadas para reuso nas dependências e no build-info
// (exibidas dinamicamente na tela "Sobre" — não há API em runtime para lê-las
// sem adicionar essas libs ao classpath de compilação).
val javafxVersion = "25.0.3"
val h2Version = "2.3.232"

val platform = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "win"
    org.gradle.internal.os.OperatingSystem.current().isLinux -> "linux"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "mac"
    else -> throw GradleException("Unsupported OS")
}

// --- Helper para o JPackage (usando a API padrão do Java) ---
val osName = System.getProperty("os.name").lowercase()
val javafxOsClassifier = when {
    "windows" in osName -> "win"
    "mac" in osName -> "mac"
    "linux" in osName -> "linux"
    else -> throw GradleException("Unsupported OS for JavaFX: $osName")
}

plugins {
    id("org.springframework.boot") version "3.5.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
    //id("application") - removido para teste de problema no shadow
    id("io.github.goooler.shadow") version "8.1.8"
    id("io.freefair.lombok") version "9.5.0"
}

group = "org.desviante"
version = appVersion

repositories {
    mavenCentral()
    maven { url = uri("https://dlsc.com/maven") }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Resolve o executável "jpackage" do MESMO toolchain (Java 25) usado para compilar o
// projeto. Sem isso, as tasks jpackage* chamam o "jpackage" do PATH do sistema, que
// pode apontar para outra versão de JDK e embutir um runtime incompatível com o
// bytecode compilado (UnsupportedClassVersionError ao rodar o app empacotado).
val javaToolchainService = extensions.getByType<JavaToolchainService>()
val jpackageExecutable: File = javaToolchainService.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.get().executablePath.asFile.parentFile.resolve(
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) "jpackage.exe" else "jpackage"
)

// A exclusão do commons-logging continua sendo uma boa prática.
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    exclude(group = "commons-logging", module = "commons-logging")
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
    exclude(group = "javax.servlet")
    exclude(group = "ch.qos.logback")
}

dependencies {

    val javafxOsClassifier = when {
        org.gradle.internal.os.OperatingSystem.current().isWindows -> "win"
        org.gradle.internal.os.OperatingSystem.current().isLinux -> "linux"
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "mac"
        else -> throw GradleException("Unsupported OS for JavaFX")
    }

    // Biblioteca CalendarFX
    implementation("com.calendarfx:view:12.0.1")

    //micrometer e blockhound
    implementation(platform("io.micrometer:micrometer-bom:1.15.2"))
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:context-propagation:1.1.3") // substitui micrometer-context
    implementation("io.projectreactor.tools:blockhound:1.0.9.RELEASE") // versão compatível
    implementation("com.fasterxml:classmate:1.5.1")

    // logger
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Dependências da aplicação principal
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.glassfish:jakarta.el:4.0.2")
    implementation("org.springframework.boot:spring-boot-starter-jdbc"){
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        exclude(group = "org.apache.logging.log4j")
    }
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    // Liquibase para migrações de banco
    implementation("org.liquibase:liquibase-core:4.24.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    runtimeOnly("com.h2database:h2:$h2Version")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    implementation("com.google.apis:google-api-services-tasks:v1-rev20250518-2.0.0")    
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.api-client:google-api-client-jackson2:2.2.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.auth:google-auth-library-credentials:1.19.0")
    implementation("com.google.oauth-client:google-oauth-client:1.34.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.oauth-client:google-oauth-client-java6:1.34.1")
    implementation("com.google.http-client:google-http-client-jackson2:1.42.3")

    // Dependências de teste
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Adiciona as dependências JavaFX (necessárias para o jlink)
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxOsClassifier")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxOsClassifier")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxOsClassifier")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$javafxOsClassifier")

    // Adiciona dependências Jackson básicas
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")

    // Usa a constante simples para garantir que o compilador não se confunda.
    testImplementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxOsClassifier")
    testImplementation("org.openjfx:javafx-controls:$javafxVersion:$javafxOsClassifier")
    testImplementation("org.openjfx:javafx-fxml:$javafxVersion:$javafxOsClassifier")
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "org.desviante.SimpleTaskBoardManagerApplication"
    }

    // mergeServiceFiles() só mescla META-INF/services/*. Várias dependências do
    // Spring (spring-boot, spring-boot-autoconfigure, etc.) trazem seu próprio
    // META-INF/spring.factories no MESMO caminho; sem merge explícito, o Shadow
    // mantém só um deles e descarta os demais silenciosamente. Isso já causou a
    // ausência das entradas de PropertySourceLoader (Properties/Yaml) do
    // spring-boot core, fazendo o Spring Boot nunca carregar o
    // application.properties do classpath — o app subia sem
    // "spring.datasource.url" nenhum, com "Could not resolve placeholder
    // 'spring.datasource.url'" na inicialização do DataConfig.
    append("META-INF/spring.factories")
    append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
    append("META-INF/spring-autoconfigure-metadata.properties")
}

// Configuração do Spring Boot
springBoot {
    buildInfo {
        // Java e Spring Boot já expõem sua versão em runtime (System.getProperty,
        // SpringBootVersion.getVersion()). JavaFX e H2 (runtimeOnly, fora do classpath
        // de compilação) e Gradle não têm API equivalente aqui, então gravamos os
        // valores conhecidos em build time para a tela "Sobre" ler via BuildProperties.
        properties {
            additional = mapOf(
                "javafx.version" to javafxVersion,
                "gradle.version" to gradle.gradleVersion,
                "h2.version" to h2Version
            )
        }
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.fasterxml.jackson.core") {
            useVersion("2.13.3")
            because("Evitar substituição automática pelo jackson-bom da API do Google")
        }
    }
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("all")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf("--module-path", project.configurations.compileClasspath.get().asPath))
}

// A configuração do bootJar continua a mesma, mas não será usada pelo jlink.
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    archiveClassifier.set("app")
}

// Configuração de testes cross-platform
tasks.withType<Test> {
    useJUnitPlatform()
    
    // Configurações específicas para Linux para evitar problemas de processo
    if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
        maxParallelForks = 1
        forkEvery = 1
        systemProperty("java.awt.headless", "true")
        systemProperty("testfx.robot", "awt")
        systemProperty("testfx.headless", "true")
        systemProperty("prism.order", "sw")
        systemProperty("prism.text", "t2k")
    }
}

// Configuração para jpackage (instalador Windows)
tasks.register<Exec>("jpackage") {
    dependsOn("shadowJar")

    val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
    val appName = "SimpleTaskBoardManager"
    val iconFile = file("src/main/resources/icon.ico")
    val jpackageInputDir = file("${layout.buildDirectory.get()}/jpackage-input")

    commandLine(
        jpackageExecutable.absolutePath,
        "--input", jpackageInputDir.absolutePath,
        "--name", appName,
        "--main-jar", shadowJar.name,
        "--main-class", "org.desviante.SimpleTaskBoardManagerApplication",
        "--type", "exe",
        "--dest", "${layout.buildDirectory.get()}/dist",
        "--java-options", "-Xmx2048m --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED --add-opens=javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED --add-opens=javafx.base/com.sun.javafx.binding=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED --add-opens=com.fasterxml.jackson.databind/com.fasterxml.jackson.databind=com.google.http.client.jackson2 --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.math=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.function=ALL-UNNAMED --add-opens=java.base/java.util.stream=ALL-UNNAMED --add-opens=java.base/java.util.zip=ALL-UNNAMED --add-opens=java.base/java.util.regex=ALL-UNNAMED --add-opens=java.base/java.util.logging=ALL-UNNAMED --add-opens=java.base/java.util.prefs=ALL-UNNAMED --add-opens=java.base/java.util.spi=ALL-UNNAMED --add-opens=java.base/java.util.jar=ALL-UNNAMED",
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--win-upgrade-uuid", "550e8400-e29b-41d4-a716-446655440000",
        "--win-help-url", "https://github.com/lgjor/simple-task-board-manager",
        "--win-update-url", "https://github.com/lgjor/simple-task-board-manager/releases",
        "--vendor", "AuDesviante",
        "--app-version", appVersion,
        "--icon", iconFile.absolutePath
    )
    
    doFirst {
        // Cria o diretório de destino se não existir
        file("${layout.buildDirectory.get()}/dist").mkdirs()

        // Verifica se o ícone existe
        if (!iconFile.exists()) {
            throw GradleException("Ícone não encontrado: ${iconFile.absolutePath}")
        }

        // Recria o diretório de input do jpackage contendo APENAS o jar atual.
        // Evita que jars de builds anteriores (ex: board-x-app.jar, board-x-plain.jar
        // deixados em build/libs) sejam incluídos no classpath do instalador,
        // o que causa "Failed to launch JVM" por classpath inconsistente.
        delete(jpackageInputDir)
        jpackageInputDir.mkdirs()
        copy {
            from(shadowJar)
            into(jpackageInputDir)
        }
    }
}

// Configuração para jpackage Linux (AppImage)
tasks.register<Exec>("jpackageLinux") {
    dependsOn("shadowJar")

    val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
    val appName = "SimpleTaskBoardManager"
    val iconFile = file("src/main/resources/icon.png")
    val jpackageInputDir = file("${layout.buildDirectory.get()}/jpackage-input-linux")

    commandLine(
        jpackageExecutable.absolutePath,
        "--input", jpackageInputDir.absolutePath,
        "--name", appName,
        "--main-jar", shadowJar.name,
        "--main-class", "org.desviante.SimpleTaskBoardManagerApplication",
        "--type", "app-image",
        "--dest", "${layout.buildDirectory.get()}/dist",
        "--java-options", "-Xmx2048m",
        "--vendor", "AuDesviante",
        "--app-version", appVersion,
        "--icon", iconFile.absolutePath
    )
    
    doFirst {
        // Cria o diretório de destino se não existir
        file("${layout.buildDirectory.get()}/dist").mkdirs()

        // Verifica se o ícone existe
        if (!iconFile.exists()) {
            logger.warn("Ícone PNG não encontrado, usando ícone padrão")
        }

        // Recria o diretório de input do jpackage contendo APENAS o jar atual
        // (veja o comentário equivalente na task "jpackage" acima).
        delete(jpackageInputDir)
        jpackageInputDir.mkdirs()
        copy {
            from(shadowJar)
            into(jpackageInputDir)
        }
    }
}

// Configuração para jpackage Linux (DEB package)
tasks.register<Exec>("jpackageLinuxDeb") {
    dependsOn("shadowJar")

    val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
    val appName = "simple-task-board-manager"
    val iconFile = file("src/main/resources/icon.png")
    val jpackageInputDir = file("${layout.buildDirectory.get()}/jpackage-input-deb")

    commandLine(
        jpackageExecutable.absolutePath,
        "--input", jpackageInputDir.absolutePath,
        "--name", appName,
        "--main-jar", shadowJar.name,
        "--main-class", "org.desviante.SimpleTaskBoardManagerApplication",
        "--type", "deb",
        "--dest", "${layout.buildDirectory.get()}/dist",
        "--java-options", "-Xmx2048m",
        "--vendor", "AuDesviante",
        "--app-version", appVersion,
        "--icon", iconFile.absolutePath,
        "--linux-app-category", "Office",
        "--linux-menu-group", "Office",
        "--linux-shortcut",
        "--linux-deb-maintainer", "lucas@desviante.org",
        "--linux-package-name", "simple-task-board-manager"
    )
    
    doFirst {
        // Cria o diretório de destino se não existir
        file("${layout.buildDirectory.get()}/dist").mkdirs()

        // Verifica se o ícone existe
        if (!iconFile.exists()) {
            logger.warn("Ícone PNG não encontrado, usando ícone padrão")
        }

        // Recria o diretório de input do jpackage contendo APENAS o jar atual
        // (veja o comentário equivalente na task "jpackage" acima).
        delete(jpackageInputDir)
        jpackageInputDir.mkdirs()
        copy {
            from(shadowJar)
            into(jpackageInputDir)
        }
    }
}

// Configuração para jpackage Linux (RPM package)
tasks.register<Exec>("jpackageLinuxRpm") {
    dependsOn("shadowJar")

    val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
    val appName = "simple-task-board-manager"
    val iconFile = file("src/main/resources/icon.png")
    val jpackageInputDir = file("${layout.buildDirectory.get()}/jpackage-input-rpm")

    commandLine(
        jpackageExecutable.absolutePath,
        "--input", jpackageInputDir.absolutePath,
        "--name", appName,
        "--main-jar", shadowJar.name,
        "--main-class", "org.desviante.SimpleTaskBoardManagerApplication",
        "--type", "rpm",
        "--dest", "${layout.buildDirectory.get()}/dist",
        "--java-options", "-Xmx2048m",
        "--vendor", "AuDesviante",
        "--app-version", appVersion,
        "--icon", iconFile.absolutePath,
        "--linux-app-category", "Office",
        "--linux-menu-group", "Office",
        "--linux-shortcut",
        "--linux-package-name", "simple-task-board-manager"
    )
    
    doFirst {
        // Cria o diretório de destino se não existir
        file("${layout.buildDirectory.get()}/dist").mkdirs()

        // Verifica se o ícone existe
        if (!iconFile.exists()) {
            logger.warn("Ícone PNG não encontrado, usando ícone padrão")
        }

        // Recria o diretório de input do jpackage contendo APENAS o jar atual
        // (veja o comentário equivalente na task "jpackage" acima).
        delete(jpackageInputDir)
        jpackageInputDir.mkdirs()
        copy {
            from(shadowJar)
            into(jpackageInputDir)
        }
    }
}

// Task para gerar AppImage usando appimagetool (comentado temporariamente)
// tasks.register<Exec>("createAppImage") {
//     // Implementação futura
// }

// Task para gerar Snap package (comentado temporariamente)
// tasks.register<Exec>("createSnap") {
//     // Implementação futura
// }

// Configuração do Javadoc com compatibilidade cross-platform
tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    
    // Configuração básica que funciona em ambos os sistemas operacionais
    // As configurações avançadas são opcionais e podem ser adicionadas manualmente se necessário
}

// Task específica para Linux que pula os testes problemáticos
tasks.register("buildLinux") {
    dependsOn("shadowJar")
    description = "Build para Linux sem executar testes"
}

// Task específica para Windows
tasks.register("buildWindows") {
    dependsOn("build")
    description = "Build completo para Windows"
}