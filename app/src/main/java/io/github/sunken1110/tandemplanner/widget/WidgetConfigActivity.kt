package io.github.sunken1110.tandemplanner.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.sunken1110.tandemplanner.data.upcoming
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.DdayStep
import io.github.sunken1110.tandemplanner.ui.common.ddayBadgeColors
import io.github.sunken1110.tandemplanner.ui.common.ddayStepOf
import io.github.sunken1110.tandemplanner.ui.common.ddayText
import io.github.sunken1110.tandemplanner.ui.theme.TandemPlannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Widget settings: opens when a widget is placed, and again from the widget's
 * long-press "설정" (Android 12+). Chooses the background — the season color
 * (default), see-through, or the user's own photo — and, for the latter two,
 * how strong it is with a slider. A preview shows the result before saving.
 *
 * Android's contract for this screen: answer RESULT_OK with the widget's id to
 * keep the widget; anything else (back, cancel) means "don't add it" on first
 * placement, or "no change" when reopened later.
 */
/** Preview shape when the widget's size isn't known yet (about a 2×1 widget). */
private const val DEFAULT_ASPECT = 2.2f

/** Longest wait for the widget to show new settings before closing anyway. */
private val APPLY_WAIT = 4.seconds

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Until saved, leaving this screen cancels.
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val initial = WidgetStyleStore.load(this, appWidgetId)
        setContent {
            TandemPlannerTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        appWidgetId = appWidgetId,
                        initial = initial,
                        onSaved = {
                            setResult(RESULT_OK, resultIntent())
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun SettingsScreen(
    appWidgetId: Int,
    initial: WidgetStyle,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var background by remember { mutableStateOf(initial.background) }
    var level by remember { mutableIntStateOf(initial.level) }
    // A newly picked photo (not saved yet), and the preview image.
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember {
        mutableStateOf(
            if (initial.background == WidgetBackground.PHOTO) WidgetStyleStore.loadPhoto(context, appWidgetId) else null
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Which part of the photo shows (see PhotoCrop), and the widget's real shape.
    var focusX by remember { mutableFloatStateOf(initial.focusX) }
    var focusY by remember { mutableFloatStateOf(initial.focusY) }
    var zoom by remember { mutableFloatStateOf(initial.zoom) }
    val aspect = remember {
        WidgetStyleStore.widgetAspect(AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId))
            ?: DEFAULT_ASPECT
    }
    /** Moves/zooms the photo, keeping the cut-out inside it. */
    fun moveTo(fx: Float, fy: Float, z: Float) {
        val photo = preview ?: return
        val zClamped = z.coerceIn(1f, WidgetStyle.MAX_ZOOM)
        val (cx, cy) = PhotoCrop.clampedFocus(photo.width, photo.height, aspect, fx, fy, zClamped)
        focusX = cx
        focusY = cy
        zoom = zClamped
    }

    // Android's photo picker: no storage permission needed, only the chosen photo is shared.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Canceled: keep whatever was chosen before.
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { WidgetStyleStore.decodeScaled(context, uri) }
            if (bitmap == null) {
                error = "사진을 불러오지 못했어요. 다른 사진을 골라 주세요."
            } else {
                pickedUri = uri
                preview = bitmap
                background = WidgetBackground.PHOTO
                error = null
                // A new photo starts centered.
                focusX = 0.5f
                focusY = 0.5f
                zoom = 1f
            }
        }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            var stamp = initial.photoStamp
            val uri = pickedUri
            if (background == WidgetBackground.PHOTO && uri != null) {
                stamp = withContext(Dispatchers.IO) { WidgetStyleStore.importPhoto(context, appWidgetId, uri) } ?: run {
                    error = "사진을 저장하지 못했어요."
                    saving = false
                    return@launch
                }
            }
            WidgetStyleStore.save(context, appWidgetId, WidgetStyle(background, level, stamp, focusX, focusY, zoom))
            // Redraw this widget with the new look, and stay open until it has been
            // drawn. Glance draws widgets in a background job; once this screen
            // closes, the app is in the background and Android may hold that job back
            // for a while, which made new settings show up late.
            runCatching {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                val requested = WidgetUpdater.refresh(context, glanceId)
                withTimeoutOrNull(APPLY_WAIT) {
                    while (WidgetStyleStore.renderedStamp(context, appWidgetId) < requested) delay(100.milliseconds)
                }
            }
            onSaved()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("위젯 배경", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))

        val photoMode = background == WidgetBackground.PHOTO && preview != null
        WidgetPreview(
            background = background,
            level = level,
            photo = preview,
            aspect = aspect,
            focusX = focusX,
            focusY = focusY,
            zoom = zoom,
            // Dragging moves the photo with the finger; pinching zooms.
            onTransform = if (!photoMode) null else { pan, zoomChange, frameWidth ->
                val photo = preview
                if (photo != null) {
                    val crop = PhotoCrop.rect(photo.width, photo.height, aspect, focusX, focusY, zoom)
                    // One screen pixel in the frame = this many photo pixels.
                    val perPx = crop[2] / frameWidth
                    moveTo(
                        focusX - pan.x * perPx / photo.width,
                        focusY - pan.y * perPx / photo.height,
                        zoom * zoomChange
                    )
                }
            }
        )
        Text(
            when {
                photoMode -> "틀 안을 끌어서 옮기고, 두 손가락으로 확대해요. 보이는 그대로 위젯에 나와요."
                background == WidgetBackground.CLEAR -> "미리보기 · 실제로는 내 배경화면 위에 보여요"
                else -> "미리보기"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(20.dp))

        // ── Background choice ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val season = Season.of(LocalDate.now().monthValue)
            Option(
                label = "계절 색",
                selected = background == WidgetBackground.SEASON,
                swatch = lerp(Color.White, season.color, 0.3f),
                modifier = Modifier.weight(1f),
                onClick = { background = WidgetBackground.SEASON }
            )
            Option(
                label = "투명",
                selected = background == WidgetBackground.CLEAR,
                swatch = Color.Gray.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f),
                onClick = { background = WidgetBackground.CLEAR }
            )
            Option(
                label = "내 사진",
                selected = background == WidgetBackground.PHOTO,
                swatch = Color(0xFF1D9E75),
                modifier = Modifier.weight(1f),
                onClick = {
                    // No photo yet: open the picker right away.
                    if (preview == null) pickPhoto() else background = WidgetBackground.PHOTO
                }
            )
        }

        if (photoMode) {
            // ── Zoom ──
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "확대",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text("%.1f배".format(zoom), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Slider(
                value = zoom,
                onValueChange = { moveTo(focusX, focusY, it) },
                valueRange = 1f..WidgetStyle.MAX_ZOOM
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { moveTo(0.5f, 0.5f, 1f) }) { Text("위치 초기화") }
                OutlinedButton(onClick = { pickPhoto() }) { Text("사진 다시 고르기") }
            }
        }

        // ── Strength (see-through and photo only) ──
        if (background != WidgetBackground.SEASON) {
            Spacer(Modifier.height(20.dp))
            val photo = background == WidgetBackground.PHOTO
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (photo) "어둡게 하기" else "바탕 진하기",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text("$level%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Slider(
                value = level.toFloat(),
                onValueChange = { level = (it / 5f).roundToInt() * 5 },
                valueRange = 0f..WidgetStyle.MAX_LEVEL.toFloat(),
                // 5 % steps: 0, 5, …, 80.
                steps = WidgetStyle.MAX_LEVEL / 5 - 1
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (photo) "사진 선명" else "배경화면 그대로",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (photo) "글자 선명" else "바탕 진하게",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            when (background) {
                WidgetBackground.SEASON -> "계절이 바뀌면 바탕색도 함께 바뀌어요. 1×1은 D-day 단계 색 타일로 보여요."
                WidgetBackground.CLEAR -> "바탕이 옅으면 글자가 흰색으로 바뀌어 배경화면 위에서도 읽혀요."
                WidgetBackground.PHOTO -> "사진은 이 폰에만 저장되고 상대에게 공유되지 않아요. 1×1은 사진 위에 D-day 알약으로 보여요."
            },
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("취소") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { save() }, enabled = !saving) { Text(if (saving) "적용 중…" else "저장") }
        }
    }
}

/** One background choice: a small swatch and its name, outlined when selected. */
@Composable
private fun Option(label: String, selected: Boolean, swatch: Color, modifier: Modifier, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(swatch)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp)
    }
}

/**
 * The widget as it will look, in its real shape ([aspect] = width / height):
 * the chosen background, then the next real event (or a sample) on top — the
 * card for wide widgets, the D-day pill for square ones. A see-through
 * background is shown over a stand-in wallpaper color.
 *
 * For a photo, the frame shows exactly the part of the photo the widget will
 * (the same [PhotoCrop] math), and [onTransform] receives drags and pinches.
 */
@Composable
private fun WidgetPreview(
    background: WidgetBackground,
    level: Int,
    photo: Bitmap?,
    aspect: Float,
    focusX: Float,
    focusY: Float,
    zoom: Float,
    onTransform: ((pan: Offset, zoomChange: Float, frameWidth: Float) -> Unit)?
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val today = LocalDate.now()
    val season = Season.of(today.monthValue)
    val item = remember {
        val items = WidgetStore.load(context).items
        val byId = items.associateBy { it.event.id }
        items.map { it.event }.upcoming(today, LocalTime.now()).firstNotNullOfOrNull { byId[it.id] }
    }
    val style = WidgetStyle(background, level)
    val text = when {
        style.lightText -> Color.White
        dark -> Color(0xFFF3EFEA)
        else -> Color(0xFF2B2622)
    }
    val alpha = level / 100f
    val square = aspect < 1.5f
    val image = remember(photo) { photo?.asImageBitmap() }
    val latestTransform by rememberUpdatedState(onTransform)

    // Stand-in wallpaper behind the widget.
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3C3489))
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        var frameWidth by remember { mutableFloatStateOf(1f) }
        Box(
            Modifier
                // Square widgets are previewed smaller, wide ones fill the row.
                .then(if (square) Modifier.width(150.dp) else Modifier.fillMaxWidth(0.88f))
                .aspectRatio(aspect.coerceIn(0.5f, 4f))
                .clip(RoundedCornerShape(20.dp))
                .onSizeChanged { frameWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        latestTransform?.invoke(pan, zoomChange, frameWidth)
                    }
                }
        ) {
            when (background) {
                WidgetBackground.SEASON -> Box(
                    Modifier.fillMaxSize().background(
                        if (dark) lerp(Color(0xFF1C1B1F), season.color, 0.14f) else lerp(Color.White, season.color, 0.16f)
                    )
                )
                WidgetBackground.CLEAR -> Box(
                    Modifier.fillMaxSize().background((if (dark) Color.Black else Color.White).copy(alpha = alpha))
                )
                WidgetBackground.PHOTO -> {
                    if (image != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = PhotoCrop.rect(image.width, image.height, aspect, focusX, focusY, zoom)
                            drawImage(
                                image = image,
                                srcOffset = IntOffset(r[0].toInt(), r[1].toInt()),
                                srcSize = IntSize(
                                    r[2].toInt().coerceIn(1, image.width - r[0].toInt()),
                                    r[3].toInt().coerceIn(1, image.height - r[1].toInt())
                                ),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
                }
            }

            val author = item?.color ?: Color(0xFFE64980)
            val (badgeBg, badgeFg) = if (item != null) {
                ddayBadgeColors(item.color, ddayStepOf(item.event, today), dark)
            } else ddayBadgeColors(author, DdayStep.TOMORROW, dark)
            val badge = item?.let { ddayText(it.event, today) } ?: "D-1"
            val title = item?.event?.title ?: "하와이 여행"

            if (square) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Pill(badge, badgeBg, badgeFg)
                    Spacer(Modifier.height(5.dp))
                    Text(title, fontSize = 11.sp, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(44.dp).background(author))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("다음 일정", fontSize = 11.sp, color = text.copy(alpha = 0.8f))
                        Text(
                            title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(item?.author ?: "하윤", fontSize = 11.sp, color = text.copy(alpha = 0.8f), maxLines = 1)
                    }
                    Pill(badge, badgeBg, badgeFg)
                }
            }
        }
    }
}

/** The D-day pill of the preview. */
@Composable
private fun Pill(text: String, background: Color, content: Color) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 11.dp, vertical = 3.dp)
    )
}
