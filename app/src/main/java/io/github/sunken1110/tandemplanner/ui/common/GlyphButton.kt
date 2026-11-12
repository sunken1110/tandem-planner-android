package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A text button showing a symbol ("‹", "›") that TalkBack reads as [description]
 * ("이전 달", "하루 뒤") instead of the symbol itself.
 */
@Composable
fun GlyphButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 20.sp
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Text(glyph, fontSize = fontSize, modifier = Modifier.clearAndSetSemantics {})
    }
}
