package com.gorlah.cert.namecheap

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.gorlah.cert.DnsChallengeException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Paths

class NamecheapClient private constructor(
    private val credentials: NamecheapCredentials,
    private val clientIp: String,
) {

    companion object {
        private val jsonMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()!!
        private val xmlMapper = XmlMapper.builder().findAndAddModules().build()!!
        private val client = OkHttpClient()

        fun getDefaultInstance(): NamecheapClient {
            val credentialsLocation = System.getenv("NAMECHEAP_CREDENTIALS")
                ?: throw AssertionError("Environment variable 'NAMECHEAP_CREDENTIALS' must be defined.")
            val credentials = jsonMapper.readValue<NamecheapCredentials>(Paths.get(credentialsLocation).toFile())
            val clientIp = fetchClientIp()
            return NamecheapClient(credentials, clientIp)
        }

        private fun fetchClientIp(): String {
            val request = Request.Builder()
                .get()
                .url("https://checkip.amazonaws.com/")
                .build()

            return client.newCall(request).execute().use {
                it.body?.string()?.trim() ?: throw IllegalStateException("Unable to determine public client IP.")
            }
        }
    }

    fun getDnsHosts(domain: String): List<DnsHost> {
        val url = urlBuilder()
            .addQueryParameter("Command", "namecheap.domains.dns.getHosts")
            .addQueryParameter("SLD", domain.split(".")[0])
            .addQueryParameter("TLD", domain.split(".").drop(1).joinToString("."))
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        return client.newCall(request).execute().use {
            val response = xmlMapper.readTree(it.body?.string())
            if (response["Status"].textValue() == "OK") {
                val dnsHosts = response["CommandResponse"]["DomainDNSGetHostsResult"]["host"]
                jsonMapper.convertValue(dnsHosts)
            } else {
                if (response["Status"].textValue() == "ERROR") {
                    if (response["Errors"].any { error -> error["Number"].textValue() == "1011150" }) {
                        throw DnsChallengeException("Error processing request for current IP of '$clientIp'. Verify whitelisted IPs at: https://ap.www.namecheap.com/settings/tools/apiaccess/whitelisted-ips")
                    } else {
                        throw DnsChallengeException("Error processing request")
                    }
                } else {
                    throw DnsChallengeException("Unexpected error")
                }
            }
        }
    }

    fun setDnsHosts(domain: String, dnsHosts: List<DnsHost>): List<DnsHost> {
        val url = urlBuilder()
            .addQueryParameter("Command", "namecheap.domains.dns.setHosts")
            .addQueryParameter("SLD", domain.split(".")[0])
            .addQueryParameter("TLD", domain.split(".").drop(1).joinToString("."))

        dnsHosts
            .mapIndexed { index, it -> index + 1 to it}
            .forEach { (index, it) -> url
                .addQueryParameter("HostName$index", it.name)
                .addQueryParameter("RecordType$index", it.type)
                .addQueryParameter("Address$index", it.address)
                .addQueryParameter("TTL$index", it.ttl.toString())
            }

        val request = Request.Builder()
            .get()
            .url(url.build())
            .build()

        return client.newCall(request).execute().use {
            dnsHosts
        }
    }

    private fun urlBuilder(): HttpUrl.Builder {
        return HttpUrl.Builder()
            .scheme("https")
            .host("api.namecheap.com")
            .addPathSegments("xml.response")
            .addQueryParameter("ApiUser", credentials.apiUser)
            .addQueryParameter("ApiKey", credentials.apiKey)
            .addQueryParameter("UserName", credentials.userName)
            .addQueryParameter("ClientIp", clientIp)
    }

    private data class NamecheapCredentials(
        @field:JsonProperty("api_key")
        val apiKey: String,

        @field:JsonProperty("api_user")
        val apiUser: String,

        @field:JsonProperty("user_name")
        val userName: String,
    )

    data class DnsHost(
        @field:JsonProperty("Name")
        val name: String,

        @field:JsonProperty("Type")
        val type: String,

        @field:JsonProperty("Address")
        val address: String,

        @field:JsonProperty("TTL")
        val ttl: Int,
    )
}