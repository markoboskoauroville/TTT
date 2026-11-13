/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.dictate.nlp.MaTypingLanguage
import dev.patrickgold.florisboard.dictate.nlp.MaNgram
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates
import dev.patrickgold.florisboard.ime.text.key.KeyCode

private const val BLANK_STR_PATTERN = "^\\s*$"

// Frequency stored for a word learned from the suggestion strip (issue #241) — the maximum, matching what
// the user dictionary settings screen assigns to a hand-added word.
private const val USER_DICTIONARY_FREQ = 255

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
        // Every installed language's dictionary is ready, because the typing language can move the
        // suggestions to the other one mid-sentence (MaTypingLanguage).
        subtypeManager.subtypesFlow.collectLatestIn(scope) { all ->
            all.forEach { preload(it) }
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = true,
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        // Using a cache because I have no idea how fast the runBlocking is
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    // Set by a glide-typing commit: the word commit itself triggers one resetSuggestions → suggest() that
    // would immediately wipe the just-shown glide alternatives. This one-shot flag makes that next suggest()
    // a no-op so the alternatives stay in the strip until the user's next input (issue #127).
    @Volatile
    private var holdNextSuggest = false

    @Volatile
    private var suggestJob: kotlinx.coroutines.Job? = null

    fun suggest(subtype: Subtype, content: EditorContent) {
        if (holdNextSuggest) {
            holdNextSuggest = false
            return
        }
        val reqTime = SystemClock.uptimeMillis()
        // THE LANGUAGE HE IS TYPING (27.10.2026: "if I'm typing something that is not English, it
        // should give me Croatian suggestions"). Read from the words before the cursor when that is
        // on, else the badge. The shipped dictionary is the one of the installed subtype in that
        // language; the keyboard's own subtype, its layout and the transcription are not touched.
        val typing = MaTypingLanguage.of(content.textBeforeSelection)
        val shippedSubtype = if (subtype.primaryLocale.language.lowercase() == typing) subtype
            else subtypeManager.subtypes.firstOrNull { it.primaryLocale.language.lowercase() == typing }
            // No installed keyboard in that language, but its dictionary ships in the APK (Croatian, since
            // 7.11.2026): the same keyboard, read with that language's dictionary. Marko's profile has only
            // en-US, so under the HR badge the shipped engine used to be dropped entirely.
            ?: if (typing in dev.patrickgold.florisboard.ime.nlp.latin.GlideDictionaryCatalog.BUNDLED) {
                subtype.copy(primaryLocale = dev.patrickgold.florisboard.lib.FlorisLocale.from(typing))
            } else null
        // One suggestion job at a time: a keystroke cancels the one before it (7.11.2026, ANR on the
        // emulator: four jobs ran at once, all building spelling candidates, and the garbage they made
        // stalled the main thread for 5 s). LatinLanguageProvider checks for cancellation between phases.
        suggestJob?.cancel()
        suggestJob = scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = true,
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    emptyList()
                }
                // Guarded: a word engine that throws (live test 7.11.2026: a number being typed, "09155",
                // left the strip frozen on the previous word) must not take the address book down with it.
                else -> runCatching {
                    getSuggestionProvider(shippedSubtype ?: subtype).suggest(
                        subtype = shippedSubtype ?: subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = true,
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }.getOrDefault(emptyList())
            }
            // The personal model goes in front of the shipped one.
            //
            // It knows nothing about language in general and a great deal about the words written on
            // this phone, which is exactly the set the shipped dictionary is missing: place names,
            // project names, the jargon of one trade. Where it has an answer at all it is the better
            // answer, and where it has none it returns nothing and costs nothing.
            //
            // Duplicates are dropped rather than shown twice: the same word arriving from both is
            // the common case, not the exception.
            val personalTiers = HashMap<String, Int>()
            val personal = runCatching {
                MaNgram.predict(
                    textBeforeCursor = content.textBeforeSelection,
                    currentWord = content.currentWordText,
                    isIncognito = keyboardManager.activeState.isIncognitoMode,
                )
            }.getOrDefault(emptyList()).filter { hit ->
                // SwiftKey offers a word it does not know only once it has been used more than once, and never
                // a known misspelling (live test 7.11.2026: one typed "goverment" came back first for good).
                val shipped = dev.patrickgold.florisboard.dictate.nlp.MaShippedWords
                // His own corrections (tier 4) and what Haiku taught (tier 0) always pass.
                hit.word.any { it.isLetter() } && (
                    hit.tier !in 1..3 || (
                        !shipped.isMisspelling(hit.word) &&
                            (hit.count >= 2 || shipped.isKnown(hit.word) || !shipped.isLoaded("en"))
                        )
                    )
            }.also { hits -> hits.forEach { personalTiers[it.word] = it.tier } }.mapTo(ArrayList()) { hit ->
                WordSuggestionCandidate(
                    text = hit.word,
                    secondaryText = null,
                    confidence = 0.9,
                    isEligibleForAutoCommit = false,
                    sourceProvider = null,
                )
            }
            // THE SHIPPED DICTIONARY ONLY SPEAKS WHEN IT IS SPEAKING HIS LANGUAGE.
            //
            // This is the bug he photographed: the badge said HR and the row offered "what",
            // "existing", "are", "for", "to", "and", "in". None of those came from the personal
            // model — that one was split by language and is innocent. They came from the shipped
            // dictionary, which is chosen per subtype and falls back to an English one when the
            // subtype has no dictionary of its own.
            //
            // So a provider whose locale does not match the badge is DROPPED ENTIRELY. Not ranked
            // lower, dropped. **A wrong-language suggestion is not a weaker answer, it is a wrong
            // one**, and an English row under a Croatian badge is worse than an empty row: the empty
            // row tells the truth, which is that nothing here knows Croatian yet.
            //
            // What is left when it is dropped is his own Croatian model, which is the thing that
            // learns as he writes. That is the honest state of it, and it fills.
            // The typing language decides now (MaTypingLanguage); with detection off it is the badge.
            val providerAgrees = shippedSubtype != null
            val isEmailField = keyboardManager.activeState.keyVariation ==
                dev.patrickgold.florisboard.ime.text.key.KeyVariation.EMAIL_ADDRESS
            val isPhoneField = keyboardManager.activeState.keyboardMode ==
                dev.patrickgold.florisboard.ime.keyboard.KeyboardMode.PHONE
            val addresses = if (keyboardManager.activeState.isIncognitoMode) emptyList() else runCatching {
                dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.suggest(
                    content.textBeforeSelection, isEmailField, isPhoneField,
                    isUrlField = keyboardManager.activeState.keyVariation ==
                        dev.patrickgold.florisboard.ime.text.key.KeyVariation.URI,
                ).plus(
                    // Contact names for a capitalised word ("Mar" → "Marko Boško"), opt-in (MaAddressBook).
                    dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.names(content.textBeforeSelection),
                ).plus(
                    // Streets, towns, post codes (MaPlaces, Marko 13.11.2026: "if I start to type street, then it just
                    // search in the database of all the streets"). Offline, nothing typed leaves the phone.
                    dev.patrickgold.florisboard.dictate.nlp.MaPlaces.suggest(
                        content.textBeforeSelection,
                        isAddressField = editorInstance.activeInfo.inputAttributes.variation ==
                            dev.patrickgold.florisboard.ime.editor.InputAttributes.Variation.POSTAL_ADDRESS,
                    ),
                ).distinct().map { address ->
                    WordSuggestionCandidate(
                        text = address, secondaryText = null, confidence = 1.0,
                        isEligibleForAutoCommit = false, sourceProvider = null,
                    )
                }
            }.getOrDefault(emptyList())
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    suggestedForWord = content.currentWordText
                    internalSuggestions = reqTime to buildList {
                        // Saved email addresses first, whole (MaAddressBook, Marko 7.11.2026: "TTT must nicely
                        // save all email addresses"): "mar" or "marko.b" offers marko.bosko@gmail.com, and an
                        // empty email field offers the most used ones.
                        addAll(addresses)
                        addAll(emojiSuggestions.takeIf { addresses.isEmpty() }.orEmpty())
                        // A spelling fix from the shipped engine goes before the personal model (live test
                        // 7.11.2026, build 491: after the personal model began learning typed words, "mozda"
                        // typed twice came back first and blocked "možda"). Only his own corrections (tier 4)
                        // and Haiku's words (tier 0) for the typed word keep the fix out of first place.
                        val fix = suggestions.firstOrNull()?.takeIf { providerAgrees && it.isEligibleForAutoCommit }
                        val typedNow = content.currentWordText.lowercase()
                        val fixWins = fix != null && fix.text.toString().lowercase() != typedNow && personal.none {
                            it.text.toString().lowercase() == typedNow &&
                                personalTiers[it.text.toString()]?.let { t -> t !in 1..3 } == true
                        }
                        if (fixWins) add(fix!!)
                        // The literal call below is what the gate checks for; the fix is not shown twice.
                        if (fixWins) personal.removeAll { it.text.toString().lowercase() == fix!!.text.toString().lowercase() }
                        // A personal word with no context behind it (tier 1, a plain unigram) waits behind the
                        // shipped engine's top two when that engine has answers: "see you t" offered "the" and
                        // "to" from the personal model before "too", "tomorrow" (Parakeet + typing test,
                        // 7.11.2026). Personal words that match the previous words (tiers 2-3), his corrections
                        // and a capitalised word being typed (names) keep first place.
                        val shippedRest = if (providerAgrees) suggestions.filter { it !== fix || !fixWins } else emptyList()
                        val typingName = content.currentWordText.firstOrNull()?.isUpperCase() == true
                        val loose = if (shippedRest.size >= 2 && !typingName) {
                            personal.filter { personalTiers[it.text.toString()] == dev.patrickgold.florisboard.dictate.nlp.MaNgramModel.TIER_UNIGRAM }
                        } else emptyList()
                        personal.removeAll(loose.toSet())
                        addAll(personal)
                        if (providerAgrees) {
                            val seen = personal.mapTo(HashSet()) { it.text.toString().lowercase() }
                            val shipped = shippedRest.filter { it.text.toString().lowercase() !in seen }
                            addAll(shipped.take(2))
                            shipped.take(2).forEach { seen.add(it.text.toString().lowercase()) }
                            loose.filter { seen.add(it.text.toString().lowercase()) }.forEach { add(it) }
                            addAll(shipped.drop(2).filter { it.text.toString().lowercase() !in seen })
                        } else {
                            addAll(loose)
                        }
                    // One word once: a town from MaPlaces is often also a word of the personal model ("Zagreb" twice).
                    }.distinctBy { it.text.toString() }
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>, holdNext: Boolean = false) {
        val reqTime = SystemClock.uptimeMillis()
        holdNextSuggest = holdNext
        runBlocking {
            suggestedForWord = null
            internalSuggestions = reqTime to suggestions
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to emptyList()
        }
    }

    /** The word the shown suggestions were computed for; null when they were set directly. */
    @Volatile
    private var suggestedForWord: String? = null

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        // Only a fix computed for the word now being typed (live test 7.11.2026, a slow phone: space came
        // before the suggestions for "is" were ready, and the fix for "i", "I", replaced "is").
        val forWord = suggestedForWord
        if (forWord != null && !forWord.equals(editorInstance.activeContent.currentWordText, ignoreCase = true)) return null
        // Nothing is ever replaced behind his back: the AUTO switch for correcting words must be on,
        // and a word with a digit in it (AT2020, B2B, 4K) is a name or a code, never a typo.
        if (!prefs.suggestion.autoCorrect.get()) return null
        if (editorInstance.activeContent.currentWordText.any { it.isDigit() }) return null
        return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
    }

    /** Outcome of [addToUserDictionary], so the caller knows what (if anything) to tell the user. */
    enum class AddToDictionaryResult { ADDED, ALREADY_PRESENT, UNAVAILABLE }

    /**
     * Adds [candidate]'s word to the personal dictionary for [subtype]'s language (issue #241) and re-runs
     * the suggestions so it is treated as known from the very next keystroke — which is the point of the
     * feature: [LatinLanguageProvider] consults the user dictionary in `isKnownWord`, so a learned word is
     * never autocorrected again.
     *
     * Stored at the maximum frequency, matching what the settings screen uses when a word is added by hand.
     */
    fun addToUserDictionary(subtype: Subtype, candidate: SuggestionCandidate): AddToDictionaryResult {
        val word = candidate.text.toString().trim()
        if (word.isEmpty()) return AddToDictionaryResult.UNAVAILABLE
        val dao = DictionaryManager.default().florisUserDictionaryDao()
            ?: return AddToDictionaryResult.UNAVAILABLE // the personal dictionary is switched off
        val locale = subtype.primaryLocale
        return runCatching {
            if (dao.queryExactFuzzyLocale(word, locale).isNotEmpty()) {
                AddToDictionaryResult.ALREADY_PRESENT
            } else {
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = word,
                        freq = USER_DICTIONARY_FREQ,
                        locale = locale.localeTag(),
                        shortcut = null,
                    )
                )
                scope.launch { suggest(subtypeManager.activeSubtype, editorInstance.activeContent) }
                AddToDictionaryResult.ADDED
            }
        }.getOrDefault(AddToDictionaryResult.UNAVAILABLE)
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    // Need to re-trigger the suggestions algorithm. Only word candidates reach this
                    // now: the clipboard branch went with the clipboard suggestion itself.
                    suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                }
            }
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    private fun assembleCandidates() {
        runBlocking {
            val candidates = when {
                // Word suggestions only. The clipboard used to elbow its way in here as a
                // candidate chip, and it is gone: the copy buckets are the clipboard now, they have
                // their own strip above the keyboard, and two clipboards on one screen is what
                // produced a suggestion row drawn in two layers on top of itself.
                isSuggestionOn() -> {
                    buildList {
                        internalSuggestionsGuard.withLock {
                            addAll(internalSuggestions.second)
                        }
                    }
                }
                else -> emptyList()
            }
            activeCandidates = candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    /**
     * THIS NO LONGER MOVES ANYTHING, AND THAT IS THE WHOLE POINT.
     *
     * It expanded the bar when the suggestions emptied and collapsed it when they came back. On a
     * live screen that is several times a second: a space empties them, the next letter refills
     * them, a backspace empties them again. **The bar walked up and down and took the text above it
     * with it**, and he told me three times, in the end describing it as seasickness.
     *
     * The last attempt guarded only a held delete key. That was too narrow — the switching happens on
     * ordinary typing too — and a narrow fix for a symptom he had already reported twice is worse
     * than none, because it looks addressed.
     *
     * **So the automatic switching is gone, not narrowed.** The bar opens when he opens it and stays
     * open until he long-presses it. Nothing else moves it.
     *
     * ### What is lost, honestly
     *
     * A row of actions no longer appears by itself when there is nothing to suggest. That was the
     * feature this implemented, and it is worth exactly one thing: a few pixels of screen when the
     * suggestions are empty. **A few pixels are not worth a moving keyboard**, and he can reach the
     * actions with one long press whenever he wants them.
     *
     * The function is kept rather than deleted, because it is called from three places on the
     * typing path and a signature that exists and does nothing is a smaller change than three call
     * sites removed at the end of a session — see §150a for what that costs. It is now the one place
     * to look when somebody asks why the bar never moves.
     */
    @Suppress("UNUSED_PARAMETER")
    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        // Deliberately empty. See above.
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

}
