/*
 * TTT, by Mantra Productions.
 */

package dev.patrickgold.florisboard.dictate.nlp

import androidx.compose.runtime.mutableStateOf

/**
 * The AI key at the end of the prediction row.
 *
 * ### The shape, which is the same shape as the key search
 *
 * The local n-gram model predicts from what he has actually written, and it is right most of the
 * time and free every time. When it is not right, he presses one key and a model is asked for
 * candidates instead — and **whichever one he picks is taught back to the local model.**
 *
 * So this is not "AI prediction". It is a way of teaching the local model quickly, on the words it
 * happens to be wrong about, at the moment he notices. The n-gram learns from his committed text
 * anyway; this reaches the same place in one press instead of over weeks, and only where it matters.
 *
 * > **The model is a teacher for the database, not a replacement for it.**
 *
 * The same rule as `MaKeySearch`, and written down in `MANTRA_MANIFEST/modules/find-by-typing.md`.
 *
 * ### Why it is a key and not automatic
 *
 * Every press costs money and a wait of a second or two. Automatic would spend both on every word,
 * including the overwhelming majority the local model already gets right, and would put a pause into
 * typing — the one thing a keyboard may never do. **He asks when he wants it**, which is also the
 * only moment anybody knows the local guess was wrong.
 *
 * ### The state
 *
 * Compose state in an object rather than a flow through NlpManager, because these suggestions do not
 * come from a provider, do not participate in auto-commit, and must not be learned from as though
 * the engine had produced them. They are a temporary overlay on the row, cleared the moment he picks
 * one, types anything, or presses the key again.
 */
object MaAiPredict {

    /** The words the model offered, in its order. Empty means the row shows its ordinary guesses. */
    val words = mutableStateOf<List<String>>(emptyList())

    /** True while a question is in flight, so the key can say so rather than looking dead. */
    val busy = mutableStateOf(false)

    /** How many to ask for. Five fits the row without scrolling and is enough to be worth a press. */
    const val WANTED = 5

    /** The most recent context, kept so a pick can be taught with the words it followed. */
    var context: String = ""
        private set

    /**
     * The text before the cursor when the key was pressed. The words belong to that text only: the
     * moment it changes (a letter typed, one deleted, the field emptied), they are cleared. Phone
     * test, build 421: they stayed in the row through backspace, space and an emptied field.
     */
    var askedAt: String? = null
        private set

    fun clear() {
        words.value = emptyList()
        busy.value = false
        askedAt = null
    }

    /**
     * The question.
     *
     * Bare words back, one per line, and nothing else — no numbering, no explanation, no sentence
     * about being happy to help. Every one of those has to be stripped by [readWords] anyway, and a
     * prompt that invites prose is a prompt that gets prose.
     *
     * The half-typed word is given separately from the text before it, because the two mean
     * different things: one constrains the SPELLING of the answer and the other constrains its
     * SENSE. A model given them as one string treats the fragment as the last word of the sentence
     * and completes the sentence instead of the word.
     */
    fun prompt(before: String, current: String, language: String): String = buildString {
        // The language is stated three times, and that is not clumsiness.
        //
        // The text before the cursor is often in the other language — he writes a sentence in
        // English, presses the badge, and starts one in Croatian — and a model shown mostly English
        // and asked for "the next word" answers in English however the header is worded. Saying it
        // once at the top loses to the evidence in the middle. **Nothing is mixed: the badge decides,
        // and mixing is his to do by hand, one sentence at a time.**
        append("You are a phone keyboard's word prediction for ").append(language).append(".\n")
        append("EVERY answer must be a ").append(language)
            .append(" word. Never answer in another language, even if the text so far is in one.\n")
        append("Give the ").append(WANTED).append(" most likely ").append(language)
            .append(" words, one per line, nothing else.\n")
        append("No numbering, no punctuation, no explanation.\n\n")
        append("Text so far: ").append(before.takeLast(300).ifBlank { "(nothing yet)" }).append('\n')
        if (current.isNotBlank()) {
            append("The word being typed starts with: ").append(current).append('\n')
            append("Every answer must start with those letters.\n")
        } else {
            append("Predict the next word.\n")
        }
    }

    /**
     * The words out of whatever came back.
     *
     * Defensive on purpose. A model asked for bare words will still sometimes number them, quote
     * them, bullet them or introduce them, and the row has no room to be forgiving: a candidate
     * reading `1. "hello"` would be committed with the numbering attached.
     *
     * When [current] is not blank, anything that does not continue what he has typed is dropped
     * rather than shown. Tapping a suggestion replaces the composing word, so a suggestion that does
     * not start with his letters would silently rewrite what he had already typed.
     */
    fun readWords(reply: String, current: String): List<String> {
        val prefix = current.trim().lowercase()
        return reply.lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("-").removePrefix("*").removePrefix("•")
                    .trim()
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim('"', '\'', '`', ' ', '.', ',')
            }
            .filter { it.isNotBlank() && !it.contains(' ') && it.length <= 40 }
            .filter { prefix.isEmpty() || it.lowercase().startsWith(prefix) }
            .distinct()
            .take(WANTED)
            .toList()
    }

    /** Remembers the context a set of suggestions was asked in, so a pick can be taught with it. */
    fun remember(before: String) {
        context = before.takeLast(200)
        askedAt = before
    }

    /** Whether words asked at [askedAt] still belong where the cursor is, with [before] before it. */
    fun stillFor(before: String): Boolean = askedAt != null && askedAt == before

    /**
     * What to teach the local model when he picks one.
     *
     * The chosen word WITH the few words before it, not the word alone. An n-gram learns sequences;
     * a bare word teaches it that the word exists, which it already knew, and nothing about when to
     * offer it. The whole reason this key exists is to fix a wrong guess in a context, so the
     * context is the part that must be stored.
     */
    fun lesson(chosen: String): String {
        val tail = context.trim().split(Regex("\\s+")).takeLast(6).joinToString(" ")
        return if (tail.isBlank()) chosen else "$tail $chosen"
    }
}
