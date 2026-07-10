package com.er1cmo.noteassistant.assistant.runtime.state

enum class VoiceInteractionMode(val storageValue: String, val label: String) {
    HoldToTalk("hold_to_talk", "按住说话"),
    StreamingConversation("streaming_conversation", "流式对话"),
    ;

    companion object {
        fun fromStorage(value: String?): VoiceInteractionMode =
            values().firstOrNull { it.storageValue == value } ?: StreamingConversation
    }
}

enum class AssistantEntrySource(val storageValue: String) {
    Text("text"),
    PushToTalk("push_to_talk"),
    StreamingButton("streaming_button"),
    WakeWord("wakeword"),
}

enum class StreamingConversationState(val storageValue: String) {
    Inactive("inactive"),
    Starting("starting"),
    ListeningForSpeech("listening_for_speech"),
    UserSpeaking("user_speaking"),
    SubmittingTurn("submitting_turn"),
    Thinking("thinking"),
    Speaking("speaking"),
    WaitingForNextTurn("waiting_for_next_turn"),
    Stopping("stopping"),
    Recovering("recovering"),
    Error("error"),
}

enum class VoiceActivityState(val storageValue: String) {
    Disabled("disabled"),
    Warmup("warmup"),
    WaitingForSpeech("waiting_for_speech"),
    SpeechDetected("speech_detected"),
    SpeechActive("speech_active"),
    EndOfSpeech("end_of_speech"),
    NoSpeechTimeout("no_speech_timeout"),
}
