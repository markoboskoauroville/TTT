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

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.MaScratchBox

/**
 * The Quick Settings tile "TTT RE" (Marko, 13.11.2026: "if I'm not in any application ... I will use RE to remotely
 * control WhatsApp"): a tap opens TTT on its scratch box with the keyboard up, so RE Magic and the WhatsApp tab are
 * reachable from anywhere, with no other app open.
 */
class MaScratchTile : TileService() {

    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "TTT RE"; updateTile() }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, FlorisAppActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MaScratchBox.EXTRA, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            startActivityAndCollapse(i)
        }
    }
}
