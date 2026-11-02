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

package dev.patrickgold.florisboard.app.setup

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.dictate.MaKeyImport
import dev.patrickgold.florisboard.dictate.MaSettingsVault
import dev.patrickgold.florisboard.dictate.MaVault
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisScreenScope
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisBulletSpacer
import org.florisboard.lib.compose.FlorisStep
import org.florisboard.lib.compose.FlorisStepLayout
import org.florisboard.lib.compose.FlorisStepLayoutScope
import org.florisboard.lib.compose.FlorisStepState
import org.florisboard.lib.compose.stringRes

/** The provider recommended to new (non-technical) users: fast and free for everyday dictation. */
private const val RECOMMENDED_PROVIDER_ID = "groq"

@Composable
fun SetupScreen() = FlorisScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.collectAsState()

    // Dictate onboarding: the active transcription provider must have a usable key (or be keyless)
    // before the user can dictate. This drives the new "Connect a free AI service" step.
    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val activeProviderId by prefs.dictate.transcriptionProviderId.collectAsState()
    val isProviderConfigured = isProviderConfigured(accounts, activeProviderId)
    var providerSkipped by rememberSaveable { mutableStateOf(false) }
    // The floating-button step is optional and has no completion signal of its own, so (like the
    // provider step) a flag lets the user move past it to the final page once they've decided.
    // Talk to Type: starts passed, so setup never shows the floating-button page. The bubble
    // was removed, and a wizard step offering a feature that no longer exists is worse than no step.
    var floatingButtonStepPassed by rememberSaveable { mutableStateOf(true) }

    val requestNotification =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            scope.launch {
                if (isGranted) {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.GRANTED)
                } else {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.DENIED)
                }
            }
        }

    var isMicGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            isMicGranted = isGranted
        }

    // The two grants that happen on a system screen we cannot get a result from.
    //
    // Neither returns anything when the user comes back — there is no launcher contract for
    // all-files access or for the accessibility toggle — so the only honest way to know is to look
    // again. Polling while the wizard is open costs nothing and means the step ticks itself off the
    // moment he returns, instead of sitting there completed but looking undone.
    var hasAllFiles by remember { mutableStateOf(MaVault.hasFullAccess()) }
    var allFilesSkipped by rememberSaveable { mutableStateOf(false) }
    var hasAccessibility by remember { mutableStateOf(DictateAccessibilityService.isRunning) }
    var accessibilitySkipped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400L)
            hasAllFiles = MaVault.hasFullAccess()
            hasAccessibility = DictateAccessibilityService.isRunning
        }
    }

    content(
        isFlorisBoardEnabled,
        isFlorisBoardSelected,
        isMicGranted,
        isProviderConfigured,
        providerSkipped,
        { providerSkipped = true },
        floatingButtonStepPassed,
        { floatingButtonStepPassed = true },
        accounts,
        context,
        navController,
        requestNotification,
        requestMic,
        hasNotificationPermission,
        hasAllFiles,
        allFilesSkipped,
        { allFilesSkipped = true },
        hasAccessibility,
        accessibilitySkipped,
        { accessibilitySkipped = true },
        scope,
    )
}

/** Reads the current clipboard text (used to paste an API key without opening the on-screen keyboard). */
private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    return cm.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

/** Masks an API key for on-screen confirmation, e.g. "gsk_…AB12" (keeps the ends, hides the middle). */
private fun maskKey(key: String): String =
    if (key.length > 8) "${key.take(4)}…${key.takeLast(4)}" else "•".repeat(key.length)

/** True once the active transcription provider has a saved key, or is a keyless endpoint (Ollama). */
private fun isProviderConfigured(accounts: ProviderAccounts, providerId: String): Boolean {
    if (accounts.getOrEmpty(providerId).hasKey) return true
    val preset = ProviderRegistry.byId(providerId)
    return preset != null && preset.apiKeyUrl == null
}

@Composable
private fun FlorisScreenScope.content(
    isFlorisBoardEnabled: Boolean,
    isFlorisBoardSelected: Boolean,
    isMicGranted: Boolean,
    isProviderConfigured: Boolean,
    providerSkipped: Boolean,
    onSkipProvider: () -> Unit,
    floatingButtonStepPassed: Boolean,
    onPassFloatingButton: () -> Unit,
    accounts: ProviderAccounts,
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    requestMic: ManagedActivityResultLauncher<String, Boolean>,
    hasNotificationPermission: NotificationPermissionState,
    hasAllFiles: Boolean,
    allFilesSkipped: Boolean,
    onSkipAllFiles: () -> Unit,
    hasAccessibility: Boolean,
    accessibilitySkipped: Boolean,
    onSkipAccessibility: () -> Unit,
    scope: CoroutineScope,
) {

    fun targetStep(): Int = when {
        !isFlorisBoardEnabled -> Steps.EnableIme.id
        !isFlorisBoardSelected -> Steps.SelectIme.id
        !isMicGranted -> Steps.GrantMicPermission.id
        // Storage before keys: the backup this step unlocks is what the next step reads.
        !hasAllFiles && !allFilesSkipped -> Steps.GrantAllFiles.id
        !isProviderConfigured && !providerSkipped -> Steps.SetUpProvider.id
        // Accessibility last of the grants, because it is the one that is optional in the sense
        // that dictation works without it — everything it powers is a convenience on top.
        !hasAccessibility && !accessibilitySkipped -> Steps.GrantAccessibility.id
        // Nothing left to grant: land on the last real step, which now carries the Finish
        // button. There is no FinishUp page to fall through to any more.
        else -> Steps.GrantAccessibility.id
    }

    val stepState = rememberSaveable(saver = FlorisStepState.Saver) {
        FlorisStepState.new(init = targetStep())
    }

    content {
        /**
         * Leave setup, whichever page he is on.
         *
         * One definition, used by the Finish button and by Skip setup, so the two cannot end setup
         * differently — the flag has to be written or the wizard returns on the next launch, and
         * `popUpTo` has to be inclusive or Back walks straight into it again.
         */
        fun finishSetup() {
            scope.launch { prefs.internal.isImeSetUp.set(true) }
            navController.navigate(Routes.Settings.Home) {
                popUpTo(Routes.Setup.Screen) { inclusive = true }
            }
        }

        LaunchedEffect(
            isFlorisBoardEnabled, isFlorisBoardSelected, isMicGranted,
            hasNotificationPermission, isProviderConfigured, providerSkipped,
            floatingButtonStepPassed,
            // Without these two in the key list the wizard would not notice a grant returning from
            // the system screen, and would sit on a step he had already completed.
            hasAllFiles, allFilesSkipped, hasAccessibility, accessibilitySkipped,
        ) {
            stepState.setCurrentAuto(targetStep())
        }

        // Below block allows to return from the system IME enabler activity
        // as soon as it gets selected.
        LaunchedEffect(Unit) {
            while (true) {
                delay(200L)
                val isEnabled = InputMethodUtils.isFlorisboardEnabled(context)
                if (stepState.getCurrentAuto().value == Steps.EnableIme.id &&
                    stepState.getCurrentManual().value == -1 &&
                    !isFlorisBoardEnabled &&
                    !isFlorisBoardSelected &&
                    hasNotificationPermission == NotificationPermissionState.NOT_SET &&
                    isEnabled
                ) {
                    context.launchActivity(FlorisAppActivity::class) {
                        it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                }
            }
        }
        FlorisStepLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            stepState = stepState,
            backLabel = stringRes(R.string.setup__nav_back),
            nextLabel = stringRes(R.string.setup__nav_next),
            header = {
                StepText(stringRes(R.string.setup__intro_message))
                // Skip the whole thing, from any page.
                //
                // He reinstalls several times a day and knows every one of these pages by heart.
                // Skipping a single slide was never the problem — the problem is being asked at
                // all, six times, by a wizard whose entire content he wrote.
                //
                // In the header rather than at the bottom, so it is reachable on the page that has
                // no other button and cannot be scrolled past on the pages that do. Nothing is lost
                // by taking it: every grant it offers is also offered in Settings, and the ones
                // already granted stay granted.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { finishSetup() }) {
                        Text(
                            text = "Skip setup \u2014 go to Settings",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            },
            steps = steps(
                context, navController, requestNotification, requestMic,
                isProviderConfigured, onSkipProvider, onPassFloatingButton, accounts,
                hasAllFiles, onSkipAllFiles, hasAccessibility, onSkipAccessibility, scope,
                { finishSetup() },
            ),
            footer = {
                footer(context)
            },
        )
    }
}

@Composable
private fun footer(context: Context) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
        TextButton(onClick = { context.launchUrl(privacyPolicyUrl) }) {
            Text(text = stringRes(R.string.setup__footer__privacy_policy))
        }
        FlorisBulletSpacer()
        val repositoryUrl = stringRes(R.string.florisboard__repo_url)
        TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
            Text(text = stringRes(R.string.setup__footer__repository))
        }
    }
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.steps(
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    requestMic: ManagedActivityResultLauncher<String, Boolean>,
    isProviderConfigured: Boolean,
    onSkipProvider: () -> Unit,
    onPassFloatingButton: () -> Unit,
    accounts: ProviderAccounts,
    hasAllFiles: Boolean,
    onSkipAllFiles: () -> Unit,
    hasAccessibility: Boolean,
    onSkipAccessibility: () -> Unit,
    scope: CoroutineScope,
    /**
     * Ends setup: writes the flag and goes to Settings.
     *
     * Passed in rather than rebuilt here, because it is the same action the Skip link at the top
     * performs and there must be exactly one definition of "setup is over" — two would eventually
     * disagree about whether the flag was written, and a flag that is not written brings the whole
     * wizard back on the next launch.
     */
    finishSetup: () -> Unit,
): List<FlorisStep> {

    // Sorts one file into every provider and stores the result. Returns the sentence the step shows,
    // so the composable stays free of preference plumbing. The active transcription and rewording
    // providers are pointed at the two that do those jobs here, but only when they actually got a
    // key, so a file holding just one of them does not leave the app pointed at an empty provider.
    fun importKeys(text: String): String {
        val result = MaKeyImport.importAll(text, accounts)
        if (result.added > 0) {
            scope.launch {
                this@steps.prefs.dictate.providerAccounts.set(result.accounts)
                if (result.accounts.accounts["assemblyai"]?.apiKey.orEmpty().isNotBlank()) {
                    this@steps.prefs.dictate.transcriptionProviderId.set("assemblyai")
                }
                if (result.accounts.accounts["anthropic"]?.apiKey.orEmpty().isNotBlank()) {
                    this@steps.prefs.dictate.rewordingProviderId.set("anthropic")
                }
            }
            MaVault.write(text)
        }
        return result.summary
    }

    return listOfNotNull(
        FlorisStep(
            id = Steps.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
        ) {
            StepText(stringRes(R.string.setup__enable_ime__description))
            StepButton(label = stringRes(R.string.setup__enable_ime__open_settings_btn)) {
                InputMethodUtils.showImeEnablerActivity(context)
            }
        },
        FlorisStep(
            id = Steps.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
        ) {
            StepText(stringRes(R.string.setup__select_ime__description))
            StepButton(label = stringRes(R.string.setup__select_ime__switch_keyboard_btn)) {
                InputMethodUtils.showImePicker(context)
            }
        },
        FlorisStep(
            id = Steps.GrantMicPermission.id,
            title = stringRes(R.string.setup__grant_mic_permission__title),
        ) {
            StepText(stringRes(R.string.setup__grant_mic_permission__description))
            StepButton(stringRes(R.string.setup__grant_mic_permission__btn)) {
                requestMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        FlorisStep(
            id = Steps.GrantAllFiles.id,
            title = "Let it read your files",
        ) {
            StepText(
                "Android will not let this app open a file it does not own without this. Your key " +
                    "backup in Documents is such a file \u2014 so without this, the next step cannot " +
                    "read the backup the previous version left for you.\n\n" +
                    "Find TTT in the list and turn it on."
            )
            if (hasAllFiles) {
                StepText("Granted. You can go on.")
            } else {
                StepButton("Grant all-files access") {
                    runCatching { context.startActivity(MaVault.accessIntent(context)) }
                }
                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                    onClick = { onSkipAllFiles() },
                ) {
                    Text(
                        text = "Skip \u2014 I will type my keys in",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        FlorisStep(
            id = Steps.SetUpProvider.id,
            title = stringRes(R.string.setup__provider__title),
        ) {
            ProviderSetupStep(
                onImport = ::importKeys,
                onSkip = onSkipProvider,
                hasAllFiles = hasAllFiles,
            )
        },
        FlorisStep(
            id = Steps.GrantAccessibility.id,
            title = "Turn on the accessibility service",
        ) {
            StepText(
                "This is what lets the app press buttons on screen, jump between fields, and " +
                    "record with no keyboard open. Dictation works without it; everything built " +
                    "on top of it does not."
            )
            if (hasAccessibility) {
                StepText("Running. You can go on.")
            } else {
                // Two buttons in the order the phone wants them, which is not the obvious order.
                //
                // A sideloaded app has its accessibility switch greyed out until restricted
                // settings are allowed, and that lives behind the three-dot menu of App info —
                // somewhere nobody finds without being told. Sending him straight to the
                // accessibility list first would show him a switch he cannot move and no reason
                // why.
                StepText(
                    "1. Open App info, tap the three dots at the top right, and choose " +
                        "\"Allow restricted settings\". Skip this if your phone does not offer it."
                )
                StepButton("Open App info") {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            },
                        )
                    }
                }
                StepText("2. Then find TTT in Accessibility and switch it on.")
                StepButton("Open Accessibility settings") {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                    onClick = { onSkipAccessibility() },
                ) {
                    Text(
                        text = "Skip \u2014 set this up later",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // OUTSIDE the branch, and that is the whole point.
            //
            // It was inside the `else`, so somebody who already had accessibility running — which is
            // everybody after the first install — reached the last page of setup with no button on
            // it at all and no way forward. A dead end at the end of the wizard, which is the worst
            // place to put one.
            //
            // Setup ends here whatever the state of that switch, because the switch is optional and
            // finishing is not.
            StepButton(label = "Finish") { finishSetup() }
        },
    )
}

/**
 * The key step: one picker.
 *
 * It used to be three, one per provider, which asked the user to know which key belongs to which
 * service before importing it. The parser already knows. So this reads the file once, sorts every
 * key it finds to the right provider, and says what it filed where. The same import runs in the key
 * manager, so the two behave identically.
 */
@Composable
private fun FlorisStepLayoutScope.ProviderSetupStep(
    onImport: (String) -> String,
    onSkip: () -> Unit,
    hasAllFiles: Boolean,
) {
    val context = LocalContext.current
    // Restoring settings is a suspending read, so this step needs its own scope.
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
            }.getOrNull().orEmpty()
            note = onImport(text)
        }
    }

    StepText(
        "Point this at your keys file. Every key in it is sorted to the provider it belongs to, " +
            "so one file with all of them is the normal case. Nothing is ever pasted."
    )
    Spacer(modifier = Modifier.height(8.dp))
    // Restore first, because for him it is the answer.
    //
    // The previous version left a backup in Documents, and reading it needs no picker and no
    // remembering where the file was put — the location is known. It only appears when all-files
    // access has actually been granted and a backup is actually there, so it is never a button
    // that cannot work.
    // Settings first, because it is the one that costs him most to redo by hand.
    //
    // Offered rather than applied. It overwrites what is currently set, which is exactly right on a
    // fresh install and a real loss on a configured one — so the choice stays his, the same way the
    // key restore works.
    if (hasAllFiles && MaVault.settingsExist()) {
        StepButton(label = "Restore all settings") {
            scope.launch {
                note = if (MaSettingsVault.restore()) {
                    "Settings restored. Your rows, keys order and preferences are back."
                } else {
                    "Could not read " + MaVault.SETTINGS_DISPLAY_PATH
                }
            }
        }
    }
    if (hasAllFiles && MaVault.exists()) {
        StepButton(label = "Restore keys from backup") {
            val text = MaVault.read()
            note = if (text.isNullOrBlank()) {
                "The backup at " + MaVault.DISPLAY_PATH + " is empty."
            } else {
                onImport(text)
            }
        }
        StepText("Found a backup at " + MaVault.DISPLAY_PATH + ".")
    }
    StepButton(label = "Load keys from file") {
        picker.launch(arrayOf("*/*"))
    }
    if (note.isNotEmpty()) {
        StepText(note)
    }
    Spacer(modifier = Modifier.height(4.dp))
    // Naming the two that matter, and what stops working without each.
    //
    // The step used to name three providers and say what each one does, which reads as background
    // rather than as a requirement. Somebody finishing setup with only a transcription key has a
    // working app right up to the moment they press Ctrl+P, and then gets an error about a missing
    // key for a feature they did not know needed a second one. Better said here, once, before the
    // file is even chosen.
    StepText(
        "You want three keys in that file:\n\n" +
            "AssemblyAI — the transcribing. Without it, dictation cannot work at all.\n\n" +
            "Anthropic (Claude) — the proofreading and rewording, including Ctrl+P. Without it, " +
            "dictation still works and Ctrl+P shows an error.\n\n" +
            "Groq — fast Whisper, used to work out which language you are speaking. Optional: " +
            "without it the language stays whatever you set by hand.\n\n" +
            "All of this can be changed later under API keys."
    )

    TextButton(
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        onClick = onSkip,
    ) {
        Text(
            text = "Set up later",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Four steps, down from seven.
 *
 * The crash-report notification step is gone: nothing posts crash notifications any more, so asking
 * for the permission was asking for nothing. The floating-button step is gone too, since the
 * floating bubble itself was removed from this build and the step was still advertising it.
 */
private sealed class Steps(val id: Int) {
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object GrantMicPermission : Steps(id = 3)

    /**
     * All-files access, and it sits **before** the keys on purpose.
     *
     * The previous version leaves a key backup in Documents, and Android will not let the app read
     * a file it does not own without this. So a setup that asked for the keys first sent him to a
     * picker to find a backup the app was not allowed to open — which is exactly how it went.
     */
    data object GrantAllFiles : Steps(id = 4)
    data object SetUpProvider : Steps(id = 5)

    /**
     * Accessibility, guided, because the switch alone is not enough on this phone.
     *
     * A sideloaded app has its accessibility toggle greyed out until restricted settings are
     * allowed, which lives behind the overflow menu of App info and is findable by nobody who has
     * not been told. Two buttons, in the order the phone wants them.
     */
    data object GrantAccessibility : Steps(id = 6)
}
