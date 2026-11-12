package io.github.sunken1110.tandemplanner.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.MAX_NAME_LENGTH
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.data.takeWhole
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.dayColor
import io.github.sunken1110.tandemplanner.ui.effects.SeasonParticles
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import kotlinx.coroutines.launch

/** The four seasons' colors in a row (the accent above the title); built once. */
private val SeasonBar = Brush.horizontalGradient(Season.entries.map { it.color })

/**
 * First-run screen: enter a display name, then either create a new shared
 * calendar or join one with a 6-character code from the partner.
 *
 * At the bottom, "이전 자리 되찾기" opens a third path for someone who reinstalled
 * the app: room code + their recovery code bring back their seat (name, color
 * and the right to edit their own events). No name is needed there; the old one
 * is restored.
 */
@Composable
fun SetupScreen(calendar: SharedCalendar) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val today = currentDate()
    val season = Season.of(today.monthValue)
    val dark = isSystemInDarkTheme()

    var name by rememberSaveable { mutableStateOf(calendar.myName) }
    var code by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var showRecover by rememberSaveable { mutableStateOf(false) }
    var recoverRoom by rememberSaveable { mutableStateOf("") }
    var recoverCode by rememberSaveable { mutableStateOf("") }
    var recoverError by remember { mutableStateOf<String?>(null) }

    fun validName(): Boolean {
        nameError = name.isBlank()
        if (nameError) haptics.reject()
        return !nameError
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(dayColor(today).copy(alpha = if (dark) 0.08f else 0.12f))
    ) {
        SeasonParticles(season = season, dark = dark)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Small four-season accent bar.
            Box(
                Modifier
                    .size(width = 44.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SeasonBar)
            )
            Spacer(Modifier.height(16.dp))
            Text("공동 달력 시작하기", fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "한 사람이 달력을 만들고, 다른 사람은 코드를 입력해서 함께 써요.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.takeWhole(MAX_NAME_LENGTH); nameError = false },
                label = { Text("내 이름") },
                singleLine = true,
                isError = nameError,
                supportingText = {
                    Text(if (nameError) "이름을 입력해 주세요" else "일정에 누가 썼는지 표시돼요")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            // ── Create ──
            Button(
                onClick = {
                    if (!validName() || busy) return@Button
                    haptics.tap()
                    busy = true
                    scope.launch {
                        val ok = calendar.createRoom(name.trim())
                        busy = false
                        if (ok) haptics.confirm() else haptics.reject()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("새 공동 달력 만들기", fontSize = 16.sp) }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Text(
                    "또는 받은 코드로 참여",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.height(16.dp))

            // ── Join ──
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(SharedCalendar.CODE_LENGTH)
                    codeError = null
                },
                label = { Text("공유 코드 ${SharedCalendar.CODE_LENGTH}자리") },
                singleLine = true,
                isError = codeError != null,
                supportingText = {
                    val msg = codeError
                    if (msg != null) Text(msg) else Text("함께 쓸 사람에게 받은 코드")
                },
                textStyle = LocalTextStyle.current.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (!validName() || busy) return@OutlinedButton
                    if (code.length != SharedCalendar.CODE_LENGTH) {
                        codeError = "코드 ${SharedCalendar.CODE_LENGTH}자리를 입력해 주세요"
                        haptics.reject()
                        return@OutlinedButton
                    }
                    haptics.tap()
                    busy = true
                    scope.launch {
                        val ok = calendar.joinRoom(code, name.trim())
                        busy = false
                        if (ok) {
                            haptics.confirm()
                        } else {
                            // Show join errors under the code field instead of globally.
                            codeError = calendar.lastError
                            calendar.lastError = null
                            haptics.reject()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("참여하기", fontSize = 16.sp) }

            // ── Recover a seat (after reinstalling) ──
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { showRecover = !showRecover; recoverError = null },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text(if (showRecover) "닫기" else "앱을 다시 설치했나요? 이전 자리 되찾기", fontSize = 13.sp) }
            AnimatedVisibility(visible = showRecover) {
                Column {
                    Text(
                        "쓰던 달력의 공유 코드와, 그때 받아 둔 복구 코드를 입력하면 이름과 색, 내 일정이 그대로 돌아와요.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = recoverRoom,
                        onValueChange = {
                            recoverRoom = it.uppercase().filter { ch -> ch.isLetterOrDigit() }
                                .take(SharedCalendar.CODE_LENGTH)
                            recoverError = null
                        },
                        label = { Text("공유 코드 ${SharedCalendar.CODE_LENGTH}자리") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recoverCode,
                        // Hyphens and spaces are allowed while typing; they are ignored later.
                        onValueChange = {
                            recoverCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(20)
                            recoverError = null
                        },
                        label = { Text("복구 코드") },
                        placeholder = { Text("1-ABCD-EFGH-JKMN") },
                        singleLine = true,
                        isError = recoverError != null,
                        supportingText = {
                            val msg = recoverError
                            if (msg != null) Text(msg) else Text("공동 달력 화면의 '공유'에서 받아 둔 코드")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (busy) return@OutlinedButton
                            haptics.tap()
                            busy = true
                            scope.launch {
                                val ok = calendar.recoverSeat(recoverRoom, recoverCode)
                                busy = false
                                if (ok) {
                                    haptics.confirm()
                                } else {
                                    recoverError = calendar.lastError
                                    calendar.lastError = null
                                    haptics.reject()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("자리 되찾기", fontSize = 16.sp) }
                }
            }

            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(visible = busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("연결하는 중…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Errors not tied to the code field (e.g. failed sign-in or creation).
            calendar.lastError?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
