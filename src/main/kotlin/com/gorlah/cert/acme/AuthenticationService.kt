package com.gorlah.cert.acme

import org.shredzone.acme4j.Login

interface AuthenticationService {
    fun login(email: String): Login?
    fun createAccount(email: String): Login
}