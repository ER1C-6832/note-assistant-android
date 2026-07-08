package com.er1cmo.noteassistant.assistant.runtime.identity

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DeviceIdentityManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun ensureIdentity(): DeviceIdentity {
        val clientIdValue = settingsRepository.assistantClientId.first()
        val deviceIdValue = settingsRepository.assistantDeviceId.first()
        val serialNumberValue = settingsRepository.assistantSerialNumber.first()
        val hmacKeyValue = settingsRepository.assistantHmacKey.first()

        if (clientIdValue.isNotBlank() && deviceIdValue.isNotBlank() && serialNumberValue.isNotBlank()) {
            val existingHmac = deriveHmacKey(clientIdValue, deviceIdValue, serialNumberValue, hmacKeyValue)
            val existing = DeviceIdentity(
                clientId = clientIdValue,
                deviceId = deviceIdValue,
                serialNumber = serialNumberValue,
                hmacKey = existingHmac,
            )
            settingsRepository.saveAssistantIdentity(
                clientId = existing.clientId,
                deviceId = existing.deviceId,
                serialNumber = existing.serialNumber,
                hmacKey = existing.hmacKey,
            )
            return existing
        }

        val clientId = clientIdValue.ifBlank { UUID.randomUUID().toString() }
        val deviceId = deviceIdValue.ifBlank { generatePseudoMac(clientId) }
        val serialNumber = serialNumberValue.ifBlank { generateSerialNumber(deviceId) }
        val hmacKey = deriveHmacKey(clientId, deviceId, serialNumber, hmacKeyValue)
        val identity = DeviceIdentity(
            clientId = clientId,
            deviceId = deviceId,
            serialNumber = serialNumber,
            hmacKey = hmacKey,
        )
        settingsRepository.saveAssistantIdentity(
            clientId = identity.clientId,
            deviceId = identity.deviceId,
            serialNumber = identity.serialNumber,
            hmacKey = identity.hmacKey,
        )
        return identity
    }

    suspend fun resetIdentity(): DeviceIdentity {
        settingsRepository.clearAssistantIdentityAndActivation()
        return ensureIdentity()
    }

    private fun deriveHmacKey(clientId: String, deviceId: String, serialNumber: String, existing: String): String {
        return existing.ifBlank { sha256Hex("$clientId|$deviceId|$serialNumber") }
    }

    private fun generatePseudoMac(clientId: String): String {
        val hash = sha256Bytes("$clientId|${UUID.randomUUID()}")
        val tail = hash.take(5).map { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
        return listOf("02", *tail.toTypedArray()).joinToString(":")
    }

    private fun generateSerialNumber(deviceId: String): String {
        val macClean = deviceId.lowercase(Locale.US).replace(":", "")
        val shortHash = sha256Hex(macClean).take(8).uppercase(Locale.US)
        return "SN-$shortHash-$macClean"
    }

    private fun sha256Hex(value: String): String = sha256Bytes(value).joinToString("") { byte ->
        String.format(Locale.US, "%02x", byte.toInt() and 0xff)
    }

    private fun sha256Bytes(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
