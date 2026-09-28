package com.henrisusanto.vitmate.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class LocalizedActivityContext(
    baseActivity: Activity,
    private val configContext: Context
) : ContextWrapper(baseActivity) {
    override fun getResources(): Resources = configContext.resources
    override fun getAssets(): AssetManager = configContext.assets
}
