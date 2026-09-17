# Meeting Brief v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Android APK that captures meeting messages by speech or typing, creates offline work-focused summaries and recommendations, stores them locally, and supports full-text search.

**Architecture:** Kotlin and Jetpack Compose provide a chat-style UI. Room stores meetings and message rows; pure Kotlin analyzers extract assignees, deadlines, actions, cautions, and missing-information recommendations without external APIs. Android speech recognition supplies transient transcription while original audio is never saved.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Android SpeechRecognizer/RecognizerIntent, JUnit 4, Gradle 8.9, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-09-17-meeting-brief-design.md`

## Global Constraints

- Separate Android APK with no account, server, or API key.
- Android minSdk 26 and targetSdk 35.
- Store text only; never persist raw audio.
- Typing, local storage, search, and summarization work offline.
- Speech availability may depend on the recognition service installed on the phone.
- Korean-first UI with large controls and a clean chat-like layout.

---

### Task 1: Android scaffold and summary domain

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/multi0819/meetingbrief/Summary.kt`
- Test: `app/src/test/java/com/multi0819/meetingbrief/SummaryTest.kt`

**Interfaces:**
- Produces: `MeetingSummary`, `OfflineSummaryEngine.summarize(List<String>): MeetingSummary`

- [ ] **Step 1: Write failing summary tests**

```kotlin
@Test fun extractsWorkAndDeadline() {
  val result = OfflineSummaryEngine.summarize(listOf("김대리는 냉각기 압력을 내일 오전까지 확인하세요"))
  assertTrue(result.actions.any { it.contains("김대리") && it.contains("냉각기") })
  assertTrue(result.deadlines.any { it.contains("내일 오전") })
}
@Test fun recommendsMissingOwnerAndDeadline() {
  val result = OfflineSummaryEngine.summarize(listOf("냉각기 압력을 확인하세요"))
  assertTrue(result.recommendations.any { it.contains("담당자") })
  assertTrue(result.recommendations.any { it.contains("기한") })
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `gradle testDebugUnitTest --tests com.multi0819.meetingbrief.SummaryTest`
Expected: compilation failure because `OfflineSummaryEngine` does not exist.

- [ ] **Step 3: Add minimal pure-Kotlin analyzer**

```kotlin
data class MeetingSummary(
 val notices:List<String>, val actions:List<String>, val deadlines:List<String>,
 val cautions:List<String>, val confirmations:List<String>, val recommendations:List<String>
)
object OfflineSummaryEngine {
 fun summarize(lines:List<String>):MeetingSummary {
  val clean=lines.map(String::trim).filter(String::isNotBlank)
  val deadlines=clean.filter { Regex("오늘|내일|모레|까지|오전|오후|\\d{1,2}[./월-]\\d{1,2}").containsMatchIn(it) }
  val actions=clean.filter { Regex("하세요|바랍니다|확인|점검|조치|보고|제출|완료").containsMatchIn(it) }
  val cautions=clean.filter { Regex("주의|금지|위험|고장|이상").containsMatchIn(it) }
  val confirmations=clean.filter { Regex("확인 필요|미정|추후|모름|검토").containsMatchIn(it) }
  val rec=buildList {
   if(actions.isNotEmpty() && clean.none { Regex("[가-힣]{2,4}(님|씨|대리|과장|팀장|부장|담당)").containsMatchIn(it) }) add("담당자가 지정되지 않았습니다.")
   if(actions.isNotEmpty() && deadlines.isEmpty()) add("완료 기한이 지정되지 않았습니다.")
   if(clean.none { it.contains("보고") || it.contains("공유") }) add("완료 후 보고 방법을 확인하세요.")
  }
  return MeetingSummary(clean,actions,deadlines,cautions,confirmations,rec)
 }
}
```

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `gradle testDebugUnitTest --tests com.multi0819.meetingbrief.SummaryTest`
Expected: all `SummaryTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: scaffold meeting app and offline summary engine"
```

### Task 2: Room storage and search

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/Data.kt`
- Test: `app/src/androidTest/java/com/multi0819/meetingbrief/MeetingDaoTest.kt`

**Interfaces:**
- Produces: `Meeting`, `MeetingMessage`, `MeetingWithMessages`, `MeetingDao`, `MeetingDb`
- Search contract: `MeetingDao.search(query:String): Flow<List<Meeting>>`

- [ ] **Step 1: Write failing in-memory Room tests**

```kotlin
@Test fun searchFindsTopicAndBody() = runTest {
 val id=dao.insertMeeting(Meeting(topic="냉각기 점검"))
 dao.insertMessage(MeetingMessage(meetingId=id,text="김대리 압력 확인"))
 assertEquals(id, dao.searchOnce("냉각기").single().id)
 assertEquals(id, dao.searchOnce("김대리").single().id)
}
```

- [ ] **Step 2: Run instrumentation test and confirm RED**

Run: `gradle connectedDebugAndroidTest`
Expected: compilation failure because Room models and DAO are absent.

- [ ] **Step 3: Add normalized Room entities and DAO**

```kotlin
@Entity data class Meeting(@PrimaryKey(autoGenerate=true) val id:Long=0,val topic:String,val startedAt:Long=System.currentTimeMillis(),val endedAt:Long?=null,val summaryText:String="",val recommendationsText:String="",val updatedAt:Long=System.currentTimeMillis())
@Entity(indices=[Index("meetingId")]) data class MeetingMessage(@PrimaryKey(autoGenerate=true) val id:Long=0,val meetingId:Long,val text:String,val createdAt:Long=System.currentTimeMillis())
@Dao interface MeetingDao {
 @Insert suspend fun insertMeeting(v:Meeting):Long
 @Insert suspend fun insertMessage(v:MeetingMessage):Long
 @Query("SELECT DISTINCT m.* FROM Meeting m LEFT JOIN MeetingMessage x ON x.meetingId=m.id WHERE m.topic LIKE '%'||:q||'%' OR x.text LIKE '%'||:q||'%' OR m.summaryText LIKE '%'||:q||'%' OR m.recommendationsText LIKE '%'||:q||'%' ORDER BY m.updatedAt DESC") fun search(q:String):Flow<List<Meeting>>
}
```

- [ ] **Step 4: Run storage tests and confirm GREEN**

Run: `gradle connectedDebugAndroidTest`
Expected: meeting persists and both topic/body queries return it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/androidTest
git commit -m "feat: store and search meeting records locally"
```

### Task 3: ViewModel and meeting workflow

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/MeetingViewModel.kt`
- Test: `app/src/test/java/com/multi0819/meetingbrief/MeetingWorkflowTest.kt`

**Interfaces:**
- Consumes: `MeetingDao`, `OfflineSummaryEngine`
- Produces: `MeetingUiState`, `MeetingViewModel.addMessage`, `summarize`, `finishMeeting`, `search`

- [ ] **Step 1: Write failing workflow tests with an in-memory fake DAO**

```kotlin
@Test fun summarizingPersistsSectionsAndRecommendations() = runTest {
 val vm=MeetingWorkflow(fakeStore)
 val id=vm.start("설비 전달사항")
 vm.add(id,"냉각기 상태를 확인하세요")
 vm.summarize(id)
 assertTrue(fakeStore.meeting(id).summaryText.contains("냉각기"))
 assertTrue(fakeStore.meeting(id).recommendationsText.contains("담당자"))
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `gradle testDebugUnitTest --tests com.multi0819.meetingbrief.MeetingWorkflowTest`
Expected: `MeetingWorkflow` unresolved.

- [ ] **Step 3: Implement workflow with injected store and clock**

```kotlin
class MeetingWorkflow(private val store:MeetingStore,private val now:()->Long=System::currentTimeMillis) {
 suspend fun start(topic:String)=store.create(Meeting(topic=topic,startedAt=now()))
 suspend fun add(id:Long,text:String)=store.addMessage(MeetingMessage(meetingId=id,text=text.trim(),createdAt=now()))
 suspend fun summarize(id:Long) { val summary=OfflineSummaryEngine.summarize(store.messages(id).map{it.text}); store.saveSummary(id,summary,now()) }
 suspend fun finish(id:Long)=store.finish(id,now())
}
```

- [ ] **Step 4: Run workflow and domain tests**

Run: `gradle testDebugUnitTest`
Expected: all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add meeting capture and summary workflow"
```

### Task 4: Kakao-style Compose UI

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/MainActivity.kt`
- Create: `app/src/main/java/com/multi0819/meetingbrief/MeetingScreens.kt`
- Create: `app/src/main/java/com/multi0819/meetingbrief/Theme.kt`
- Test: `app/src/androidTest/java/com/multi0819/meetingbrief/MeetingScreenTest.kt`

**Interfaces:**
- Consumes: `MeetingViewModel` state and actions
- Produces: home/search list, new-meeting screen, chat message editor, summary cards, record detail screen

- [ ] **Step 1: Write failing Compose tests**

```kotlin
@Test fun meetingScreenKeepsInputAndSummaryActionsVisible() {
 compose.setContent { MeetingScreen(FakeMeetingState()) }
 compose.onNodeWithText("전달사항 입력").assertExists()
 compose.onNodeWithText("음성").assertExists()
 compose.onNodeWithText("요약").assertExists()
}
```

- [ ] **Step 2: Run UI tests and confirm RED**

Run: `gradle connectedDebugAndroidTest`
Expected: `MeetingScreen` unresolved.

- [ ] **Step 3: Implement the UI**

Use a pale background, white app bar, rounded yellow outgoing message bubbles, 52dp minimum touch targets, `LazyColumn` message history, and a fixed bottom composer with microphone, text field, send, and summary actions. Show summary sections as compact white cards and search as a single prominent field on the home screen.

- [ ] **Step 4: Run UI tests and capture a rendered screenshot**

Run: `gradle connectedDebugAndroidTest`
Expected: controls and content descriptions are present and clickable.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/androidTest
git commit -m "feat: add clean chat-style meeting interface"
```

### Task 5: Speech-to-text without audio persistence

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/SpeechInput.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/multi0819/meetingbrief/MeetingScreens.kt`
- Test: `app/src/test/java/com/multi0819/meetingbrief/SpeechResultTest.kt`

**Interfaces:**
- Produces: `SpeechInputController.start()`, `stop()`, `onText(String)`, `SpeechText.clean(String):String`

- [ ] **Step 1: Test transcript cleaning and duplicate suppression**

```kotlin
@Test fun trimsAndRejectsDuplicatePartialResult() {
 val state=SpeechText()
 assertEquals("밸브 확인",state.accept("  밸브 확인 "))
 assertNull(state.accept("밸브 확인"))
}
```

- [ ] **Step 2: Run test and confirm RED**

Run: `gradle testDebugUnitTest --tests com.multi0819.meetingbrief.SpeechResultTest`
Expected: `SpeechText` unresolved.

- [ ] **Step 3: Implement SpeechRecognizer lifecycle**

Request `RECORD_AUDIO`, start Korean recognition via `RecognizerIntent.EXTRA_LANGUAGE="ko-KR"`, append final text through `onText`, destroy the recognizer when the screen closes, and never create an audio file or MediaRecorder.

- [ ] **Step 4: Run all tests and manually verify permission denial**

Run: `gradle testDebugUnitTest connectedDebugAndroidTest`
Expected: tests pass; denied permission shows a short explanation while typed input remains usable.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat: add transient Korean speech input"
```

### Task 6: Share, delete, export, and restore

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/MeetingExport.kt`
- Modify: `app/src/main/java/com/multi0819/meetingbrief/MeetingScreens.kt`
- Test: `app/src/test/java/com/multi0819/meetingbrief/MeetingExportTest.kt`

**Interfaces:**
- Produces: `MeetingFormatter.toShareText`, `BackupCodec.encode`, `BackupCodec.decode`

- [ ] **Step 1: Write failing format and round-trip tests**

```kotlin
@Test fun shareTextContainsRequiredSections() { val text=MeetingFormatter.toShareText(sample); assertTrue(text.contains("[전달사항]")); assertTrue(text.contains("[추천사항]")) }
@Test fun backupRoundTripsKoreanText() { assertEquals(sample,BackupCodec.decode(BackupCodec.encode(sample))) }
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `gradle testDebugUnitTest --tests com.multi0819.meetingbrief.MeetingExportTest`
Expected: formatter and codec unresolved.

- [ ] **Step 3: Implement UTF-8 JSON backup and Android share sheet**

Use Storage Access Framework for export/import, validate a numeric backup version before replacing records in a Room transaction, share plain text with `Intent.ACTION_SEND`, and require confirmation before deleting a meeting.

- [ ] **Step 4: Run all tests**

Run: `gradle testDebugUnitTest connectedDebugAndroidTest`
Expected: all tests pass and Korean text survives round-trip.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat: share backup and restore meeting records"
```

### Task 7: CI build and APK delivery

**Files:**
- Create: `.github/workflows/android.yml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: installable `app-debug.apk` artifact

- [ ] **Step 1: Configure deterministic CI**

Use Java 17, Gradle 8.9, then run `gradle testDebugUnitTest assembleDebug --stacktrace` and upload `app/build/outputs/apk/debug/app-debug.apk` as `MeetingBrief-debug-apk`.

- [ ] **Step 2: Run local verification**

Run: `gradle testDebugUnitTest assembleDebug --stacktrace`
Expected: zero failed tests and `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect APK**

Run: `file app/build/outputs/apk/debug/app-debug.apk && sha256sum app/build/outputs/apk/debug/app-debug.apk`
Expected: Android APK file plus a non-empty SHA-256 digest.

- [ ] **Step 4: Commit and push**

```bash
git add .github app/build.gradle.kts
git commit -m "ci: verify and package meeting brief apk"
```

- [ ] **Step 5: Download the successful CI artifact**

Verify the workflow conclusion is `success`, download the artifact, test the ZIP, and provide the APK plus a ZIP fallback.
