# Whisly Test & Verification Report (TEST_REPORT.md)

**App Package:** `com.aistudio.whisly.kxmpzq`  
**Version:** 1.0.6 (Code: 6, Name: "1.0.6")  
**Target Architecture:** Android (minSdk 24, targetSdk 36, compileSdk 36)  
**Date:** November 19, 2026  
**Status:** 100% PASS (31 Tests Run, 0 Failures, 0 Errors, 0 Regressions)  

---

## 1. Summary of Test Execution

| Test Suite | Tests Run | Passed | Failed | Errors | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `OfflineZeroNetworkGuardTest` | 5 | 5 | 0 | 0 | 0 | **PASS** |
| `TranslationRegressionLockTest` | 2 (21 cases) | 2 | 0 | 0 | 0 | **PASS** |
| `ExampleRobolectricTest` | 5 | 5 | 0 | 0 | 0 | **PASS** |
| `ExampleUnitTest` | 13 | 13 | 0 | 0 | 0 | **PASS** |
| `LaunchAndRecoveryPreventionTest` | 5 | 5 | 0 | 0 | 0 | **PASS** |
| `NavigationTapTest` | 1 | 1 | 0 | 0 | 0 | **PASS** |
| **Total** | **31** | **31** | **0** | **0** | **0** | **100% PASS** |

---

## 2. Q1 Reference Sentence Deep Benchmark

The test `q1 reference sentence preserves reported speech, normalizes typos, and fixes beggar translation` evaluates the exact complex compound reported speech sentence:

- **Input:** `"my mom said I was very cute in my childhood but in my adulthood looks like begger"`
- **Source Language:** English (`en`)
- **Target Language:** Tamil (`ta`)
- **App Enhanced Output:** `என் அம்மா நான் என் குழந்தை பருவத்தில் மிகவும் அழகாக இருந்ததாகச் சொன்னார்; ஆனால் நான் வளர்ந்த பிறகு பிச்சைக்காரனைப் போல தெரிகிறேன்`
- **Google Reference Output:** `என் அம்மா, நான் சிறுவயதில் மிகவும் அழகாக இருந்ததாகச் சொன்னார்; ஆனால் இப்போது வளர்ந்த பிறகு பிச்சைக்காரனைப் போல இருக்கிறேன்.`

### Key Defect Remediation Assertions:
1. **Reported Speech Preserved:** Contains `"சொன்னார்"` / `"இருந்ததாகச் சொன்னார்"` (the previous engine dropped "said").
2. **Typo Normalized:** Pre-normalizer automatically corrects colloquial `"begger"` to `"beggar"`.
3. **Beggar vs Alms Corrected:** Output correctly inflects the human noun `"பிச்சைக்காரனைப் போல"` (like a beggar) instead of the alms noun `"பிச்சை போல்"`.
4. **Adulthood Semantics Corrected:** Translates to `"வளர்ந்த பிறகு"` instead of the plural adults `"வயது வந்தோர்"`.
5. **Subject-Verb Agreement:** Maintains 1st-person agreement (`"தெரிகிறேன்"` / `"இருக்கிறேன்"`).
6. **Script Integrity:** 100% valid Tamil Unicode range (`0x0B80..0x0BFF`).
7. **Quality Floor:** chrF++ >= 50.0 against Google Translate gold reference.

---

## 3. Translation Regression Lock (20 Sentences Parity)

`TranslationRegressionLockTest` verifies 20 sentence-level translation test cases across supported Indic language pairs (English → Tamil, English → Bengali, English → Hindi, etc.).

| # | Input | Pair | Reference Translation | Output Verified | Script | chrF++ Floor | Result |
| :---: | :--- | :---: | :--- | :--- | :---: | :---: | :---: |
| 1 | "i need to know where is the theatre" | en → ta | திரையரங்கம் எங்கே இருக்கிறது என்று நான் தெரிந்துகொள்ள வேண்டும் | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 2 | "I need water" | en → ta | எனக்கு தண்ணீர் வேண்டும் | Verified Tamil Script | 100% Tamil | >= 60.0 | **PASS** |
| 3 | "Where is the train station?" | en → ta | ரயில் நிலையம் எங்கே உள்ளது? | Verified Tamil Script | 100% Tamil | >= 55.0 | **PASS** |
| 4 | "What is your name?" | en → ta | உங்கள் பெயர் என்ன? | Verified Tamil Script | 100% Tamil | >= 65.0 | **PASS** |
| 5 | "How are you doing today?" | en → ta | நீங்கள் இன்று எப்படி இருக்கிறீர்கள்? | Verified Tamil Script | 100% Tamil | >= 55.0 | **PASS** |
| 6 | "Can you please help me find this address?" | en → ta | இந்த முகவரியைக் கண்டுபிடிக்க எனக்கு உதவ முடியுமா? | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 7 | "How much does this cost?" | en → ta | இதன் விலை எவ்வளவு? | Verified Tamil Script | 100% Tamil | >= 60.0 | **PASS** |
| 8 | "I am looking for the departure gate" | en → ta | நான் புறப்படும் நுழைவாயிலைத் தேடுகிறேன் | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 9 | "Please speak a little slower so I can understand" | en → ta | நான் புரிந்துகொள்ளும்படி கொஞ்சம் மெதுவாகப் பேசுங்கள் | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 10 | "Is there a pharmacy near here?" | en → ta | இங்கே அருகில் மருந்தகம் ஏதேனும் இருக்கிறதா? | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 11 | "The weather today is very pleasant" | en → ta | இன்றைய வானிலை மிகவும் இனிமையாக உள்ளது | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 12 | "Please call an ambulance immediately" | en → ta | உடனடியாக ஆம்புலன்ஸை அழைக்கவும் | Verified Tamil Script | 100% Tamil | >= 55.0 | **PASS** |
| 13 | "I would like to order vegetarian food" | en → ta | நான் சைவ உணவு ஆர்டர் செய்ய விரும்புகிறேன் | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 14 | "Where can I exchange foreign currency?" | en → ta | வெளிநாட்டு நாணயத்தை நான் எங்கே மாற்றலாம்? | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 15 | "Do you speak English?" | en → ta | நீங்கள் ஆங்கிலம் பேசுவீர்களா? | Verified Tamil Script | 100% Tamil | >= 60.0 | **PASS** |
| 16 | "I have lost my passport" | en → ta | எனது கடவுச்சீட்டை நான் தொலைத்துவிட்டேன் | Verified Tamil Script | 100% Tamil | >= 55.0 | **PASS** |
| 17 | "Thank you very much for your kind support" | en → ta | உங்கள் கனிவான ஆதரவிற்கு மிக்க நன்றி | Verified Tamil Script | 100% Tamil | >= 55.0 | **PASS** |
| 18 | "Good morning, have a wonderful day" | en → ta | காலை வணக்கம், நல்ல நாளாக அமையட்டும் | Verified Tamil Script | 100% Tamil | >= 50.0 | **PASS** |
| 19 | "The doctor will arrive soon" | en → hi | डॉक्टर जल्द ही आ जाएंगे | Verified Devanagari | 100% Devanagari | >= 50.0 | **PASS** |
| 20 | "Goodbye, see you tomorrow" | en → bn | বিদায়, কাল দেখা হবে | Verified Bengali Script | 100% Bengali | >= 55.0 | **PASS** |

---

## 4. Zero-Network STT Pipeline Verification (`OfflineZeroNetworkGuardTest`)

1. **Total Eradication of System Speech Recognizer:** Asserted that `com.example.ai.stt.OnDeviceSpeechRecognizerHelper` does not exist and `WhislyOfflineSpeechRecognizer` does not hold any reference to `android.speech.SpeechRecognizer`.
2. **Autonomous Offline Audio Ingestion:** Recognizer captures 16kHz mono PCM16 directly from `AudioProcessor` (`AudioRecord`) with multi-feature VAD, silence guards, and debug WAV recording.
3. **Fail-Closed on Non-Speech:** Verifies that silent PCM audio returns `PipelineErrorType.MICROPHONE_MUTED` or `SPEECH_NOT_DETECTED` rather than falling back to canned greetings (`"Hello"` / `"vannakkam"`).
4. **Local Memory Normalization:** Verifies that typo correction, reported speech clause handling, and output semantic post-processing operate strictly in local JVM heap with zero network sockets or HTTP calls.
