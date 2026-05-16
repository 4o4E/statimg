plugins {
    kotlin("jvm") version Versions.KOTLIN
    kotlin("plugin.serialization") version Versions.KOTLIN apply false
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.application")
    apply(plugin = "com.github.johnrengelman.shadow")

    group = Versions.GROUP
    version = providers.gradleProperty("version").orElse(Versions.VERSION).get()

    repositories {
        mavenCentral()
        maven("https://nexus.e404.top:3443/repository/maven-snapshots/")
    }

    dependencies {
        if (!name.startsWith("http-server-")) return@dependencies
        val os = name.removePrefix("http-server-")
        implementation(project(":http-server")) {
            exclude("org.jetbrains.skiko")
        }

        // skiko
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

tasks.register("manualTest") {
    group = "verification"
    description = "运行所有手动渲染测试。"
    dependsOn(":core:manualTest")
}
