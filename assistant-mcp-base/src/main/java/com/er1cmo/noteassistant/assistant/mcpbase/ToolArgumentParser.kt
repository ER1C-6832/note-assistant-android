package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONArray
import org.json.JSONObject

class ToolArgumentParser(private val json: JSONObject) {
    fun requireString(name: String): String {
        val value = json.optString(name).trim()
        require(value.isNotBlank()) { "缺少必填参数：$name" }
        return value
    }

    fun optionalString(name: String, fallback: String = ""): String = json.optString(name, fallback).trim()

    fun requireLong(name: String): Long {
        require(json.has(name) && !json.isNull(name)) { "缺少必填参数：$name" }
        return json.optLong(name).takeIf { it > 0L }
            ?: error("参数 $name 必须是正整数")
    }

    fun optionalLong(name: String): Long? = if (json.has(name) && !json.isNull(name)) json.optLong(name) else null

    fun boolean(name: String, fallback: Boolean = false): Boolean = json.optBoolean(name, fallback)

    fun int(name: String, fallback: Int = 0): Int = json.optInt(name, fallback)

    fun stringList(name: String): List<String> {
        val array = json.optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    fun longList(name: String): List<Long> {
        val array = json.optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optLong(index)
                if (value > 0L) add(value)
            }
        }
    }

    fun raw(): JSONObject = json

    companion object {
        fun parse(argumentsJson: String): Result<ToolArgumentParser> = runCatching {
            val trimmed = argumentsJson.trim().ifBlank { "{}" }
            ToolArgumentParser(JSONObject(trimmed))
        }

        fun parseObject(argumentsJson: String): Result<JSONObject> = runCatching {
            JSONObject(argumentsJson.trim().ifBlank { "{}" })
        }
    }
}

fun JSONObject.optJsonArrayOrEmpty(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
