package com.example.quickstash.data.settings

data class ConnectionSettings(
    val host: String = "",
    val secondaryHost: String = "",
    val port: Int = 445,
    val share: String = "",
    val username: String = "",
    val password: String = "",
    val rootPath: String = "",
    val skipSeconds: Int = 10,
    val slideshowSeconds: Int = 5,
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && share.isNotBlank()
}
