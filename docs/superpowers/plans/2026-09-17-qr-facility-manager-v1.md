# QR Facility Manager v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable offline Android APK for QR-based equipment inspection and maintenance.

**Architecture:** Kotlin/Jetpack Compose app with Room for structured data, app-private photo storage, ML Kit/CameraX QR scanning, stable equipment UUIDs, and Storage Access Framework backup/restore. UI is feature-oriented and all core workflows remain offline.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose Material 3, Room, CameraX, ML Kit Barcode Scanning, ZXing Core, Android PdfDocument, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-17-qr-facility-manager-design.md`

## Global Constraints
- Android APK.
- Core features work fully offline.
- No server or login in v1.
- Existing `program2` and `GeumtoPhotoCleaner` repositories remain untouched.
- Destructive restore/delete actions require confirmation.

---

### Task 1: Android project and CI
**Files:** Gradle settings/build files, manifest, GitHub Actions workflow.
**Interfaces:** Produces a buildable `app` module and debug APK artifact.
- [ ] Add Gradle/Android project configuration and Compose dependencies.
- [ ] Add Android manifest permissions for camera.
- [ ] Add CI workflow that runs unit tests and `assembleDebug`, uploading `app-debug.apk`.
- [ ] Commit.

### Task 2: Domain model and automatic range evaluation
**Files:** `domain/Models.kt`, `domain/InspectionEvaluator.kt`, unit tests.
**Interfaces:** Produces equipment/check-item/result models and `InspectionEvaluator.evaluate`.
- [ ] Write evaluator tests for inside, below, above, and missing ranges.
- [ ] Run tests and verify expected initial failure.
- [ ] Implement evaluator and domain models.
- [ ] Run tests and commit.

### Task 3: Offline Room persistence
**Files:** `data/Entities.kt`, `data/Dao.kt`, `data/AppDatabase.kt`, `data/Repository.kt`.
**Interfaces:** Produces CRUD/Flow APIs for equipment, check items, inspections, and maintenance.
- [ ] Add DAO/database tests for insert/query relationships.
- [ ] Verify initial failure.
- [ ] Implement Room entities, DAO, database and repository.
- [ ] Run tests and commit.

### Task 4: Main dashboard, equipment registration/list/detail
**Files:** `MainActivity.kt`, `ui/App.kt`, `ui/Screens.kt`, `ui/Theme.kt`, `ui/AppViewModel.kt`.
**Interfaces:** Produces Home/List/Register/Detail navigation and large-card dark navy UI.
- [ ] Add ViewModel tests for equipment registration and dashboard counts.
- [ ] Verify initial failure.
- [ ] Implement UI state/ViewModel and screens.
- [ ] Run tests and commit.

### Task 5: QR generation and scanning
**Files:** `qr/QrCodec.kt`, `qr/QrScannerScreen.kt`, tests.
**Interfaces:** Encodes `facilityqr://equipment/<uuid>`, parses only valid app QR values, scans QR offline.
- [ ] Write QR parsing tests for valid/invalid payloads.
- [ ] Verify failure.
- [ ] Implement QR codec, bitmap generation, CameraX/ML Kit scanner.
- [ ] Run tests and commit.

### Task 6: Inspection and maintenance workflows
**Files:** `ui/InspectionScreens.kt`, `ui/MaintenanceScreens.kt`, ViewModel additions.
**Interfaces:** Supports free check-item definitions, automatic numeric abnormal status, inspection history, maintenance notes/worker and before/after photo URIs.
- [ ] Add behavior tests for result evaluation/storage.
- [ ] Verify failure.
- [ ] Implement screens and persistence calls.
- [ ] Run tests and commit.

### Task 7: Backup/restore and QR label PDF
**Files:** `backup/BackupManager.kt`, `qr/QrLabelPdf.kt`, tests.
**Interfaces:** ZIP backup contains database/photos/version metadata; PDF labels contain QR and equipment text.
- [ ] Add backup manifest validation tests.
- [ ] Verify failure.
- [ ] Implement ZIP export/import validation and PDF generation.
- [ ] Run tests and commit.

### Task 8: End-to-end verification and release artifact
**Files:** README and CI fixes if required.
**Interfaces:** Produces tested debug APK artifact from GitHub Actions.
- [ ] Run all unit tests.
- [ ] Run debug build.
- [ ] Verify requirements against spec.
- [ ] Push branch and verify GitHub Actions.
- [ ] Download artifact and confirm APK exists.
- [ ] Commit any final documentation fixes.
