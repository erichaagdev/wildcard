package com.gorlah.cert

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun logger(kClass: KClass<*>): Logger = LoggerFactory.getLogger(kClass.java)