package top.e404.status.render.client

import kotlinx.serialization.json.Json

internal object ClientJson {
    val instance: Json = Json {
        // 下游只关心当前客户端声明的字段，服务端新增字段时保持兼容。
        ignoreUnknownKeys = true
    }
}
