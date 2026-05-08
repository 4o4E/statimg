plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
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

dependencies {
    implementation(project(":core"))
    // ktor
    implementation(ktor("server-core"))
    implementation(ktor("server-netty"))

    implementation(ktor("server-call-logging"))
    implementation(ktor("server-compression"))
    implementation(ktor("server-content-negotiation-jvm"))
    implementation(ktor("serialization-kotlinx-json-jvm"))

    // serialization
    implementation(kotlinx("serialization-core-jvm", "1.5.0"))
    implementation(kotlinx("serialization-json", "1.5.0"))
    // kaml
    implementation(kaml)
}

tasks {
    runShadow {
        workingDir = rootProject.projectDir.resolve("run")
        doFirst {
            if (workingDir.isFile) workingDir.delete()
            workingDir.mkdirs()
        }

        jvmArgs = mutableListOf(
            "-Xmx8g",
            "-Xms8g",
        )
    }

    shadowJar {
        archiveFileName.set("${project.name}.jar")
        val jar = project.rootDir.resolve("jar")
        doLast {
            jar.mkdir()
            project.layout.buildDirectory.get().asFile
                .resolve("libs/${project.name}.jar")
                .copyTo(jar.resolve("${project.name}.jar"), true)
        }
    }
}
