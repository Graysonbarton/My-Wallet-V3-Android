package info.blockchain.wallet.metadata

import info.blockchain.wallet.crypto.AESUtil
import info.blockchain.wallet.metadata.data.MetadataBody
import info.blockchain.wallet.util.FormatsUtil
import info.blockchain.wallet.util.MetadataUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.zipWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.json.JSONException
import org.spongycastle.crypto.CryptoException
import org.spongycastle.util.encoders.Base64
import org.spongycastle.util.encoders.Hex
import retrofit2.HttpException

class MetadataInteractor(
    private val metadataService: MetadataService
) {
    fun fetchMagic(address: String): Single<ByteArray> =
        metadataService.getMetadata(address).map {
            val encryptedPayloadBytes = Base64.decode(it.payload.toByteArray(StandardCharsets.UTF_8))
            if (it.prevMagicHash != null) {
                val prevMagicBytes = Hex.decode(it.prevMagicHash)
                MetadataUtil.magic(encryptedPayloadBytes, prevMagicBytes)
            } else {
                MetadataUtil.magic(encryptedPayloadBytes, null)
            }
        }

    fun putMetadata(payloadJson: String, metadata: Metadata): Completable {
        if (!FormatsUtil.isValidJson(payloadJson)) {
            return Completable.error(JSONException("Payload is not a valid json object."))
        }

        val encryptedPayloadBytes: ByteArray =
            Base64.decode(AESUtil.encryptWithKey(metadata.encryptionKey, payloadJson))
        return fetchMagic(metadata.address)
            .onErrorReturn { ByteArray(0) }
            .flatMapCompletable { m ->
                val magic = if (m.isEmpty()) null else m
                val message = MetadataUtil.message(encryptedPayloadBytes, magic)
                val sig = metadata.node.signMessage(String(Base64.encode(message)))
                val body = MetadataBody(
                    version = METADATA_VERSION,
                    payload = String(Base64.encode(encryptedPayloadBytes)),
                    signature = sig,
                    prevMagicHash = magic?.let {
                        Hex.toHexString(it)
                    },
                    typeId = metadata.type
                )
                metadataService.putMetadata(metadata.address, body)
            }.retryWhen { errors ->
                errors.zipWith(
                    Flowable.range(0, FETCH_MAGIC_HASH_ATTEMPT_LIMIT)
                )
                    .flatMap { (error, attempt) ->
                        if (error is HttpException && error.code() == 404 && attempt < FETCH_MAGIC_HASH_ATTEMPT_LIMIT) {
                            Flowable.timer(1, TimeUnit.SECONDS)
                        } else {
                            Flowable.error(error)
                        }
                    }
            }
    }

    fun loadRemoteMetadata(metadata: Metadata): Maybe<String> {
        return metadataService.getMetadata(metadata.address)
            .toMaybe()
            .map {
                decryptMetadata(metadata, it.payload)
            }.onErrorResumeNext {
                if (it is HttpException && it.code() == 404) { // haven't been created{
                    Maybe.empty()
                } else Maybe.error(it)
            }
    }

    private fun decryptMetadata(metadata: Metadata, payload: String): String =
        try {
            AESUtil.decryptWithKey(metadata.encryptionKey, payload).apply {
                if (!FormatsUtil.isValidJson(this)) {
                    throw CryptoException("Malformed plaintext")
                }
            }
        } catch (e: CryptoException) {
            metadata.unpaddedEncryptionKey?.let {
                AESUtil.decryptWithKey(it, payload)
            } ?: throw e
        }

    companion object {
        const val METADATA_VERSION = 1
        const val FETCH_MAGIC_HASH_ATTEMPT_LIMIT = 1
    }
}
