package de.local.reelplaylist

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/** Liest Reel-/Post-Links aus dem Instagram-Datenexport (JSON; Fallback: beliebiger Text/HTML). */
object Parser {
    private val rx = Regex("""instagram\.com\\?/(?:[A-Za-z0-9._]+\\?/)?(reel|p|tv)\\?/([A-Za-z0-9_-]{6,})""")

    private fun key(s: String): String? {
        val m = rx.find(s) ?: return null
        val type = if (m.groupValues[1] == "reel") "reel" else "p"
        return "$type/${m.groupValues[2]}"
    }

    private fun walk(n: Any?, out: MutableList<Pair<String, Long>>) {
        when (n) {
            is JSONObject -> {
                val href = n.optString("href", "")
                if (href.isNotEmpty()) key(href)?.let { out.add(it to n.optLong("timestamp", 0L)) }
                for (k in n.keys()) walk(n.opt(k), out)
            }
            is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i), out)
        }
    }

    /** Ergebnis: neueste zuerst (wie in der Instagram-Aktivitätenliste), ohne Duplikate. */
    fun extract(text: String): List<String> {
        val found = ArrayList<Pair<String, Long>>()
        try { walk(JSONTokener(text).nextValue(), found) } catch (_: Exception) {}
        if (found.isEmpty()) {
            rx.findAll(text).forEach { m -> key(m.value)?.let { found.add(it to 0L) } }
        }
        val seen = HashSet<String>()
        return found.sortedByDescending { it.second }.map { it.first }.filter { seen.add(it) }
    }
}

object Store {
    const val IG = "com.instagram.android"
    private fun p(c: Context) = c.getSharedPreferences("rp", Context.MODE_PRIVATE)

    fun flag(c: Context, k: String, def: Boolean) = p(c).getBoolean(k, def)
    fun setFlag(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    fun saveRaw(c: Context, list: String, keys: List<String>) {
        File(c.filesDir, "list_$list.txt").writeText(keys.joinToString("\n"))
        setIndex(c, list, 0)
    }

    /** Gefilterte/sortierte Liste, wie sie tatsächlich abgespielt wird. */
    fun items(c: Context, list: String): List<String> {
        val f = File(c.filesDir, "list_$list.txt")
        if (!f.exists()) return emptyList()
        var l = f.readLines().filter { it.isNotBlank() }
        if (flag(c, "reelsOnly", true)) l = l.filter { it.startsWith("reel/") }
        if (flag(c, "reverse", false)) l = l.reversed()
        return l
    }

    fun active(c: Context) = p(c).getString("active", "likes")!!
    fun setActive(c: Context, v: String) = p(c).edit().putString("active", v).apply()
    fun index(c: Context, list: String) = p(c).getInt("idx_$list", 0)
    fun setIndex(c: Context, list: String, i: Int) = p(c).edit().putInt("idx_$list", i).apply()
    fun resetIndexes(c: Context) { setIndex(c, "likes", 0); setIndex(c, "saved", 0) }

    fun pagerId(c: Context) = p(c).getString("pagerId", "clips_viewer")!!
    fun setPagerId(c: Context, v: String) = p(c).edit().putString("pagerId", v).apply()

    fun addDebug(c: Context, line: String) {
        val cur = (p(c).getString("dbg", "") ?: "").split("\n").filter { it.isNotBlank() }
        if (line in cur) return
        p(c).edit().putString("dbg", (cur + line).takeLast(15).joinToString("\n")).apply()
    }
    fun debugLog(c: Context) = p(c).getString("dbg", "") ?: ""
}

object Launcher {
    fun open(c: Context, key: String): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$key/"))
        i.setPackage(Store.IG)
        var f = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        if (Store.flag(c, "clear", true)) f = f or Intent.FLAG_ACTIVITY_CLEAR_TASK
        i.addFlags(f)
        return try { c.startActivity(i); true } catch (e: Exception) { false }
    }
}
