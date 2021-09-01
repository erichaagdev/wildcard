package com.gorlah.cert.acme

import org.shredzone.acme4j.Certificate
import org.shredzone.acme4j.Order

interface AcmeService {
    fun getOrder(domain: String): Order?
    fun createOrder(domain: String): Order
    fun finalizeOrder(order: Order, domain: String)
}