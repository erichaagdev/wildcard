package com.gorlah.cert.dns

interface DnsClient {
    fun resolveTxt(domain: String): List<Answer>
}