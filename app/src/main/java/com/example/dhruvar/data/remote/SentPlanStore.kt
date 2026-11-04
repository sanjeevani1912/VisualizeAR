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

/**
 * Remembers which local layouts were sent to the dashboard, keyed by the layout id.
 * The saved layout files themselves are never changed by sending.
 */
class SentPlanStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("dhruvar_sent_plans", Context.MODE_PRIVATE)

    fun all(): Map<String, SentPlan> {
        val out = HashMap<String, SentPlan>()
        for ((layoutId, raw) in prefs.all) {
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
        val j = JSONObject()
            .put("planId", sent.planId)
            .put("code", sent.code)
            .put("version", sent.version)
            .put("status", sent.status)
            .put("sentAt", sent.sentAtEpochMs)
        prefs.edit().putString(layoutId, j.toString()).apply()
    }
}
