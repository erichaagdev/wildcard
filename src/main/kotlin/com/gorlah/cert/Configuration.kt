package com.gorlah.cert

data class Configuration(
    val bucket: String,
    val domain: String,
    val email: String,
    val location: String?,
)