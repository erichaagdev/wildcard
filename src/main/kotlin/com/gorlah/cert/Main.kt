package com.gorlah.cert

import com.gorlah.cert.acme.AcmeService
import com.gorlah.cert.acme.AuthenticationService
import com.gorlah.cert.acme.AuthorizationProcessor
import com.gorlah.cert.acme.DefaultAcmeService
import com.gorlah.cert.acme.DefaultAuthenticationService
import com.gorlah.cert.acme.DefaultAuthorizationProcessor
import com.gorlah.cert.acme.DnsChallengeProcessor
import com.gorlah.cert.configuration.CommandLineConfigurationService
import com.gorlah.cert.dns.GoogleDnsClient
import com.gorlah.cert.file.FileRepository
import com.gorlah.cert.file.GoogleFileRepository
import com.gorlah.cert.keypair.KeyPairGenerator
import com.gorlah.cert.keypair.RSAKeyPairGenerator
import com.gorlah.cert.namecheap.NamecheapDnsChallengeProcessor
import com.gorlah.cert.util.logger
import org.shredzone.acme4j.Authorization
import org.shredzone.acme4j.Login
import org.shredzone.acme4j.Order
import org.shredzone.acme4j.Session
import org.shredzone.acme4j.Status
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date

class Main

private val log = logger(Main::class)

private val dnsClient = GoogleDnsClient.getDefaultInstance()
private val dnsChallengeProcessor: DnsChallengeProcessor = NamecheapDnsChallengeProcessor(dnsClient)
private val authorizationProcessor: AuthorizationProcessor = DefaultAuthorizationProcessor(dnsChallengeProcessor)

private lateinit var acmeService: AcmeService
private lateinit var authenticationService: AuthenticationService

fun main(arguments: Array<String>) {
    val configuration = CommandLineConfigurationService(arguments).load() ?: return
    val fileRepository: FileRepository = GoogleFileRepository(configuration.bucket, configuration.location ?: "")
    val keyPairGenerator: KeyPairGenerator = RSAKeyPairGenerator()
    val session = Session("acme://letsencrypt.org/staging")
    authenticationService = DefaultAuthenticationService(fileRepository, keyPairGenerator, session)
    val login = fetchLogin(configuration.email)
    acmeService = DefaultAcmeService(fileRepository, keyPairGenerator, login)
    processOrder(configuration.domain)
}

private fun processOrder(domain: String): Order? {
    return try {
        val order = fetchOrder(domain)
        val certificate = order.certificate?.certificate
        if (order.status == Status.VALID && certificate != null) {
            val expiration = certificate.notAfter
            val renewal = expiration.toInstant().minus(30, ChronoUnit.DAYS)
            if (Instant.now().isBefore(renewal)) {
                log.info("Current certificate expires on '${dateFormat(expiration)}' and can be renewed starting on '${dateFormat(renewal)}'")
            }
        }
        if (order.status == Status.PENDING) {
            order.authorizations.forEach {
                if (it.status == Status.PENDING) {
                    processAuthorization(it)
                }
            }
            if (order.authorizations.none { it.status != Status.VALID }) {
                finalizeOrder(order, domain)
            }
        }
        order
    } catch (e: Exception) {
        log.error(e.message, e)
        null
    }
}

private fun fetchLogin(email: String): Login {
    log.info("Logging in with account '$email'")
    val login = authenticationService.login(email)
    return if (login == null) {
        log.debug("Account for '$email' not found")
        log.debug("Creating account for '$email'")
        authenticationService.createAccount(email).apply {
            log.debug("Account created for '$email' with location '$accountLocation'")
        }
    } else {
        log.debug("Logged in with account for '$email' with location '${login.accountLocation}'")
        login
    }
}

private fun fetchOrder(domain: String): Order {
    log.debug("Fetching order for '$domain'")
    val order = acmeService.getOrder(domain)
    return if (order == null) {
        log.debug("Order for '$domain' not found")
        log.info("Creating order for '$domain'")
        acmeService.createOrder(domain).apply {
            log.debug("Order created for '$domain' with location '$location'")
        }
    } else {
        log.info("Found order for '$domain' with status '${order.status}'")
        order
    }
}

private fun processAuthorization(authorization: Authorization) {
    log.info("Processing authorization for '${authorization.identifier.domain}'")
    val cleanup = Thread { authorizationProcessor.cleanup(authorization) }
    try {
        Runtime.getRuntime().addShutdownHook(cleanup)
        authorizationProcessor.process(authorization)
    } finally {
        try { authorization.update() } catch (_: Exception) { }
        log.info("Authorization for '${authorization.identifier.domain}' finished with status '${authorization.status}'")
        authorizationProcessor.cleanup(authorization)
        Runtime.getRuntime().removeShutdownHook(cleanup)
    }
}

private fun finalizeOrder(order: Order, domain: String) {
    log.info("Finalizing order for '$domain'")
    acmeService.finalizeOrder(order, domain)
    log.debug("Order for '$domain' finished with status '${order.status}'")
    val certificate = order.certificate?.certificate
    if (certificate != null) {
        val expiration = dateFormat(certificate.notAfter)
        log.info("Certificate for '$domain' expires on '$expiration'")
    }
}

private fun dateFormat(date: Date): String {
    return dateFormat(date.toInstant())
}

private fun dateFormat(instant: Instant): String {
    return DateTimeFormatter.ISO_LOCAL_DATE.format(instant.atZone(ZoneOffset.UTC))
}