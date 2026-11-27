package com.example.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Raw JSON slicing and ECMA-262 / V8-faithful compact JSON serialization helpers.
 * Enables byte-exact SHA-256 verification and emission of Web `.examset.json` v5 sections
 * without `org.json` key-reordering, `/` escaping, or `.0` double formatting drift.
 */
object WebJsonSerializer {

    /**
     * Removes JSON whitespace outside string literals while preserving exact token formatting
     * (escape sequences, key order, and numeric lexemes).
     */
    fun minifyJsonPreserveRawTokens(raw: String): String {
        val sb = StringBuilder(raw.length)
        var inString = false
        var escaped = false
        for (i in raw.indices) {
            val ch = raw[i]
            if (inString) {
                sb.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    ' ', '\n', '\r', '\t' -> Unit
                    '"' -> {
                        inString = true
                        sb.append(ch)
                    }
                    else -> sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Extracts exact raw JSON substrings for every immediate member of a named top-level
     * JSON object property (e.g., `"sections"`), returning memberName -> rawSlice.
     */
    fun extractJsonObjectMemberRawSlices(
        rawJson: String,
        targetObjectKey: String
    ): Map<String, String> {
        val len = rawJson.length
        var idx = skipWs(rawJson, 0)
        if (idx >= len || rawJson[idx] != '{') return emptyMap()
        idx = skipWs(rawJson, idx + 1)

        while (idx < len && rawJson[idx] != '}') {
            if (rawJson[idx] != '"') return emptyMap()
            val (key, afterKey) = readJsonString(rawJson, idx) ?: return emptyMap()
            idx = skipWs(rawJson, afterKey)
            if (idx >= len || rawJson[idx] != ':') return emptyMap()
            val valStart = skipWs(rawJson, idx + 1)
            val valEnd = findJsonValueEnd(rawJson, valStart)
            if (valEnd <= valStart) return emptyMap()

            if (key == targetObjectKey && rawJson[valStart] == '{') {
                return parseObjectMembersFromSlice(rawJson, valStart, valEnd)
            }

            idx = skipWs(rawJson, valEnd)
            if (idx < len && rawJson[idx] == ',') {
                idx = skipWs(rawJson, idx + 1)
            }
        }
        return emptyMap()
    }

    private fun parseObjectMembersFromSlice(
        rawJson: String,
        objStart: Int,
        objEnd: Int
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var idx = skipWs(rawJson, objStart + 1, objEnd)
        while (idx < objEnd && rawJson[idx] != '}') {
            if (rawJson[idx] != '"') break
            val (key, afterKey) = readJsonString(rawJson, idx, objEnd) ?: break
            idx = skipWs(rawJson, afterKey, objEnd)
            if (idx >= objEnd || rawJson[idx] != ':') break
            val valStart = skipWs(rawJson, idx + 1, objEnd)
            val valEnd = findJsonValueEnd(rawJson, valStart, objEnd)
            if (valEnd <= valStart) break

            result[key] = rawJson.substring(valStart, valEnd)

            idx = skipWs(rawJson, valEnd, objEnd)
            if (idx < objEnd && rawJson[idx] == ',') {
                idx = skipWs(rawJson, idx + 1, objEnd)
            }
        }
        return result
    }

    private fun skipWs(s: String, start: Int, limit: Int = s.length): Int {
        var i = start
        while (i < limit) {
            when (s[i]) {
                ' ', '\n', '\r', '\t' -> i++
                else -> return i
            }
        }
        return i
    }

    private fun readJsonString(s: String, quoteIdx: Int, limit: Int = s.length): Pair<String, Int>? {
        if (quoteIdx >= limit || s[quoteIdx] != '"') return null
        val sb = StringBuilder()
        var i = quoteIdx + 1
        while (i < limit) {
            val ch = s[i]
            when (ch) {
                '"' -> return sb.toString() to (i + 1)
                '\\' -> {
                    if (i + 1 >= limit) return null
                    val next = s[i + 1]
                    when (next) {
                        '"', '\\', '/' -> {
                            sb.append(next)
                            i += 2
                        }
                        'b' -> {
                            sb.append('\b')
                            i += 2
                        }
                        'f' -> {
                            sb.append('\u000C')
                            i += 2
                        }
                        'n' -> {
                            sb.append('\n')
                            i += 2
                        }
                        'r' -> {
                            sb.append('\r')
                            i += 2
                        }
                        't' -> {
                            sb.append('\t')
                            i += 2
                        }
                        'u' -> {
                            if (i + 6 > limit) return null
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16) ?: return null
                            sb.append(code.toChar())
                            i += 6
                        }
                        else -> {
                            sb.append(next)
                            i += 2
                        }
                    }
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return null
    }

    private fun findJsonValueEnd(s: String, start: Int, limit: Int = s.length): Int {
        if (start >= limit) return -1
        return when (s[start]) {
            '"' -> {
                var i = start + 1
                var escaped = false
                while (i < limit) {
                    val ch = s[i]
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        return i + 1
                    }
                    i++
                }
                -1
            }
            '{', '[' -> {
                var depth = 0
                var i = start
                var inStr = false
                var escaped = false
                while (i < limit) {
                    val ch = s[i]
                    if (inStr) {
                        if (escaped) escaped = false
                        else if (ch == '\\') escaped = true
                        else if (ch == '"') inStr = false
                    } else {
                        when (ch) {
                            '"' -> inStr = true
                            '{', '[' -> depth++
                            '}', ']' -> {
                                depth--
                                if (depth == 0) return i + 1
                            }
                        }
                    }
                    i++
                }
                -1
            }
            else -> {
                var i = start
                while (i < limit) {
                    when (s[i]) {
                        ',', '}', ']', ' ', '\n', '\r', '\t' -> return i
                        else -> i++
                    }
                }
                i
            }
        }
    }

    fun toJsCompactString(value: Any?): String {
        val sb = StringBuilder()
        writeJsValue(sb, value, indentStep = 0, depth = 0)
        return sb.toString()
    }

    fun toJsPrettyString(value: Any?, indentSpaces: Int = 2): String {
        val sb = StringBuilder()
        writeJsValue(sb, value, indentStep = indentSpaces.coerceAtLeast(0), depth = 0)
        return sb.toString()
    }

    private fun writeJsValue(sb: StringBuilder, value: Any?, indentStep: Int, depth: Int) {
        when (value) {
            null, JSONObject.NULL -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int -> sb.append(value.toString())
            is Long -> sb.append(value.toString())
            is Float -> writeJsDouble(sb, value.toDouble())
            is Double -> writeJsDouble(sb, value)
            is Number -> sb.append(value.toString())
            is String -> appendJsQuotedString(sb, value)
            is JSONArray -> {
                val n = value.length()
                if (n == 0) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                for (i in 0 until n) {
                    if (i > 0) sb.append(',')
                    if (indentStep > 0) {
                        sb.append('\n')
                        appendSpaces(sb, (depth + 1) * indentStep)
                    }
                    val elem = if (value.isNull(i)) null else value.opt(i)
                    writeJsValue(sb, elem, indentStep, depth + 1)
                }
                if (indentStep > 0) {
                    sb.append('\n')
                    appendSpaces(sb, depth * indentStep)
                }
                sb.append(']')
            }
            is JSONObject -> {
                val keys = orderedJsKeys(value)
                if (keys.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                keys.forEachIndexed { idx, key ->
                    if (idx > 0) sb.append(',')
                    if (indentStep > 0) {
                        sb.append('\n')
                        appendSpaces(sb, (depth + 1) * indentStep)
                    }
                    appendJsQuotedString(sb, key)
                    sb.append(if (indentStep > 0) ": " else ":")
                    val propVal = if (value.isNull(key)) null else value.opt(key)
                    writeJsValue(sb, propVal, indentStep, depth + 1)
                }
                if (indentStep > 0) {
                    sb.append('\n')
                    appendSpaces(sb, depth * indentStep)
                }
                sb.append('}')
            }
            else -> appendJsQuotedString(sb, value.toString())
        }
    }

    private fun orderedJsKeys(obj: JSONObject): List<String> {
        val indexKeys = mutableListOf<Pair<Long, String>>()
        val otherKeys = mutableListOf<String>()
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            val idxVal = parseJsArrayIndexKey(k)
            if (idxVal != null) {
                indexKeys.add(idxVal to k)
            } else {
                otherKeys.add(k)
            }
        }
        if (indexKeys.isEmpty()) return otherKeys
        indexKeys.sortBy { it.first }
        return buildList(indexKeys.size + otherKeys.size) {
            indexKeys.forEach { add(it.second) }
            addAll(otherKeys)
        }
    }

    private fun parseJsArrayIndexKey(key: String): Long? {
        if (key.isEmpty() || key.length > 10) return null
        if (key == "0") return 0L
        if (key[0] < '1' || key[0] > '9') return null
        for (i in 1 until key.length) {
            if (key[i] !in '0'..'9') return null
        }
        val parsed = key.toLongOrNull() ?: return null
        return if (parsed in 0L..4_294_967_294L) parsed else null
    }

    private fun writeJsDouble(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) {
            sb.append("null")
            return
        }
        if (d == 0.0) {
            sb.append("0")
            return
        }
        if (d % 1.0 == 0.0 && abs(d) <= 9_007_199_254_740_991.0) {
            sb.append(d.toLong().toString())
        } else {
            sb.append(d.toString())
        }
    }

    private fun appendJsQuotedString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (i in s.indices) {
            when (val ch = s[i]) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    private fun appendSpaces(sb: StringBuilder, count: Int) {
        repeat(count) { sb.append(' ') }
    }
}
