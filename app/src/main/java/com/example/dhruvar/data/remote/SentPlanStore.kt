package com.example.dhruvar.data.remote

import android.content.Context
import org.json.JSONObject

/** The dashboard's record of one plan that was sent from this phone. */
data class SentPlan(
    val planId: String,
    val code: String,
    val version: Int,
    /** Server status: SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, CHANGES_REQUESTED or WITHDRAWN. */
    val status: String,
    val sentAtEpochMs: Long
) {
    /** True when the reviewer is waiting for the planner to change and resend the plan. */
    val canResubmit: Boolean get() = status == STATUS_CHANGES_REQUESTED || status == STATUS_REJECTED

    companion object {
        const val STATUS_CHANGES_REQUESTED = "CHANGES_REQUESTED"
        const val STATUS_REJECTED = "REJECTED"
    }
}

/** The few SharedPreferences calls [SentPlanStore] needs, so the JVM tests can use a plain map. */
interface StringPrefs {
    fun all(): Map<String, *>
    fun put(key: String, value: String)
}

/**
 * Remembers which local layouts were sent to the dashboard, keyed by the layout id.
 * The saved layout files themselves are never changed by sending.
 *
 * Records belong to one account: each signed-in user id gets its own preferences file
 * ([prefsNameFor]), so on a shared phone one user never sees another user's sent plans.
 * Signed out ([userId] null) there are no records at all. The older shared file
 * "dhruvar_sent_plans" is left untouched; refreshing statuses after sign-in rebuilds a user's
 * records from the server.
 */
class SentPlanStore(private val prefs: StringPrefs?) {

    constructor(context: Context, userId: String?) : this(
        userId?.takeIf { it.isNotBlank() }?.let { id ->
            val sp = context.applicationContext.getSharedPreferences(prefsNameFor(id), Context.MODE_PRIVATE)
            object : StringPrefs {
                override fun all(): Map<String, *> = sp.all
                override fun put(key: String, value: String) {
                    sp.edit().putString(key, value).apply()
                }
            }
        }
    )

    fun all(): Map<String, SentPlan> {
        val source = prefs ?: return emptyMap()
        val out = HashMap<String, SentPlan>()
        for ((layoutId, raw) in source.all()) {
            val text = raw as? String ?: continue
            try {
                val j = JSONObject(text)
                out[layoutId] = SentPlan(
                    planId = j.getString("planId"),
                    code = j.getString("code"),
                    version = j.optInt("version", 1),
                    status = j.optString("status", "SUBMITTED"),
                    sentAtEpochMs = j.optLong("sentAt", 0L)
                )
            } catch (_: Exception) {
                // A damaged entry is skipped, never allowed to break the Home screen.
            }
        }
        return out
    }

    fun put(layoutId: String, sent: SentPlan) {
        val target = prefs ?: return
        val j = JSONObject()
            .put("planId", sent.planId)
            .put("code", sent.code)
            .put("version", sent.version)
            .put("status", sent.status)
            .put("sentAt", sent.sentAtEpochMs)
        target.put(layoutId, j.toString())
    }

    companion object {
        /** One preferences file per server user id. */
        fun prefsNameFor(userId: String): String =
            "dhruvar_sent_plans_u_" + userId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }
}
