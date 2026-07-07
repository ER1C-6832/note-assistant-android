package com.er1cmo.noteassistant.assistant.runtime.audio

interface AudioRecorder {
    fun start(onPcmFrame: (ByteArray) -> Unit)
    fun stop()
}

interface AudioPlayer {
    fun play(pcm: ByteArray)
    fun stop()
}

interface OpusEncoder {
    fun encode(pcm: ByteArray): ByteArray
}

interface OpusDecoder {
    fun decode(opus: ByteArray): ByteArray
}

class FakeAudioRecorder : AudioRecorder {
    private var recording = false

    override fun start(onPcmFrame: (ByteArray) -> Unit) {
        recording = true
        onPcmFrame(ByteArray(0))
    }

    override fun stop() {
        recording = false
    }

    fun isRecording(): Boolean = recording
}

class FakeAudioPlayer : AudioPlayer {
    private var playing = false

    override fun play(pcm: ByteArray) {
        playing = true
    }

    override fun stop() {
        playing = false
    }

    fun isPlaying(): Boolean = playing
}

class PassthroughOpusEncoder : OpusEncoder {
    override fun encode(pcm: ByteArray): ByteArray = pcm
}

class PassthroughOpusDecoder : OpusDecoder {
    override fun decode(opus: ByteArray): ByteArray = opus
}
