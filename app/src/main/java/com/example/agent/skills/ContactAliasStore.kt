package com.example.agent.skills

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

data class ResolvedContact(
    val name: String,
    val phoneNumber: String
)

object ContactAliasStore {
    private const val TAG = "SAIF_AGENT"

    // Hardcoded initial Hindi / English relationship alias mappings
    private val DEFAULT_ALIASES = mapOf(
        "papa" to listOf("father", "dad", "daddy", "pitaji", "पापा", "पिताजी"),
        "dad" to listOf("father", "papa", "daddy", "पापा"),
        "mummy" to listOf("mother", "mom", "maa", "mataji", "मम्मी", "माँ"),
        "mom" to listOf("mother", "mummy", "maa", "मम्मी"),
        "bhai" to listOf("brother", "bro", "bhaiya", "भाई", "भैया"),
        "didi" to listOf("sister", "sis", "दीदी"),
        "behan" to listOf("sister", "sis", "बहन"),
        "wife" to listOf("patni", "biwi", "पत्नी", "बीवी"),
        "husband" to listOf("pati", "पति")
    )

    fun resolveContact(context: Context, query: String): List<ResolvedContact> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return emptyList()

        val results = mutableListOf<ResolvedContact>()
        val aliases = mutableListOf(cleanQuery)
        DEFAULT_ALIASES[cleanQuery]?.let { aliases.addAll(it) }

        try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                    val number = if (numberIdx >= 0) c.getString(numberIdx) ?: "" else ""
                    val lowerName = name.lowercase()

                    for (alias in aliases) {
                        if (lowerName.contains(alias) || alias.contains(lowerName)) {
                            val cleanNumber = number.replace(Regex("[^0-9+]"), "")
                            if (cleanNumber.isNotBlank() && !results.any { it.name.equals(name, ignoreCase = true) }) {
                                results.add(ResolvedContact(name, cleanNumber))
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contact query failed: ${e.message}")
        }

        return results
    }

    fun getSampleContactNames(context: Context): List<String> {
        val names = mutableListOf<String>()
        try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (c.moveToNext() && names.size < 25) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                    if (name.isNotBlank() && !names.contains(name)) {
                        names.add(name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading contact names for STT bias: ${e.message}")
        }
        return names
    }

    fun normalizePhoneNumber(raw: String, defaultCountryCode: String = "+91"): String {
        var digits = raw.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("+")) return digits
        if (digits.startsWith("0")) digits = digits.removePrefix("0")
        if (digits.length == 10) return "$defaultCountryCode$digits"
        return digits
    }
}
