package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DlSticker
import com.example.data.model.DlStickerPack
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens

private val richTokenRegex = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\|\|[^|]+\|\||https?://[^\s<>()]+)""")

fun containsRichFormatting(text: String): Boolean = richTokenRegex.containsMatchIn(text)

@Suppress("DEPRECATION")
@Composable
fun RichMessageText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    baseColor: Color = LocalDlTokens.current.textPrimary
) {
    val tokens = LocalDlTokens.current
    val uriHandler = LocalUriHandler.current
    val revealedSpoilers = remember(text) { mutableStateMapOf<Int, Boolean>() }
    val codeBg = tokens.surfaceCard

    val annotated = remember(text, revealedSpoilers.toMap(), accentColor, baseColor, codeBg) {
        buildAnnotatedString {
            var cursor = 0
            var spoilerIndex = 0
            richTokenRegex.findAll(text).forEach { match ->
                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }
                val token = match.value
                when {
                    token.startsWith("**") && token.endsWith("**") && token.length > 4 -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(token.substring(2, token.length - 2))
                        }
                    }
                    token.startsWith("*") && token.endsWith("*") && token.length > 2 -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(token.substring(1, token.length - 1))
                        }
                    }
                    token.startsWith("`") && token.endsWith("`") && token.length > 2 -> {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = codeBg,
                                color = accentColor
                            )
                        ) {
                            append(" ${token.substring(1, token.length - 1)} ")
                        }
                    }
                    token.startsWith("||") && token.endsWith("||") && token.length > 4 -> {
                        val idx = spoilerIndex++
                        val revealed = revealedSpoilers[idx] == true
                        val inner = token.substring(2, token.length - 2)
                        pushStringAnnotation(tag = "SPOILER", annotation = idx.toString())
                        if (revealed) {
                            withStyle(SpanStyle(background = accentColor.copy(alpha = 0.22f))) {
                                append(" $inner ")
                            }
                        } else {
                            val mask = "█".repeat(inner.length.coerceIn(4, 18))
                            withStyle(
                                SpanStyle(
                                    background = tokens.textTertiary.copy(alpha = 0.35f),
                                    color = Color.Transparent
                                )
                            ) {
                                append(mask)
                            }
                        }
                        pop()
                    }
                    token.startsWith("http://") || token.startsWith("https://") -> {
                        pushStringAnnotation(tag = "URL", annotation = token)
                        withStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(token)
                        }
                        pop()
                    }
                    else -> append(token)
                }
                cursor = match.range.last + 1
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    ClickableText(
        text = annotated,
        style = style.copy(color = baseColor),
        modifier = modifier,
        onClick = { offset ->
            val spoilerAnn = annotated.getStringAnnotations(tag = "SPOILER", start = offset, end = offset).firstOrNull()
            if (spoilerAnn != null) {
                val idx = spoilerAnn.item.toIntOrNull() ?: return@ClickableText
                revealedSpoilers[idx] = !(revealedSpoilers[idx] ?: false)
                return@ClickableText
            }
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { urlAnn ->
                try {
                    uriHandler.openUri(urlAnn.item)
                } catch (_: Exception) {
                }
            }
        }
    )
}

@Suppress("DEPRECATION")
@Composable
fun DlCodeBlock(
    rawText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(rawText) { mutableStateOf(false) }

    val lines = rawText.lines()
    val headerLang = if (lines.firstOrNull()?.startsWith("[") == true && lines.first().endsWith("]")) {
        lines.first().removePrefix("[").removeSuffix("]").uppercase()
    } else {
        "CODE"
    }
    val body = if (headerLang != "CODE" && lines.size > 1) lines.drop(1).joinToString("\n") else rawText

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DlRadius.sm),
        color = tokens.codeSurface,
        border = BorderStroke(DlSizing.divider, tokens.codeBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.codeHeaderSurface)
                    .padding(horizontal = 10.dp, vertical = DlSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = headerLang,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(body))
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = DlSpacing.xxs)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.conv_code_copy_cd),
                        tint = if (copied) tokens.statusOnline else tokens.codeMutedInk,
                        modifier = Modifier.size(DlSizing.iconXs)
                    )
                    Spacer(Modifier.width(DlSpacing.xs))
                    Text(
                        text = if (copied) stringResource(R.string.conv_code_copied) else stringResource(R.string.conv_code_copy),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) tokens.statusOnline else tokens.codeMutedInk
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = body,
                    style = tokens.messagingType.codeBlock,
                    color = tokens.codeSyntax
                )
            }
        }
    }
}

@Composable
fun StickerPickerRow(
    onSelectSticker: (DlSticker) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DlSpacing.md, vertical = DlSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DlSpacing.sm)
    ) {
        DlStickerPack.builtInStickers.forEach { sticker ->
            Surface(
                shape = RoundedCornerShape(DlRadius.pillInput),
                color = tokens.surfaceElevated,
                border = BorderStroke(DlSizing.divider, tokens.hairline),
                modifier = Modifier
                    .clip(RoundedCornerShape(DlRadius.pillInput))
                    .clickable { onSelectSticker(sticker) }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = DlSpacing.md, vertical = DlSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(sticker.glyph, fontSize = 24.sp)
                    Text(
                        sticker.localizedLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }
        }
    }
}
