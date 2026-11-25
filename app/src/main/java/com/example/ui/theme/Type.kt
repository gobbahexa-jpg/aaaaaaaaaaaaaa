package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

val NotoSansSinhalaFontFamily = FontFamily(
    Font(R.font.noto_sans_sinhala, FontWeight.Normal),
    Font(R.font.noto_sans_sinhala, FontWeight.Medium),
    Font(R.font.noto_sans_sinhala, FontWeight.SemiBold),
    Font(R.font.noto_sans_sinhala, FontWeight.Bold)
)

val NotoSansTamilFontFamily = FontFamily(
    Font(R.font.noto_sans_tamil, FontWeight.Normal),
    Font(R.font.noto_sans_tamil, FontWeight.Medium),
    Font(R.font.noto_sans_tamil, FontWeight.SemiBold),
    Font(R.font.noto_sans_tamil, FontWeight.Bold)
)

@Immutable
data class DlMessagingTypography(
    val messageBody: TextStyle,
    val messageSender: TextStyle,
    val messageTimestamp: TextStyle,
    val chatTitle: TextStyle,
    val chatPreview: TextStyle,
    val unreadCount: TextStyle,
    val sectionOverline: TextStyle,
    val codeBlock: TextStyle
)

fun createDlTypography(scale: Float = 1f, language: String = "en"): Typography {
    val s = scale.coerceIn(0.85f, 1.35f)
    val isIndic = language.lowercase() in setOf("si", "ta")
    val headingFont = when (language.lowercase()) {
        "si" -> NotoSansSinhalaFontFamily
        "ta" -> NotoSansTamilFontFamily
        else -> SpaceGroteskFontFamily
    }
    val bodyFont = when (language.lowercase()) {
        "si" -> NotoSansSinhalaFontFamily
        "ta" -> NotoSansTamilFontFamily
        else -> PlusJakartaSansFontFamily
    }
    val labelSmallFont = when (language.lowercase()) {
        "si" -> NotoSansSinhalaFontFamily
        "ta" -> NotoSansTamilFontFamily
        else -> JetBrainsMonoFontFamily
    }
    val lineBoost = if (isIndic) 1.12f else 1f
    val letterAdjust = if (isIndic) 0.sp else (-0.3).sp
    return Typography(
        displaySmall = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.Bold,
            fontSize = (28 * s).sp,
            lineHeight = (34 * s * lineBoost).sp,
            letterSpacing = if (isIndic) 0.sp else (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * s).sp,
            lineHeight = (28 * s * lineBoost).sp,
            letterSpacing = letterAdjust
        ),
        titleLarge = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (19 * s).sp,
            lineHeight = (25 * s * lineBoost).sp,
            letterSpacing = if (isIndic) 0.sp else (-0.2).sp
        ),
        titleMedium = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16 * s).sp,
            lineHeight = (22 * s * lineBoost).sp
        ),
        titleSmall = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14 * s).sp,
            lineHeight = (20 * s * lineBoost).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * s).sp,
            lineHeight = (22 * s * lineBoost).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * s).sp,
            lineHeight = (20 * s * lineBoost).sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * s).sp,
            lineHeight = (17 * s * lineBoost).sp
        ),
        labelLarge = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14 * s).sp,
            lineHeight = (18 * s * lineBoost).sp
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * s).sp,
            lineHeight = (16 * s * lineBoost).sp
        ),
        labelSmall = TextStyle(
            fontFamily = labelSmallFont,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * s).sp,
            lineHeight = (14 * s * lineBoost).sp
        )
    )
}

fun createDlMessagingTypography(scale: Float = 1f, language: String = "en"): DlMessagingTypography {
    val s = scale.coerceIn(0.85f, 1.35f)
    val isIndic = language.lowercase() in setOf("si", "ta")
    val headingFont = when (language.lowercase()) {
        "si" -> NotoSansSinhalaFontFamily
        "ta" -> NotoSansTamilFontFamily
        else -> SpaceGroteskFontFamily
    }
    val bodyFont = when (language.lowercase()) {
        "si" -> NotoSansSinhalaFontFamily
        "ta" -> NotoSansTamilFontFamily
        else -> PlusJakartaSansFontFamily
    }
    val monoOrIndic = when (language.lowercase()) {
        "si" -> NotoSansSinhalaFontFamily
        "ta" -> NotoSansTamilFontFamily
        else -> JetBrainsMonoFontFamily
    }
    val lineBoost = if (isIndic) 1.12f else 1f
    return DlMessagingTypography(
        messageBody = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * s).sp,
            lineHeight = (21 * s * lineBoost).sp
        ),
        messageSender = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (12 * s).sp,
            lineHeight = (16 * s * lineBoost).sp
        ),
        messageTimestamp = TextStyle(
            fontFamily = monoOrIndic,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * s).sp,
            lineHeight = (14 * s * lineBoost).sp,
            fontFeatureSettings = if (isIndic) null else "tnum"
        ),
        chatTitle = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16 * s).sp,
            lineHeight = (22 * s * lineBoost).sp
        ),
        chatPreview = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * s).sp,
            lineHeight = (18 * s * lineBoost).sp
        ),
        unreadCount = TextStyle(
            fontFamily = monoOrIndic,
            fontWeight = FontWeight.Bold,
            fontSize = (11 * s).sp,
            lineHeight = (14 * s * lineBoost).sp,
            fontFeatureSettings = if (isIndic) null else "tnum"
        ),
        sectionOverline = TextStyle(
            fontFamily = headingFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (12 * s).sp,
            lineHeight = (16 * s * lineBoost).sp
        ),
        codeBlock = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * s).sp,
            lineHeight = (17 * s).sp
        )
    )
}

val Typography = createDlTypography(1f)
val DefaultMessagingTypography = createDlMessagingTypography(1f)
