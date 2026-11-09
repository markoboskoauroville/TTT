/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * The RE Magic icon (Marko, 9.11.2026: "it must have its icon inside the settings, which looks the
 * same as the button icon"): the key draws the bold word "re" with the small AutoAwesome sparkle
 * above its right side (MaFeatureRow, MaFeatureKey.REPROMPT), so this is the same two things: the
 * letters r and e as strokes, and Material's own AutoAwesome paths scaled into the top right corner.
 */
val MaReIcon: ImageVector by lazy {
    val white = SolidColor(Color.White)
    ImageVector.Builder(
        name = "MaRe", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        // r
        addPath(
            pathData = PathParser().parsePathString("M3.5,20.5 V12.5 M3.5,15.5 Q4.5,12.2 8,12.6").toNodes(),
            stroke = white, strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
        // e
        addPath(
            pathData = PathParser().parsePathString(
                "M10.8,16.8 H18 Q18,12.6 14.4,12.6 Q10.8,12.6 10.8,16.6 Q10.8,20.8 14.4,20.8 Q16.6,20.8 17.6,19.2",
            ).toNodes(),
            stroke = white, strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
        // the sparkle, Material's own, small and high on the right
        group(scaleX = 0.5f, scaleY = 0.5f, translationX = 12.5f, translationY = 0f) {
            for (node in Icons.Default.AutoAwesome.root) {
                if (node is VectorPath) addPath(pathData = node.pathData, fill = white)
            }
        }
    }.build()
}
