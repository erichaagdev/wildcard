package com.gorlah.cert

fun interface ConfigurationService {
    fun load(): Configuration?
}