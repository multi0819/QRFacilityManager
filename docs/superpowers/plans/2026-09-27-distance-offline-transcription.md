# Distance Offline Transcription Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record a meeting continuously until the user presses stop, then transcribe Korean speakers 2–3 m from the phone completely on-device and feed the preserved transcript into the existing correction, summary, search, and storage flow.

**Architecture:** Replace the restart-based Android `SpeechRecognizer` flow with an `AudioRecord` foreground service that writes 16 kHz mono PCM into a recoverable WAV file. After stop, a repository-owned coroutine decodes sequential audio windows through sherpa-onnx's Korean int8 Zipformer model, merges the windows, preserves the raw transcript, and exposes recording/transcription state to Compose.

**Tech Stack:** Kotlin, Android SDK 26–35, Jetpack Compose, coroutines/Flow, Room, foreground service, `AudioRecord`, sherpa-onnx Android Kotlin/JNI API 1.13.8, `sherpa-onnx-zipformer-korean-2024-06-24` int8 model, JUnit 4, AndroidX instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-27-distance-offline-transcription-design.md`

## Global Constraints

- Record until the user presses `녹음 중단`; never stop because of a speech pause or fixed duration.
- The target is ordinary meeting speech from people 2–3 m from the phone, not TV or speaker audio.
- No API key, cloud transcription, or upload of audio/text.
- Preserve the complete raw transcript separately before correction or summary.
- Use 16 kHz, mono, PCM 16-bit audio and app-private storage.
- Delete temporary audio only after transcription and meeting-message persistence both succeed; retain recoverable audio after failure.
- Keep `minSdk=26`, `targetSdk=35`, and ship arm64-v8a for the user's Samsung Android device.
- Release as `versionCode=7`, `versionName="2.6.0"` with a new signed/unsigned APK artifact according to the existing build workflow.

## Review Focus

- An hour-long recording must not accumulate audio in RAM or truncate its WAV header; Task 1 tests chunked writes and finalization.
- Pause/resume and process/service recreation must not reorder or overwrite audio; Tasks 1 and 2 test append and saved-session recovery.
- A phone call or another app taking the microphone must preserve all audio recorded so far and show a recoverable error; Task 2 tests recorder failure transitions.
- Overlapping transcription windows must not duplicate or drop boundary words; Task 3 tests merge behavior with repeated and non-repeated boundaries.
- Failed transcription or failed Room persistence must not delete the source recording; Task 4 tests cleanup ordering and retry checkpoints.

---

## File Structure

- `recording/RecordingContract.kt`: recording commands, states, session metadata, and recorder interface.
- `recording/WavFileWriter.kt`: streaming PCM writes, header finalization, and append/recovery behavior.
- `recording/AndroidPcmRecorder.kt`: thin `AudioRecord` adapter.
- `recording/MeetingRecordingService.kt`: foreground-service lifecycle and notification controls.
- `transcription/TranscriptionContract.kt`: engine/result/progress interfaces.
- `transcription/KoreanOfflineTranscriber.kt`: sherpa-onnx adapter and model configuration.
- `transcription/TranscriptWindowing.kt`: window creation and overlap merging, independent of Android/JNI.
- `transcription/TranscriptionRepository.kt`: checkpointed background decoding, persistence ordering, retry, and temporary-file cleanup.
- `recording/RecordingViewModel.kt`: state exposed to Compose and user commands.
- `MainActivity.kt`: meeting-screen controls, permission flow, progress, transcript insertion, and error/retry UI.
- `Data.kt`: raw/corrected transcript persistence fields and Room migration.

### Task 1: Recoverable streaming WAV recording core

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/recording/RecordingContract.kt`
- Create: `app/src/main/java/com/multi0819/meetingbrief/recording/WavFileWriter.kt`
- Test: `app/src/test/java/com/multi0819/meetingbrief/recording/WavFileWriterTest.kt`

**Interfaces:**
- Produces: `data class RecordingSession(val meetingId: Long, val file: File, val startedAt: Long, val accumulatedFrames: Long)`
- Produces: `sealed interface RecordingState { Idle; Recording(session, elapsedMs, level); Paused(session, elapsedMs); Stopped(session); Failed(session?, message) }`
- Produces: `interface PcmRecorder { suspend fun start(session: RecordingSession, sink: PcmSink); suspend fun pause(); suspend fun resume(); suspend fun stop(): RecordingSession }`
- Produces: `class WavFileWriter(file: File, sampleRate: Int = 16000, channels: Short = 1, bitsPerSample: Short = 16)` with `write(samples: ShortArray, count: Int)`, `checkpoint(): Long`, and `closeAndFinalize()`.

- [ ] **Step 1: Write failing WAV tests**

  Add `writesLittleEndianPcmWithoutBufferingWholeRecording`, `finalizesHeaderWithActualDataLength`, `resumeAppendsAfterCheckpoint`, and `diskWriteFailureKeepsLastValidCheckpoint`. Assert RIFF/WAVE headers, byte counts, sample order, and recovery truncation.

- [ ] **Step 2: Run the focused tests and confirm failure**

  Run: `./gradlew testDebugUnitTest --tests '*WavFileWriterTest'`
  Expected: FAIL because recording contracts and `WavFileWriter` do not exist.

- [ ] **Step 3: Implement contracts and `WavFileWriter`**

  Stream each supplied buffer directly to `RandomAccessFile`; update the two WAV length fields only at checkpoints/finalization. Do not retain prior samples in memory.

- [ ] **Step 4: Run focused tests**

  Run: `./gradlew testDebugUnitTest --tests '*WavFileWriterTest'`
  Expected: PASS.

- [ ] **Step 5: Commit**

  Commit: `feat: add recoverable streaming wav writer`

### Task 2: Foreground recording service and microphone failure recovery

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/recording/AndroidPcmRecorder.kt`
- Create: `app/src/main/java/com/multi0819/meetingbrief/recording/MeetingRecordingService.kt`
- Create: `app/src/androidTest/java/com/multi0819/meetingbrief/recording/MeetingRecordingServiceTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: Task 1 `RecordingSession`, `RecordingState`, `PcmRecorder`, and `WavFileWriter`.
- Produces: `MeetingRecordingService.ACTION_START`, `ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_STOP` intents with `EXTRA_MEETING_ID`.
- Produces: `object RecordingStateBus { val state: StateFlow<RecordingState> }`.

- [ ] **Step 1: Add failing service instrumentation tests**

  Add tests named `startCreatesForegroundNotificationAndSessionFile`, `pauseResumeUsesSameSession`, `stopFinalizesFile`, `serviceRestartRestoresSessionMetadata`, and `audioRecordFailurePublishesFailedWithoutDeletingFile` using an injected fake `PcmRecorder`.

- [ ] **Step 2: Run instrumentation compilation/test and confirm failure**

  Run: `./gradlew assembleDebug compileDebugAndroidTestKotlin`
  Expected: FAIL because service classes and manifest declarations do not exist.

- [ ] **Step 3: Implement `AndroidPcmRecorder`**

  Configure `AudioRecord` for `MediaRecorder.AudioSource.UNPROCESSED` when supported and fall back to `VOICE_RECOGNITION`, 16 kHz mono PCM 16-bit. Copy fixed-size buffers to `WavFileWriter`, calculate RMS for the level meter, and checkpoint periodically.

- [ ] **Step 4: Implement and register the foreground service**

  Add `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, and the existing `RECORD_AUDIO` permission; declare the service with `foregroundServiceType="microphone"`. Persist only session path, meeting ID, elapsed frames, and state in app-private preferences for recovery.

- [ ] **Step 5: Run service tests**

  Run: `./gradlew assembleDebug compileDebugAndroidTestKotlin`
  Expected: PASS compilation; run connected tests when an emulator/device is available.

- [ ] **Step 6: Commit**

  Commit: `feat: record meetings in a foreground service`

### Task 3: Korean offline transcription engine and boundary-safe merging

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/transcription/TranscriptionContract.kt`
- Create: `app/src/main/java/com/multi0819/meetingbrief/transcription/TranscriptWindowing.kt`
- Create: `app/src/main/java/com/multi0819/meetingbrief/transcription/KoreanOfflineTranscriber.kt`
- Create: `app/src/test/java/com/multi0819/meetingbrief/transcription/TranscriptWindowingTest.kt`
- Modify: `app/build.gradle.kts`
- Add: `app/src/main/assets/sherpa-onnx-zipformer-korean-2024-06-24/encoder-epoch-99-avg-1.int8.onnx`
- Add: `app/src/main/assets/sherpa-onnx-zipformer-korean-2024-06-24/decoder-epoch-99-avg-1.onnx`
- Add: `app/src/main/assets/sherpa-onnx-zipformer-korean-2024-06-24/joiner-epoch-99-avg-1.int8.onnx`
- Add: `app/src/main/assets/sherpa-onnx-zipformer-korean-2024-06-24/tokens.txt`
- Add: arm64-v8a sherpa-onnx 1.13.8 JNI libraries under `app/src/main/jniLibs/arm64-v8a/`

**Interfaces:**
- Produces: `data class AudioWindow(val firstFrame: Long, val samples: FloatArray)`.
- Produces: `data class TranscriptionProgress(val completedFrames: Long, val totalFrames: Long, val text: String)`.
- Produces: `interface TranscriptionEngine { suspend fun transcribe(wav: File, checkpointFrame: Long = 0, onProgress: (TranscriptionProgress) -> Unit): String }`.
- Produces: `fun mergeTranscript(previous: String, next: String, maxOverlapChars: Int = 80): String`.

- [ ] **Step 1: Add failing window/merge tests**

  Test 30-second windows with 2-second overlap, recordings shorter than one window, exact repeated boundaries, similar-but-not-identical Korean boundaries, silence windows, and boundaries with no repeated text. Assert chronological text with no duplicate or dropped content.

- [ ] **Step 2: Run tests and confirm failure**

  Run: `./gradlew testDebugUnitTest --tests '*TranscriptWindowingTest'`
  Expected: FAIL because the transcription interfaces and merge functions do not exist.

- [ ] **Step 3: Implement windowing and merging**

  Read WAV frames incrementally; emit 30-second float windows with 2-second overlap. Remove only an exact normalized suffix/prefix overlap of at least two Korean syllables; otherwise retain both strings.

- [ ] **Step 4: Integrate pinned sherpa-onnx runtime and Korean int8 model**

  Configure `OfflineRecognizer` with the four exact asset paths above, CPU provider, `numThreads=2`, and greedy search. Copy compressed model assets once into an app-private model directory before recognizer creation; validate every required file and report a model error without touching the WAV.

- [ ] **Step 5: Run focused and full unit tests**

  Run: `./gradlew testDebugUnitTest`
  Expected: PASS.

- [ ] **Step 6: Commit**

  Commit: `feat: add on-device Korean transcription engine`

### Task 4: Checkpointed transcription, Room persistence, and safe cleanup

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/transcription/TranscriptionRepository.kt`
- Create: `app/src/test/java/com/multi0819/meetingbrief/transcription/TranscriptionRepositoryTest.kt`
- Modify: `app/src/main/java/com/multi0819/meetingbrief/Data.kt`
- Modify: `app/src/main/java/com/multi0819/meetingbrief/MainActivity.kt`

**Interfaces:**
- Consumes: Task 3 `TranscriptionEngine` and `TranscriptionProgress`.
- Produces: `enum class TranscriptStatus { NONE, RECORDING, READY, TRANSCRIBING, COMPLETE, FAILED }`.
- Produces: persisted `Meeting.rawTranscript`, `Meeting.correctedTranscript`, `Meeting.audioPath`, `Meeting.transcriptStatus`, and `Meeting.transcriptCheckpointFrame`.
- Produces: `fun transcribe(meetingId: Long): Flow<TranscriptionJobState>` and `suspend fun retry(meetingId: Long)`.

- [ ] **Step 1: Add failing repository tests**

  Test progress checkpoints, recreation resumes from the last frame, successful transcription saves raw text before deleting audio, Room failure retains audio, engine failure retains audio and status, empty audio produces a clear failure, and cancellation retains both checkpoint and file.

- [ ] **Step 2: Run tests and confirm failure**

  Run: `./gradlew testDebugUnitTest --tests '*TranscriptionRepositoryTest'`
  Expected: FAIL because repository and schema fields do not exist.

- [ ] **Step 3: Add Room schema fields and migration**

  Increment the database version once; migrate existing rows with empty transcript fields, `NONE` status, and zero checkpoint without deleting existing meetings.

- [ ] **Step 4: Implement repository state machine**

  Process on `Dispatchers.Default`, checkpoint after every decoded window, save `rawTranscript` transactionally, create the corrected text from a separate pure post-processing call, and delete audio only after both texts and status `COMPLETE` commit successfully.

- [ ] **Step 5: Run repository and all unit tests**

  Run: `./gradlew testDebugUnitTest`
  Expected: PASS.

- [ ] **Step 6: Commit**

  Commit: `feat: persist and resume offline transcription`

### Task 5: Recording, pause/resume, stop, progress, and retry UI

**Files:**
- Create: `app/src/main/java/com/multi0819/meetingbrief/recording/RecordingViewModel.kt`
- Create: `app/src/test/java/com/multi0819/meetingbrief/recording/RecordingViewModelTest.kt`
- Modify: `app/src/main/java/com/multi0819/meetingbrief/MainActivity.kt`
- Remove: `app/src/main/java/com/multi0819/meetingbrief/ContinuousSpeech.kt`
- Remove: `app/src/main/java/com/multi0819/meetingbrief/SpeechInput.kt`

**Interfaces:**
- Consumes: Task 2 service actions/state and Task 4 `TranscriptionRepository`.
- Produces: `data class RecordingUiState(val phase: Phase, val elapsedMs: Long, val level: Float, val progressPercent: Int, val transcript: String, val error: String?)`.
- Produces: `start(meetingId)`, `pause()`, `resume()`, `stopAndTranscribe()`, and `retryTranscription(meetingId)` commands.

- [ ] **Step 1: Add failing view-model state tests**

  Test start→recording, pause→paused, resume→recording, stop→transcribing→complete, stop with empty audio, recorder failure, transcription failure/retry, and a one-hour elapsed time display without overflow.

- [ ] **Step 2: Run tests and confirm failure**

  Run: `./gradlew testDebugUnitTest --tests '*RecordingViewModelTest'`
  Expected: FAIL because `RecordingViewModel` does not exist.

- [ ] **Step 3: Implement view model and replace the old recognizer UI**

  Show separate `녹음 시작`, `일시정지`/`계속`, and `녹음 중단` controls, elapsed time, live input level, `전체 음성을 글로 변환 중… N%`, recoverable errors, and `다시 변환`. On completion insert the full corrected transcript into the existing editable field and request select-all once; keep raw transcript available in the meeting detail.

- [ ] **Step 4: Connect summary/search to preserved text**

  Make `요약` consume the corrected full transcript and keep existing manually entered messages. Include raw/corrected transcript in Room search queries without changing the Kakao-style visual language.

- [ ] **Step 5: Run all unit tests and compile Android tests**

  Run: `./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin`
  Expected: PASS.

- [ ] **Step 6: Commit**

  Commit: `feat: add meeting recording and transcription controls`

### Task 6: Release packaging and real-device acceptance test

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/android.yml` if the existing artifact workflow needs the larger APK handled explicitly.
- Create: `docs/testing/distance-transcription-acceptance.md`

**Interfaces:**
- Consumes: complete Tasks 1–5 behavior.
- Produces: installable `MeetingBrief-v2.6.apk` and test record with device/build, room conditions, distance, duration, expected text, actual text, and observed omissions.

- [ ] **Step 1: Add packaging assertions**

  Add a CI shell check that the APK contains the three int8 model files, `tokens.txt`, arm64-v8a JNI libraries, foreground service declaration, and version `2.6.0`; fail if any are absent.

- [ ] **Step 2: Build and run automated verification**

  Run: `./gradlew clean testDebugUnitTest assembleDebug lintDebug`
  Expected: all tests and lint pass and the APK is produced.

- [ ] **Step 3: Verify clean installation**

  Remove only the test installation of `com.multi0819.workbrief`, install the generated APK with `adb install`, launch it, grant microphone/notification permissions, and confirm the version is `2.6.0`.

- [ ] **Step 4: Run the required distance tests on the Samsung device**

  Record fixed scripts at 0.5 m, 2 m, and 3 m; then record at least five minutes of natural multi-speaker conversation from 2–3 m, a 30-minute continuity run, pause/resume, screen-off, background, interrupted microphone, and failed/retried transcription. Record actual omissions instead of declaring success from build output.

- [ ] **Step 5: Gate release on observed behavior**

  Accept only if the beginning, middle, and end are present, no prior text disappears, pause/resume preserves order, retry uses the retained source file, and summaries do not invent a responsible person or deadline. If 2–3 m accuracy is inadequate, keep the build labeled test-only and tune capture/model parameters before release.

- [ ] **Step 6: Commit and publish the artifact**

  Commit: `build: package MeetingBrief 2.6 offline transcription`

