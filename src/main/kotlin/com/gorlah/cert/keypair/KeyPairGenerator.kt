package com.gorlah.cert.keypair

import java.security.KeyPair

fun interface KeyPairGenerator {
    fun generate(): KeyPair
}