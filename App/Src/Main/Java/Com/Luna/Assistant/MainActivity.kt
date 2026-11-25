package com.luna.assistant
import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val provs = AiClient.provs.keys.toTypedArray()
    private lateinit var p: SharedPreferences
    private lateinit var holder: FrameLayout
    private var dark = true
    private val fg get() = if (dark) Color.WHITE else Color.parseColor("#2A1B4D")
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun tv(t: String, sz: Float = 15f) = TextView(this).apply { text = t; textSize = sz; setTextColor(fg) }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); isAllCaps = false
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0xCCB65CFF.toInt()) }
        setOnClickListener { f() }
    }
    private fun card(vararg v: View) = col().apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(if (dark) 0x33FFFFFF else 0x66FFFFFF) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) }
        v.forEach { addView(it) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("luna", 0); dark = p.getBoolean("dark", true)
        holder = FrameLayout(this)
        val tabs = LinearLayout(this).apply { setPadding(dp(8), dp(6), dp(8), dp(12)) }
        val pages = listOf<Pair<String, () -> View>>("Home" to ::homePage, "Boost" to ::boostPage, "Perms" to ::permsPage, "AI" to ::aiPage)
        for ((n, f) in pages) tabs.addView(btn(n) { show(f) }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        val c = col().apply {
            setPadding(0, dp(36), 0, 0)
            addView(tv("Luna  ✦", 30f).apply { setPadding(dp(18), 0, 0, 0) })
            addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(tabs)
        }
        setContentView(FrameLayout(this).apply { addView(AuraView(this@MainActivity, dark)); addView(c) })
        show(::homePage)
    }
    private fun show(f: () -> View) {
        holder.removeAllViews()
        holder.addView(ScrollView(this).apply { addView(f().apply { setPadding(dp(14), dp(8), dp(14), dp(8)) }) })
    }
    private fun startLuna() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startForegroundService(Intent(this, LunaService::class.java))
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }
    override fun onRequestPermissionsResult(c: Int, ps: Array<out String>, r: IntArray) { if (c == 1) startLuna() }

    private fun homePage(): LinearLayout {
        val out = tv("", 16f)
        val q = EditText(this).apply { hint = "Luna se type karke bolo"; setTextColor(fg); setHintTextColor(Color.GRAY) }
        val chips = LinearLayout(this)
        for ((n, cmd) in listOf("Torch" to "torch on", "YouTube" to "youtube", "WiFi" to "wifi", "Settings" to "settings"))
            chips.addView(btn(n) { Actions.handle(this, cmd) { r -> runOnUiThread { out.text = r } } }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        return col().apply {
            addView(card(tv("Bolo: \"Hello Luna\" — wo background mein sunti rehti hai"), btn("Start Luna") { startLuna() },
                btn("Stop Luna") { stopService(Intent(this@MainActivity, LunaService::class.java)) }))
            addView(card(tv("Quick actions"), chips))
            addView(card(q, btn("Send") { Actions.handle(this@MainActivity, q.text.toString()) { r -> runOnUiThread { out.text = r } } }, out))
        }
    }

    private fun boostPage(): LinearLayout {
        val info = tv("")
        fun refresh() {
            val mi = ActivityManager.MemoryInfo(); getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
            val bt = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
            val rssi = try { (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.rssi } catch (e: Exception) { -127 }
            val w = if (rssi > -60) "strong" else if (rssi > -75) "theek" else "weak - router ke paas jao"
            val base = "RAM free: ${mi.availMem / 1048576} / ${mi.totalMem / 1048576} MB\nBattery temp: $bt °C\nWiFi signal: $rssi dBm ($w)"
            info.text = base
            thread {
                val t0 = System.nanoTime()
                val ms = try { Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 2000) }; ((System.nanoTime() - t0) / 1000000).toString() } catch (e: Exception) { "no net" }
                runOnUiThread { info.text = "$base\nPing: $ms ms" }
            }
        }
        refresh()
        return col().apply {
            addView(card(tv("Lag check", 18f), info, btn("Refresh") { refresh() }))
            addView(card(tv("Game Boost: background apps band karta hai taaki game smooth chale. Android kuch apps wapas khol sakta hai."),
                btn("Game Boost ON") {
                    val am = getSystemService(ActivityManager::class.java); var n = 0
                    for (a in packageManager.getInstalledApplications(0))
                        if (a.flags and ApplicationInfo.FLAG_SYSTEM == 0 && a.packageName != packageName) { am.killBackgroundProcesses(a.packageName); n++ }
                    Toast.makeText(this@MainActivity, "$n apps ko band karne bheja", Toast.LENGTH_SHORT).show(); refresh()
                }))
        }
    }

    private fun permsPage(): LinearLayout {
        val all = listOf("Mic (awaaz sunne ke liye)" to Manifest.permission.RECORD_AUDIO, "Call" to Manifest.permission.CALL_PHONE,
            "SMS" to Manifest.permission.SEND_SMS, "Contacts" to Manifest.permission.READ_CONTACTS, "Notifications" to Manifest.permission.POST_NOTIFICATIONS)
        val boxes = all.map { (n, perm) -> CheckBox(this).apply { text = n; setTextColor(fg); isChecked = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED } }
        return col().apply {
            addView(card(tv("Jo permission dena hai wahi select karo", 16f), *boxes.toTypedArray(),
                btn("Allow selected") { requestPermissions(all.filterIndexed { i, _ -> boxes[i].isChecked }.map { it.second }.toTypedArray(), 2) }))
            addView(card(tv("Background mein apps kholne ke liye"), btn("Display over apps") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }))
        }
    }

    private fun aiPage(): LinearLayout {
        val sp = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, provs); setSelection(provs.indexOf(p.getString("prov", "GitHub"))) }
        val key = EditText(this).apply { hint = "API key / GitHub token"; setText(p.getString("key_" + provs[sp.selectedItemPosition], "")); setTextColor(fg); setHintTextColor(Color.GRAY) }
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, i: Int, l: Long) { key.setText(p.getString("key_" + provs[i], "")) }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        return col().apply {
            addView(card(tv("AI chuno (fail ho to agla AI auto lagega)"), sp, key,
                btn("Get key / token (signup page)") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiClient.provs[sp.selectedItem.toString()]))) },
                btn("Save") { p.edit().putString("prov", sp.selectedItem.toString()).putString("key_" + sp.selectedItem, key.text.toString().trim()).apply() }))
            addView(card(btn(if (dark) "Light theme" else "Dark theme") { p.edit().putBoolean("dark", !dark).apply(); recreate() }))
        }
    }
}
