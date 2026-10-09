package com.qmusic.data.local

import android.content.Context

/** 隐私政策同意状态：首次启动弹窗，同意后不再打扰 */
object PrivacyPref {

    private const val NAME = "qmusic_privacy"
    private const val KEY_ACCEPTED = "accepted"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

    fun setAccepted(context: Context, accepted: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPTED, accepted)
            .apply()
    }
}