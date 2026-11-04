package io.github.sunken1110.tandemplanner.ui.checklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.model.Season

/** The green of a finished list (progress bar, "모두 완료", the event chip). */
val CompleteGreen = Color(0xFF2FB86B)

/** Same size as the quick-add bar's height, so the two line up as one row. */
private val BUTTON_SIZE = 48.dp

/**
 * Round button left of the quick-add bar that opens the shared checklists.
 * A badge shows how many items are still unchecked over all lists.
 */
@Composable
fun ChecklistButton(remaining: Int, season: Season, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(BUTTON_SIZE)
                .semantics {
                    contentDescription = if (remaining > 0) "체크리스트, 남은 항목 ${remaining}개" else "체크리스트"
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                ChecklistIcon(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
        }
        AnimatedVisibility(
            visible = remaining > 0,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-2).dp)
        ) {
            Text(
                if (remaining > 99) "99+" else remaining.toString(),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 18.dp)
                    .clip(CircleShape)
                    // A deep tone of the season, so white text reads even on winter's silver.
                    .background(lerp(season.colors[2], Color.Black, 0.35f))
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            )
        }
    }
}

/** A small note with one checked and one open line, drawn with strokes. */
@Composable
fun ChecklistIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.085f
        val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // The note.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.06f),
            size = Size(w * 0.8f, h * 0.88f),
            cornerRadius = CornerRadius(w * 0.18f),
            style = line
        )
        // First line: checked.
        val check = Path().apply {
            moveTo(w * 0.27f, h * 0.36f)
            lineTo(w * 0.35f, h * 0.44f)
            lineTo(w * 0.48f, h * 0.28f)
        }
        drawPath(check, color, style = line)
        drawLine(color, Offset(w * 0.58f, h * 0.37f), Offset(w * 0.73f, h * 0.37f), stroke, StrokeCap.Round)
        // Second line: still open.
        drawCircle(color, radius = stroke * 0.85f, center = Offset(w * 0.37f, h * 0.67f), style = Stroke(stroke * 0.8f))
        drawLine(color, Offset(w * 0.58f, h * 0.67f), Offset(w * 0.73f, h * 0.67f), stroke, StrokeCap.Round)
    }
}
