plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.patcher") version "1.7.7"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val leafMavenPublicUrl = "https://maven.nostal.ink/repository/maven-snapshots/"

repositories {
    mavenCentral()
    maven(paperMavenPublicUrl) {
        content { onlyForConfigurations(configurations.paperclip.name) }
    }
}

dependencies {
    remapper("net.fabricmc:tiny-remapper:0.10.4:fat")
    decompiler("org.vineflower:vineflower:1.10.1")
    paperclip("io.papermc:paperclip:3.0.3")
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
        maven("https://ci.pluginwiki.us/plugin/repository/everything/") // Leaf Config - ConfigurationMaster-API
    }
}

paperweight {
    serverProject.set(project(":leaf-server"))

    remapRepo = paperMavenPublicUrl
    decompileRepo = paperMavenPublicUrl

    usePaperUpstream(providers.gradleProperty("paperCommit")) {
        withPaperPatcher {
            apiPatchDir = layout.projectDirectory.dir("patches/api")
            apiOutputDir = layout.projectDirectory.dir("Leaf-API")

            serverPatchDir = layout.projectDirectory.dir("patches/server")
            serverOutputDir = layout.projectDirectory.dir("Leaf-Server")
        }

        patchTasks.register("generatedApi") {
            isBareDirectory = true
            upstreamDirPath = "paper-api-generator/generated"
            patchDir = layout.projectDirectory.dir("patches/generated-api")
            outputDir = layout.projectDirectory.dir("paper-api-generator/generated")
        }
    }
}

tasks.generateDevelopmentBundle {
    apiCoordinates = "cn.dreeam.leaf:leaf-api"
    libraryRepositories = listOf(
        "https://repo.maven.apache.org/maven2/",
        paperMavenPublicUrl,
        leafMavenPublicUrl,
    )
}

publishing {
    if (project.providers.gradleProperty("publishDevBundle").isPresent) {
        publications.create<MavenPublication>("devBundle") {
            artifact(tasks.generateDevelopmentBundle) {
                artifactId = "dev-bundle"
            }
        }
    }
}

allprojects {
    publishing {
        repositories {
            maven {
                name = "leaf"
                url = uri(leafMavenPublicUrl)

                credentials.username = System.getenv("REPO_USER")
                credentials.password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}