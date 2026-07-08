package com.er1cmo.noteassistant.assistant.runtime.activation

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.identity.DeviceIdentityManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class OtaActivationClient @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceIdentityManager: DeviceIdentityManager,
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun runFakeActivation(): OtaActivationOutcome = withContext(Dispatchers.IO) {
        val identity = deviceIdentityManager.ensureIdentity()
        val fakeWebsocketUrl = "wss://fake.local/xiaozhi/${identity.clientId.take(8)}"
        val fakeToken = "phase3-fake-token"
        settingsRepository.saveAssistantWebSocketConfig(fakeWebsocketUrl, fakeToken)
        settingsRepository.setAssistantActivationStatus(true)
        settingsRepository.clearAssistantActivationData()
        settingsRepository.saveAssistantLastJson(
            """
            {
              "fake": true,
              "websocket": {
                "url": "$fakeWebsocketUrl",
                "token": "***"
              }
            }
            """.trimIndent(),
        )
        OtaActivationOutcome(
            state = OtaActivationState.Activated,
            message = "Fake activation 成功，已保存 fake WebSocket 配置；Phase3 仍不会执行便签工具。",
            websocketUrl = fakeWebsocketUrl,
        )
    }

    suspend fun runRealOtaAndActivation(onLog: (String) -> Unit = {}): OtaActivationOutcome = withContext(Dispatchers.IO) {
        val identity = deviceIdentityManager.ensureIdentity()
        val otaUrl = settingsRepository.assistantOtaUrl.first()
        val authorizationUrl = settingsRepository.assistantAuthorizationUrl.first()
        val activationVersion = settingsRepository.assistantActivationVersion.first()
        require(otaUrl.isNotBlank()) { "OTA URL 未配置" }
        require(identity.clientId.isNotBlank()) { "Client ID 未生成" }
        require(identity.deviceId.isNotBlank()) { "Device ID 未生成" }
        require(identity.serialNumber.isNotBlank()) { "序列号未生成" }
        require(identity.hmacKey.isNotBlank()) { "HMAC 密钥未生成" }

        onLog("OTA 请求开始：$otaUrl")
        val otaResponse = requestOta(
            otaUrl = otaUrl,
            activationVersion = activationVersion,
        )
        settingsRepository.saveAssistantLastJson(otaResponse.redactedJson)

        if (!otaResponse.websocketUrl.isNullOrBlank()) {
            val token = otaResponse.websocketToken ?: DEFAULT_TOKEN_WHEN_EMPTY
            settingsRepository.saveAssistantWebSocketConfig(otaResponse.websocketUrl, token)
            onLog("WebSocket 配置已下发：${otaResponse.websocketUrl}")
        }

        val activation = otaResponse.activation
        if (activation == null) {
            settingsRepository.setAssistantActivationStatus(true)
            settingsRepository.clearAssistantActivationData()
            return@withContext OtaActivationOutcome(
                state = OtaActivationState.Activated,
                message = "OTA 成功，服务端未要求验证码激活。",
                websocketUrl = otaResponse.websocketUrl,
            )
        }

        settingsRepository.setAssistantActivationStatus(false)
        settingsRepository.saveAssistantActivationData(
            code = activation.code,
            challenge = activation.challenge,
            message = activation.message,
        )
        onLog("设备需要激活，验证码：${activation.code}")
        onLog("请打开 $authorizationUrl 添加设备并输入验证码")

        val activated = pollActivation(
            otaUrl = otaUrl,
            activation = activation,
            onLog = onLog,
        )
        if (activated) {
            settingsRepository.setAssistantActivationStatus(true)
            settingsRepository.clearAssistantActivationData()
            OtaActivationOutcome(
                state = OtaActivationState.Activated,
                message = "设备激活成功。",
                websocketUrl = otaResponse.websocketUrl,
            )
        } else {
            settingsRepository.setAssistantActivationStatus(false)
            OtaActivationOutcome(
                state = OtaActivationState.Failed,
                message = "激活超时或失败，请确认验证码是否已在授权页面输入。",
                websocketUrl = otaResponse.websocketUrl,
                activationCode = activation.code,
            )
        }
    }

    private suspend fun requestOta(
        otaUrl: String,
        activationVersion: String,
    ): OtaResponse {
        val identity = deviceIdentityManager.ensureIdentity()
        val payload = buildOtaPayload(identity.hmacKey, identity.deviceId).toString()
        val request = Request.Builder()
            .url(otaUrl)
            .headers(buildOtaHeaders(identity, activationVersion))
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("OTA 服务器返回 HTTP ${response.code}: ${responseBody.take(200)}")
            }
            return parseOtaResponse(responseBody)
        }
    }

    private fun buildOtaHeaders(
        identity: com.er1cmo.noteassistant.assistant.runtime.identity.DeviceIdentity,
        activationVersion: String,
    ): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
            .add("Device-Id", identity.deviceId)
            .add("Client-Id", identity.clientId)
            .add("Content-Type", "application/json")
            .add("User-Agent", "$BOARD_TYPE/$APP_NAME-$APP_VERSION")
            .add("Accept-Language", "zh-CN")
        if (activationVersion == "v2") {
            builder.add("Activation-Version", APP_VERSION)
        }
        return builder.build()
    }

    private fun buildOtaPayload(hmacKey: String, deviceId: String): JSONObject {
        return JSONObject()
            .put(
                "application",
                JSONObject()
                    .put("version", APP_VERSION)
                    .put("elf_sha256", hmacKey),
            )
            .put(
                "board",
                JSONObject()
                    .put("type", BOARD_TYPE)
                    .put("name", APP_NAME)
                    .put("ip", getLocalIpAddress())
                    .put("mac", deviceId),
            )
    }

    private suspend fun pollActivation(
        otaUrl: String,
        activation: ActivationInfo,
        onLog: (String) -> Unit,
    ): Boolean {
        val identity = deviceIdentityManager.ensureIdentity()
        val activateUrl = "${otaUrl.trimEnd('/')}/activate"
        val signature = hmacSha256Hex(identity.hmacKey, activation.challenge)
        val payload = JSONObject()
            .put(
                "Payload",
                JSONObject()
                    .put("algorithm", "hmac-sha256")
                    .put("serial_number", identity.serialNumber)
                    .put("challenge", activation.challenge)
                    .put("hmac", signature),
            )
            .toString()

        val requestBuilder = Request.Builder()
            .url(activateUrl)
            .headers(buildActivationHeaders(identity))
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))

        onLog("激活轮询开始：$activateUrl")
        repeat(ACTIVATION_MAX_RETRIES) { attempt ->
            try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            onLog("激活成功：HTTP 200")
                            return true
                        }
                        202 -> {
                            if (attempt == 0 || (attempt + 1) % 6 == 0) {
                                onLog("等待用户输入验证码：${activation.code}，第 ${attempt + 1}/$ACTIVATION_MAX_RETRIES 次检查")
                            }
                        }
                        else -> {
                            val body = response.body?.string().orEmpty().take(200)
                            onLog("激活服务器返回 HTTP ${response.code}，继续等待：$body")
                        }
                    }
                }
            } catch (exception: Exception) {
                onLog("激活请求异常，继续重试：${exception.message ?: exception::class.java.simpleName}")
            }
            delay(ACTIVATION_RETRY_INTERVAL_MS)
        }
        return false
    }

    private fun buildActivationHeaders(identity: com.er1cmo.noteassistant.assistant.runtime.identity.DeviceIdentity): okhttp3.Headers {
        return okhttp3.Headers.Builder()
            .add("Activation-Version", "2")
            .add("Device-Id", identity.deviceId)
            .add("Client-Id", identity.clientId)
            .add("Content-Type", "application/json")
            .build()
    }

    private fun hmacSha256Hex(key: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface -> Collections.list(networkInterface.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
                ?: FALLBACK_IP
        } catch (_: Exception) {
            FALLBACK_IP
        }
    }

    private companion object {
        const val APP_NAME = "note-assistant-android"
        const val BOARD_TYPE = "android"
        const val APP_VERSION = "0.1.0-phase3"
        const val FALLBACK_IP = "127.0.0.1"
        const val DEFAULT_TOKEN_WHEN_EMPTY = "test-token"
        const val ACTIVATION_MAX_RETRIES = 60
        const val ACTIVATION_RETRY_INTERVAL_MS = 5_000L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
