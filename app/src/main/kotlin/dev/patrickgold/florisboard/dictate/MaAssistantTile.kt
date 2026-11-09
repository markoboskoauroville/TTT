/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile "RE" (MaAssistant): a press starts RE listening, a second press stops it and runs the
 * command over the app in front. The same entry as the accessibility button of the TTT service.
 */
class MaAssistantTile : TileService() {

    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "RE"; updateTile() }
    }

    override fun onClick() {
        MaAssistant.toggle(this)
    }
}
