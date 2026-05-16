import org.gradle.api.component.AdhocComponentWithVariants

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

val manualTest by sourceSets.creating {
    kotlin.srcDir("src/manualTest/kotlin")
    resources.srcDir("src/manualTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[manualTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[manualTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

fun currentSkikoTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val suffix = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
    return when {
        os.contains("windows") -> "windows-$suffix"
        os.contains("linux") -> "linux-$suffix"
        else -> error("Unsupported OS for core Skiko runtime: $os")
    }
}

dependencies {
    // slf4j
    implementation("org.slf4j:slf4j-api:2.0.7")
    // log4j2
    implementation(log4j("core"))
    implementation(log4j("slf4j2-impl")) {
        exclude("org.slf4j")
    }
    // 异步
    implementation("com.lmax:disruptor:3.4.4")

    api(ktor("client-core-jvm"))
    api(ktor("client-okhttp-jvm"))

    // serialization
    api(kotlinx("serialization-core-jvm", "1.5.0"))
    api(kotlinx("serialization-json", "1.5.0"))

    // skiko
    implementation(skiko(currentSkikoTarget()))
    implementation("top.e404.tavolo:tavolo-common:${Versions.TAVOLO}")
    implementation("top.e404.tavolo:tavolo-graphics:${Versions.TAVOLO}")

    // test
    testImplementation(kotlin("test", Versions.KOTLIN))
    // kaml
    testImplementation(kaml)
}

java {
    withSourcesJar()
}

(components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
    skip()
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["java"])
            artifactId = "github-readme-stats-render-core"

            pom {
                name.set("github-readme-stats-render-core")
                description.set("Core rendering and fetching library for github-readme-stats-render.")
                url.set("https://github.com/4o4E/github-readme-stats-render")

                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("4o4E")
                        name.set("4o4E")
                    }
                }

                scm {
                    url.set("https://github.com/4o4E/github-readme-stats-render")
                    connection.set("scm:git:https://github.com/4o4E/github-readme-stats-render.git")
                    developerConnection.set("scm:git:https://github.com/4o4E/github-readme-stats-render.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/4o4E/github-readme-stats-render")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.register<Test>("manualTest") {
    group = "verification"
    description = "运行需要人工查看输出图片的渲染测试。"
    testClassesDirs = manualTest.output.classesDirs
    classpath = manualTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    workingDir = rootProject.projectDir.resolve("run").also { it.mkdir() }
    systemProperty("manualTest.outputDir", rootDir.resolve("run").absolutePath)
    useJUnitPlatform()
}
