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

package dev.patrickgold.florisboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.data.prefs.DictateLegacyMigrator
import dev.patrickgold.florisboard.dictate.MaLog
import dev.patrickgold.florisboard.dictate.nlp.MaDictationCorrections
import dev.patrickgold.florisboard.dictate.nlp.MaNgram
import dev.patrickgold.florisboard.ime.clipboard.ClipboardManager
import dev.patrickgold.florisboard.ime.core.SubtypeManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.media.emoji.FlorisEmojiCompat
import dev.patrickgold.florisboard.ime.nlp.NlpManager
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingManager
import dev.patrickgold.florisboard.ime.theme.ThemeManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.crashutility.CrashUtility
import dev.patrickgold.florisboard.lib.devtools.Flog
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var FlorisApplicationReference = WeakReference<FlorisApplication?>(null)

@Suppress("unused")
class FlorisApplication : Application() {
    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    override fun onCreate() {
        super.onCreate()
        // The model on the phone runs in its own process (MaLlmService, ":llm", §191). Nothing of the
        // keyboard starts there: two processes on one preference store would corrupt it, and init
        // empties the cache directory.
        if (isLlmProcess()) return
        FlorisApplicationReference = WeakReference(this)
        dev.patrickgold.florisboard.dictate.llm.MaLocalLlm.attach(this)
        // The key ring is read before every call that needs an API key, from places that were never
        // handed a Context. Bound here, once, with the application context.
        dev.patrickgold.florisboard.dictate.MaKeyRingStore.bind(this)
        // The Claude cost log (MaClaudeCost): every answered request is priced and written down.
        dev.patrickgold.florisboard.dictate.MaClaudeCost.attach(this)
        // Models already in Download/models/: used at once at the exact size, their checksum checked
        // once in the background (MaSharedModels). Every start, so also the first after an update.
        dev.patrickgold.florisboard.dictate.llm.MaSharedModels.verifyInBackground(this)
        // Models still in the private folder move to Documents/TTTmini/models/, where adb and the Mantra
        // file transfer can copy them to another phone (Marko, 8.11.2026). Background, once per start.
        val appForMove = this
        Thread({ runCatching { dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider.migrateToShared(appForMove) } },
            "ma-model-move").apply { priority = Thread.MIN_PRIORITY }.start()
        dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient.claudeUsageListener =
            { model, input, output -> dev.patrickgold.florisboard.dictate.MaClaudeCost.record(model, input, output) }
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    private fun isLlmProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            runCatching { java.io.File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n') }.getOrDefault("")
        }
        return name.endsWith(":llm")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Under memory pressure, free the on-device STT model from RAM (it can be ~100 MB up to ~700 MB).
        // Safe mid-transcription (deferred until it finishes); the next on-device dictation rebuilds it.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider.unloadCachedModel()
        }
    }

    fun init() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = FlorisPreferenceStore.initAndroid(
                context = this@FlorisApplication,
                datastoreName = FlorisPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            DictateLegacyMigrator.migrateIfNeeded(this@FlorisApplication)
            DictateLegacyMigrator.seedDeviceLanguageIfNeeded()
            DictateLegacyMigrator.migrateProviderKeyringIfNeeded()
            // No prompt runs on an unnamed "Default" model any more (§191).
            dev.patrickgold.florisboard.dictate.MaPromptModel.nameBlankModels()
            DictateLegacyMigrator.removeLivePromptActionIfNeeded(this@FlorisApplication)
            DictateLegacyMigrator.migratePromptsActionIfNeeded(this@FlorisApplication)
            DictateLegacyMigrator.migratePromptsLayoutToRowIfNeeded()
            DictateLegacyMigrator.applySunriseThemeIfNeeded()
            DictateLegacyMigrator.applyRowV2IfNeeded(this@FlorisApplication)
            DictateLegacyMigrator.applyPanelV14IfNeeded()
            DictateLegacyMigrator.applyRemovalV15IfNeeded()
            DictateLegacyMigrator.applyLanguagesV16IfNeeded(this@FlorisApplication)
            DictateLegacyMigrator.applyDashboardV18IfNeeded()
            DictateLegacyMigrator.applyAutoOffV1IfNeeded()
            DictateLegacyMigrator.applyAutoOffV2IfNeeded()
            DictateLegacyMigrator.applyActionRowV19IfNeeded()
            DictateLegacyMigrator.applyActionRowV20IfNeeded()
            DictateLegacyMigrator.movePredictionsBesidePromptsIfNeeded()
            DictateLegacyMigrator.keepPredictionRowOn()
            DictateLegacyMigrator.addTranslateKeyIfNeeded()
            DictateLegacyMigrator.addRepromptKeyIfNeeded()
            DictateLegacyMigrator.addWhatsAppKeyIfNeeded()
            DictateLegacyMigrator.addEngineKeysIfNeeded()
            MaLog.initialize(this@FlorisApplication)
            MaNgram.initialize(this@FlorisApplication)
            dev.patrickgold.florisboard.dictate.MaProviders.appContext = this@FlorisApplication
            dev.patrickgold.florisboard.dictate.nlp.MaAddressBook.initialize(this@FlorisApplication)
            MaDictationCorrections.initialize(this@FlorisApplication)
            // After initialize, which loads whatever the model already knows, so the backfill adds
            // to it rather than racing the load and being overwritten by it.
            MaNgram.backfillFromHistory(this@FlorisApplication)
            DictateLegacyMigrator.restoreKeysFromVaultIfEmpty(this@FlorisApplication)
            DictateLegacyMigrator.reofferRateAndDonateIfNeeded()
            preferenceStoreLoaded.value = true
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.florisApplication(): FlorisApplication {
    return when (this) {
        is FlorisApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.florisApplication()
            else -> FlorisApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as FlorisApplication } ?: FlorisApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager
