plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(ktor("client-core-jvm"))
    api(kotlinx("serialization-json", "1.5.0"))
    implementation(ktor("client-okhttp-jvm"))

    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(ktor("client-mock-jvm"))
}
