package de.local.reelplaylist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        const val REQ_LIKES = 1
        const val REQ_SAVED = 2
    }

    private lateinit var status: TextView
    private lateinit var idxInput: EditText
    private lateinit var pagerInput: EditText
    private lateinit var dbg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        status = TextView(this).apply { textSize = 15f }
        root.addView(status)

        root.btn("1) Likes importieren (liked_posts.json)") { pick(REQ_LIKES) }
        root.btn("2) Gespeichert importieren (saved_posts.json)") { pick(REQ_SAVED) }

        root.addView(TextView(this).apply { text = "\nListe, die abgespielt wird:" })
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        rg.addView(RadioButton(this).apply { text = "Likes"; id = 101 })
        rg.addView(RadioButton(this).apply { text = "Gespeichert"; id = 102 })
        rg.check(if (Store.active(this) == "saved") 102 else 101)
        rg.setOnCheckedChangeListener { _, checked ->
            Store.setActive(this, if (checked == 102) "saved" else "likes")
            refresh()
        }
        root.addView(rg)

        idxInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Start bei Nr. (leer = Position behalten)"
        }
        root.addView(idxInput)
        root.btn("▶ Start / Weiter (öffnet Instagram)") { start() }
        root.btn("■ Stop (Tasten und Buttons aus)") {
            Store.setFlag(this, "session", false)
            NavService.instance?.hideOverlay()
            toast("Gestoppt")
        }

        root.addView(TextView(this).apply { text = "\nOptionen:" })
        root.sw("Nur /reel/-Links (keine Foto-Posts)", "reelsOnly", true, true)
        root.sw("Reihenfolge umkehren (älteste zuerst)", "reverse", false, true)
        root.sw("Lautstärketasten: leiser = nächstes, lauter = vorheriges", "volume", true)
        root.sw("Schwebende Buttons » « ✕ in Instagram", "overlay", true)
        root.sw("Instagram-Task bei jedem Wechsel leeren (Zurück = sofort raus)", "clear", true)
        root.sw("Swipe-Erkennung (experimentell)", "swipe", false)
        root.sw("Debug: Scroll-Views mitschreiben", "debug", false)

        pagerInput = EditText(this).apply {
            setText(Store.pagerId(this@MainActivity))
            hint = "Teil der View-ID des Reel-Pagers"
        }
        root.addView(pagerInput)
        root.btn("Pager-ID speichern") {
            Store.setPagerId(this, pagerInput.text.toString().trim().ifEmpty { "clips_viewer" })
            toast("Gespeichert")
        }

        root.btn("Bedienungshilfen öffnen") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        root.addView(TextView(this).apply { text = "\nDebug-Log (zuletzt gesehene Scroll-Views in Instagram):" })
        dbg = TextView(this).apply { textSize = 11f }
        root.addView(dbg)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun LinearLayout.btn(label: String, onClick: () -> Unit) {
        addView(Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        })
    }

    private fun LinearLayout.sw(label: String, key: String, def: Boolean, resetIdx: Boolean = false) {
        addView(Switch(context).apply {
            text = label
            isChecked = Store.flag(context, key, def)
            setOnCheckedChangeListener { _, v ->
                Store.setFlag(context, key, v)
                if (resetIdx) { Store.resetIndexes(context); refresh() }
            }
        })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun pick(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val list = when (requestCode) {
            REQ_LIKES -> "likes"
            REQ_SAVED -> "saved"
            else -> return
        }
        Thread {
            try {
                val txt = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val keys = Parser.extract(txt)
                Store.saveRaw(this, list, keys)
                runOnUiThread { toast("${keys.size} Links importiert"); refresh() }
            } catch (e: Exception) {
                runOnUiThread { toast("Fehler: ${e.message}") }
            }
        }.start()
    }

    private fun start() {
        val list = Store.active(this)
        val items = Store.items(this, list)
        if (items.isEmpty()) { toast("Liste leer – erst importieren (oder Filter lockern)"); return }
        val n = idxInput.text.toString().toIntOrNull() ?: (Store.index(this, list) + 1)
        val i = (n - 1).coerceIn(0, items.size - 1)
        Store.setIndex(this, list, i)
        Store.setFlag(this, "session", true)
        if (!Launcher.open(this, items[i])) toast("Instagram ließ sich nicht öffnen")
        else if (!serviceEnabled()) toast("Achtung: Bedienungshilfe ist noch nicht aktiv!")
    }

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("$packageName/${NavService::class.java.name}") || s.contains("$packageName/.NavService")
    }

    private fun refresh() {
        val l = Store.items(this, "likes").size
        val s = Store.items(this, "saved").size
        val list = Store.active(this)
        status.text = "Likes: $l  |  Gespeichert: $s\n" +
            "Bedienungshilfe: ${if (serviceEnabled()) "aktiv ✓" else "NICHT aktiv ✗"}\n" +
            "Aktuelle Position (${if (list == "saved") "Gespeichert" else "Likes"}): ${Store.index(this, list) + 1}"
        idxInput.setText("")
        dbg.text = Store.debugLog(this)
    }
}
