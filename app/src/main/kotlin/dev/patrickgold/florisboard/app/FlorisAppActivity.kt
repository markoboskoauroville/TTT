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

package dev.patrickgold.florisboard.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.DisposableEffect
import dev.patrickgold.florisboard.dictate.MaSettingsResume
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.app.ext.ExtensionImportScreenType
import dev.patrickgold.florisboard.app.setup.NotificationPermissionState
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.LocalPreviewFieldController
import dev.patrickgold.florisboard.lib.compose.rememberPreviewFieldController
import dev.patrickgold.florisboard.lib.util.AppVersionUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import java.util.concurrent.atomic.AtomicBoolean
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.hideAppIcon
import org.florisboard.lib.android.showAppIcon
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.collectIn

enum class AppTheme(val id: String) {
    AUTO("auto"),
    AUTO_AMOLED("auto_amoled"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED_DARK("amoled_dark");
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

class FlorisAppActivity : ComponentActivity() {
    private val prefs by FlorisPreferenceStore
    private val appContext by appContext()
    private val cacheManager by cacheManager()
    private var appTheme by mutableStateOf(AppTheme.AUTO)
    private var showAppIcon = true
    private var resourcesContext by mutableStateOf(this as Context)
    private var intentToBeHandled by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen should be installed before calling super.onCreate()
        installSplashScreen().apply {
            setKeepOnScreenCondition { !appContext.preferenceStoreLoaded.value }
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs.other.settingsTheme.asFlow().collectIn(lifecycleScope) {
            appTheme = it
        }
        prefs.other.settingsLanguage.asFlow().collectIn(lifecycleScope) {
            val config = Configuration(resources.configuration)
            val locale = if (it == "auto") FlorisLocale.default() else FlorisLocale.fromTag(it)
            config.setLocale(locale.base)
            resourcesContext = createConfigurationContext(config)
        }
        if (AndroidVersion.ATMOST_API28_P) {
            prefs.other.showAppIcon.asFlow().collectIn(lifecycleScope) {
                showAppIcon = it
            }
        }

        // We defer the setContent call until the datastore model is loaded, until then the splash screen stays drawn
        val isModelLoaded = AtomicBoolean(false)
        appContext.preferenceStoreLoaded.collectIn(lifecycleScope) { loaded ->
            if (!loaded || isModelLoaded.getAndSet(true)) return@collectIn
            // Check if android 13+ is running and the NotificationPermission is not set
            // THIS is why the wizard came back on every update.
            //
            // Inherited from upstream: if the notification permission has never been answered, it
            // set `isImeSetUp = false` and the whole setup reappeared. On a phone where that
            // permission is simply never granted — his — the condition is true forever, so every
            // single launch after every single install walked back through six pages he had
            // already finished, several times a day.
            //
            // Setup reappearing is not a way to ask for a permission. Once somebody has finished
            // it, **finished is a fact and not a state to be revoked** by an unrelated toggle.
            // Anything that still needs asking can ask where it is needed.
            //
            // Left as it was for a first run, which is the only time it was ever right: there
            // `isImeSetUp` is already false and this line changes nothing.
            if (AndroidVersion.ATLEAST_API33_T &&
                prefs.internal.notificationPermissionState.get() == NotificationPermissionState.NOT_SET &&
                !prefs.internal.isImeSetUp.get()
            ) {
                prefs.internal.notificationPermissionState.set(NotificationPermissionState.NOT_SET)
            }
            AppVersionUtils.updateVersionOnInstallAndLastUse(this, prefs)
            setContent {
                ProvideLocalizedResources(
                    resourcesContext,
                    // Settings/Setup/About/Home prose uses the full product name ("Dictate
                    // Keyboard"); the launcher label and on-keyboard UI keep the short app_name.
                    appName = R.string.app_name_full,
                ) {
                    FlorisAppTheme(theme = appTheme) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            AppContent()
                        }
                    }
                }
            }
            onNewIntent(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        // Settings left: what changed goes to his other phones (MaSettingsSync, off by default).
        dev.patrickgold.florisboard.dictate.MaSettingsSync.syncSoon()

        // App icon visibility control was restricted in Android 10.
        // See https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,%20android.os.UserHandle)
        if (AndroidVersion.ATMOST_API28_P) {
            if (showAppIcon) {
                this.showAppIcon()
            } else {
                this.hideAppIcon()
            }
        }
    }

    /** Bumped when the launcher shortcut or the Quick Settings tile asks for the scratch box (MaScratchBox). */
    private var scratchTick by androidx.compose.runtime.mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MaScratchBox.EXTRA, false)) {
            scratchTick++
            intent.removeExtra(MaScratchBox.EXTRA)
        }

        if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.clipData != null) {
            intentToBeHandled = intent
            return
        }
        intentToBeHandled = null
    }

    @Composable
    private fun AppContent() {
        val navController = rememberNavController()
        val previewFieldController = rememberPreviewFieldController()

        // Where he is standing in settings, recorded as he moves, so the gear key on the feature row
        // can put him back here rather than at the top of the settings home.
        //
        // The listener rather than a call in each screen: there are more than thirty of these and a
        // screen added later that forgot to report itself would be a hole nobody notices until the
        // gear key sends them somewhere stale. The graph already knows where it is.
        val resumeContext = LocalContext.current

        // Come back where he left, which is the half that was missing.
        //
        // The route has been recorded on every navigation since the gear key was built, and nothing
        // ever read it on launch — so he would set something three screens down, go back to typing,
        // reopen the settings, and land on the top of the tree again. Recording without restoring is
        // the bookkeeping without the benefit.
        //
        // Once per process, not on every recomposition: it navigates, and a navigation that repeats
        // would fight the back button. Home stays underneath, so back still leads out rather than
        // trapping him on the screen he was resumed to.
        var resumed by rememberSaveable { mutableStateOf(false) }
        // READ THE BOOKMARK BEFORE ANYTHING CAN WRITE IT (Marko, 13.11.2026: "if I am in some deep settings and I go to
        // my typing and I click settings again, it doesn't bring me to main menu. It brings me exactly to that settings.")
        // The destination listener below is called at once with the start destination (the settings home) and wrote
        // "settings/home" over the bookmark before this effect read it, so a fresh start always stayed on the home.
        val bookmark = remember { MaSettingsResume.lastPath(resumeContext) }
        LaunchedEffect(Unit) {
            if (!resumed) {
                resumed = true
                // A deep link wins over the bookmark, always. Something asked for a particular
                // screen — the wand's gear asks for the wand's list — and restoring wherever the
                // settings were last left would answer a different question and look like the link
                // is broken. The bookmark is for opening the settings with nothing in mind.
                val askedFor = (resumeContext as? android.app.Activity)?.intent
                    ?.takeIf { it.action == android.content.Intent.ACTION_VIEW }
                    ?.data?.toString()
                    ?.startsWith("ui://florisboard/") == true
                val last = bookmark
                if (!askedFor && last != MaSettingsResume.DEFAULT_PATH) {
                    runCatching {
                        navController.navigate(
                            android.net.Uri.parse("ui://florisboard/" + last),
                        )
                    }
                }
            }
        }

        DisposableEffect(navController) {
            val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                val path = MaSettingsResume.pathOf(destination)
                android.util.Log.i("TTT-SETTINGS", "at ${destination.route} -> $path (resumed=$resumed)")
                // The first home of a fresh start is not where he went: it is where the window begins, and the resume
                // is about to move him on. Every later destination, home included, is recorded.
                if (path != null && !(path == MaSettingsResume.DEFAULT_PATH && !resumed)) {
                    MaSettingsResume.rememberRoute(resumeContext, path)
                }
            }
            navController.addOnDestinationChangedListener(listener)
            onDispose { navController.removeOnDestinationChangedListener(listener) }
        }

        val isImeSetUp by prefs.internal.isImeSetUp.collectAsState()

        // The NavHost start destination is captured once: recomputing it when isImeSetUp later flips
        // would rebuild the graph and reset the back stack (which is why finishing setup used to drop
        // any onward navigation). Completing setup navigates explicitly instead.
        val startDestination = remember {
            if (isImeSetUp) Routes.Settings.Home::class else Routes.Setup.Screen::class
        }

        // Marko: a set-up install opens on About.
        //
        // Once the keyboard works, the app gets opened for one reason: to read the build number and
        // see whether the update actually landed. Home leads with "try out your setup", which is the
        // one thing there is no need to do at that moment, and the build number was two taps further
        // on. Navigating rather than changing the start destination keeps Home underneath, so the
        // The app used to navigate to About on every launch. That is gone: opening the settings
        // and being shown a credits page instead of the settings is a bug however it was justified,
        // and the version line at the top of Home now carries everything that page was opened for.

        // After the onboarding's optional floating-button step finishes setup, jump on to the
        // floating-button settings so the user lands exactly where the step pointed them. One-shot.
        val openFloatingButtonAfterSetup by prefs.internal.openFloatingButtonAfterSetup.collectAsState()
        LaunchedEffect(isImeSetUp, openFloatingButtonAfterSetup) {
            if (isImeSetUp && openFloatingButtonAfterSetup) {
                prefs.internal.openFloatingButtonAfterSetup.set(false)
                navController.navigate(Routes.Settings.DictateFloatingButton)
            }
        }

        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalPreviewFieldController provides previewFieldController,
        ) {
            ProvideDefaultDialogPrefStrings(
                confirmLabel = stringRes(R.string.action__ok),
                dismissLabel = stringRes(R.string.action__cancel),
                neutralLabel = stringRes(R.string.action__default),
            ) {
                Column(
                    modifier = Modifier
                        //.statusBarsPadding()
                        .navigationBarsPadding()
                        .conditional(LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            displayCutoutPadding()
                        }
                        .imePadding(),
                ) {
                    if (isImeSetUp) MaScratchBoxField(scratchTick)
                    Routes.AppNavHost(
                        modifier = Modifier.weight(1.0f),
                        navController = navController,
                        startDestination = startDestination,
                    )
                }
                // Show the "What's new" surface once after an update (only when setup is complete, so
                // it never competes with the onboarding flow). For the 5.0 milestone this is the
                // full-screen tour; for other updates it stays the compact changelog dialog. The tour
                // and dialog are mutually exclusive per update so they never both appear.
                if (isImeSetUp) {
                    val whatsNewContext = LocalContext.current
                    // Every "What's new" tour the user hasn't seen yet (e.g. 4.x → 5.1 gets both 5.0 and
                    // 5.1); empty means fall back to the compact changelog dialog for this update.
                    val autoQueue = remember {
                        AppVersionUtils.pendingTourVersions(
                            whatsNewContext, prefs, WHATS_NEW_TOURS.map { it.version },
                        )
                    }
                    if (autoQueue.isEmpty()) {
                        ChangelogDialog()
                    }
                    // Always composed so Settings › About can re-open any tour; only auto-shows when queued.
                    WhatsNewTour(autoQueue = autoQueue)
                }
            }
        }

        LaunchedEffect(intentToBeHandled) {
            val intent = intentToBeHandled
            if (intent != null) {
                if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
                    navController.handleDeepLink(intent)
                } else {
                    val data = if (intent.action == Intent.ACTION_VIEW) {
                        intent.data!!
                    } else {
                        intent.clipData!!.getItemAt(0).uri
                    }
                    val workspace = runCatching { cacheManager.readFromUriIntoCache(data) }.getOrNull()
                    navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, workspace?.uuid))
                }
            }
            intentToBeHandled = null
        }
    }
}
