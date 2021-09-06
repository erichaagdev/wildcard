package com.gorlah.cert

import com.fasterxml.jackson.annotation.JsonProperty

data class DnsAnswer(
    val data: String,
    val name: String,

    @field:JsonProperty("TTL")
    val ttl: Int,
)
