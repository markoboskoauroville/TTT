/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.LocalNavController
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.DisposableEffect
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons

/**
 * Every permission this keyboard needs, numbered, in the order they have to be granted.
 *
 * ### Why a screen of its own, at the top of the settings
 *
 * They were spread across a setup wizard he sees once and a scatter of error messages he sees when
 * something has already failed. Reinstalling — which he does several times a day — meant hunting
 * them one at a time, each from a different starting point.
 *
 * **Numbered, because the order is not arbitrary.** Restricted settings must be allowed before
 * accessibility can be switched on at all; the microphone is useless without the keyboard enabled.
 * A list in the order of execution is a list he can work down without thinking, which is the whole
 * point of putting it in one place.
 *
 * ### Each row says whether it is done, and opens the place that fixes it
 *
 * **A permission screen that only names permissions is a worse version of the system settings.** The
 * value is entirely in the two things beside the name: whether it is granted, and one tap to the
 * exact page that grants it.
 *
 * ### It rechecks when he comes back
 *
 * Android grants happen in another app, so this screen is always looking at a stale answer after a
 * trip out. Re-reading on RESUME means the tick appears the moment he returns, rather than the
 * screen insisting a thing is missing that he has just granted.
 */
@Composable
fun MaPermissionsScreen() = FlorisScreen {
    title = "Permissions"

    content {
        val context = LocalContext.current
        val navController = LocalNavController.current
        val lifecycleOwner = LocalLifecycleOwner.current
        // Bumped on every resume; the checks below read it so they re-run.
        var generation by remember { mutableIntStateOf(0) }
        OnResume(lifecycleOwner) { generation++ }

        // KEYS FIRST, PERMISSIONS SECOND, AND THE KEYS ARE NOT A STEP.
        //
        // The permissions are granted once, on a new phone, and then never touched again. The keys
        // are edited, tested, imported and replaced constantly. **A screen ordered by the sequence a
        // new install goes through is ordered wrongly for every day after it**, and this one put the
        // thing he uses at the bottom, behind seven numbered steps he had finished months ago.
        //
        // The keys also lost their number. `steps.size + 1` made them the eighth step of a
        // seven-step setup — a number that says "do this once, in order", which is the opposite of
        // what they are.
        MaKeysHeaderRow(onClick = { navController.navigate(Routes.Settings.DictateKeys) })

        Spacer(Modifier.height(20.dp))
        MaRestrictedSettingsHelp()

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        Text(
            text = "Granted once, on a new phone. In this order \u2014 each opens the page that grants it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val steps = remember(generation) {
            runCatching { maPermissionSteps(context) }.getOrDefault(emptyList())
        }
        steps.forEachIndexed { i, step ->
            MaPermissionRow(
                number = i + 1,
                title = step.title,
                detail = step.detail,
                granted = step.granted,
                onClick = { runCatching { context.startActivity(step.intent()) } },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Whether this keyboard is enabled in the system, asked three ways.
 *
 * Any yes is a yes. The three are independent, and each is wrapped separately so a source that
 * throws contributes nothing rather than sinking the other two.
 */
private fun maKeyboardEnabled(context: Context): Boolean {
    val viaManager = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }.getOrDefault(false)
    if (viaManager) return true

    val viaSecure = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS,
        )?.contains(context.packageName) == true
    }.getOrDefault(false)
    if (viaSecure) return true

    return runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.contains(context.packageName) == true
    }.getOrDefault(false)
}

/** One permission: what it is, whether it is granted, and where to go to grant it. */
private class MaPermissionStep(
    val title: String,
    val detail: String,
    val granted: Boolean,
    val intent: () -> Intent,
)

/**
 * The list, in the order they must be done.
 *
 * Built fresh on each check rather than held, because every `granted` here is a fact about the
 * system that this app does not own and cannot be notified about.
 */
private fun maPermissionSteps(context: Context): List<MaPermissionStep> {
    fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:" + context.packageName)
    }
    val steps = mutableListOf<MaPermissionStep>()

    steps += MaPermissionStep(
        title = "Enable the keyboard",
        detail = "Turn TTT on in the list of input methods",
        // Every `granted` below is wrapped, and every one defaults to "not granted".
        //
        // These are queries into the system, and any of them can throw on a phone that answers
        // differently — a manufacturer's ROM, a locked-down profile, a future API. **One screen that
        // cannot open is worse than one row that says the wrong thing**, and the safe default is the
        // one that leaves the step visible with the button that fixes it.
        // THREE ANSWERS, AND ANY ONE OF THEM IS ENOUGH.
        //
        // This said "not enabled" on a phone where the keyboard was enabled, switched on, and being
        // typed with — photographed on 21.8.2026 with the system list showing TTT on.
        //
        // It asked one question: does Settings.Secure.ENABLED_INPUT_METHODS contain our package
        // name. That string is a system setting, and on a modern Android it is not reliably readable
        // by an ordinary app — it comes back null or short, and a null contains nothing, so the
        // check answers "no" for a keyboard that is on. **A single reading that can fail silently is
        // not a detection, it is a guess with one source.**
        //
        // So it asks three different things and takes any yes:
        //
        //   1  InputMethodManager.enabledInputMethodList — the public API for exactly this
        //      question, answered by the input method service itself rather than by a settings
        //      string we are reading over its shoulder.
        //   2  the Secure string, kept as a fallback for a ROM where the manager answers oddly.
        //   3  DEFAULT_INPUT_METHOD — the keyboard currently in use. If it is ours it is enabled;
        //      nothing can be the default without being enabled.
        //
        // Each is wrapped on its own, so one throwing does not take the other two with it. That was
        // the second half of the bug: all three would have sat inside one runCatching and the first
        // failure would have discarded the answers of the other two.
        granted = maKeyboardEnabled(context),
        intent = { Intent(Settings.ACTION_INPUT_METHOD_SETTINGS) },
    )

    steps += MaPermissionStep(
        title = "Microphone",
        detail = "Without it nothing can be recorded",
        granted = runCatching {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false),
        // The app-details page rather than a request dialog: an input method cannot show a runtime
        // permission prompt, and a page that is always reachable beats a dialog that sometimes is.
        intent = ::appDetails,
    )

    steps += MaPermissionStep(
        title = "Allow restricted settings",
        // "SKIP IF YOUR PHONE DOES NOT OFFER IT" was wrong, and wrong in the way that costs hours.
        //
        // The three dots are not a property of the phone. Since Android 13 a sideloaded app has
        // `ACCESS_RESTRICTED_SETTINGS` set to errored, and the overflow item appears only once the
        // system has SEEN the app blocked at that gate. It is a response to an attempt, not a
        // feature of the model — which is why it turns up "suddenly, after hours": something tripped
        // the gate in the background.
        //
        // So the instruction is now the one that works: be refused first, deliberately, and the menu
        // arrives. The two commands underneath are the other two ways out.
        detail = "Three dots in App info. Not there? Try to switch the accessibility service on, " +
            "let it refuse you, then come back — that is what makes the menu appear",
        // Android exposes no flag for this one, so it is answered by its CONSEQUENCE instead.
        //
        // Restricted settings is the gate that stops a sideloaded app's accessibility service from
        // being switched on at all. So an accessibility service that is RUNNING is proof the gate was
        // opened — not an assumption about it, a thing that could not have happened otherwise.
        //
        // Still false when accessibility is off, which is the honest answer: at that point the gate
        // may or may not be open, and the step he has to do next is the same either way.
        //
        // Before this it was hardcoded false and the row never ticked, ever. That is worse than it
        // sounds on a screen whose whole purpose is to say what is left to do: a step that is always
        // outstanding teaches him to ignore the numbers, and then the ones that mean something are
        // ignored too.
        granted = runCatching {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                DictateAccessibilityService.isRunning
        }.getOrDefault(false),
        intent = ::appDetails,
    )

    steps += MaPermissionStep(
        title = "Accessibility service",
        detail = "The magic finger, the reader and the recording bar all need it",
        granted = runCatching { DictateAccessibilityService.isRunning }.getOrDefault(false),
        intent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        steps += MaPermissionStep(
            title = "All files access",
            detail = "Only for importing keys and audio from storage",
            granted = runCatching { Environment.isExternalStorageManager() }.getOrDefault(false),
            intent = { Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) },
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        steps += MaPermissionStep(
            title = "Notifications",
            detail = "So a long transcription can say when it is done",
            granted = runCatching {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false),
            intent = ::appDetails,
        )
    }

    return steps
}

@Composable
private fun MaPermissionRow(
    number: Int,
    title: String,
    detail: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The number, because the order is the instruction.
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (granted) MaGrantedBg else MaPendingBg,
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (granted) "\u2713" else "$number",
                color = if (granted) MaGranted else MaPending,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Runs [onResume] every time the screen comes back to the front.
 *
 * A permission is granted in another app, so this screen holds a stale answer the moment he leaves
 * it. Without this, a permission he has just granted keeps reading as missing until the screen is
 * closed and reopened — which looks like the grant not having worked.
 *
 * `DisposableEffect`, not `LaunchedEffect`: an observer added and never removed outlives the screen
 * and fires against a composition that is gone. The first version did exactly that.
 */
@Composable
private fun OnResume(owner: LifecycleOwner, onResume: () -> Unit) {
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private val MaGranted = Color(0xFF56D364)
private val MaGrantedBg = Color(0x2256D364)
private val MaPending = Color(0xFFE8A64B)
private val MaPendingBg = Color(0x22E8A64B)

/**
 * The keys, at the top, without a step number.
 *
 * Not a `MaPermissionRow`: that one carries a number and a tick, and neither is true here. **A key is
 * not a step and it is never simply "done"** — it can be present and dead, present for one provider
 * and missing for another, or working this morning and out of credit this afternoon. A tick claiming
 * otherwise is the kind of false reassurance that costs an afternoon.
 */
@Composable
private fun MaKeysHeaderRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "API keys", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Import, test and manage every key",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * THE TWO WAYS PAST RESTRICTED SETTINGS THAT DO NOT NEED THE MENU.
 *
 * **The app cannot lift this restriction itself, and that is the point of it.** The gate exists so a
 * sideloaded app cannot grant itself accessibility, and anything here that claimed to "force a
 * refresh" would be an app asking the system to stop restricting it. So this screen does the only
 * honest thing: it hands him the commands and gets out of the way.
 *
 * Both need Shizuku or a computer. Neither is a workaround for a bug — they are the mechanisms the
 * three-dot menu drives, reached directly.
 *
 * The commands are COPYABLE rather than typed. He dictates rather than types, and a package name
 * with two dots in it is exactly the sort of string that is mistyped once and then debugged for
 * twenty minutes.
 */
@Composable
private fun MaRestrictedSettingsHelp() {
    val clipboard = LocalClipboardManager.current
    Text(
        text = "If the three dots are missing",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
    Text(
        text = "The menu appears only after Android has refused this app once. Fastest: try to " +
            "switch the accessibility service on, let it refuse, come back.\n\n" +
            "These two do it without the menu. Both need Shizuku running, or a computer.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    for ((what, command) in listOf(
        "Lift the restriction on this install" to
            "appops set com.mantraproductions.tttlight ACCESS_RESTRICTED_SETTINGS allow",
        "Install so it never applies" to
            "pm install -i com.android.vending -r ttt.apk",
    )) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = what,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(command)) }) { Text("COPY") }
        }
    }
    Text(
        // Said plainly, because the second one changes what every FUTURE build inherits and that is
        // worth knowing before he runs it rather than after.
        text = "The second names the Play Store as the installer, so the restriction never applies " +
            "— to that install and every update over it. It does not make the app a Play app and " +
            "grants nothing else.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
