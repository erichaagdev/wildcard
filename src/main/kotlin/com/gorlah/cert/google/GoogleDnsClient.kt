package com.gorlah.cert.google

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.gorlah.cert.DnsAnswer
import com.gorlah.cert.DnsClient
import okhttp3.OkHttpClient
import okhttp3.Request

class GoogleDnsClient private constructor(): DnsClient {

    companion object {
        private val jsonMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()!!
        private val client = OkHttpClient()

        fun getDefaultInstance(): DnsClient {
            return GoogleDnsClient()
        }
    }

    override fun resolveTxt(domain: String): List<DnsAnswer> {
        val request = Request.Builder()
            .get()
            .url("https://dns.google/resolve?name=$domain&type=TXT")
            .build()

        return client.newCall(request).execute().use {
            val response = jsonMapper.readTree(it.body?.string())
            val answers = response["Answer"] ?: return listOf()
            jsonMapper.convertValue(answers)
        }
    }
}