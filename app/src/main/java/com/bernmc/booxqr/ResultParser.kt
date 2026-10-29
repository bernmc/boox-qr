package com.bernmc.booxqr

import java.net.URLEncoder

/** Classifies decoded content. Pure Kotlin, no Android dependencies. */
object ResultParser {
    enum class Kind { URL, TEXT, WIFI, EMAIL, PHONE, GEO, SMS, CONTACT, BARCODE }

    data class Parsed(
        val kind: Kind,
        /** Raw string handed to ACTION_VIEW, or null when the content is not openable. */
        val openUri: String? = null,
        val wifiSsid: String? = null,
        val wifiPassword: String? = null,
    )

    fun classify(text: String, isQr: Boolean): Parsed {
        val t = text.trim()
        val lower = t.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") ->
                Parsed(Kind.URL, openUri = t)
            lower.startsWith("mailto:") -> Parsed(Kind.EMAIL, openUri = t)
            lower.startsWith("matmsg:") -> {
                val f = parseFields(t.substring("matmsg:".length))
                val to = f["TO"].orEmpty()
                val params = buildList {
                    f["SUB"]?.let { add("subject=" + enc(it)) }
                    f["BODY"]?.let { add("body=" + enc(it)) }
                }
                val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
                Parsed(Kind.EMAIL, openUri = "mailto:$to$query")
            }
            lower.startsWith("tel:") -> Parsed(Kind.PHONE, openUri = t)
            lower.startsWith("geo:") -> Parsed(Kind.GEO, openUri = t)
            lower.startsWith("sms:") || lower.startsWith("smsto:") ->
                Parsed(Kind.SMS, openUri = t)
            lower.startsWith("mecard:") || lower.startsWith("begin:vcard") -> Parsed(Kind.CONTACT)
            lower.startsWith("wifi:") -> {
                val f = parseFields(t.substring("wifi:".length))
                Parsed(Kind.WIFI, wifiSsid = f["S"], wifiPassword = f["P"])
            }
            !isQr -> Parsed(Kind.BARCODE)
            else -> Parsed(Kind.TEXT)
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Parses "KEY:value;KEY:value;;" with backslash escapes. Keys are upper-cased. */
    fun parseFields(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var inValue = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    (if (inValue) value else key).append(body[i + 1])
                    i++
                }
                !inValue && c == ':' -> inValue = true
                c == ';' -> {
                    if (inValue && key.isNotEmpty()) out.putIfAbsent(key.toString().uppercase(), value.toString())
                    key.setLength(0)
                    value.setLength(0)
                    inValue = false
                }
                else -> (if (inValue) value else key).append(c)
            }
            i++
        }
        if (inValue && key.isNotEmpty()) out.putIfAbsent(key.toString().uppercase(), value.toString())
        return out
    }
}
