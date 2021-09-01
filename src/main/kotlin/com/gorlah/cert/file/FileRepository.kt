package com.gorlah.cert.file

import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.Certificate
import kotlin.reflect.KClass

interface FileRepository {
    fun <T : Any> load(path: Path, kClass: KClass<T>): T?
    fun <T : Any> save(path: Path, any: T): T
    fun copy(from: Path, to: Path): Boolean
    fun delete(path: Path): Boolean
    fun move(from: Path, to: Path): Boolean
    fun loadKeyPair(path: Path): KeyPair?
    fun saveKeyPair(path: Path, keyPair: KeyPair): KeyPair
    fun saveCertificate(path: Path, certificate: Certificate): Certificate
    fun saveCertificateChain(path: Path, certificates: List<Certificate>): List<Certificate>
}