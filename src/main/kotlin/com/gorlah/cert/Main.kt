package com.gorlah.cert

import com.gorlah.cert.google.GoogleDnsClient
import com.gorlah.cert.google.GoogleFileRepository
import com.gorlah.cert.namecheap.NamecheapDnsChallengeProcessor
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

private lateinit var configurationService: ConfigurationService

private lateinit var acmeService: AcmeService
private lateinit var authenticationService: AuthenticationService
private lateinit var authorizationProcessor: AuthorizationProcessor
private lateinit var dnsChallengeProcessor: DnsChallengeProcessor
private lateinit var dnsClient: DnsClient
private lateinit var fileRepository: FileRepository
private lateinit var keyPairGenerator: KeyPairGenerator

fun main(arguments: Array<String>) {
    configurationService = initializeConfigurationService(arguments)
    val configuration = configurationService.load() ?: return
    initializeServices(configuration)
    processOrder(configuration.domain)
}

private fun initializeConfigurationService(arguments: Array<String>): ConfigurationService {
    return CommandLineConfigurationService(arguments)
}

private fun initializeServices(configuration: Configuration) {
    dnsClient = GoogleDnsClient.getDefaultInstance()
    dnsChallengeProcessor = NamecheapDnsChallengeProcessor(dnsClient)
    authorizationProcessor = DefaultAuthorizationProcessor(dnsChallengeProcessor)

    fileRepository = GoogleFileRepository(configuration.bucket, configuration.location ?: "")
    keyPairGenerator = RSAKeyPairGenerator()
    val session = fetchSession(configuration.production)
    authenticationService = DefaultAuthenticationService(fileRepository, keyPairGenerator, session)

    val login = fetchLogin(configuration.email)
    acmeService = DefaultAcmeService(fileRepository, keyPairGenerator, login)
}

fun fetchSession(production: Boolean): Session {
    return if (production) Session("acme://letsencrypt.org") else Session("acme://letsencrypt.org/staging")
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
        log.info("Account for '$email' not found")
        log.info("Creating account for '$email'")
        authenticationService.createAccount(email).apply {
            log.info("Account created for '$email' with location '$accountLocation'")
        }
    } else {
        log.info("Logged in with account for '$email' with location '${login.accountLocation}'")
        login
    }
}

private fun fetchOrder(domain: String): Order {
    log.info("Fetching order for '$domain'")
    val order = acmeService.getOrder(domain)
    return if (order == null) {
        log.info("Order for '$domain' not found")
        log.info("Creating order for '$domain'")
        acmeService.createOrder(domain).apply {
            log.info("Order created for '$domain' with location '$location'")
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
    log.info("Order for '$domain' finished with status '${order.status}'")
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