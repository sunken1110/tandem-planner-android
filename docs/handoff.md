# Tandem 인수인계 문서

작성 시점: 2026-11-06, 버전 3.1 (versionCode 8) + 리뷰 패치(`review_v3_1.zip`) 기준.

> **이 문서의 성격**: 코드가 **이렇게 동작하도록 의도했다**는 설명이다. 리뷰 패치는 실제 빌드로 검증되지 않았다. 코드와 이 문서가 다르면 어느 쪽이 맞는지 판단하고 보고한다. 문서를 정답으로 가정하지 않는다.
>
> 기능 전체 설명은 저장소의 `README.md`에 있다. 이 문서는 README에 없는 **결정의 이유와 함정**을 중심으로 정리한다.

---

## 1. 구조

```
tandemplanner/
├─ MainActivity.kt        스플래시 → 인트로 → 설정 화면 or 달력. 위젯에서 열기 처리
├─ AppState.kt            AndroidViewModel. SharedCalendar와 SharedChecklists를 회전 후에도 유지
├─ AppConfig.kt           조절값: 애니메이션 시간, 길이 제한(MAX_EVENT_TITLE=200, 이름 12자), 디버그 날짜, USE_GPU_GLOW
├─ ai/                    EventParser(인터페이스, AI 교체 지점) / RuleBasedEventParser / TimeRecall
├─ model/                 Season(계절과 색), Holidays(연도별 공휴일)
├─ data/                  Event, EventDraft, SharedCalendar(Firebase), Checklist(SharedChecklists)
├─ ui/calendar/           CalendarScreen: 전체 조립
├─ ui/year/               YearGrid, YearHeader(지남/남음 토글과 오도미터)
├─ ui/month/              MonthDetail, MonthBands(여러 날 띠), EventRow(체크리스트 칩), SelectionRing
├─ ui/editor/             EventEditorSheet, TimeWheel
├─ ui/checklist/          ChecklistButton, ChecklistSheet(목록·일정 연결 달력), ChecklistDetail(항목), ChecklistEffects
├─ ui/common/             DayCell, MonthGrid, EventMarks, CalendarLabels(koreanDayLabel), TimeFormat, WheelPicker, GlyphButton, Members, YearPaint
├─ ui/effects/            Haptics, IntroOverlay, ReduceMotion, SeasonParticles
├─ ui/quickadd/, setup/, share/, theme/
└─ widget/                TandemWidget(Glance), WidgetStore, WidgetStyle(배경·사진), WidgetUpdater, WidgetRefreshWorker, WidgetConfigActivity
```

## 2. Firestore 데이터 모델

필드는 **추가만** 할 수 있다. 이름이나 타입을 바꾸면 구버전 앱과 데이터를 함께 쓸 수 없다.

| 경로 | 주요 필드 |
|---|---|
| `rooms/{code}` | `members`(uid 목록, 최대 2명, 순서가 색을 정함), `names`(uid→이름), `recovery`(uid→SHA-256 32바이트), `formerUids`(옛 uid→새 uid), `createdAt` |
| `rooms/{code}/claims/{uid}` | `seat`, `secret`: 자리 복구 증명용 |
| `rooms/{code}/events/{id}` | `date`(ISO 문자열), `time`("HH:mm" 또는 ""), `title`(200자 이하), `endDate`(여러 날 일정만, 없으면 필드 삭제), `authorUid`, `authorName`, `editedByUid`, `editedByName`, `original`(작성자 원본 스냅샷 맵), `createdAt`, `updatedAt` |
| `rooms/{code}/lists/{id}` | `title`, `eventId`(연결 해제 시 필드 삭제), `createdBy`, `createdAt`, `updatedAt` |
| `rooms/{code}/lists/{id}/items/{id}` | `text`, `done`, `doneBy`, `order`(Double, 분수 순서), `createdBy`, `lastBy`, `createdAt`, `updatedAt` |
| `rooms/{code}/presence/{uid}` | `listId`, `at`(서버 시각) |

- 방 코드는 6자리이고, `SecureRandom`으로 만든다.
- 이벤트 파싱은 `str()` 헬퍼를 써서, 잘못된 문서 하나가 앱을 죽이지 않게 한다.
- 규칙은 저장소의 `firestore.rules`에 있다. 규칙은 필드마다 타입과 크기를 검사하고, 작성자와 수정 이력을 위조하지 못하게 막는다.

## 3. 주요 결정과 이유

### 달력·일정
- **시간 표기는 모든 곳에서 "오후 3:00"**, 시간 없는 일정은 **"하루 종일"**이다. 24시간제와 "오후 3시"는 사용자가 거부했다. 일관성이 이유다.
- 날짜 표기는 `koreanDayLabel()`("11월 9일 (월)")로 통일한다. 기간은 "11월 16일(월)~20일(금)"처럼 양쪽에 요일을 붙인다.
- 표시 순서: 여러 날 일정 → 하루 종일 → 시간순.
- 상단 "N일 지남"을 탭하면 "N일 남음"으로 바뀐다. 0~9 숫자 바퀴가 굴러가는 오도미터이고, easing은 `CubicBezier(0.2, 0.8, 0.2, 1)`이다. 선택은 prefs `"year_header"/"show_left"`에 저장된다.

### 체크리스트
- 리스트는 독립적으로 둘 수도 있고, 일정에 연결할 수도 있다(`eventId`). **한 일정에 여러 리스트**를 연결할 수 있다.
- 기본 이름은 `"{일정 제목} 체크리스트"`이고, 같은 이름이 있으면 " 2"처럼 번호를 붙인다(`defaultChecklistTitle`).
- 일정 아래 칩에서는 일정 제목 접두어를 단어 경계에서 뺀다(`checklistLabel`). 예: "제주 여행 짐" → "짐". 남는 게 없으면 "체크리스트".
- **칩은 항상 한 줄**(일정 줄 높이를 일정하게 유지)이다. 들어가는 만큼만 보여 주고 나머지는 "+N"으로 접는다.
  - **진행률 "3/8"은 항상 보인다.** 칸이 모자라면 이름만 잘린다.
  - "+N"을 누르면 팝업 메뉴가 뜬다(Popup + AnimatedVisibility, 크기 0.9→1, 열기 180ms / 닫기 120ms).
  - 팝업에는 **그림자 없이 테두리만** 쓴다. 투명도 애니메이션 중에는 그림자가 안 그려지다가 끝에 툭 나타나기 때문이다.
- 색: 남은 항목이 있으면 계절 강조색, 완료되면 초록(`CompleteGreen`).
- 체크리스트 버튼 배지는 **완료할 리스트 수**를 센다. 빈 리스트와 끝난 일정에 연결된 리스트는 세지 않는다(`openListCount`).
- 일정에서 연 리스트에서 뒤로 가면 전체 목록이 아니라 **달력으로** 돌아간다.
- "일정에 연결"은 시간순 목록이 아니라 **작은 월 달력**에서 고른다. 날짜를 누르고, 일정을 고르고(기간이 블록으로 칠해짐), "연결"로 확정한다.
- 삭제는 4초 안에 "되돌리기"할 수 있다(`UNDO_WINDOW`). "보는 중" 표시는 20초마다 신호를 보내고, 60초 안의 신호만 유효하다.
- 마지막 항목을 **체크로** 끝냈을 때만 계절 모양 꽃가루가 터진다. 삭제로 끝났거나 되돌리기로 항목이 돌아온 경우는 제외한다.

### 위젯 (Glance)
- 위젯은 1×1 타일, 2×1 카드, 2×2 이상 목록 세 가지이고, 모두 크기를 조절할 수 있다.
- `SizeMode.Exact`를 쓴다. 고정 크기 버킷을 쓰면 런처가 알려 주는 크기와 어긋나서 공백이 크게 생겼다. 그래서 실제 높이로 줄 수를 계산한다.
  - 기기마다 생기는 오차에 대비해 **8dp 안전 여유**(`SAFETY_MARGIN`)를 둔다.
  - `listChrome(scale)` = 20dp + (16·2 + 19)·scale + 13dp + 28dp. `scale`은 시스템 글꼴 배율이다.
  - 목록 줄 높이 `ROW_HEIGHT` 22dp. 카드 줄 칸은 `SMALL_LINE` 16dp / `TITLE_LINE` 19dp이고, `CardLine`으로 위쪽에 고정한다.
- 크기가 달라도 보이는 일정 수가 같아야 한다. 이건 사용자 요구다.
- 2칸 너비 목록은 `날짜 · 점 · 제목`만 보여 준다(오늘 일정은 날짜 대신 시간). 4칸 너비는 시간 열을 추가한다.
  - 점이 세로로 정렬되도록 위젯마다 날짜 열 폭을 통일한다.
  - 시간이 없는 일정은 속이 빈 점으로 표시한다.
- 카드 제목은 `AndroidRemoteViews`와 `widget_card_title.xml`(TextView, `includeFontPadding=false`, `fallbackLineSpacing=false`)로 그린다.
  - Glance Text는 제목이 잘리지 않을 때(BoringLayout)와 "…"로 잘릴 때(StaticLayout) 한글 높이가 약 1.5dp 달랐다.
  - 고정 칸, 위쪽 고정, LRM 문자 모두 실패해서 이 방법으로 해결했다.
- 위젯 그리기는 `WidgetUpdater`의 `Mutex`로 한 번에 하나만 실행한다. `render()`는 그릴 때마다 1시간 주기 작업을 다시 등록한다.
- 사진 배경은 실제 `LocalSize` 비율로 자르고, 160–640px로 제한한다. LruCache 16MB(키 `"$id:$stamp"`)에 보관하고, 위젯이 삭제되면 캐시를 비운다.

## 4. 알려진 함정 (다시 밟지 말 것)

1. **Glance는 Row/Column/Box 하나에 자식을 최대 10개까지만** 그리고, 나머지는 **조용히 버린다.** 그래서 목록이 길 때 "+" 버튼이 사라졌다. 줄은 10개씩 중첩 Column으로 묶는다.
2. **`IntrinsicSize.Min` 아래에서 `SubcomposeLayout`을 쓰면 크래시**가 난다. 11월 4일 체크리스트 칩 버그가 이것이었다. EventRow의 칩은 일반 `Layout`으로 후보를 모두 미리 배치한다.
3. Compose 성능 원칙:
   - 애니메이션 값은 draw 단계나 `graphicsLayer {}` 안에서만 읽는다.
   - 파생 값은 `derivedStateOf`로 만든다.
   - 짧은 주기 타이머는 필요한 작은 컴포저블 안에 가둔다(예: `ViewingNow`).
4. `SharedChecklists.stop()`은 **회전할 때도 호출된다.** 그래서 stop에서 데이터를 지우면 안 된다. 방 코드가 바뀔 때만 `start()`에서 지운다.
5. 드래그 중에는 **id 순서만** 고정한다(`workingIds`). 내용은 실시간 상태에서 다시 읽는다(`currentTodo()`). 옮기던 항목이 사라지면 드래그를 끝낸다.
6. 지연 쓰기(체크 후 이동, 스와이프 삭제)는 줄의 scope가 아니라 **부모 목록의 scope**에서 실행한다. 줄이 화면에서 사라져도 쓰기가 유지되게 하려는 것이다.
7. "보는 중" 신호는 `LifecycleStartEffect`로 앱이 백그라운드로 가면 멈춘다.
8. 접근성: 기호 버튼은 `GlyphButton`으로 설명을 붙인다. 오도미터, 연 카드, 배지는 `clearAndSetSemantics`로 한 문장만 읽게 한다.

## 5. 3.1 리뷰 패치에서 바꾼 것 (검증 대상)

`review_v3_1.zip`, 36개 파일. 새 파일은 `AppState.kt`, `ui/common/GlyphButton.kt`이다.

- **버그 수정**
  - "보는 중"이 백그라운드에서 계속 유지됨
  - 체크 직후 화면을 넘기면 저장이 사라짐
  - 드래그와 상대 편집이 충돌함, 가장자리 자동 스크롤이 과함
  - 삭제로 완료했는데 축하가 나옴, 꽃가루가 목록 뒤에 가려짐
  - 회전하면 상태가 초기화됨
  - 200자 넘는 제목이 규칙에 거부됨
  - 에러 토스트가 사라질 때 빈칸이 됨
  - 앱 시작 직후 1.2초 동안 투명 화면이 터치를 받음
- **위젯**
  - 사진 비율
  - 큰 글씨에서 + 버튼이 잘림
  - 1×1 문구가 잘림
  - 1시간 주기 작업 복구
  - 동시에 그리기
  - 설정 화면 사진을 메인 스레드에서 디코딩
- **성능**
  - 1시간 갱신 쿼리를 `date >= 오늘 - MAX_EVENT_DAYS`로 제한
  - `derivedStateOf` 적용
  - 사진 캐시
  - 브러시 재사용
  - 하이라이트 정리
- **정리**: Duration 상수, `koreanDayLabel`, `rangeLabel` 삭제, 죽은 코드와 틀린 주석 정리
- **일부러 남긴 것**
  - `USE_GPU_GLOW` 비교 스위치: 사용자가 직접 비교하려고 남겼다.
  - 진행 바 애니메이션: 이미 draw 단계에서 읽고 있어서 수정할 필요가 없었다.

## 6. 아직 하지 않은 일

**리뷰에서 보류한 것**
- 리스트마다 붙는 실시간 items 리스너의 비용 → 리스트 문서에 완료/전체 개수 필드를 추가하는 안
- 다른 폰이 자리를 복구했을 때 원래 폰이 오류 상태로 멈춤 → PERMISSION_DENIED나 members에 내가 없는 경우를 감지해 안내
- 위젯 설정 화면의 미리보기가 실제 위젯과 다름 → 데모 먼저
- Compose 안정성 설정(`java.time`) → `build.gradle.kts` 변경이 필요하므로 사용자에게 제안
- DayNumber의 안 쓰는 파라미터

**로드맵**
- AI 체크리스트 추천: 체크리스트가 필요한 일정을 판단한다. `ChecklistSuggester` 인터페이스로 만들고, 구현은 Python으로 학습한 n-gram 로지스틱 회귀를 JSON으로 내보내는 방식 또는 LLM이다.
- 체크리스트 2차: 템플릿, 담당자, 리스트 빠른 추가("장보기 우유, 계란"), 완료 항목 비우기
- 체크리스트 위젯, 항목 마감일, 고정 D-day
- R8 다시 켜기, Firestore 최소 버전으로 "업데이트 필요" 화면
- 빠른 추가 바가 오늘 일정을 가리는 문제 → A안: "N일 지남" 글자와 여백 축소

---

## 7. 이 프로젝트의 첫 작업 (복사해서 쓰는 지시문)

> 프로젝트 지침과 인수인계 문서(handoff.md)를 먼저 읽어 줘. 그다음, 3.1 리뷰 패치가 적용된 현재 Tandem 코드를 검토해 줘.
> 1. **오류**: 컴파일 오류 가능성, 크래시, 동작 버그. 특히 5장의 수정 사항이 의도대로 동작하는지, 새로운 문제를 만들지 않았는지 확인해 줘.
> 2. **최적화**: 성능, Firestore 비용, 메모리, 접근성.
> 3. **문서 일치 여부**: 코드가 인수인계 문서와 프로젝트 지침(데이터 모델, 결정 사항, 함정 목록, 코드 규칙)에 맞는지. 다르면 코드와 문서 중 어느 쪽을 고쳐야 하는지 의견을 줘.
> 결과는 심각도 순으로 정리하고, 확실한 것과 추정을 구분해 줘. 코드 수정은 내가 확인한 뒤에 진행해 줘.
