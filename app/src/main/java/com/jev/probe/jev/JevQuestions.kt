package com.jev.probe.jev

import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.kb.LogEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fixed Jev question set, ported verbatim from tools/jev/questions.py (the
 * wording that passed calibration). Instructions/criteria in English; chat text
 * stays Chinese. The state `from` field uses "me"/"other" (the instructions
 * already refer to "the other person" throughout).
 */
object JevQuestions {

    /**
     * Appended to every question so the D-stage `background` field (relationship,
     * contact notes, knowledge-base hits) reads as given context rather than as
     * an off-topic digression that should be penalized.
     */
    const val BACKGROUND_NOTE =
        " Facts given in background are provided context, not off-topic."

    private fun noul(instructions: String, t: String, f: String) = JSONObject().apply {
        put("type", "noul")
        put("instructions", instructions + BACKGROUND_NOTE)
        put("criteria", JSONObject().put("true", t).put("false", f))
    }

    private fun choice(instructions: String, criteria: Map<String, String>) = JSONObject().apply {
        put("type", "choice")
        put("instructions", instructions + BACKGROUND_NOTE)
        put("criteria", JSONObject().also { c -> criteria.forEach { (k, v) -> c.put(k, v) } })
    }

    private fun score(instructions: String, levels: List<String>) = JSONObject().apply {
        put("type", "score")
        put("instructions", instructions + BACKGROUND_NOTE)
        put("criteria", JSONArray().also { a -> levels.forEach { a.put(it) } })
    }

    /**
     * The 7 crush-decision questions (JEV get my crush). Same answer keys as the
     * upstream set so Analysis/Overlay keep working; semantics re-aimed at the
     * relationship-stage workflow:
     *
     * - true_intent    → relationship stage
     * - danger_level   → cringe / overreach risk (油腻、越界、用力过猛)
     * - she_needs      → suggested reply style
     * - should_reply_now → is it worth replying substantively right now
     * - best_action    → next move type
     * - tension_resolved → date-invitation window (邀约窗口期)
     * - literal_question → is the other person testing / hinting (话里有话)
     */
    fun judge(): JSONObject = JSONObject().apply {
        put("literal_question", noul(
            "Is the other person's latest message carrying subtext - a test, a hint, " +
                "an invitation disguised as small talk? Judge from the whole thread, not one sentence.",
            "The latest message is plain small talk with no hidden test, hint, or bait.",
            "There is subtext: fishing for attention, testing how much you care, " +
                "hinting they want to talk more or meet up, teasing to see your reaction, " +
                "or a cold short line that really means they feel neglected."
        ))
        put("true_intent", choice(
            "What stage is this relationship at, given the full conversation? " +
                "Judge by reciprocity: who initiates, how long the replies are, whether they " +
                "ask questions back, use emojis/teasing, or mention meeting offline.",
            linkedMapOf(
                "just_added" to ("Just added as contacts (or one cold open exchanged). " +
                    "Chat is stiff, short answers, no personal topics yet."),
                "icebreaking" to ("Icebreaking: a few light exchanges happened, but topics stay " +
                    "surface-level and they reply politely rather than eagerly."),
                "daily_chat" to ("Comfortable daily chat: they reply consistently, sometimes " +
                    "initiate, share bits of their life, but nothing flirty yet."),
                "flirty" to ("Flirty: teasing, emojis, late-night chats, compliments, " +
                    "or both sides clearly enjoy the conversation beyond politeness."),
                "dating_window" to ("Meeting up is on the table: they brought up meeting, " +
                    "agreed to a plan, or the vibe is one concrete invite away from a date."),
                "committed" to ("Effectively together: exclusivity, 明确的男女朋友 dynamics, " +
                    "or planning life together.")
            )
        ))
        put("danger_level", score(
            "How likely is a clumsy next message to hurt the relationship - reading as " +
                "needy, greasy (油腻), overeager, or crossing a line they have not invited? " +
                "Score by how much rope the other person has given, not by how much you like them.",
            listOf(
                "Very safe: warm mutual chat, almost anything reasonable lands fine.",
                "Mostly safe; only a wildly inappropriate reply would feel off.",
                "Slight risk: they are friendly but not flirty - too much eagerness would show your hand.",
                "They reply short or slow; pushing topics or over-texting would read as needy.",
                "Clear mismatch in investment: you care visibly more; overtures would feel heavy.",
                "They show mild discomfort signals; a greasy or presumptuous reply would push them away.",
                "They have hinted at distance; anything beyond light and respectful is a mistake.",
                "They are close to pulling away; only space and no-pressure respect can keep this alive.",
                "They said something close to 'stop' - boundaries were named; do not push at all.",
                "They asked for no contact or blocked / deleted; do not message."
            )
        ))
        put("should_reply_now", noul(
            "Should you reply substantively right now, or hold back? " +
                "Substantive means adding content, a question, or an emotion that moves the chat. " +
                "Answer FALSE when the ball is in their court (you just sent the last message and " +
                "they have not replied), when they clearly need space, or when a shorter, slower " +
                "reply preserves the tension better than an instant one.",
            "A reply now helps: they asked something, left a hook, or the momentum is good.",
            "Do not push now: double-texting, chasing, or instant long replies would lower your value."
        ))
        put("best_action", choice(
            "What type of next move is best? Ignore timing. Choose only the move type. " +
                "When in doubt prefer the move that invites THEM to invest more, not the one " +
                "that spends more of your own effort.",
            linkedMapOf(
                "light_banter" to ("Keep it playful and light - joke, tease gently, react to " +
                    "what they said without escalating."),
                "share_self" to ("Share something about your own life - a story, a photo, an " +
                    "opinion - that gives them a window into you."),
                "ask_about_them" to ("Ask about something they mentioned before - shows you " +
                    "listen; NOT generic interview questions like 在干嘛/吃了吗."),
                "make_date" to ("Propose a concrete, low-pressure meetup tied to a shared " +
                    "interest already mentioned in the chat."),
                "cool_down" to ("Reply shorter and slower, or not at all this round; give space " +
                    "and let them come to you."),
                "acknowledge" to ("Simply receive what they said - warmth without a new push.")
            )
        ))
        put("she_needs", choice(
            "What tone would the ideal next message take? Judge the LATEST message first. " +
                "If they are upset or distant, sincerity wins over cleverness. " +
                "If they are playful, matching their energy beats being earnest.",
            linkedMapOf(
                "humor" to ("They are in a playful mood; a funny or teasing reply keeps the spark."),
                "sincere" to ("They shared something real or feel down; an honest, warm reply fits."),
                "push_pull" to ("Interest is mutual but unspoken; light push-pull tension fits."),
                "cool_down" to ("They seem busy or lukewarm; relaxed and undemanding fits."),
                "date_invite" to ("The window is open; wrapping the chat into a concrete invite fits.")
            )
        ))
        put("tension_resolved", noul(
            "Is this a good window to invite them to do something offline? " +
                "Answer true only when the conversation is warm, they are engaged, and a " +
                "concrete low-pressure invite (coffee, an event tied to a shared interest) " +
                "would feel natural - not when the chat is cold, stalled, or they just hinted " +
                "at being busy.",
            "Good window: engaged conversation, positive mood, a natural excuse to meet exists.",
            "Not a window: chat is cold, slow, transactional, or they signaled being busy."
        ))
    }

    /**
     * Build Jev state from a snapshot (last 10 messages), optionally carrying
     * the D-stage knowledge context.
     *
     * @param background relationship + contact notes + matched knowledge notes.
     * @param history older messages for this contact, already de-duplicated
     *        against what is on screen.
     *
     * Both extra fields are omitted when empty, so a user with no knowledge base
     * sends exactly the same body v1.2 did.
     */
    fun buildState(
        snapshot: ChatSnapshot,
        relationship: String,
        background: String = "",
        history: List<LogEntry> = emptyList()
    ): JSONObject {
        val msgs = JSONArray()
        val last10 = snapshot.messages.takeLast(10)
        for (m in last10) {
            msgs.put(JSONObject().put("from", m.side).put("text", m.text))
        }
        val chat = JSONObject()
            .put("relationship", relationship)
            .put("messages", msgs)
            .put("latest_from", last10.lastOrNull()?.side ?: "other")
        val state = JSONObject().put("chat", chat)
        if (background.isNotBlank()) state.put("background", background)
        if (history.isNotEmpty()) {
            val h = JSONArray()
            history.forEach { h.put(JSONObject().put("from", it.side).put("text", it.text)) }
            state.put("history", h)
        }
        return state
    }

    /** The best_reply ranking question over exactly 3 candidates (Chinese text kept). */
    fun rankQuestion(candidates: List<String>): JSONObject {
        require(candidates.size == 3) { "rankQuestion expects exactly 3 candidates" }
        val keys = listOf("reply_a", "reply_b", "reply_c")
        val criteria = JSONObject()
        keys.forEachIndexed { i, k -> criteria.put(k, candidates[i]) }
        val q = JSONObject().apply {
            put("type", "choice")
            put("instructions",
                "Which candidate reply is the best next message for growing this crush's " +
                    "interest, given the stage and the other person's mood? " +
                    "Prefer a reply that matches the best action type and tone. " +
                    "Penalize needy, greasy, over-long, or boundary-crossing replies. " +
                    "If the window is open, prefer the reply that moves toward a concrete meetup." + BACKGROUND_NOTE)
            put("criteria", criteria)
        }
        return JSONObject().put("best_reply", q)
    }
}
