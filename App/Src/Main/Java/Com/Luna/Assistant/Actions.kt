package com.luna.assistant
import android.content.*
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.*
import android.telephony.SmsManager
import kotlin.concurrent.thread

object Actions {
    private fun calc(s: String): Double {
        var i = 0
        fun pk() = if (i < s.length) s[i] else '\u0000'
        fun num(): Double { val st = i; while (pk().isDigit() || pk() == '.') i++; return s.substring(st, i).toDouble() }
        fun term(): Double { var v = num(); while (pk() == '*' || pk() == '/') { val o = s[i++]; val r = num(); v = if (o == '*') v * r else v / r }; return v }
        fun expr(): Double { var v = term(); while (pk() == '+' || pk() == '-') { val o = s[i++]; val r = term(); v = if (o == '+') v + r else v - r }; return v }
        return expr()
    }
    private fun number(c: Context, name: String): String? {
        if (name.any { it.isDigit() }) return name.filter { it.isDigit() || it == '+' }
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }
    private fun go(c: Context, i: Intent) = c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun handle(c: Context, text: String, reply: (String) -> Unit) {
        val l = text.lowercase().trim()
        val p = c.getSharedPreferences("luna", 0)
        try {
            if (l.contains("torch") || l.contains("flash")) {
                val on = !(l.contains("off") || l.contains("band") || l.contains("bujha"))
                val cm = c.getSystemService(CameraManager::class.java)
                cm.setTorchMode(cm.cameraIdList[0], on)
                return reply(if (on) "G Sir, torch on ho gayi" else "G Sir, torch off ho gayi")
            }
            if (l.contains("calculate") || Regex("\\d\\s*(plus|minus|into|times|divided by|[+*/x-])\\s*\\d").containsMatchIn(l)) {
                val e = l.replace("plus", "+").replace("minus", "-").replace("into", "*").replace("times", "*")
                    .replace("divided by", "/").replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*").filter { it in "0123456789.+-*/" }
                val d = calc(e)
                return reply("G Sir, iska answer ye raha: " + if (d == d.toLong().toDouble()) d.toLong() else d)
            }
            if (Regex("\\bcall\\b").containsMatchIn(l)) {
                val n = (Regex("call\\s+(.+)").find(l)?.groupValues?.get(1) ?: Regex("(.+?)\\s+ko\\s+call").find(l)!!.groupValues[1])
                    .replace(Regex("\\b(karo|kro|kar do|please)\\b"), "").trim()
                val num = number(c, n) ?: return reply("Sir, $n ka number nahi mila")
                go(c, Intent(Intent.ACTION_CALL, Uri.parse("tel:$num"))); return reply("G Sir, $n ko call laga raha hoon")
            }
            Regex("(?:sms|message)\\s+(\\S+)\\s+(.+)").find(l)?.let {
                val num = number(c, it.groupValues[1]) ?: return reply("Sir, number nahi mila")
                SmsManager.getDefault().sendTextMessage(num, null, it.groupValues[2], null, null); return reply("G Sir, sms bhej diya")
            }
            if (l.contains("wifi")) { go(c, Intent(Settings.ACTION_WIFI_SETTINGS)); return reply("G Sir, WiFi settings khol di") }
            if (l.contains("bluetooth")) { go(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return reply("G Sir, Bluetooth settings khol di") }
            if (l.contains("setting")) { go(c, Intent(Settings.ACTION_SETTINGS)); return reply("G Sir, settings khol di") }
            if (l.contains("youtube") || Regex("\\b(play|chalao)\\b").containsMatchIn(l)) {
                val q = l.replace(Regex("\\b(youtube|search|play|song|video|on|karo|kro|chalao|open|kholo)\\b"), "").trim()
                if (q.isEmpty()) c.packageManager.getLaunchIntentForPackage("com.google.android.youtube")?.let { go(c, it) }
                else go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                return reply("G Sir, YouTube par $q chala raha hoon")
            }
            val app = Regex("(?:open|kholo|khol)\\s+(.+)").find(l)?.groupValues?.get(1) ?: Regex("(.+?)\\s+(?:kholo|open)").find(l)?.groupValues?.get(1)
            if (app != null) {
                val pm = c.packageManager
                val a = pm.getInstalledApplications(0).firstOrNull { pm.getLaunchIntentForPackage(it.packageName) != null && pm.getApplicationLabel(it).toString().lowercase().contains(app.trim()) }
                if (a != null) { go(c, pm.getLaunchIntentForPackage(a.packageName)!!); return reply("G Sir, $app khol raha hoon") }
            }
        } catch (e: Exception) { return reply("Sir, kaam nahi ho paya: ${e.message}") }
        thread { reply(AiClient.askAny(p, text)) }
    }
}
