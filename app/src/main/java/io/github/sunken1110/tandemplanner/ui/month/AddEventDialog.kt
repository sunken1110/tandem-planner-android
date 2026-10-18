package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Dialog for adding an event on [date]. Title is required; time is free-form and optional.
 *
 * @param onSave    called with (time, title), both trimmed.
 * @param onInvalid called when saving is attempted with an empty title.
 */
@Composable
fun AddEventDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onInvalid: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}월 ${date.dayOfMonth}일 일정") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text("일정 내용") },
                    placeholder = { Text("친구와 저녁 약속") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("일정 내용을 입력해 주세요") }
                    } else null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("시간 (선택)") },
                    placeholder = { Text("18:30") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    showError = true
                    onInvalid()
                } else {
                    onSave(time.trim(), title.trim())
                }
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
