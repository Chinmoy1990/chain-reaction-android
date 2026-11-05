package com.chainreaction.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.mutableStateOf

/** Game sound: one soft "pop" (res/raw/pop.wav) for placing orbs and for each explosion wave. */
object Sounds {
    private const val PREFS = "settings"
    private const val KEY_MUTED = "muted"

    private var pool: SoundPool? = null
    private var popId = 0
    private val random = java.util.Random()
    private var prefs: android.content.SharedPreferences? = null

    private val mutedState = mutableStateOf(false)
    val muted: Boolean get() = mutedState.value

    fun toggleMute() {
        mutedState.value = !mutedState.value
        prefs?.edit()?.putBoolean(KEY_MUTED, mutedState.value)?.apply()
    }

    fun init(context: Context) {
        if (pool != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mutedState.value = prefs!!.getBoolean(KEY_MUTED, false)
        pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build().also {
                popId = it.load(context, R.raw.pop, 1)
            }
    }

    fun tap() {
        if (!muted) pool?.play(popId, 0.55f, 0.55f, 1, 0, 1f)
    }

    /** [wave] = index of the explosion wave within the current chain. */
    fun burst(wave: Int) {
        if (muted) return
        // tiny pitch wobble so a long chain doesn't sound like a machine gun
        val rate = 0.96f + random.nextFloat() * 0.08f
        pool?.play(popId, 0.9f, 0.9f, 1, 0, rate)
    }

    fun release() {
        pool?.release()
        pool = null
    }
}
