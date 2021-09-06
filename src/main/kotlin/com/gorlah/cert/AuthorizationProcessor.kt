package com.gorlah.cert

import org.shredzone.acme4j.Authorization

interface AuthorizationProcessor {
    fun process(authorization: Authorization)
    fun cleanup(authorization: Authorization)
}