package com.example.dhruvar

import com.example.dhruvar.data.remote.SentPlan
import com.example.dhruvar.data.remote.SentPlanStore
import com.example.dhruvar.data.remote.StringPrefs
import com.example.dhruvar.data.remote.reviewerFeedback
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewerFeedbackTest {

    private fun plan(json: String) = JSONObject(json)

    @Test
    fun reasonOfLatestDecisionAndReviewerCommentsAreShown() {
        val p = plan(
            """{"ownerId":"planner","decisions":[
                 {"version":1,"decision":"changes","reason":"Old v1 reason","decidedAt":10},
                 {"version":2,"decision":"changes","reason":"Add more vehicles","decidedAt":50}],
               "comments":[
                 {"version":2,"authorId":"reviewer","text":"Move the antenna north","createdAt":60},
                 {"version":2,"authorId":"planner","text":"my own note","createdAt":70},
                 {"version":1,"authorId":"reviewer","text":"old comment","createdAt":5}]}"""
        )
        assertEquals(listOf("Add more vehicles", "Move the antenna north"), reviewerFeedback(p, 2))
    }

    @Test
    fun nothingForANewVersionOrMissingFields() {
        val p = plan("""{"ownerId":"planner","decisions":[{"version":1,"reason":"x is wrong here","decidedAt":1}]}""")
        assertEquals(emptyList<String>(), reviewerFeedback(p, 2))
        assertEquals(emptyList<String>(), reviewerFeedback(plan("{}"), 1))
    }

    @Test
    fun keepsOnlyTheNewestThree() {
        val comments = (1..5).joinToString(",") { """{"version":1,"authorId":"r","text":"c$it","createdAt":$it}""" }
        assertEquals(listOf("c3", "c4", "c5"), reviewerFeedback(plan("""{"ownerId":"o","comments":[$comments]}"""), 1))
    }

    @Test
    fun feedbackSurvivesTheStoreAndOldRecordsStillLoad() {
        val map = HashMap<String, String>()
        val prefs = object : StringPrefs {
            override fun all(): Map<String, *> = map
            override fun put(key: String, value: String) { map[key] = value }
        }
        val store = SentPlanStore(prefs)
        store.put("a", SentPlan("p", "PLN-1", 2, "CHANGES_REQUESTED", 1L, listOf("Add more vehicles")))
        map["old"] = """{"planId":"q","code":"PLN-2","version":1,"status":"SUBMITTED","sentAt":3}"""
        val all = store.all()
        assertEquals(listOf("Add more vehicles"), all.getValue("a").feedback)
        assertEquals(emptyList<String>(), all.getValue("old").feedback)
    }
}
