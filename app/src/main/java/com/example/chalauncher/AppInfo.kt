package com.example.chalauncher

import android.graphics.drawable.Drawable
import android.content.Intent

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable,
    val launchIntent: Intent?,
    var clickCount: Int = 1
)
