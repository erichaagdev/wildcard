package com.gorlah.cert.acme

import com.gorlah.cert.util.logger
import org.shredzone.acme4j.Authorization
import org.shredzone.acme4j.Status
import org.shredzone.acme4j.challenge.Dns01Challenge
import org.shredzone.acme4j.exception.AcmeRetryAfterException

class DefaultAuthorizationProcessor(
    private val dnsChallengeProcessor: DnsChallengeProcessor
): AuthorizationProcessor {

    private companion object {
        private val log = logger(DefaultAuthenticationService::class)
    }

    override fun process(authorization: Authorization) {
        dnsChallengeProcessor.process(authorization.identifier.domain, getDnsChallenge(authorization))
        while (authorization.status != Status.VALID) {
            log.debug("Authorization for '${authorization.identifier.domain}' processing with status '${authorization.status}'...")
            Thread.sleep(10000)
            try { authorization.update() } catch (_: AcmeRetryAfterException) { }
        }
    }

    override fun cleanup(authorization: Authorization) {
        dnsChallengeProcessor.cleanup(authorization.identifier.domain, getDnsChallenge(authorization))
    }

    private fun getDnsChallenge(authorization: Authorization): Dns01Challenge {
        return authorization.findChallenge(Dns01Challenge::class.java)
            ?: throw IllegalStateException("${Dns01Challenge.TYPE} challenge not found for authorization " +
                    "${authorization.identifier}!")
    }
}