package com.gorlah.cert

import org.shredzone.acme4j.AccountBuilder
import org.shredzone.acme4j.Login
import org.shredzone.acme4j.Session
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

class DefaultAuthenticationService(
    private val fileRepository: FileRepository,
    private val keyPairGenerator: KeyPairGenerator,
    private val session: Session,
) : AuthenticationService {

    override fun login(email: String): Login? {
        val path = toPath(email)
        val account = fileRepository.load(path.resolve("account.json"), AccountDocument::class) ?: return null
        val keyPair = fileRepository.loadKeyPair(path.resolve("keypair.pem")) ?: return null
        return session.login(account.location, keyPair)
    }

    override fun createAccount(email: String): Login {
        val accountKeyPair = keyPairGenerator.generate()
        val login = AccountBuilder()
            .addEmail(email)
            .agreeToTermsOfService()
            .useKeyPair(accountKeyPair)
            .createLogin(session)
        val path = toPath(email)
        fileRepository.saveKeyPair(path.resolve("keypair.pem"), accountKeyPair)
        fileRepository.save(path.resolve("account.json"), AccountDocument(login.accountLocation))
        return login
    }

    private fun toPath(email: String): Path {
        return Paths.get("accounts/$email")
    }

    private data class AccountDocument(val location: URL)
}