import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.publish.PublishingExtension

plugins {
    kotlin("jvm") version Versions.KOTLIN
    kotlin("plugin.serialization") version Versions.KOTLIN apply false
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    application
    `java-library`
}

val isCi = providers.environmentVariable("GITHUB_ACTIONS")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val isReleaseCi = providers.provider {
    isCi.get() && providers.environmentVariable("GITHUB_REF_TYPE").orNull == "tag"
}
val localSnapshotVersion = "${Versions.VERSION}-SNAPSHOT"
val ciReleaseVersion = providers.environmentVariable("GITHUB_REF_NAME")
val publishVersion = providers.provider {
    if (isReleaseCi.get()) ciReleaseVersion.orNull ?: localSnapshotVersion else localSnapshotVersion
}
val publishGroup = "top.e404.githubreadmestatsrender"
val projectUrl = "https://github.com/4o4E/github-readme-stats-render"
val scmConnection = "scm:git:$projectUrl.git"
val nexusReleasesUrl = "https://nexus.e404.top:3443/repository/maven-releases/"
val nexusSnapshotsUrl = "https://nexus.e404.top:3443/repository/maven-snapshots/"
val publishableProjects = mapOf(
    "core" to "Core rendering and fetching library for github-readme-stats-render.",
    "http-client" to "Typed Ktor HTTP client for github-readme-stats-render.",
)

fun nexusCredential(propertyName: String, ciSecretEnvName: String): Provider<String> =
    if (isCi.get()) providers.environmentVariable(ciSecretEnvName) else providers.gradleProperty(propertyName)

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.application")
    apply(plugin = "com.github.johnrengelman.shadow")

    group = publishGroup
    version = publishVersion.get()

    repositories {
        if (providers.gradleProperty("useMavenLocal").map(String::toBoolean).orElse(false).get()) {
            mavenLocal()
        }
        mavenCentral()
        maven(nexusReleasesUrl)
        maven(nexusSnapshotsUrl)
    }

    dependencies {
        if (!name.startsWith("http-server-")) return@dependencies
        val os = name.removePrefix("http-server-")
        implementation(project(":http-server")) {
            exclude("org.jetbrains.skiko")
        }

        // Skiko 原生运行时需要按平台拆分依赖。
        implementation(skiko(
            when (os) {
                "win" -> "windows-x64"
                else -> "linux-x64"
            }
        ))
    }

    application {
        mainClass.set("top.e404.status.render.App")
        applicationDefaultJvmArgs = listOf(
            "-Dio.netty.tryReflectionSetAccessible=true",
            "--add-opens",
            "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.util=ALL-UNNAMED"
        )
    }

    java {
        withSourcesJar()
    }

    tasks {
        runShadow {
            workingDir = rootDir.resolve("run")
            doFirst {
                if (workingDir.isFile) workingDir.delete()
                workingDir.mkdirs()
            }
        }

        shadowJar {
            archiveFileName.set("${project.name}.jar")
        }

        build {
            if (project.name.startsWith("http-server-")) {
                dependsOn(shadowJar)
            }
        }

        test {
            useJUnitPlatform()
            workingDir = rootProject.projectDir.resolve("run")
            workingDir.mkdir()
        }
    }
}

subprojects {
    val publishDescription = publishableProjects[project.name]
    if (publishDescription != null) {
        apply(plugin = "org.gradle.java-library")
        apply(plugin = "com.vanniktech.maven.publish")

        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            val artifactName = "github-readme-stats-render-${project.name}"

            coordinates(
                groupId = rootProject.group.toString(),
                artifactId = artifactName,
                version = rootProject.version.toString(),
            )
            configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
            if (isReleaseCi.get()) {
                publishToMavenCentral()
                signAllPublications()
            }

            pom {
                name.set(artifactName)
                description.set(publishDescription)
                url.set(projectUrl)

                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("4o4E")
                        name.set("4o4E")
                        email.set("869951226@qq.com")
                        organization.set("4o4E")
                        organizationUrl.set("https://github.com/4o4E")
                    }
                }

                scm {
                    url.set(projectUrl)
                    connection.set(scmConnection)
                    developerConnection.set(scmConnection)
                }
            }
        }

        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "Nexus"
                    val targetUrl = if (rootProject.version.toString().endsWith("-SNAPSHOT")) {
                        nexusSnapshotsUrl
                    } else {
                        nexusReleasesUrl
                    }
                    url = uri(targetUrl)
                    credentials {
                        username = nexusCredential("nexus.username", "NEXUS_USERNAME").orNull
                        password = nexusCredential("nexus.password", "NEXUS_PASSWORD").orNull
                    }
                }
            }
        }

        if (!isReleaseCi.get()) {
            listOf("publishToMavenCentral", "publishAndReleaseToMavenCentral").forEach { taskName ->
                tasks.register(taskName) {
                    group = "publishing"
                    description = "Maven Central 发布保护任务"
                    doFirst {
                        error("Maven Central 只允许通过 CI tag 发布，本地请使用 publishAllPublicationsToNexusRepository 或 publishToMavenLocal。")
                    }
                }
            }
        }

        tasks.matching { it.name.contains("MavenCentral") }.configureEach {
            doFirst {
                if (!isCi.get()) {
                    error("Maven Central 只允许在 CI 发布，本地请使用 Nexus 或 mavenLocal。")
                }
                if (!isReleaseCi.get()) {
                    error("Maven Central 只允许通过 GitHub tag 发布。")
                }
                if (rootProject.version.toString().endsWith("-SNAPSHOT")) {
                    error("Maven Central 只能发布正式版本，SNAPSHOT 请发 Nexus。")
                }
            }
        }
    }
}

tasks.register("manualTest") {
    group = "verification"
    description = "运行所有手动渲染测试。"
    dependsOn(":core:manualTest")
}
