package com.example.quickstash.domain

data class TierInfo(
    val manual: String? = null,
    val auto: String? = null,
) {
    val effective: String? get() = manual ?: auto
}
