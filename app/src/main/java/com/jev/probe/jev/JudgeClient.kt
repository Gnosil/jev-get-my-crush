package com.jev.probe.jev

import android.util.Log
import com.jev.probe.core.Analysis
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Choice
import com.jev.probe.core.Prefs
import com.jev.probe.core.RankedReply
import com.jev.probe.core.Score
import com.jev.probe.core.kb.ChatContext
import org.json.JSONObject

/**
 * The judgment route. Two shapes:
 * - PROVIDER_RELAY (default in this fork): an OpenAI-compatible chat-completions
 *   endpoint on our own relay. The question set is flattened into a strict-JSON
 *   prompt; one retry on malformed JSON, then per-question nulls (the analysis
 *   degrades, never breaks).
 * - Other providers: the legacy Jev decisions API, unchanged.
 * Nothing generative happens here.
 */
class JudgeClient(private val prefs: Prefs) {

    /**
     * The 7 judgment questions (fast, ~1s). Errors are returned, not thrown.
     *
     * @param ctx D-stage knowledge context; null or empty means the request body
     *        is byte-for-byte what v1.2 sent.
     */
    fun judge(snapshot: ChatSnapshot, relationship: String, ctx: ChatContext? = null): Analysis {
        val start = System.currentTimeMillis()
        return try {
            val answers = postDecisions(
                snapshot, relationship, ctx,
                JevQuestions.judge()
            )
            Analysis(
                trueIntent = parseChoice(answers.optJSONObject("true_intent")),
                dangerLevel = parseScore(answers.optJSONObject("danger_level")),
                sheNeeds = parseChoice(answers.optJSONObject("she_needs")),
                shouldReplyNow = answers.optJSONObject("should_reply_now")?.optDouble("noul"),
                bestAction = parseChoice(answers.optJSONObject("best_action")),
                tensionResolved = answers.optJSONObject("tension_resolved")?.optDouble("noul"),
                literalQuestion = answers.optJSONObject("literal_question")?.optDouble("noul"),
                rankedReplies = emptyList(),
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Log.w(TAG, "judge failed: ${e.message}")
            Analysis(null, null, null, null, null, null, null, emptyList(),
                System.currentTimeMillis() - start, error = e.message ?: "判断接口请求失败")
        }
    }

    /** Ask Jev which of the candidate replies is best; throws on failure. */
    fun rank(
        snapshot: ChatSnapshot,
        relationship: String,
        candidates: List<String>,
        ctx: ChatContext? = null
    ): List<RankedReply> {
        val questions = JSONObject().put("best_reply",
            JevQuestions.rankQuestion(candidates).getJSONObject("best_reply"))
        val answers = postDecisions(snapshot, relationship, ctx, questions)
        return parseRanked(answers.optJSONObject("best_reply"), candidates)
    }

    /**
     * POST one decisions request, with the knowledge fields when there are any.
     *
     * Defensive retry: whether the live `alpha/decisions` endpoint accepts the
     * new `background` / `history` state fields or rejects unknown ones with a
     * 4xx is not verified against production yet (see the A-stage report). If a
     * request carrying them comes back 4xx, it is sent again once without them,
     * so an unverified field can degrade the analysis but never break it.
     */
    private fun postDecisions(
        snapshot: ChatSnapshot,
        relationship: String,
        ctx: ChatContext?,
        questions: JSONObject
    ): JSONObject {
        val background = ctx?.background(relationship) ?: ""
        val history = ctx?.history ?: emptyList()
        val enriched = background.isNotBlank() || history.isNotEmpty()
        return try {
            send(JevQuestions.buildState(snapshot, relationship, background, history), questions)
        } catch (e: ApiException) {
            if (enriched && e.status != null && e.status in 400..499) {
                Log.w(TAG, "judge HTTP ${e.status} with background/history; retrying plain")
                send(JevQuestions.buildState(snapshot, relationship), questions)
            } else throw e
        }
    }

    private fun send(state: JSONObject, questions: JSONObject): JSONObject {
        if (prefs.judgeProvider == Prefs.PROVIDER_RELAY) return relayJudge(state, questions)
        val url = prefs.judgeEndpoint()
        val body = JSONObject()
            .put("model", prefs.judgeModel)
            .put("state", state)
            .put("questions", questions)
        val resp = HttpJson.post(url, prefs.judgeKey, body, Route.JUDGE, HttpJson.headersFor(url))
        return resp.optJSONObject("answers") ?: JSONObject()
    }

    // ------------------------------------------------------------ relay judge

    /**
     * One chat-completions call that answers the same question set with strict
     * JSON. The state object rides in as the user message verbatim (chat text
     * stays Chinese, instructions stay English). Malformed JSON is retried once
     * with a sterner system message; a second failure returns empty answers.
     */
    private fun relayJudge(state: JSONObject, questions: JSONObject): JSONObject {
        val spec = questionSpec(questions)
        val sys = "You are a strict decision engine. You never chat, never explain. " +
            "Answer EVERY question below about the conversation state. " +
            "Output exactly one JSON object: {\"answers\": {<question_key>: <answer>, ...}} with no markdown, " +
            "no code fences, no commentary.\n" +
            "Answer formats by question type:\n" +
            "- choice: {\"choice\": \"<one allowed key>\", \"confidence\": <0..1>}\n" +
            "- score: {\"score\": <integer within the level range>, \"confidence\": <0..1>}\n" +
            "- noul: {\"noul\": <1 or 0>}\n" +
            "Questions:\n$spec"
        val user = "Conversation state (JSON; chat text is Chinese, from=me is the user, from=other is the crush):\n" +
            state.toString()
        for (attempt in 0 until 2) {
            val s = if (attempt == 0) sys else "$sys\nYour previous reply was not valid JSON. " +
                "Output ONLY the JSON object, nothing else."
            val content = chatLowTemp(s, user)
            val answers = extractAnswers(content) ?: continue
            if (answers.length() > 0) return answers
        }
        return JSONObject()
    }

    /** Flatten the question set into an English spec the judge model can follow. */
    private fun questionSpec(questions: JSONObject): String {
        val sb = StringBuilder()
        questions.keys().forEach { key ->
            val q = questions.optJSONObject(key) ?: return@forEach
            val type = q.optString("type")
            sb.append("- ").append(key).append(" (").append(type).append("): ")
                .append(q.optString("instructions")).append('\n')
            val criteria = q.opt("criteria")
            when (criteria) {
                is JSONObject -> criteria.keys().forEach { k ->
                    sb.append("    ").append(k).append(": ").append(criteria.opt(k)).append('\n')
                }
                is org.json.JSONArray -> for (i in 0 until criteria.length()) {
                    sb.append("    level ").append(i + 1).append(": ").append(criteria.opt(i)).append('\n')
                }
                else -> {}
            }
        }
        return sb.toString()
    }

    /** Pull {"answers": {...}} out of a model reply that may be fenced or prefixed. */
    private fun extractAnswers(content: String): JSONObject? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(content.substring(start, end + 1)).optJSONObject("answers")
        } catch (_: Exception) { null }
    }

    /** Chat round trip with temperature pinned to 0: judgment, not creativity. */
    private fun chatLowTemp(system: String, user: String): String {
        val url = prefs.judgeEndpoint()
        val messages = org.json.JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", prefs.judgeModel)
            .put("messages", messages)
            .put("temperature", 0.0)
        val resp = HttpJson.post(url, prefs.judgeKey, body, Route.JUDGE, HttpJson.headersFor(url))
        return resp.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: ""
    }

    private fun parseChoice(o: JSONObject?): Choice? {
        o ?: return null
        val probs = HashMap<String, Double>()
        o.optJSONObject("probabilities")?.let { p ->
            p.keys().forEach { k -> probs[k] = p.optDouble(k) }
        }
        return Choice(o.optString("choice"), o.optDouble("confidence", 0.0), probs)
    }

    private fun parseScore(o: JSONObject?): Score? {
        o ?: return null
        val legend = o.optJSONObject("legend")
        val maxLevel = legend?.keys()?.asSequence()?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: 9
        return Score(o.optDouble("score", 0.0), o.optDouble("confidence", 0.0), maxLevel)
    }

    private fun parseRanked(o: JSONObject?, candidates: List<String>): List<RankedReply> {
        val keys = listOf("reply_a", "reply_b", "reply_c")
        val probs = o?.optJSONObject("probabilities")
        // The relay judge answers {"choice": "reply_x"} without probabilities;
        // fall back to a rank-shaped score so the overlay's "% " badges stay sane.
        val picked = o?.optString("choice")?.takeIf { it.isNotBlank() }
        val pickedIdx = keys.indexOf(picked)
        val fallback = doubleArrayOf(0.5, 0.3, 0.2)
        val list = candidates.mapIndexed { i, text ->
            val p = probs?.optDouble(keys.getOrElse(i) { "" }, Double.NaN)
            val prob = if (p != null && !p.isNaN()) p
            else if (pickedIdx >= 0) fallback[(pickedIdx + keys.size - i) % keys.size]
            else 0.0
            RankedReply(text, prob)
        }
        return list.sortedByDescending { it.prob }
    }

    companion object { private const val TAG = "JEVASSIST" }
}
