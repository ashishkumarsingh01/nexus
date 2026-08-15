# NEXUS — Offline-First Personal AI Assistant (Android)

Status: **All 12 phases implemented.**

Everything used in this project is free: Kotlin, Jetpack Compose, Android
SDK, Room, MediaPipe's on-device LLM inference library, GitHub Actions
(free build minutes), and every recommended model is a free, open-weight
download. No API keys, no subscriptions, no cloud AI calls anywhere in the
core app.

## What's implemented

| Phase | Feature | Notes |
|---|---|---|
| 1 | Android project + Compose UI shell | NEXUS header, status pills, chat, input bar |
| 2 | Local AI (`ModelManager`) | MediaPipe LLM Inference (`tasks-genai`), `.task` GGUF-converted models, user-imported via Settings |
| 3 | Text conversation | `LocalAIEngine` + `PromptManager`, persona-constrained prompt |
| 4 | Local memory (Room) | Explicit `"remember that…"` commands only; conversation history off by default |
| 5 | Tool system | `Tool` interface, `ToolRouter`, `PermissionLevel` (SAFE/SENSITIVE/DANGEROUS) |
| 6 | Device tools | Battery, storage, time, date, volume, network, device info, RAM |
| 7 | File tools | SAF-scoped (one user-granted folder), search/list/read/create/rename/copy/move/delete (delete requires confirmation) |
| 8 | Voice I/O | Android `SpeechRecognizer` (prefers on-device) + `TextToSpeech`, both toggleable |
| 9 | Wake word | Lightweight loop built on the speech recognizer, off by default (documented tradeoff vs. a dedicated wake-word SDK) |
| 10 | App control | Curated allow-list launch via `AppTool`, no `QUERY_ALL_PACKAGES` |
| 11 | Agentic tasks | `AgentPlanner`: "find my X notes and create a revision summary" → search → read → summarize → save, with per-step failure recovery |
| 12 | Optional online mode | `OnlineTool` (free, no-key Open-Meteo weather lookup), off by default, fully separable from the offline core |

Plus: `NotificationTool` + `CommunicationTool` (contacts/SMS/calls — every
send/call requires explicit confirmation), full `PermissionScreen`,
`MemoryScreen`, `FileAccessScreen`, and "Delete all NEXUS data" in Settings.

## Getting a local model (free)

NEXUS needs a MediaPipe-format `.task` model file, imported via
**Settings → Import Model**. Free options (all require accepting a license,
not payment):
- **Gemma 2B / Gemma 2 2B** — available pre-converted to `.task` format from
  Google's MediaPipe model samples on Kaggle/Hugging Face
- **Phi-2**, **Falcon-RW-1B**, **StableLM 3B** — also supported by the same
  MediaPipe LLM Inference API

Search "MediaPipe LLM Inference .task model download" for the current
download links — Google occasionally moves these between Kaggle and Hugging
Face, so a fixed link here could go stale.

## Building — from a phone, no Android Studio

`.github/workflows/build.yml` builds the APK on GitHub's servers on every
push to `main`. No Android Studio, Gradle, or SDK needed on-device.

1. Create a GitHub repo, push this `nexus/` folder's contents to it.
2. Open the repo's **Actions** tab — the build runs automatically (or tap
   **Run workflow**).
3. Wait for green, then open the run → **Artifacts** → download
   `nexus-debug-apk` (a zip containing the installable `.apk`).
4. Extract, tap the `.apk` on your phone, allow "install unknown apps" if
   prompted, install.

No `gradlew`/`gradle-wrapper.jar` binary is committed (can't be generated
offline in the environment that produced this code) — the CI workflow
installs Gradle 8.7 directly instead, so this doesn't block the automated
build.

## Building — with Android Studio, if you get access to one later

1. Open `nexus/` in Android Studio (Koala/Ladybug+). It regenerates the
   Gradle wrapper automatically on first sync.
2. Sync (needs network, to pull AndroidX/Compose/MediaPipe/Room deps).
3. Run on a device/emulator with API 26+.

## Honest caveats

- I could not run an actual Gradle build in the environment that produced
  this code (no Android SDK, no network) — this is based on careful manual
  review of API usage and version compatibility, not a verified build log.
  The GitHub Actions run is the real first compile; if it fails, paste me
  the error log and I'll fix it.
- `IntentRouter`'s tool-triggering is keyword/regex-based, not a learned
  classifier or model-driven function call — documented in
  `tools/IntentRouter.kt`. MediaPipe's `LlmInference` API is plain
  text-in/text-out in this phase, so this is the deterministic, auditable
  alternative rather than trusting the model to emit structured tool calls.
- `AgentPlanner` handles one concrete multi-step workflow (find → read →
  summarize → save), matching the spec's worked example, not a general
  planner — for the same reason above.
- Wake word (`WakeWordManager`) reuses the speech recognizer in a restart
  loop rather than a dedicated always-on keyword-spotting model, to avoid
  adding an unverified native SDK dependency. Off by default; the tradeoff
  is documented in the file.
- Weather (Phase 12) is the only online feature implemented as a concrete
  example — web search/news are structurally supported by `OnlineTool` /
  `ToolRouter` but not built out, since those typically need either a paid
  API key or scraping that breaks without warning; adding either would
  compromise the "everything free" and "never fabricate" requirements.

## Privacy

- `android:allowBackup="false"` — no cloud/local backup extraction.
- No analytics, no ads, no telemetry anywhere in this codebase.
- Model files, memories, conversation history, and file access are all
  local-only; nothing leaves the device unless you explicitly enable Online
  Mode, and even then only city names for weather lookups are sent, to
  Open-Meteo, and to nowhere else.
