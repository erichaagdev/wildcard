package com.gorlah.cert.acme

import com.gorlah.cert.file.FileRepository
import com.gorlah.cert.keypair.KeyPairGenerator
import com.gorlah.cert.util.logger
import org.shredzone.acme4j.Login
import org.shredzone.acme4j.Order
import org.shredzone.acme4j.Status
import org.shredzone.acme4j.exception.AcmeRetryAfterException
import org.shredzone.acme4j.util.CSRBuilder
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

class DefaultAcmeService(
    private val fileRepository: FileRepository,
    private val keyPairGenerator: KeyPairGenerator,
    private val login: Login,
): AcmeService {

    private companion object {
        private val log = logger(DefaultAcmeService::class)
    }

    override fun getOrder(domain: String): Order? {
        val path = toPath(domain)
        val order = fileRepository.load(path.resolve("order.json"), OrderDocument::class) ?: return null
        return login.bindOrder(order.location)
    }

    override fun createOrder(domain: String): Order {
        val order = login.account.newOrder()
            .domain(domain)
            .domain("*.$domain")
            .create()
        val path = toPath(domain)
        fileRepository.save(path.resolve("order.json"), OrderDocument(order.location))
        return order
    }

    override fun finalizeOrder(order: Order, domain: String) {
        if (order.status != Status.VALID) {
            val domainKeyPair = fileRepository.loadKeyPair(Paths.get("sites/$domain/privkey.pem")) ?: keyPairGenerator.generate()
            val csr = CSRBuilder().apply {
                addDomain(domain)
                addDomain("*.$domain")
                sign(domainKeyPair)
            }
            fileRepository.saveKeyPair(Paths.get("sites/$domain/privkey.pem"), domainKeyPair)
            order.execute(csr.encoded)
        }

        while (order.status != Status.VALID) {
            log.info("Finalization for '$domain' in progress...")
            Thread.sleep(10000)
            try { order.update() } catch (_: AcmeRetryAfterException) { }
        }

        val certificate = order.certificate ?: return
        fileRepository.saveCertificateChain(Paths.get("sites/$domain/fullchain.pem"), certificate.certificateChain)
    }

    private fun toPath(domain: String): Path {
        return Paths.get("orders/$domain")
    }

    private data class OrderDocument(val location: URL)
}