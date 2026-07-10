package com.er1cmo.noteassistant.assistant.wakeword

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKwsKeywordsFile
import com.k2fsa.sherpa.onnx.getKwsModelConfig
import java.util.Locale

data class WakeWordDetectionResult(
    val keyword: String,
    val latencyMs: Long,
)

class SherpaWakeWordDetector(
    private val context: Context,
    private val config: WakeWordConfig,
) {
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    fun initialize() {
        validateAssets(context.assets)
        require(config.phrase.grammar.isNotBlank()) { "唤醒词 grammar 不能为空" }
        val kwsConfig = KeywordSpotterConfig(
            featConfig = getFeatureConfig(sampleRate = config.sampleRate, featureDim = 80),
            modelConfig = getKwsModelConfig(),
            keywordsFile = getKwsKeywordsFile(),
            keywordsScore = config.keywordsScore,
            keywordsThreshold = config.keywordsThreshold,
            numTrailingBlanks = config.numTrailingBlanks,
        )
        spotter = KeywordSpotter(assetManager = context.assets, config = kwsConfig)
        resetStream()
    }

    fun accept(samples: FloatArray): WakeWordDetectionResult? {
        val kws = spotter ?: return null
        val activeStream = stream ?: return null
        val startedAt = System.currentTimeMillis()
        activeStream.acceptWaveform(samples = samples, sampleRate = config.sampleRate)
        var detected: String? = null
        while (kws.isReady(activeStream)) {
            kws.decode(activeStream)
            val result = kws.getResult(activeStream).keyword
            if (result.isNotBlank()) {
                detected = result
                resetStream()
                break
            }
        }
        return detected?.let {
            WakeWordDetectionResult(
                keyword = it,
                latencyMs = System.currentTimeMillis() - startedAt,
            )
        }
    }

    fun resetStream() {
        runCatching { stream?.release() }
        stream = spotter?.createStream(config.phrase.grammar)
    }

    fun release() {
        runCatching { stream?.release() }
        runCatching { spotter?.release() }
        stream = null
        spotter = null
    }

    private fun validateAssets(assetManager: AssetManager) {
        val names = runCatching {
            assetManager.list(WakeWordConfig.MODEL_DIR)?.toSet().orEmpty()
        }.getOrElse { emptySet() }
        val missing = REQUIRED_MODEL_FILES.filterNot { it in names }
        check(missing.isEmpty()) {
            "Sherpa KWS 模型资产缺失：assets/${WakeWordConfig.MODEL_DIR} 缺少 ${missing.joinToString()}"
        }
    }

    companion object {
        val REQUIRED_MODEL_FILES: List<String> = listOf(
            "encoder-epoch-13-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-13-avg-2-chunk-16-left-64.onnx",
            "joiner-epoch-13-avg-2-chunk-16-left-64.onnx",
            "tokens.txt",
            "keywords_xiaozhi.txt",
        )
    }
}

fun normalizeWakeWordHit(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\s\\p{Punct}，。！？、,.!?：:；;\"'`~·_\\-]"), "")
    .replace("xiaozi", "xiaozhi")
    .replace("xiaozhi", "小智")
