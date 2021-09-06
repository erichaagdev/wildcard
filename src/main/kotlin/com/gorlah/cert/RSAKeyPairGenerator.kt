package com.gorlah.cert

import org.shredzone.acme4j.util.KeyPairUtils
import java.security.KeyPair

class RSAKeyPairGenerator : KeyPairGenerator {

    override fun generate(): KeyPair {
        return KeyPairUtils.createKeyPair(2048)
    }
}