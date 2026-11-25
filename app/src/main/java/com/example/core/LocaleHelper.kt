package com.example.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.fragment.app.FragmentActivity
import java.util.Locale

data class AppLanguageOption(
    val code: String,
    val nativeName: String
)

object LocaleHelper {
    val supportedLanguages: List<AppLanguageOption> = listOf(
        AppLanguageOption(code = "en", nativeName = "English"),
        AppLanguageOption(code = "si", nativeName = "සිංහල"),
        AppLanguageOption(code = "ta", nativeName = "தமிழ்")
    )

    fun normalizeCode(code: String?): String = when (code?.lowercase()?.trim()) {
        "si" -> "si"
        "ta" -> "ta"
        else -> "en"
    }

    fun localizedContext(base: Context, languageCode: String): Context {
        val code = normalizeCode(languageCode)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val configCtx = base.createConfigurationContext(config)
        return object : ContextWrapper(base) {
            override fun getResources() = configCtx.resources
            override fun getAssets() = configCtx.assets
        }
    }

    fun getString(context: Context?, languageCode: String, resId: Int, vararg formatArgs: Any): String {
        val base = context ?: return ""
        val localized = localizedContext(base, languageCode)
        return if (formatArgs.isEmpty()) {
            localized.getString(resId)
        } else {
            localized.getString(resId, *formatArgs)
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
