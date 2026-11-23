# Environment and requirements

Whisly is an Android application. The primary build is the Gradle project in `whisly/`.
There is no Python build step for the app itself; Python is used only for the optional
control-plane API and the evaluation harness.

## Android app (required)

| Requirement | Value |
|---|---|
| JDK | 17 or newer |
| Gradle | Provided by the wrapper (`whisly/gradlew.bat` / `./gradlew`) |
| Android Gradle Plugin | 9.1.1 |
| Android SDK Platform | API 36 |
| Kotlin | 2.2.10 |
| Device ABI | arm64-v8a |
| Minimum Android version | API 24 (Android 7.0) |

Create `whisly/local.properties` (git-ignored) with your own SDK path:

```properties
sdk.dir=C\\:\\\\Users\\\\you\\\\AppData\\\\Local\\\\Android\\\\Sdk
```

Then:

```bash
cd whisly
./gradlew.bat clean assembleDebug testDebugUnitTest lint
```

Recorded result: build successful, 47 tests passed, 0 lint errors (105 warnings).

## Bundled model

`whisly/app/src/main/assets/whisper/tiny/` contains the INT8 Whisper Tiny export used for
English STT. These ONNX files are part of the source asset set and are intentionally tracked.
No other model is bundled. See `whisly/docs/MODELS.md`.

## Optional control-plane API

Python requirements for the unused FastAPI control plane are in
[`whisly/backend/requirements.txt`](../whisly/backend/requirements.txt).

```bash
cd whisly/backend
python -m venv .venv
# Windows: .venv\Scripts\activate   |   macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## Evaluation harness

Python scripts under `whisly/scripts/` and `whisly/deliverables/` are analysis tooling. They are
not required to build or run the app. Results and their limitations are documented in
`whisly/docs/EVAL_RESULTS.md`.
