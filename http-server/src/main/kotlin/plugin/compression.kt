package top.e404.statimg.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize

fun Application.compression() {
    install(Compression) {
        gzip {
            priority = 1.0
            // 只压缩大于 1KB 的内容
            minimumSize(1024)
        }
    }
}