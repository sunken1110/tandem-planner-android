package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event

/**
 * One event in the day list: a season-colored side bar, an author chip,
 * optional time, title and a delete button.
 */
@Composable
fun EventRow(event: Event, accent: Color, authorName: String, authorColor: Color, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.4f), accent)))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    authorName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = authorColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(authorColor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
                if (event.time.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(event.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(event.title, fontSize = 15.sp)
        }
        TextButton(onClick = onDelete) { Text("삭제") }
    }
}
