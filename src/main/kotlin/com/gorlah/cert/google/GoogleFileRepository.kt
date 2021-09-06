package com.gorlah.cert.google

import com.fasterxml.jackson.databind.json.JsonMapper
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.gorlah.cert.FileRepository
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.shredzone.acme4j.util.KeyPairUtils
import java.nio.channels.Channels
import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.Certificate
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.reflect.KClass

class GoogleFileRepository(
    private val bucketName: String,
    private val location: String,
) : FileRepository {

    private companion object {
        val storage = StorageOptions.getDefaultInstance().service!!
        val mapper = JsonMapper.builder().findAndAddModules().build()!!
    }

    override fun <T : Any> save(path: Path, any: T): T {
        val blobInfo = BlobInfo.newBuilder(toBlobId(path))
            .setContentType("application/json")
            .build()
        Channels.newWriter(storage.writer(blobInfo), Charsets.UTF_8).use {
            mapper.writeValue(it, any)
        }
        return any
    }

    override fun <T : Any> load(path: Path, kClass: KClass<T>): T? {
        return try {
            Channels.newReader(storage.reader(toBlobId(path)), Charsets.UTF_8).use {
                mapper.readValue(it, kClass.java)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun copy(from: Path, to: Path): Boolean {
        val blob = storage.get(toBlobId(from)) ?: return false
        blob.copyTo(toBlobId(to)).result
        return true
    }

    override fun delete(path: Path): Boolean {
        return storage.get(toBlobId(path)).delete()
    }

    override fun move(from: Path, to: Path): Boolean {
        return copy(from, to) && delete(from)
    }

    override fun saveKeyPair(path: Path, keyPair: KeyPair): KeyPair {
        val blobInfo = BlobInfo.newBuilder(toBlobId(path))
            .setContentType("text/plain")
            .build()
        Channels.newWriter(storage.writer(blobInfo), Charsets.UTF_8).use {
            KeyPairUtils.writeKeyPair(keyPair, it)
        }
        return keyPair
    }

    override fun loadKeyPair(path: Path): KeyPair? {
        return try {
            Channels.newReader(storage.reader(toBlobId(path)), Charsets.UTF_8).use {
                KeyPairUtils.readKeyPair(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun saveCertificate(path: Path, certificate: Certificate): Certificate {
        return saveCertificateChain(path, listOf(certificate)).first()
    }

    override fun saveCertificateChain(path: Path, certificates: List<Certificate>): List<Certificate> {
        val blobInfo = BlobInfo.newBuilder(toBlobId(path))
            .setContentType("text/plain")
            .build()
        Channels.newWriter(storage.writer(blobInfo), Charsets.UTF_8).use { writer ->
            JcaPEMWriter(writer).use { pemWriter ->
                certificates.forEach { pemWriter.writeObject(it) }
            }
        }
        return certificates
    }

    private fun toBlobId(path: Path): BlobId {
        return BlobId.of(bucketName, Path.of(location).resolve(path).invariantSeparatorsPathString.substringAfter("./"))
    }
}