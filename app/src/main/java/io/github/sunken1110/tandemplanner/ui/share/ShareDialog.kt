package io.github.sunken1110.tandemplanner.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.MAX_NAME_LENGTH
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.data.takeWhole
import io.github.sunken1110.tandemplanner.ui.common.MemberDot
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import io.github.sunken1110.tandemplanner.ui.common.memberLabel
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Share dialog: shows the room code (copy / send via any app), lists members,
 * lets the user rename themselves, and shows their personal recovery code
 * (or a button to create one).
 */
@Composable
fun ShareDialog(calendar: SharedCalendar, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val code = calendar.roomCode.orEmpty()
    var copied by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(calendar.myName) }
    val scope = rememberCoroutineScope()
    var recoveryCopied by remember { mutableStateOf(false) }
    var confirmRenew by remember { mutableStateOf(false) }
    var recoveryBusy by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    fun makeRecoveryCode() {
        if (recoveryBusy) return
        haptics.tap()
        recoveryBusy = true
        scope.launch {
            val ok = calendar.createRecoveryCode()
            recoveryBusy = false
            confirmRenew = false
            recoveryCopied = false
            if (ok) {
                recoveryError = null
                haptics.confirm()
            } else {
                // Shown here: the screen's error toast would be hidden behind the dialog.
                recoveryError = calendar.lastError
                calendar.lastError = null
                haptics.reject()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공동 달력") },
        text = {
            // Scrollable: with the recovery card the content can outgrow small screens.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (calendar.members.size < 2) "함께 쓸 사람에게 이 코드를 알려 주세요."
                    else "두 사람이 함께 쓰고 있어요.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))

                // ── Room code ──
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(code, fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("공동 달력 코드", code))
                        haptics.tap()
                        copied = true
                    }) { Text(if (copied) "복사됨" else "코드 복사") }
                    TextButton(onClick = {
                        haptics.tap()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "공동 달력에 초대할게요! 앱에서 '코드로 참여'를 누르고 이 코드를 입력해 주세요: $code"
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "코드 보내기"))
                    }) { Text("보내기") }
                }

                // ── Members ──
                Spacer(Modifier.height(8.dp))
                Text("함께 쓰는 사람", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                calendar.members.forEach { uid ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        MemberDot(color = calendar.colorOf(uid), size = 10.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(calendar.memberLabel(uid), fontSize = 15.sp)
                    }
                }
                if (calendar.members.size < 2) {
                    Text("아직 한 명만 있어요", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ── Rename ──
                Spacer(Modifier.height(10.dp))
                if (editingName) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.takeWhole(MAX_NAME_LENGTH) },
                        label = { Text("새 이름") },
                        singleLine = true,
                        isError = newName.isBlank(),
                        supportingText = if (newName.isBlank()) {
                            { Text("이름을 입력해 주세요") }
                        } else null
                    )
                    Row {
                        TextButton(onClick = { editingName = false; newName = calendar.myName }) { Text("취소") }
                        TextButton(onClick = {
                            if (newName.isBlank()) {
                                haptics.reject()
                            } else {
                                calendar.rename(newName.trim())
                                haptics.confirm()
                                editingName = false
                            }
                        }) { Text("저장") }
                    }
                } else {
                    TextButton(onClick = { editingName = true }) { Text("내 이름 바꾸기") }
                }

                // ── Recovery code ──
                Spacer(Modifier.height(10.dp))
                Text("내 복구 코드", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "앱을 지우거나 폰을 바꾼 뒤 이 코드로 내 자리를 되찾을 수 있어요. " +
                        "안전한 곳에 적어 두세요. 한 번 쓰면 새 코드로 바뀌어요.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                val recovery = calendar.recoveryCode
                if (calendar.hasRecoveryCode && recovery != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(recovery, fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("복구 코드", recovery))
                            haptics.tap()
                            recoveryCopied = true
                        }) { Text(if (recoveryCopied) "복사됨" else "복사") }
                        // Two taps: replacing the code makes the written-down one useless.
                        TextButton(
                            onClick = { if (confirmRenew) makeRecoveryCode() else confirmRenew = true },
                            enabled = !recoveryBusy
                        ) { Text(if (confirmRenew) "이전 코드는 못 써요. 바꾸기" else "새로 만들기") }
                    }
                } else {
                    TextButton(onClick = { makeRecoveryCode() }, enabled = !recoveryBusy) {
                        Text(if (recoveryBusy) "만드는 중…" else "복구 코드 만들기")
                    }
                }
                recoveryError?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    )
}
