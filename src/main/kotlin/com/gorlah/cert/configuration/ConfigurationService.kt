package com.gorlah.cert.configuration

fun interface ConfigurationService {
    fun load(): Configuration?
}