package com.gorlah.cert

interface DnsClient {
    fun resolveTxt(domain: String): List<DnsAnswer>
}