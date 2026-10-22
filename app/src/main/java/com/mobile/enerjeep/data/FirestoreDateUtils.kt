package com.mobile.enerjeep.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Reads legacy Firestore dates without assuming every createdAt field is a Timestamp. */
fun DocumentSnapshot.safeTimestamp(fieldName: String): Timestamp? {
    // ESTIMATE: a just-written serverTimestamp() shows up with a local estimate
    // instead of null, so brand-new / just-resolved records sort correctly at once.
    val raw = get(fieldName, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return when (raw) {
        is Timestamp -> raw
        is Date -> Timestamp(raw)
        is Number -> {
            val value = raw.toLong()
            if (value <= 0L) null
            else Timestamp(Date(if (value < 100_000_000_000L) value * 1000L else value))
        }
        is String -> parseSafeTimestamp(raw)?.let { Timestamp(Date(it)) }
        else -> null
    }
}

private fun parseSafeTimestamp(raw: String): Long? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    try { return Instant.parse(value).toEpochMilli() } catch (_: Exception) { }

    val numeric = value.toLongOrNull()
    if (numeric != null) {
        val milliseconds = if (numeric > -100_000_000_000L && numeric < 100_000_000_000L) numeric * 1000L else numeric
        return milliseconds
    }

    val patterns = listOf(
        "MMM dd, yyyy • hh:mm a", "MMM d, yyyy • h:mm a",
        "MMM dd, yyyy hh:mm a", "MMM d, yyyy h:mm a",
        "MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "yyyy/MM/dd",
        "MM/dd/yyyy", "M/d/yyyy", "dd/MM/yyyy"
    )
    for (locale in listOf(Locale.getDefault(), Locale.ENGLISH).distinct()) {
        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, locale).apply { isLenient = false }
                val parsed = formatter.parse(value) ?: continue
                return parsed.time
            } catch (_: Exception) { }
        }
    }
    return null
}