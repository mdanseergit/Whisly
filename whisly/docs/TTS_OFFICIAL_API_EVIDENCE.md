# Official Android TTS API evidence (for fix (d) gating)

Retrieved 2026-11-23 from developer.android.com. These are verbatim excerpts from the fetched
reference pages; they are the API contract used by the TTS gate. No third-party blog or inferred
behaviour is used.

## 1. `TextToSpeech.isLanguageAvailable(Locale)`

URL: `https://developer.android.com/reference/android/speech/tts/TextToSpeech`

> `public int isLanguageAvailable (Locale loc)`
> Checks if the specified language as represented by the Locale is available and supported.
> Parameters: `loc` — The Locale describing the language to be used.
> Returns: `int` Code indicating the support status for the locale. See `LANG_AVAILABLE`,
> `LANG_COUNTRY_AVAILABLE`, `LANG_COUNTRY_VAR_AVAILABLE`, `LANG_MISSING_DATA` and
> `LANG_NOT_SUPPORTED`.

Constants on the same page (verbatim descriptions and values):

| Constant | Official description | Value |
|---|---|---|
| `LANG_AVAILABLE` | Denotes the language is available for the language by the locale, but not the country and variant. | `0` |
| `LANG_COUNTRY_AVAILABLE` | Denotes the language is available for the language and country specified by the locale, but not the variant. | `1` |
| `LANG_COUNTRY_VAR_AVAILABLE` | Denotes the language is available exactly as specified by the locale. | `2` |
| `LANG_MISSING_DATA` | Denotes the language data is missing. | `-1` |
| `LANG_NOT_SUPPORTED` | Denotes the language is not supported. | `-2` |

Gate rule for the app: only `LANG_COUNTRY_VAR_AVAILABLE` or `LANG_COUNTRY_AVAILABLE` is accepted
without further checks. `LANG_AVAILABLE` means the exact country voice is not confirmed, so it is
not accepted for speech output of a specific language. `-1` and `-2` are not available.

## 2. Voices, installed data, and offline check

Same URL:

> `Set<Voice> getVoices ()` — Query the engine about the set of available voices.
> `Voice getVoice ()` — Returns a Voice instance describing the voice currently being used for
> synthesis requests sent to the TextToSpeech engine.
> `int setVoice (Voice voice)` — Sets the text-to-speech voice.
> `getFeatures(Locale locale)` — Deprecated in API level 21. As of API level 21, please use voices.
> In order to query features of the voice, call `getVoices()` to retrieve the list of available
> voices and `Voice.getFeatures()` to retrieve the set of features.

URL: `https://developer.android.com/reference/android/speech/tts/Voice`

> `Locale getLocale ()`
> `Set<String> getFeatures ()` — Returns the set of features it supports for a given voice.
> `boolean isNetworkConnectionRequired ()` — Does the Voice require a network connection to work.

URL: `https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine`

> `String KEY_FEATURE_NOT_INSTALLED` — Feature key that indicates that the voice may need to
> download additional data to be fully functional. The download will be triggered by calling
> `TextToSpeech.setVoice(Voice)` or `TextToSpeech.setLanguage(Locale)`. Until download is complete,
> each synthesis request will either report `TextToSpeech.ERROR_NOT_INSTALLED_YET` error, or use a
> different voice to synthesize the request. Constant Value: `"notInstalled"`.

> `String KEY_FEATURE_EMBEDDED_TTS` — ... If set and supported by the engine ... the engine must
> synthesize text on-device (without making network requests). Constant Value: `"embeddedTts"`.

> `ERROR_NOT_INSTALLED_YET` — Denotes a failure caused by an unfinished download of the voice data.
> Constant Value: `-9 (0xfffffff7)`.

Gate rule for the app: a voice is usable only when its `Locale` matches the target language, it is
present in `getVoices()`, it does **not** carry `"notInstalled"`, and it does **not** require a
network connection. A network-required voice is rejected because this app has no online/backend
mode. Any fallback to a different voice is rejected rather than silently speaking non-Latin text
with a wrong-language voice.

## 3. Official voice-data intent

URL: `https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine`

> `String ACTION_INSTALL_TTS_DATA` — Activity Action: Triggers the platform TextToSpeech engine to
> start the activity that installs the resource files on the device that are required for TTS to be
> operational. Since the installation of the data can be interrupted or declined by the user, the
> application shouldn't expect successful installation upon return from that intent, and if need
> be, should check installation status with `ACTION_CHECK_TTS_DATA`. Constant Value:
> `"android.speech.tts.engine.INSTALL_TTS_DATA"`.

Gate rule for the app: on a missing voice, show exactly `Voice not available for <language>` and
offer this official intent. The user is never told to install a Google offline *speech* pack (that
message belongs to STT and is forbidden). Voice data installation is a system TTS resource action,
not an app model download.

## 4. Scope

This evidence supports fix (d) TTS gating only. It does not change the approved temporary System
TTS decision, does not bundle a voice, and does not introduce network synthesis. Per-device voice
availability remains `UNMEASURED` until a phone is attached.
