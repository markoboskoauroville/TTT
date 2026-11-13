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

package dev.patrickgold.florisboard.lib.compose

import android.app.Activity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import dev.patrickgold.florisboard.dictate.MaSettingsResume
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisAppBar
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.autoMirrorForRtl
import org.florisboard.lib.compose.florisVerticalScroll

@Composable
fun FlorisScreen(builder: @Composable FlorisScreenScope.() -> Unit) {
    val scope = remember { FlorisScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias FlorisScreenActions = @Composable RowScope.() -> Unit
typealias FlorisScreenBottomBar = @Composable () -> Unit
typealias FlorisScreenContent = PreferenceUiContent<FlorisPreferenceModel>
typealias FlorisScreenFab = @Composable () -> Unit
typealias FlorisScreenNavigationIcon = @Composable () -> Unit

interface FlorisScreenScope {
    var title: String

    /** Optional composable that replaces the plain [title] text in the app bar (e.g. an in-bar search field). */
    var titleContent: (@Composable () -> Unit)?

    var navigationIconVisible: Boolean


    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    fun actions(actions: FlorisScreenActions)

    fun bottomBar(bottomBar: FlorisScreenBottomBar)

    fun content(content: FlorisScreenContent)

    fun floatingActionButton(fab: FlorisScreenFab)

    fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon)
}

private class FlorisScreenScopeImpl : FlorisScreenScope {
    override var title: String by mutableStateOf("")
    override var titleContent: (@Composable () -> Unit)? by mutableStateOf(null)
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(true)

    private var actions: FlorisScreenActions = @Composable { }
    private var bottomBar: FlorisScreenBottomBar = @Composable { }
    private var content: FlorisScreenContent = @Composable { }
    private var fab: FlorisScreenFab = @Composable { }
    private var navigationIcon: FlorisScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        FlorisIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.autoMirrorForRtl(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
    }

    override fun actions(actions: FlorisScreenActions) {
        this.actions = actions
    }

    override fun bottomBar(bottomBar: FlorisScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: FlorisScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: FlorisScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon) {
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val colorScheme = MaterialTheme.colorScheme

        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = true
            } else {
                window.navigationBarColor = colorScheme.scrim.toArgb()
            }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { FlorisAppBar(title, navigationIcon.takeIf { navigationIconVisible }, actions, scrollBehavior, titleContent) },
            bottomBar = bottomBar,
            floatingActionButton = fab,
        ) { innerPadding ->
            // The other half of the gear key: how far down this screen he was.
            //
            // Owned here rather than left to florisVerticalScroll's own remembered state, because
            // the position has to be readable to save it and writable to put it back. Every settings
            // screen in the app goes through this scaffold, so hooking it once here covers all of
            // them and covers any screen added later without that screen having to know.
            //
            // Restoring is deliberately not automatic. consumeScroll hands the offset back only when
            // the gear key armed it and only when the route matches the one it was measured on, so
            // tapping down through the settings normally still opens each screen at its top, which
            // is what anybody expects. A list that never starts where you are looking is worse than
            // one that forgets.
            val maScrollState = rememberScrollState()
            val maRoutePath = MaSettingsResume.pathOf(LocalNavController.current.currentDestination)
            if (scrollable && maRoutePath != null) {
                LaunchedEffect(maRoutePath) {
                    MaSettingsResume.consumeScroll(context, maRoutePath)?.let {
                        // scrollTo, not animateScrollTo: this is where the screen opens, not a
                        // journey to watch. An animation here would also race the first layout pass
                        // and land short on a list whose full height is not known yet.
                        maScrollState.scrollTo(it)
                    }
                }
                // Written when the scroll settles, not while it moves. Deliberately not debounce():
                // that is a preview API in some versions of the coroutines library and an opt-in
                // annotation is a build failure named after a file nobody was editing. Watching the
                // in-progress flag needs nothing beyond what is already imported, and settling is
                // the moment worth recording anyway.
                LaunchedEffect(maRoutePath) {
                    snapshotFlow { maScrollState.isScrollInProgress }
                        .collect { moving ->
                            if (!moving) {
                                MaSettingsResume.rememberScroll(context, maRoutePath, maScrollState.value)
                            }
                        }
                }
            }
            val scrollModifier = if (scrollable) {
                Modifier.florisVerticalScroll(maScrollState)
            } else {
                Modifier
            }
            PreferenceLayout(
                FlorisPreferenceStore,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .then(scrollModifier),
                iconSpaceReserved = iconSpaceReserved,
                content = content,
            )
        }
    }
}
