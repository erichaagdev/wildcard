package com.gorlah.cert.dns

import com.fasterxml.jackson.annotation.JsonProperty

data class Answer(
    val data: String,
    val name: String,

    @field:JsonProperty("TTL")
    val ttl: Int,
)
