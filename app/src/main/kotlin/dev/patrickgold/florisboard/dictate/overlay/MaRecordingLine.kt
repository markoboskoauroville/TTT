/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

import android.widget.FrameLayout
import android.graphics.Paint
import android.graphics.Canvas
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaLog
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * One hairline across the top of the screen, while a recording is running with no keyboard in sight.
 *
 * ### Why it exists
 *
 * The volume keys record whether or not the keyboard is on screen, and that turned out to be the
 * feature — he can start dictating without opening anything. But a recording nobody can see is a
 * recording he does not know he started, and the first he learns of it is a transcript arriving from
 * a conversation he had with somebody else in the room.
 *
 * **Anything that captures a microphone must be visible while it does.** Not as a courtesy — as the
 * minimum honesty of a device that listens.
 *
 * ### Why a line and not a bubble
 *
 * There is already a floating button, and it is a control: it can be dragged, pressed, and it takes
 * a corner of the screen. This is not a control. It carries one bit of information — *this is
 * recording* — and the smallest shape that carries one bit is a line.
 *
 * Three device-pixels tall, at the very bottom, across the full width. Not touchable, not focusable,
 * and it never covers anything: at that height it sits in the gesture bar's own margin.
 *
 * ### When
 *
 * Every recording, keyboard up or not (Marko, 23.11.2026, asked for it back: hidden while the keyboard was up, he never
 * saw it). It needs TTT's accessibility service on, because an accessibility overlay is what may draw over every app.
 */
class MaRecordingLine(private val service: AccessibilityService) {

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null
    private var added = false

    /**
     * Shows or hides the line. Safe to call repeatedly with the same value.
     *
     * Idempotent on purpose: it is driven from a state flow that emits on every change to the
     * recorder, and adding a window that is already added throws.
     */
    fun show(visible: Boolean) {
        if (visible == added) return
        if (visible) add() else remove()
    }

    // timer, ticker and handler are gone with the clock. Nothing on this strip changes on a
    // schedule any more — the meter redraws from the audio level itself.

    private fun add() {
        // ONE HAIRLINE, FULL WIDTH, AND NOTHING ELSE.
        //
        // It was a black notch carrying a meter, a red dot, a clock, a bin, a send arrow and the
        // language badge — six things, in a strip stolen from the status bar, in the middle of the
        // screen. He looked at it and asked for a single meter, one pixel tall, edge to edge.
        //
        // He is right, and the reason is the one this file already argues in its own header: **this
        // is not a control, it carries one bit — the microphone is open — and the smallest shape
        // that carries one bit is a line.** Every control that had crept onto it had a better home
        // already: the bin and the send are volume down and volume up, the clock and the language
        // are on the keyboard, and none of them were reachable without looking anyway.
        //
        // Making it full width now costs nothing, because at this height there is no click-through
        // problem left to solve: a one-pixel window intercepts nothing anybody was trying to press.
        // The notch existed to keep the corners usable, and a hairline keeps the whole screen usable.
        val meter = MaVuView(service, vertical = false)

        // MATCH_PARENT wide, and as close to one pixel tall as the screen allows.
        //
        // Not `px(1)`: on his phone that is three physical pixels, which is a stripe rather than a
        // hairline. One device pixel is what he asked for and what the meter needs — a line is
        // legible at any height as long as it is moving.
        //
        // FLAG_NOT_TOUCHABLE as well as NOT_FOCUSABLE, which the old notch could not have: with no
        // controls on it there is nothing to press, so every touch goes to the app underneath and
        // the strip costs him nothing at all.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            // The very top edge, also on phones with a camera hole (23.11.2026): without this Android puts the window below
            // the cutout band, where the status bar's own icons hide a one-pixel line.
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        runCatching {
            windowManager.addView(meter, params)
            view = meter
            added = true
        }
    }

    /** One glyph as a button, sized for a thumb rather than for its own ink. */
    // glyph() built the bin and the send buttons, and both are volume keys.


    /**
     * The clock, once a second.
     *
     * Read from the recorder's own state rather than counted here, so a pause is honoured and the
     * two views can never disagree about how long he has been speaking.
     */
    // startTicking is gone with the clock it drove. The meter animates itself from the audio level;
    // nothing here needs waking once a second any more, which is a second of work per second that
    // the phone gets back for the length of every recording.

    private fun remove() {
        val v = view ?: return
        // Wrapped, because a window can already be gone if the service was torn down under it, and
        // an exception here would take the accessibility service with it — which would cost him the
        // finger, the reader and the line all at once.
        runCatching { windowManager.removeView(v) }
        view = null
        added = false
    }
}

/**
 * The VU meter, on the overlay, reading the same numbers as the one on the keyboard.
 *
 * ### It is the module, not a lookalike
 *
 * The level comes from `DictateController.audioLevel` — the same StateFlow the keyboard's meter
 * collects. The dB conversion, the floor, the peak decay and the three colours are lifted from
 * `MaRecordMeter` unchanged, so the two cannot disagree about how loud he is: **one source of
 * numbers, drawn twice, rather than two meters that happen to look similar.**
 *
 * It is drawn with a Canvas rather than composed because this window has no lifecycle owner for
 * Compose to attach to. That is a difference in the brush, not in the picture.
 *
 * ### Peak hold
 *
 * A bar alone tells you the current instant, which at speech rates is a flicker. The peak mark falls
 * at 0.6 dB per frame — slow enough to read, fast enough to follow a sentence — and it is what makes
 * the thing a meter rather than a light.
 */
private class MaVuView(context: Context, private val vertical: Boolean = false) : View(context) {

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2E.toInt() }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2DDB4.toInt() }

    private var smoothed = FLOOR_DB
    private var peakDb = FLOOR_DB

    private val tick = object : Runnable {
        override fun run() {
            val db = toDb(DictateController.audioLevel.value)
            // Fast to rise, slow to fall: an attack that lags makes the meter feel dead, a release
            // that snaps makes it feel nervous. The same asymmetry every hardware meter has.
            smoothed = if (db > smoothed) db else smoothed + (db - smoothed) * 0.3f
            peakDb = if (db > peakDb) db else (peakDb - 0.6f).coerceAtLeast(FLOOR_DB)
            invalidate()
            postDelayed(this, 40L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = if (vertical) w / 2 else h / 2
        canvas.drawRoundRect(0f, 0f, w, h, r, r, track)
        val n = norm(smoothed)
        if (n > 0f) {
            bar.color = colourFor(smoothed)
            if (vertical) {
                // Upright, it fills from the BOTTOM. Level rising is level going up — the one
                // direction a meter is allowed to grow, and the same one every mixing desk uses.
                canvas.drawRoundRect(0f, h - h * n, w, h, r, r, bar)
            } else {
                canvas.drawRoundRect(0f, 0f, w * n, h, r, r, bar)
            }
        }
        val p = norm(peakDb)
        if (p > 0f) {
            if (vertical) {
                val y = (h - h * p).coerceIn(2f, h - 2f)
                canvas.drawRect(0f, y - 1.5f, w, y + 1.5f, peakPaint)
            } else {
                val x = (w * p).coerceIn(2f, w - 2f)
                canvas.drawRect(x - 1.5f, 0f, x + 1.5f, h, peakPaint)
            }
        }
    }

    private companion object {
        const val FLOOR_DB = -54f

        fun toDb(level: Float): Float {
            val v = kotlin.math.abs(level)
            if (v <= 0.0005f) return FLOOR_DB
            return (20.0 * kotlin.math.log10(v.toDouble())).toFloat().coerceIn(FLOOR_DB, 0f)
        }

        fun norm(db: Float): Float = ((db - FLOOR_DB) / (0f - FLOOR_DB)).coerceIn(0f, 1f)

        // The same three, from MaRecordMeter: green while there is headroom, amber approaching, and
        // the app's recording red at the top.
        fun colourFor(db: Float): Int = when {
            db > -3f -> 0xFF9B3B33.toInt()
            db > -12f -> 0xFFF0883E.toInt()
            else -> 0xFF56D364.toInt()
        }
    }
}

/** The sand this app writes in. */
private const val MA_INK = 0xFFF2DDB4.toInt()

/** The amber of the meter's headroom, reused on send so the two agree. */
private const val MA_AMBER = 0xFFF0883E.toInt()

/** Recording red, the same one the keyboard's dot uses. */
private const val MA_REC_RED = 0xFFEF4444.toInt()
