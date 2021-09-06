package com.gorlah.cert

import java.security.KeyPair

fun interface KeyPairGenerator {
    fun generate(): KeyPair
}