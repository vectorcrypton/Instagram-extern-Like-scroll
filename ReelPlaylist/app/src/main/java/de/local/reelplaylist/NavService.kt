package de.local.reelplaylist

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class NavService : AccessibilityService() {

    companion object { var instance: NavService? = null }

    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlay: View? = null
    private var lastNav = 0L
    private var lastSwipe = 0L

    override fun onServiceConnected() { instance = this }

    override fun onDestroy() {
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == Store.IG) showOverlay()
                else if (pkg != packageName && pkg != "com.android.systemui" && !isIme(pkg)) hideOverlay()
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (pkg == Store.IG) onScroll(e)
        }
    }

    // Lautstärketasten: leiser = nächstes Reel, lauter = vorheriges (nur in Instagram, nur bei aktiver Session)
    override fun onKeyEvent(ev: KeyEvent): Boolean {
        if (!Store.flag(this, "session", false) || !Store.flag(this, "volume", true)) return false
        val code = ev.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP) return false
        if (rootInActiveWindow?.packageName?.toString() != Store.IG) return false
        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
            go(if (code == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
        }
        return true
    }

    // Experimentell: Scroll-Ereignis des Reel-Pagers => nächstes Reel aus der Liste
    private fun onScroll(e: AccessibilityEvent) {
        val id = e.source?.viewIdResourceName ?: ""
        if (Store.flag(this, "debug", false)) {
            Store.addDebug(this, "id=$id | cls=${e.className} | dy=${e.scrollDeltaY}")
        }
        if (!Store.flag(this, "session", false) || !Store.flag(this, "swipe", false)) return
        if (id.isEmpty() || !id.contains(Store.pagerId(this))) return
        if (e.scrollDeltaY < 0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSwipe < 1500) return
        lastSwipe = now
        go(1)
    }

    fun go(delta: Int) {
        val list = Store.active(this)
        val items = Store.items(this, list)
        if (items.isEmpty()) { toast("Liste leer"); return }
        val now = SystemClock.elapsedRealtime()
        if (now - lastNav < 600) return
        val cur = Store.index(this, list)
        val idx = (cur + delta).coerceIn(0, items.size - 1)
        if (idx == cur) { toast(if (delta > 0) "Ende der Liste" else "Anfang der Liste"); return }
        lastNav = now
        Store.setIndex(this, list, idx)
        Launcher.open(this, items[idx])
        toast("${idx + 1}/${items.size}")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun isIme(pkg: String): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private fun showOverlay() {
        if (overlay != null) return
        if (!Store.flag(this, "overlay", true) || !Store.flag(this, "session", false)) return
        val d = resources.displayMetrics.density
        val size = (44 * d).toInt()

        fun btn(t: String, a: () -> Unit) = TextView(this).apply {
            text = t
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { a() }
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0x66000000); cornerRadius = 22 * d }
            addView(btn("»") { go(1) })
            addView(btn("«") { go(-1) })
            addView(btn("✕") {
                Store.setFlag(this@NavService, "session", false)
                hideOverlay()
                toast("Playlist gestoppt")
            })
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = (4 * d).toInt()
        }
        try { wm.addView(box, lp); overlay = box } catch (_: Exception) {}
    }

    fun hideOverlay() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }
}
