package com.gorlah.cert.namecheap

import com.gorlah.cert.acme.DnsChallengeProcessor
import com.gorlah.cert.dns.DnsClient
import com.gorlah.cert.util.logger
import org.shredzone.acme4j.challenge.Dns01Challenge

class NamecheapDnsChallengeProcessor(
    private val dnsClient: DnsClient
): DnsChallengeProcessor {

    private companion object {
        const val acmeChallenge = "_acme-challenge"
        val client = NamecheapClient.getDefaultInstance()
        val log = logger(NamecheapDnsChallengeProcessor::class)
    }

    override fun process(domain: String, challenge: Dns01Challenge) {
        val txt = NamecheapClient.DnsHost(
            name = acmeChallenge,
            type = "TXT",
            address = challenge.digest,
            ttl = 60
        )
        val dnsHosts = client.getDnsHosts(domain).plus(txt)
        log.info("Adding TXT record for '$acmeChallenge.$domain' with value '${txt.address}'")
        client.setDnsHosts(domain, dnsHosts)

        while (dnsClient.resolveTxt("$acmeChallenge.$domain").none { it.data == challenge.digest }) {
            log.info("TXT record for '$acmeChallenge.$domain' in progress...")
            Thread.sleep(10000)
        }
        log.info("TXT record for '$acmeChallenge.$domain' finished")

        challenge.trigger()
        log.info("Triggered DNS challenge for '$acmeChallenge.$domain'")
    }

    override fun cleanup(domain: String, challenge: Dns01Challenge) {
        val dnsHosts = client.getDnsHosts(domain)
            .filter { it.name != acmeChallenge && it.address != challenge.digest }
        log.info("Removing TXT record for '$acmeChallenge.$domain' with value '${challenge.digest}'")
        client.setDnsHosts(domain, dnsHosts)
        log.info("Removed TXT record for '$acmeChallenge.$domain' with value '${challenge.digest}'")
    }
}