package com.gorlah.cert.acme

import org.shredzone.acme4j.Authorization

interface AuthorizationProcessor {
    fun process(authorization: Authorization)
    fun cleanup(authorization: Authorization)
}