package com.gorlah.cert.acme

import org.shredzone.acme4j.challenge.Dns01Challenge

interface DnsChallengeProcessor {
    fun process(domain: String, challenge: Dns01Challenge)
    fun cleanup(domain: String, challenge: Dns01Challenge)
}