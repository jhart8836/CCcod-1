package com.anchorone.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast

/**
 * User-initiated contact actions. These only ever open the dialer or messaging app with
 * details pre-filled — the person must press call/send themselves. Nothing is automatic.
 */
object ContactIntents {

    fun dial(context: Context, number: String) {
        val clean = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (clean.isEmpty()) return
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
    }

    fun text(context: Context, number: String, body: String) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).putExtra("sms_body", body))
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this device can handle that.", Toast.LENGTH_LONG).show()
        }
    }
}

/** Local emergency number from the network/SIM country — no permissions, no network. */
object EmergencyNumbers {
    private val byCountry = mapOf(
        "us" to "911", "ca" to "911", "mx" to "911", "pr" to "911",
        "gb" to "999", "ie" to "112", "au" to "000", "nz" to "111",
        "in" to "112", "jp" to "119", "kr" to "119", "cn" to "120", "tw" to "119",
        "hk" to "999", "sg" to "995", "my" to "999", "za" to "10177", "br" to "192",
        "ar" to "107", "il" to "101", "ph" to "911", "ae" to "998", "sa" to "997",
    )

    fun forCountry(iso: String?): String = byCountry[iso?.lowercase()] ?: "112"

    fun detect(context: Context, override: String): String {
        if (override.isNotBlank()) return override.trim()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = tm?.networkCountryIso?.takeIf { it.isNotBlank() } ?: tm?.simCountryIso
        return forCountry(iso)
    }
}
