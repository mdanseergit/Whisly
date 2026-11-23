# DIAGNOSIS9 — measured diagnosis of Whisper Tiny STT garbage output

Status: **IN PROGRESS** (Step 1 of the voice-reset plan). No conclusion is recorded here unless the
raw output that supports it is pasted in this file or in a linked, timestamped log under
`deliverables/diagnosis9/`.

Baseline: branch `voice-reset`, commit `c2496a2` (`Step 2: bundle Whisper Tiny multilingual INT8 STT`).
App under test: `com.aistudio.whisly.kxmpzq`, namespace `com.example`, `minSdk 24`, `targetSdk 36`.
Bundled STT: sherpa-onnx Whisper Tiny INT8 multilingual, assets pinned in
`app/src/main/assets/whisper/tiny/` (SHA-256 recorded in `MODELS.md`).

## 1. What is being tested

The app configuration was read from source before any measurement:

| Setting | Value | Source |
|---|---|---|
| `OfflineWhisperModelConfig.language` | forced from selected UI language, normalized before `-`; one of `as,bn,en,gu,hi,kn,ml,mr,ne,pa,sa,sd,ta,te,ur` | `SherpaOnnxSttEngine.kt:51-55,120-136,159-184` |
| `task` | `transcribe` (never `translate`) | `SherpaOnnxSttEngine.kt:130` |
| `tailPaddings` | `1000` | `SherpaOnnxSttEngine.kt:131,174` |
| `featureDim` | `80`, sample rate `16000 Hz` | `SherpaOnnxSttEngine.kt:121-123,171-173` |
| PCM scaling | `Short / 32768.0f` | `SherpaOnnxSttEngine.kt:65` |
| Decoder result used | `result.text.trim()`; `result.lang` only logged; probability **not** exposed | `SherpaOnnxSttEngine.kt:83-94` |
| VAD | 20 ms frames, 300 ms pre-roll, 700 ms hangover, adaptive noise floor; segment handed to STT is `voicedSegmentPcm16` | `AudioProcessor.kt:58-84,90-280` (see call-site confirmation below) |

The Python harness reproduces this configuration exactly where the API allows it, and records any
difference explicitly. It also runs an auto-language reference on the same bytes so that a forced
-language mismatch can be separated from a model-quality failure.

## 2. Toolchain (measured, pinned)

Recorded from the isolated evaluation venv at `C:\dev\Whisly-eval-venv` (outside OneDrive):

| Component | Version | License / source | Purpose |
|---|---|---|---|
| Python | 3.11 (`C:\Users\danse\AppData\Local\Programs\Python\Python311\python.exe`) | PSF | harness runtime |
| `numpy` | see `pip freeze` log below | BSD-3 | audio buffers |
| `torch` | `2.5.1+cpu` | BSD-3 | OpenAI Whisper reference |
| `openai-whisper` | `20240930` | MIT | official same-architecture reference |
| `sherpa-onnx` | `1.13.5` | Apache-2.0 | same engine family as Android AAR |
| `datasets` | `4.8.5` | Apache-2.0 | pinned dataset rows |
| `soundfile`, `librosa`, `jiwer` | see `pip freeze` log | see package metadata | decode / CER-WER |

Exact `pip freeze` output (captured to
`C:\Users\danse\AppData\Local\Temp\opencode\whisly_eval_pip_freeze.txt`):

```
aiohappyeyeballs==2.7.1
aiohttp==3.14.5
aiosignal==1.4.0
anyio==4.16.0
attrs==26.1.0
audioread==3.1.0
certifi==2026.11.20
cffi==2.1.1
charset-normalizer==3.5.3
click==8.5.1
cloudpickle==3.1.3
colorama==0.4.6
datasets==4.8.5
decorator==5.3.2
dill==0.4.1
filelock==4.2.4
frozenlist==1.8.0
fsspec==2026.2.0
h11==0.16.0
hf-xet==1.8.0
httpcore==1.0.9
httpx==0.28.1
huggingface_hub==1.33.0
idna==3.20
Jinja2==3.1.6
jiwer==3.0.5
joblib==1.6.0
lazy_loader==0.6
librosa==0.10.2
llvmlite==0.50.0
MarkupSafe==3.0.3
more-itertools==11.2.1
mpmath==1.3.0
msgpack==1.2.4
multidict==7.2.1
narwhals==2.29.0
networkx==3.6.1
numba==0.68.1
numpy==2.4.6
openai-whisper==20240930
packaging==26.3
pandas==3.1.1
platformdirs==4.14.8
pooch==1.9.0
propcache==0.5.4
pyarrow==26.0.1
pycparser==3.12
python-dateutil==4.0.0
PyYAML==6.0.4
RapidFuzz==3.14.6
regex==2026.11.19
requests==2.34.2
scikit-learn==1.9.1
scipy==1.17.1
sherpa-onnx==1.13.5
sherpa-onnx-core==1.13.5
soundfile==0.12.1
soxr==1.1.0
sympy==1.13.1
threadpoolctl==3.8.0
tiktoken==0.14.0
torch==2.5.1+cpu
tqdm==4.70.1
typing_extensions==4.16.0
tzdata==2026.6
urllib3==2.8.0
xxhash==4.1.0
yarl==1.25.1
```

Why `numpy 2.4.6` despite pinning `1.26.4`: the first install command used the PyTorch
CPU index and installed `numpy 1.26.4`; the second command (`openai-whisper ...`) then upgraded it
to `2.4.6` as a transitive requirement. This is recorded rather than hidden; the pinned versions
that matter for the comparison (`torch 2.5.1+cpu`, `openai-whisper 20240930`, `sherpa-onnx 1.13.5`)
are unchanged.

## 3. Datasets and provenance (measured)

| Source | Pin | License | Languages supplied | Status |
|---|---|---|---|---|
| `google/fleurs` | revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd` | `cc-by-4.0` | `en_us`, `hi_in`, `bn_in`, `ta_in`, `te_in`, `kn_in`, `ml_in`, `mr_in`, `gu_in`, `pa_in`, `ur_pk`, `or_in`, `as_in` | config presence verified; no `sat_in` config |
| `ai4bharat/IndicVoices` | revision `c96f9088f138cf89d419da7e8e643e1f05c00a87` | `cc-by-4.0` | `santali` (`valid`: 2,619 examples) | config presence verified; gated=`auto`, access **PENDING** |

Santali is not available in FLEURS, so `sat` was to use IndicVoices `santali/valid`. FLEURS is used
for the other 13 languages so that one read-speech protocol applies across them. No synthetic or
hand-written clip is used as speech-quality evidence.

### 3.1 Santali access block — three attempts, then stopped

All three attempts made earlier in this session hit the same access gate. No fourth attempt was
made, in line with the three-attempt rule.

1. Hugging Face datasets-server rows endpoint for `ai4bharat/IndicVoices`, `santali/valid`:
   HTTP `401`.
2. Direct resolved parquet URL for the same dataset/config: HTTP `401`.
3. `datasets.load_dataset("ai4bharat/IndicVoices", "santali", split="valid")`:
   `DatasetNotFoundError: Dataset 'ai4bharat/IndicVoices' is a gated dataset on the Hub. You must be authenticated to access it.`

`HF_TOKEN` is not set in this environment and the dataset access request has not been granted.
Therefore:

- Santali STT quality, garbage rate, VAD false-accept rate and TTS voice availability are
  **UNMEASURED**, not "failed" and not "passed".
- The ungated alternative `XKaab/ASR-santali_100hrs` has **no license** in its README or metadata;
  it is not used. Using it would require explicit approval.
- Current Santali registry entries (`canListen=true`, `canIdentify=true` in
  `CapabilityRegistry.kt:351-369`) are unverified and must not be shipped as ready.

**Question only you can answer:** provide an `HF_TOKEN` with accepted access to
`ai4bharat/IndicVoices` (`santali/valid`, CC BY 4.0), or approve a different license-clean Santali
speech source. Until then Santali remains `UNMEASURED` and speech `UNAVAILABLE`, while typed text
and typed translation remain available wherever ML Kit supports the language.

## 4. Clip selection protocol

- 10 clips per language, first 10 `.wav` members of the pinned `dev.tar.gz` whose basename is in
  `data/<config>/dev.tsv`; archive members and TSV rows are read in their stored order, with no
  shuffling and no cherry-picking after seeing outputs. Santali is the only exception and remains
  blocked (§3).
- Every decoded WAV is converted to mono 16 kHz PCM16 with linear interpolation. SHA-256 of each
  WAV and of the VAD segment PCM is written to the manifest and per-clip result record.
- Reference text is the dataset `transcript` field verbatim (no normalization before scoring). The
  `raw_transcript` field is retained in the manifest for inspection.
- Raw outputs are pasted beside the reference for every clip, not summarized away.

Exact `fetch_fleurs.py` stdout (log
`C:\Users\danse\AppData\Local\Temp\opencode\whisly_fetch_fleurs.txt`):

```
en_us: tsv_rows=394 indexed=394
en_us: clips=10 scanned=10
hi_in: tsv_rows=239 indexed=239
hi_in: clips=10 scanned=10
bn_in: tsv_rows=402 indexed=402
bn_in: clips=10 scanned=10
ta_in: tsv_rows=377 indexed=377
ta_in: clips=10 scanned=10
te_in: tsv_rows=311 indexed=311
te_in: clips=10 scanned=10
kn_in: tsv_rows=368 indexed=368
kn_in: clips=10 scanned=10
ml_in: tsv_rows=418 indexed=418
ml_in: clips=10 scanned=10
mr_in: tsv_rows=443 indexed=443
mr_in: clips=10 scanned=10
gu_in: tsv_rows=432 indexed=432
gu_in: clips=10 scanned=10
pa_in: tsv_rows=251 indexed=251
pa_in: clips=10 scanned=10
ur_pk: tsv_rows=267 indexed=267
ur_pk: clips=10 scanned=10
or_in: tsv_rows=392 indexed=392
or_in: clips=10 scanned=10
as_in: tsv_rows=418 indexed=418
as_in: clips=10 scanned=10
MANIFEST C:\dev\Whisly-eval\fleurs_manifest.json clips=130
```

## 5. Measurements — COMPLETE (130 measured clips + 40 adversarial cases)

### 5.1 Per-language raw outputs (10 clips × 14 languages)

Ten pinned FLEURS dev clips per language were decoded by six configurations. Every cell below is
the engine's own text, pasted verbatim beside the dataset reference. No cleanup was applied before
recording. Odia has no forced Whisper token, so its forced cells are `[WHISPER_TOKEN_MISSING]`;
Santali remains `UNMEASURED` (§3.1).

**First-run caveat (recorded, not hidden):** this matrix used sherpa-onnx's wrapper defaults
`tail_paddings=-1` and `language="en"` when no language was passed. The column labelled
`int8_auto_vad` is therefore *forced English*, not auto-detection. It also used a 320-sample ZCR
denominator instead of Kotlin's 319 and called the decoder once on a partial segment that the app
would have VAD-rejected (`en_us-10`). The exact Kotlin-matching re-run follows immediately after
and is the authoritative app-path evidence.

Copies of this raw report and the JSONL it was generated from are in
`deliverables/diagnosis9/diagnosis9_report.md` and `diagnosis9_results.jsonl`.
# DIAGNOSIS9 raw measurement matrix

Generated by `C:\dev\Whisly-eval\run_diagnosis.py` from pinned FLEURS dev clips.
Every cell below is the engine's own text; nothing was cleaned or rewritten.

## en (en_us)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | When you call someone who is thousands of miles away, you are using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. |
| 02 | Now widely available throughout the archipelago, Javanese cuisine features an array of simply seasoned dishes, the predominant flavorings the Javanese favor being peanuts, chillies, sugar (especially Javanese coconut sugar) and various aromatic spices. | Now widely available throughout the Archaeopalago, Japanese cuisine, features, and array of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chilies, sugar, especially Japanese coconut. | Now widely available throughout the Archaeopelago, Japanese cuisine features an array of simply seasoned dishes, the predominant flavorings, the Japanese favorite being peanuts, chilies, sugar, especially Japanese coconut sugar and various aromatic spices. | Now widely available throughout the Archaeopalago, Japanese cuisine, features, and array of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chilies, sugar, especially Japanese coconut. | Now widely available throughout the Archaeopalago, Japanese cuisine, features, and array of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chilies, sugar, especially Japanese coconut. | Now widely available throughout the Archaeopalago, Japanese cuisine, features in a ray of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chili, sugar, especially Japanese coconut sugar, and various aromatic spices. | Now widely available throughout the Archaeopalago, Japanese cuisine, features in a ray of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chili, sugar, especially Japanese coconut sugar, and various aromatic spices. |
| 03 | The U.N. also hopes to finalize a fund to help countries affected by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affect it by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affected by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affect it by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affect it by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affected by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affected by global warming to cope with the impacts. |
| 04 | Then, Lakkha Singh took the lead in singing the bhajans. | [VAD_NO_SEGMENT] | Then lock the things to delete and sing the "passions" | [VAD_NO_SEGMENT] | [VAD_NO_SEGMENT] | Then lock the sink to the lid and sink in the mushrooms. | Then lock the sink to the lid and sink in the mushrooms. |
| 05 | The major religion in Moldova is Orthodox Christian. | [VAD_NO_SEGMENT] | The major religion in Wildova is Orthodox Christian. | [VAD_NO_SEGMENT] | [VAD_NO_SEGMENT] | The major religion in Wildova is Orthodox Christian. | The major religion in Wildova is Orthodox Christian. |
| 06 | The East African Islands are in the Indian Ocean off the eastern coast of Africa. | [VAD_NO_SEGMENT] | The East African Islands are in the Indian Ocean off the eastern coast of Africa. | [VAD_NO_SEGMENT] | [VAD_NO_SEGMENT] | The East African Islands are in the Indian Ocean off the eastern coast of Africa. | The East African Islands are in the Indian Ocean off the eastern coast of Africa. |
| 07 | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. |
| 08 | The Tibetan Buddhism is based on the teachings of Buddha, but were extended by the mahayana path of love and by a lot of techniques from Indian Yoga. | [VAD_NO_SEGMENT] | But to begin, but Buddhism is based on the teachings of Buddhism, but we're extended by the Mayana Path of Love and by a lot of techniques from Indian Yoga. | [VAD_NO_SEGMENT] | [VAD_NO_SEGMENT] | The Tibetan bud Buddhism is based on the teachings of Buddha but were extended by the Mayana, path of love and by a lot of techniques from Indian yoga. | The Tibetan bud Buddhism is based on the teachings of Buddha but were extended by the Mayana, path of love and by a lot of techniques from Indian yoga. |
| 09 | Pronunciation is relatively easy in Italian since most words are pronounced exactly how they are written | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. | So now Seation is relatively easy in Italian since most wars are pronounced exactly how they are written. | So now Seation is relatively easy in Italian since most wars are pronounced exactly how they are written. |
| 10 | In this dynamic transport shuttle everyone is somehow connected with, and supporting, a transport system based on private cars. | in this dynamic trans-b | In this dynamic transport shuttle, everyone is sure connected with and supporting a transport system based on private cars. | in this dynamic trans-b | in this dynamic trans-b | In this dynamic transport shuttle, everyone is shown connected with and supporting a transport system based on private cars. | In this dynamic transport shuttle, everyone is shown connected with and supporting a transport system based on private cars. |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.2088 | 0.5 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:4 |
| int8_forced_full | 0.0995 | 0.5 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| fp32_forced_vad | 0.2088 | 0.5 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:4 |
| int8_auto_vad | 0.2088 | 0.5 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:4 |
| openai_forced | 0.0869 | 0.5 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_auto | 0.0869 | 0.5 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |

## hi (hi_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | मेजरकेन खाना, भूमध्य सागर में समान क्षेत्रों की तरह, रोटी, सब्जियों और मांस (विशेष रूप से सूअर का मांस) पर आधारित है, और जैतून के तेल का उपयोग करता है। | Major kinkanam pohumadheesagar mein zaman shatrum, guitar roti, subjunior and mass, Vishesh Roop se sur kamas par adharyath hai. And jatun ke tel kao pi okreta hai. | Major Kinkan Amp Humadyasagar mein Saman Shaitroom ki Tarar roti, Subjiya & Mass Vishayesh Roop se sur kamas par Adarith. And jeetoon ke tel kao piop kar tae hai. | Major kinkanam pohumadheesagar mein zaman shatrum, guitar roti, subjunior and mass, Vishesh Roop se sur kamas par adharyath hai. And jatun ke tel kao pi okreta hai. | Major's house is in the middle of the city and the city of Roti and the city of Vishesh Roop has been coming from the south of the city. And when you open your mouth, you open your mouth. | Mirror Kyle Kahnam Pooma Designer Mr Sister Roti Sabg GPU & Master BA S beraber Sharia stepses became Range 15 when the somebody started selling Sharia romping. Cody Oak Karththan해 | Major kemo na maundhyanging humiar niṬ général ni samachi, ekthyram titta routi subjiya whichśesh robes spenehmer kamaas par azaretena. And jatun ki tuy Hulk aparte teel kabyokha Draw. |
| 02 | वैज्ञानिकों का मानना है कि ओसेलोट्स गंध के द्वारा जानवरों का पीछा करते और खाते (शिकार) हैं, यह सूँघते हुए कि वे ज़मीन पर कहाँ रहे होंगे। | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | I don't know what the people say about this place are the people who live in the mountains and mountains. | sondern ,ی भीला भीखा हैं है ךरें integ kana ,甘 Newton, لا यॉए टीला कारों सकरreto проис요 Challenger  blamed इरों का करासई यहत्रे लर्js | и iln chanceto metoage v Reverend ji nawings for of G yeast alka Asian |
| 03 | आर्मंड वर्सास ने कहा, मैंने अपनी बहन और उसकी दोस्त को खो दिया, और रास्ते में व्हीलचेयर में दो दिव्यांग लोग थे, और लोग उनपर उछल रहे थे और उन्हें धक्का दे रहे थे”। | And I said, "I am not a person and a person who has been a child and I am a whole child and I am a child." And people were very happy and they were very happy. | I am the person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am | And I said, "I am not a person and a person who has been a child and I am a whole child and I am a child." And people were very happy and they were very happy. | And I said to myself and my friends and my friends and my friends and people and people and people and people and people and they were not. | Armd varsas ne kaa, me ne appnim bahan ar un ki dos ko kodya. And raste me huul jhad me, 2-3 byang lukte, And luk un par uchal rahete, and u ne dhakkad hai rahete. | Armd varsas ne kaa, me ne appnim bahan ar un ki dos ko kodya. And raste me huul jhad me, 2-3 byang lukte, And luk un par uchal rahete, and u ne dhakkad hai rahete. |
| 04 | कुतूहल गाँव में आधे घंटे टहलना समय की हानि नहीं है। | [VAD_NO_SEGMENT] | kutu hallgaume adekantik dahalna  savay ki haanim nahi hai | [VAD_NO_SEGMENT] | [VAD_NO_SEGMENT] | Kutuhal gau mein, Aade kandiktahal na, Samayi ki hani nehi hai. | ūsareyu ne Si intelligent. Cut Uphal ga usareyu branch of our intelligent mind . |
| 05 | जब घायलों को लड़ाई बंद होने के बाद अस्पताल ले जाया गया, तो लगभग 40 अन्य बचे हुए कैदी यार्ड में रुके और अपनी कोठरियों में लौटने से इनकार कर दिया. | nd when people say that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know | र ा ा ा ा ा ा ा ा ा ा ा ा ा ा ा ा | nd when people say that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know | When people were talking about this, they said, "I am going to the hospital and I am going to work on this job." | 虽 singers bo nicks sweep | க월 |
| 06 | पूर्व हाउस स्पीकर न्यूट गिंग्रिच, टेक्सास के गर्वनर रिक पैरी, और कांग्रेसवुमन मिशेल बैचमैन ने क्रमश: चौथा, पाँचवाँ और छठा स्थान हासिल किया. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | Pure House speaker newed GreenKitch Taxi's Governor Rick Barry and Congresswoman Michelle Batchman Negramsha Chathapachho and Chathasthan Hashiri. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | Poor house speaker, newed, gringed, taxi's governor Rick Barry and congresswoman, batch man, gramshah, chathya, paachua, and chathya, khashiru. | Pooor House Speaker, Newt, Green Kitch Taxa's governor Rick Barry and Congresswoman Michel Batchman Economic accident carrying officer Jatha Pachow | Pooor House Speaker, Newt, Green Kitch Taxa's governor Rick Barry and Congresswoman Michel Batchman The Ów it is established in Co-except Conference |
| 07 | रॉबिन उथप्पा ने केवल 41 गेंदों में 11 चौकों और 2 छक्कों की मदद से 70 रन बना कर पारी का सर्वोच्च स्कोर बनाया. | ndo chakongi madad sasaran bana kar pari kar servo qi school | Robbenootappan 1-1-1-2-1-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2- | ndo chakongi madad sasaran bana kar pari kar servo qi school | and now we are going to play with the game and we will play with the other players and the other players will be able to play with the other players in the game and we will play with the other players in the game and we will play with | [EMPTY] | [EMPTY] |
| 08 | यदि आप सर्दियों में आर्कटिक या अंटार्कटिक क्षेत्रों में जाते हैं, तो आप ध्रुवीय रात का अनुभव करेंगे, जिसका अर्थ है कि सूरज क्षितिज से ऊपर नहीं उगता है। | now we will start the interview with the other 3rd episode | now we will start the interview with the other 3rd of the interview | now we will start the interview with the other 3rd episode | this is the answer of the answer of the answer. | andrayat k canoei , 하게s combination of Camera sizesilledal smarts | 20 Camsum |
| 09 | अभी भी वे यह पता लगाने की कोशिश कर रहे हैं कि क्रैश कितना बड़ा था और धरती किस तरह से प्रभावित होगी. | nd bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | nd bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | nd bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | Now we are going to find out how much the crash was and how many times we have tried it. | а � Guru Batman . ohl Thay Karoof Sko' wah | ravich kwas chi so Yamash burab aşa |
| 10 | समय के साथ, जैसे-जैसे नई आबादी नए माहौल में ढलती जाती है, वह भी दूसरी आबादी की तरह अनदेखी करना शुरू कर देती है. | 7.JSNN ABDI NAMAHOLMADDAL TYJATI HURBI, DUG SRI ABDI KITARH, UNDICI KURNASHU KARDDI. | 7.JSNN ABDI NAMAHOLMADDAL TYJATI HURBI, DUG SRI ABDI KITARH, UNDICI KURNASHU KARDDI. | 7.JSNN ABDI NAMAHOLMADDAL TYJATI HURBI, DUG SRI ABDI KITARH, UNDICI KURNASHU KARDDI. | We are going to see another person who is going to see another person who is going to see another person who is going to see another person who is going to see another person who is going to see another person who is going to see another person who is going to see another person who is | Sumi ke saj jese jese nahi abadi nahi mahal meddalti jati hai ua bhi dusri abadi kiterahandeki karna shrukar de ti hai | sonraki 7 子associ ni a madh Mehul Magal Temple empezar seria,  wardu troisième southadi Achamzent nee |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 1.0581 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:1 |
| int8_forced_full | 1.1258 | 0.1 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| fp32_forced_vad | 1.0581 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:1 |
| int8_auto_vad | 1.1918 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:1 |
| openai_forced | 0.95 | 0.0384 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:1 |
| openai_auto | 1.0085 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:1 |

## bn (bn_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | বাঁধের উপর 100 ফুট চওড়া একটি জায়গা থেকে জল ছিটকে বেরোচ্ছে। | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | the actual job of the actual job of the actual job of the actual job of the actual job of the actual job of the actual job of the actual job of the actual job of the actual | radiator 4x4-1 mm | Здernapur action hook chor ~! frequent 이것lime似乎動画似乎号 får einder模样 |
| 02 | যদিও অধিকাংশ চিহ্ন শুধুমাত্র কাতালানে নির্দেশ করে কারণ এটি আইন এর মাধ্যমে প্রথম দাপ্তরিক ভাষা হিসাবে প্রতিষ্ঠিত হয়। | 3.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4. | 3.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4. | 3.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4. | I am going to start with the third one. | Jodi Odican Show Cenej Suromatro Karan Etry Eighty Between Daptary K解 | Cabinet for Investigation Report Wearing cold weather Expert the Afghanistan are the ones of the Democrats |
| 03 | স্থানীয় কার্যক্রমের উদাহরণগুলোর মধ্যে শিকার করা, মাছ ধরা, ফটোগ্রাফি করা, পাখি দেখা, এবং পার্কগুলোতে ঘোরা ও বাস্তুতন্ত্র সম্পর্কিত তথ্য নিয়ে পড়াশোনা করা অন্তর্ভুক্ত রয়েছে। | !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! | !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! | !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! | This is the car which is in Udharongulore, Mooda and Shikaal. It is a very beautiful car. Look at this. It is a very beautiful car. Oh, it is very beautiful. It is very beautiful. | Gigabyteometroid | histhanio car Jupromir Udharangulla M female who learnt Indian language and read by Fado glowing wakakede child He went room in a fire nose RT accident guest from the East |
| 04 | নতুন প্রজন্ম প্রাচীন মিশরীয়রদের স্মৃতিস্তম্ভগুলো দেখে অবাক হয়েছিলেন, যা এক হাজার বছরেরও বেশি পুরানো ছিল। | Not only the project is a mission of the 3rd, Istanbul, the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of | Note 1 Projion Mode Projion Missionary Delta 3.Estanbo-Gulow BK-OBAC-Ecylinder 1.2.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0. | Not only the project is a mission of the 3rd, Istanbul, the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of | I will not be able to do this, but I will be able to see the rest of the village of the village of Bishipurano. | Now you will see theSome observed various properties. This means that we need a single transfer ofneath. | NOTU NPPOJAN MOPRACHI MISHORIA DEWR, 3T STANDBOGULO, B.O.A.B.O.A.S.L.N.J.H.A.C.R.B.O.A.B.O.A.B.O.A.R.B.O.A.N.A.N.A.N.N.A.N.A.N.A.N.A.N.ifer, A.N.N.A.N.A.N.A.N.A.N.N.A.N.A.N.A.N.N.N.N.A.N.N.A.N.A.N.N.N.N.A.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N.N |
| 05 | আপনি প্রথম বার বিদেশে গেলে, সেখানকার মানুষকে সম্ভবত ধৈর্যশীল ও সহানুভূতিশীল মনে হবে, কারণ তারা জানে যে নতুন দেশে ভ্রমণকারীদের মানিয়ে নেওয়ার দরকার হয়। | nda mnus kishamababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nda mnus kishamababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab | We have been to the world for the first time in the world. We have been to the world for the first time in the world. We have been to the world for the first time in the world. | imonation Kakka | App leakage of your Society of adoption overseas tag vote at your sources. Shake andжardit sacred temple for September víde delta 3000 India's soul, Sanganubu technology and Sanganubu technology become a smart village itself. Cinema and O' breeze is such a global world. Then defeat government of pagkali Имagleri straight to free the world without breaking the world. |
| 06 | গবেষকদের মতামত অনুযায়ী, এটি একটি অল্প বয়সি ডায়নোসরের লেজ হলেও, নমুনায় ছোট পাখি নয় বরং প্রাপ্তবয়স্কদের পালকই দেখা গিয়েছে। | Gaby Shaktir Motamaad Unujai, AD-1.0.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 | Gaby Shock 3-Motamak 1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1. | Gaby Shaktir Motamaad Unujai, AD-1.0.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 | Gaby Shakti, Motamak, Udujai, AD-1, all the boys and the dinosaurs are late. No one is a little bit late. But you are the boys and the boys. | Gabbishaktair, Motamach Unujaer Yejaktaye Alpubarchi dinosararelejohALL步 | Ghabishakterも tumatunji Edict golpebergi dinothera Ltro Neunaiete bomber On teraf syrup thoai |
| 07 | আপনি বুঝেছেন কিনা জানি না তবে মধ্য আমেরিকা থেকে এই দেশে বেশিরভাগ পণ্য শুল্কমুক্ত ভাবে এসেছে। | ndo iibe shiir ba kono shun kono shun kono shun kono shiir ba kono shun kono shiir ba kono shun kono shiir ba kono shiir ba | ndo iibe shiir ba kono shun kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono | ndo iibe shiir ba kono shun kono shun kono shun kono shiir ba kono shun kono shiir ba kono shun kono shiir ba kono shiir ba | I don't know what you are saying, so I'm very happy that they say that they don't have any issues with their issues. | Adi sebe se鼻 | aped |
| 08 | বিজ্ঞানীরা মনে করেন যে ওসেলোটগুলি অনুসরণ কর এবং গন্ধ দ্বারা খাওয়া (শিকার) করে, যেখানে তারা মাটিতে চলার সময় ঘ্রাণ নিয়ে প্রাণীদের খুঁজে। | ndo da da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da | ndo da da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da | ndo da da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da | [speaking in foreign language] | Bikganira muni koranje Oselotkuli, umusolonkor Ebon Gondudara, Khawashikar Ebon Jekhaneta, Matipe, Chalor, Shuma, Grani, Eplanidirkuje | Bikganira muni koranje Oselotkuli, umusolonkor Ebon Gondudara, Khawashikar Ebon Jekhaneta, Matipe, Chalor, Shuma, Grani, Eplanidirkuje |
| 09 | জাতিসংঘ বৈশ্বিক উষ্ণতা দ্বারা আক্রান্ত দেশগুলোকে এর প্রভাব মোকাবেলা করতে সাহায্য করার জন্য তহবিল চূড়ান্ত করার ব্যাপারেও আশা করে । | ndoooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo | ndo dhjati shang boshi kusnu tada rapranto dej gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dh | ndoooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo | I will start the discussion with the people who are watching the movie. | Jati Shanko Boshi Kusno Tadara Aplanto Dej Gulake Erpugha Mukavela Kote Shahaja Kaurajun Nuttahabil Churanto Karar Bepare O Asha Kauri | Jati Shanko Boshi Kusno Tadara Aplanto Dej Gulake Erpugha Mukavela Kote Shahaja Kaurajun Nuttahabil Churanto Karar Bepare O Asha Kauri |
| 10 | বিজ্ঞানীরা জানাচ্ছেন সংঘর্ষের ফলে সৃষ্ট বিস্ফোরণ ছিল বিপুল। | We will be in the next few days. | We will be in the next few days. | We will be in the next few days. | We will be in the next generation of people. | Bigadira Zanacheng singochian-whole sister Beast One drum bipolar old people | we journey long we journey you will learn shining boldenس industryíp vispev Rent must learn |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 1.2643 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:0 |
| int8_forced_full | 1.0858 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:5, EMPTY:0 |
| fp32_forced_vad | 1.2643 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:0 |
| int8_auto_vad | 1.1609 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| openai_forced | 0.9851 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_auto | 1.2761 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |

## ta (ta_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | இந்த விதிகள் திருத்தப்படுவதற்கு முன்னர் அனைத்து மாநிலங்களின் ஒருமித்த ஒப்புதலையும் கோரியது மற்றும் பெரும்பாலும் அவர்களின் பிரதிநிதிகள் அங்கு இல்லாததால் மாநில அரசு மத்திய அரசை மிகவும் சாதாரணமாக எடுத்துக் கொண்டது. | இந்த விதிருத்தைப் போகுத்துக்கும் திருத்துக்கும் திருத்துக்கும் திருத்துக்கும் திருந்தும் வர | இந்த விதிருத்தைப் போகுத்துக்கும் அனைத்தும் மனில்லைகள் ஒரும் இது உப்புதலைக் கோரியது மற்றும் பெரு | இந்த விதிருத்தைப் போகுத்துக்கும் திருத்துக்கும் திருத்துக்கும் திருத்துக்கும் திருந்தும் வர | In this video, we will talk about the problem of the problem of our religion. | இந்த வ founderைத்து துரத்தவு தரைத்தாள் மந்த 13being ஒரு மிதவு புதACE கொளியது. மக்ள் உங்களைச்காக ஒரு மிதவுவை மவர்களின் பரதினி, அது அங்களை, இழத்தாளம் மானில் நல் பார்த்தில設習، மத்தங்களை சாத்தாரமத்து துரைத்து கொடுது தேன். | இப்போது இந்த வுதிரத்திருத்தான் போவர் திருத்துர்சம் முண்டார் அவளும் அனுதுacyjக因engage ஒமும்துை கோசிheavyபியில் வெலுக்கும் syntheticவு அறுடியருடிகள் சொந்தமாரிறி. |
| 02 | "கருத்து கேட்டபோது, ""விசாரணையின் போது மைக் அதிகளவு பேசுகிறார்.. நான் விசாரணைக்காக தயாராகி கொண்டிருந்ததால் அவர் கூறியதை நான் கேட்கவில்லை"", என்று மில்லர் கூறினார். " | கருத்துக்கேட்ட போகு, விசாரும் என்போது மைக்காதிக்கால் பேசிக்கிறார். நான் வ | கருத்துக்குடைய போகு, மைக்கும் காதில்லையால் பேசிக்குரார். நான் முதல் என்னைக் | கருத்துக்கேட்ட போகு, விசாரும் என்போது மைக்காதிக்கால் பேசிக்கிறார். நான் வ | I don't know if I can see the difference between the two sides of the body and the body. I don't know if I can see the difference between the two sides of the body and the body. | கருத்துக் கேட்டப் போது விசாரணையின் போது மைக்காதிக் காதில்லாவு பேசிக்கிறார். நான் விசாரணை காகத்தாயாராகிறார்கள் இருந்த segunda-லாவிர்க்கிறார். | Karet kitabode Tea Cheese husband, |
| 03 | பூச்சிகளே காற்றில் பறந்த முதல் விலங்குகள். அவற்றின் பறக்கும் திறன்  பகைவர்களிடமிருந்து எளிதாகத் தப்பவும் உணவு மற்றும் இணைகளை திறமையாகக் கண்டறியவும் உதவுகிறது. | போசுகலைக் காட்டி, பார்ந்து முதல் வில்லாம் ஒரு அவர் திரும் பார்க்கும் பிரும் பார் | போசிக்குளைக் காட்டில் பார்ந்து முதல் வில் வில் வில் வில் வில் வில் வில் வில் வில் வ | போசுகலைக் காட்டி, பார்ந்து முதல் வில்லாம் ஒரு அவர் திரும் பார்க்கும் பிரும் பார் | We have been working for the past four years. | போச் 처음கிறார். தைத்த்தில் அந்த முதல் விலங்கு தாவுடு whose distance is going over? பிராக்கும் கொண்டுவிடும்ரார்கள். | Plg KART�를 DAVIDS Within KART multifunction After 2 minutes |
| 04 | சுற்றுச்சூழலுடன் நாம் மிகவும் இணக்கமாக வாழத் தொடங்கலாம், சுற்றுச்சூழல் இயக்கத்தில் நாம் சேரலாம், எதிர்கால பாதிப்புகளை ஓரளவுக்குக் குறைப்பதற்காக நம்மால் செயல்பாட்டாளர்களாகச் செயல்படமுடியும். | சுற்றுவிட்டுக்கும் நம்மிக்குவும் இருக்கமாக வாலாத்துக்கொண்டும் நம் ச | இது விலையும் வாலையும் வாலையும் வாலையும் வாலையும் வாலையும் வாலையும் வாலைய | சுற்றுவிட்டுக்கும் நம்மிக்குவும் இருக்கமாக வாலாத்துக்கொண்டும் நம் ச | I'm not going to say anything. | சுத்துஷு laws் தழந்தான் அம்மாக ஆனார் புடையும் எய்வளேது என்னாம் வைத்துவிட்டெ பல் இழு உழென்றுப் பதிற்காக knowledgeable ரவரி ஆட்டாவது செய்வள폰 இல்லாளடத்தானே செய் apostாக செய்வளண முடியம் | சனதது வழகாவாதை அனஜம் பதை நான் சிரிலாம்idéeர்வாவாகத்திப் புகல்கலை இப் பதைப்புகிறாயும் இப்படி காரோத்து seaைப் பார் யவல கேர்புடியும். |
| 05 | பல மாவட்டங்கள் சிறிய, வசதியான பலமான,  ஜப்பானிய கோஸ்டார் பேருந்துகளால் சேவை செய்யப்படுகின்றன. | வருமாட்டுங்கள் செய்துவில் வருமானே, ன் பான் என்று கோ | பலமாவுடன் செய்யவில் வருமானே, ன் பார்னியே, கோச்டார் ப | வருமாட்டுங்கள் செய்துவில் வருமானே, ன் பான் என்று கோ | I will start the series and say this. | பலமாவட்டங்கு சிரியே வசதியானே வலமானே, ஏப்பானேனே, கோச்டார் பிருந்துகளால் செய்வி செய்வி செய்வியும் படிக்கிறேன் என்று என்று நன்று நன்று? | பலமாவட்டங்கு சிரியே வசதியானே வலமானே, ஏப்பானேனே, கோச்டார் பிருந்துகளால் செய்வி செய்வி செய்வியும் படிக்கிறேன் என்று என்று நன்று நன்று? |
| 06 | கிழக்கு ஆப்பிரிக்கத் தீவுகள் இந்தியப் பெருங்கடலில் ஆப்பிரிக்காவின் கிழக்குக் கரையோரமாக உள்ளது. | அப்படிக்கத் திவு வலிந்து என்று எல்லாப் பிருக | அப்படிக்கத் திவு வலிந்து என்று எல்லாப் பிருக | அப்படிக்கத் திவு வலிந்து என்று எல்லாப் பிருக | But the Africa people have not been able to come to Africa for a long time. | கேட்டுக்கு அப்படி கத்திவு வழிந்தியைப் பிருங்கள் அல்லவா? அப்படி காமின் கேட்குக்குக் கரையாமாக ஒள்ளது. | Buon Что-là models mi trofilà la |
| 07 | புதிய ராஜ்ஜியத்தின் புராதன எகிப்தியர்கள், தங்களின் முன்னோர்கள் கட்டிய நினைவுச் சின்னங்களைக் கண்டு வியந்தனர், அப்போதே அவை ஆயிரம் வருடங்களுக்கு மேல் பழமையானவை. | புதிராத்து என்னுடைய புராத்தான் என்று தங்குரிம் தெரியம் தெர | புதிராத்து என்னுடைய புராத்தானேன் என்று தங்குரின்னும் என்று தன | புதிராத்து என்னுடைய புராத்தான் என்று தங்குரிம் தெரியம் தெர | I will be back with the next few days. | பதுவரங்கள் இருஸயன்புவர்ிபடாத தங்கரி மனும்பருந்து mégகைத்துgmentு ஏக்கனடு பி濟adhாதால sides. விரும்பதும்ங்களுக்கும் சொல்துகொள்ள வேலைப் புடையும் இருக்கும் சொல்ல வி boldுங்கள் கண்டைகளிந்த ஏலைய சோது. | புதியோ ரக்குதின் அப்பு ஷயுமா இருக்கும் இங்கு எப்பொழுது இருக்குடன் அம்பொழுது, உதியிலை கத்தின்று என்றை போய் அழème விரடுங்கள்க மிழுக்கும். |
| 08 | தொடக்கத்தில், கிழக்கின் பைசண்ட்டைன் கலாச்சாரத்தால் ஆடைகளில் பெரிய தாக்கம் ஏற்பட்டது. | தொடுக்கு தில்கின் பயசந்தான் கலாத்தால் ஆனைகளைப் பிரியதாக் கமிர்பிர்ப்பத்து. தொடுக்கு தில்கின் பயசந்தான் க | திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்க | தொடுக்கு தில்கின் பயசந்தான் கலாத்தால் ஆனைகளைப் பிரியதாக் கமிர்பிர்ப்பத்து. தொடுக்கு தில்கின் பயசந்தான் க | then select the 'Pyzen' icon. | திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். | திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். |
| 09 | உண்மையான மக்கள் தொகை எப்பொழுதும் மாறப்போவதில்லை முந்தைய காலத்தை போன்று அவர்களுக்கு இன்று தழுவல்கள் தேவைபடுகின்றது. | உன்னை எனக்கு மக்கல் தோவையையைப்பு மாற்போதுலை முந்த | உன்னை எனக்கு மக்கல் தவையையைப்புல் மாருப் போகுதலை முந்தை | உன்னை எனக்கு மக்கல் தோவையையைப்பு மாற்போதுலை முந்த | I was born in the same age as the old age of the old age of the old age. | உன் மேன் நமக்கல் தோவையையைப் பொல்லும் மாட்டுப் போகுத்தில் உன்னையைப் பொண்டு அவர்களுக்கு இந்த தளிவுள்வுத்தைவிடிட்டனே? | உன்னை எனக்கு மக்கல் தவையையைப் போக்குத்துள்ளை முந்தையை காலத்தி பொன்று அவரில்லக்கு இந்த தலுவல்லுவில் தயவைப்பிடிக்குடன் என்னே? |
| 10 | பல பொதுவான அமைப்புகள் (உதாரணமாக ஏபிஎஸ் வகை அமைப்புகள்) இந்த இயல்பு விகிதத்திற்கு சமம் அல்லது கிட்டத்தட்ட அருகிலிருப்பது. | இந்த எல்லும் விக்குத்தத்துக்கு சம்மும் அல்லுக்கு எடுத்து தட்டையும் | இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந் | இந்த எல்லும் விக்குத்தத்துக்கு சம்மும் அல்லுக்கு எடுத்து தட்டையும் | So, we are going to do the same thing in the previous video. | பலகத்து வாணமாகே போது வாங்கே அமைப்புக்கொள்ளும் இந்த எல்லுவே விக்கத்துக்கு சம்மும் அல்லது கிட்டத்தாட்டே அடிக்கில் இருக்கிறது பலகத்து | பலகத்து வாணமாகே போது வாங்கே அமைப்புக்கொள்ளும் இந்த எல்லுவே விக்கத்துக்கு சம்மும் அல்லது கிட்டத்தாட்டே அடிக்கில் இருக்கிறது பலகத்து |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.7304 | 1.0 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| int8_forced_full | 0.7428 | 1.0 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:0 |
| fp32_forced_vad | 0.7304 | 1.0 | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| int8_auto_vad | 0.9327 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_forced | 0.7021 | 0.8936 | WRONG_SCRIPT_LATIN:5, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_auto | 0.7966 | 0.6585 | WRONG_SCRIPT_LATIN:5, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |

## te (te_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | చాలా సందర్భాల్లో, విదేశాల్లో ఒక గ్యాప్ ఇయర్ కోర్సులో చేరడం వల్ల మీ స్వంత దేశంలో తిరిగి ఉన్నత విద్యకు వెళ్లే మీ అవకాశాలను మెరుగుపరుచుకోవచ్చు. | ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndr | Ciao! | ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndr | Bye, I'm going to send her by the way guys, I'm going to upload her on the phone and I'm going to upload her on the phone and I'm going to upload her on the phone and I'm going to upload her on the phone and I'm going to upload her on the phone and | Hello Standard Value VM� Vedech down low Baka- Gelapburger Slyo-Ralumwala Mise домаM colours nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | Ciao listeners, Did Peacock &ди 완성! Hiwamara! My name is Hano Mala See you soon My thoughts on this place |
| 02 | అది నాకు అర్థం కాలేదు. ఖచ్చితంగా న్యాయం కాదు. | dzин õ õ õ õ õ õ õ | ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ | dzин õ õ õ õ õ õ õ | I don't know what to do, but I don't know what to do. | aljin at cartang kal踓toqq collaborate them at chatspeaking yai mCA arrangement | Dina kartan, kartan kalau, kalian cinta grous qadu |
| 03 | Point Marion మరియు Fairmont మధ్య సాగిన Buffalo-Pittsburgh రహదారిలో చాలా సవాలుగా ఉండే డ్రైవింగ్ పరిస్థితులు ఉంటాయి, ఇది నిర్మాణుష్యమైన  backwoods భూభాగం గుండా వెళుతుంది. | nd mario 4 nd magya sagna bhafelho pitsper gratharilu caalas avalga on de driving parisitilu guntai nd remanushe maina backwards booba gun gunta veilutun ni | nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 | nd mario 4 nd magya sagna bhafelho pitsper gratharilu caalas avalga on de driving parisitilu guntai nd remanushe maina backwards booba gun gunta veilutun ni | Point Marion, Mario, Fireman, Maggias Agna, Buffalo, Pittsburgh, Grahada, Charlotte, Savaloga, Riving, Paris, this is the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the | isses | ulsive возможность影배 |
| 04 | ఆర్థోడాక్స్  క్రిస్టియన్ మోల్డోవాలో ప్రధాన మతంగా ఉంది. | õõõõõõõõõõõõõõõõõõõõõõõõ | õõõõõõõõõõõõõõõõõõõõõõõõ | õõõõõõõõõõõõõõõõõõõõõõõõ | The auto-dox Christian Mildo will be the one that will be the one that will be the one that will | resultingo ne in krippizi do krippplease reatte neш t | Arthur doks Kristian-Mildo vuarlo Predana maternal carúng ب<\|ur\|> konga wamari |
| 05 | "నేను నా సోదరిని ఇంకా ఆమె స్నేహితుడిని కోల్పోయాను, నా మార్గంలో వీల్‌చైర్‌లలో ఇద్దరు వికలాంగులు ఉన్నారు, అందరు వ్యక్తులు దూకుతూ వారిని నెడుతున్నారు"" అని Armand Versace చెప్పారు." | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | I don't know what to say about this. I don't know how many people have been in this world. I don't know how many people have been in this world. | DANU NA PAI DABLARY ?  peersモデ scary система сам職isa  Engineers 表示ari  nuances  다양한  tycker 示 机 各 各 各 各 各 各 各 各 各 各 三 iors的 我们 我们 我们 我们 我们 我们 我们 我们 sinus 目 telev ا tant面 登字 传pet 歌 兽报系 咗 修正 工作 寒闰eb busy 6 | ніŌ, front of the youngest couple in school experiencing and place the marked description Whenever the family coming or coming around the house, there is a post of emergency leaving the tall room or coming around the house and leaving the parents home has community application and has Department of Arts please be seated |
| 06 | వేల మైళ్ల దూరంలో ఉన్న ఎవరినైనా మీరు కాల్ చేసినప్పుడు, మీరు ఒక శాటిలైట్ ని ఉపయోగిస్తున్నారు. | uğu | uğu | uğu | We are going to the temple of the temple of the temple of the temple of the temple of the temple of the temple of the temple of the temple of the temple of the temple of the temple | mengم  tote  tech imiento cutting 仲良  germs  kost 苏 kehr囒 物 食 唔ice 乲你 人 | Greetings everyone to the place Welcome to my channel Hope all of you are happy |
| 07 | దయచేసి ఈ ప్రదేశాన్ని తగిన విధంగా గౌరవించి, పవిత్రంగా భావించండి. Holocaust లేదా Nazis గురించి వేళాకోళం చేయకండి. | uğucoge kleta nase sgurin chmela column chm | !!!! | uğucoge kleta nase sgurin chmela column chm | So, this is the process of taking a video of the government. We will be back with the prevention of the government. | 3-6ской renowned Thai 3-6 covers 4 stars 3-6 kay Casq | । bury sandy 섬は talk about the dangerous mean didn't run away । Mueller's ।urd voters |
| 08 | కీటకాలు గాలిలోకి తీసుకువెళ్ళే మొదటి జంతువులు. వాటి ఎగిరే సామర్థ్యం శత్రువులను మరింత సులభంగా తప్పించుకోవడానికి, ఆహారం మరియు సహచరులను మరింత సమర్థవంతంగా కనుగొనడంలో సహాయపడింది. | õeeta kaalu garalu kathis kovelle moda igeanthu ulivati õeegre saamadem sithrul on marintusulabanga tappinch kova daanik ki? | õeeta kaalu garalu kathis kovelle moda igeanthu ulivati õeegre saamadem sithrul on marintusulabanga tappinch kova daanik ki? | õeeta kaalu garalu kathis kovelle moda igeanthu ulivati õeegre saamadem sithrul on marintusulabanga tappinch kova daanik ki? | We have to get to know the number of people in the world in the world. | shortenedě483 Meente Sulu Banga TıpinchPatix maker او ڭا | These are the 7th grade students from Karalakth, which is their 4th name Inother speak, the 7th grade students are also very high |
| 09 | ఇది సాధారణ ఆచారంగా మారింది, కానీ ఇనుము బండ్ల యొక్క చెక్క చక్రాలపై మరింత అరుగుదల కలిగించింది. | õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ | õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ | õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ | This is the first time I have seen this, I have seen it for many years. | Hiromenunni Bhaary headlines | Լր  couple لم ִապև:「  Deus  flags﹗  podíaﷺ™事情﷕‥break た﷥‥ LY parle﷥‥ banyak  ᜽ISAﷺ‥ 饧 Administr և Դա earlier ց рублейﷺ 〇  fundamentally﷥‥‥ ֲ Soldier ֲ ˜‥‥ 愈 ։ ৡ Թ‥ Ա  ։ ։ ˋ Ԯ Ի ց ։ � (# ole է Է äm ʻ Ծ ։ Ի և ։ և ։  modo 一 Ի ։ ։ ։ ։ ָ ։ ,ց ։ ։ ։ |
| 10 | ఉపసంస్కృతి యొక్క సభ్యులు తమ సభ్యత్వాన్ని ఒక విభిన్న మరియు ఆధునికత, పద్దతులు, మరియు యాస భాషలో ఉండే సంకేత రూప శైలితో తెలియపరుస్తారు. | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | I will be talking about the subject of the subject of the subject. | � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � � | [EMPTY] |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.9357 | 0.0 | WRONG_SCRIPT_LATIN:6, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| int8_forced_full | 1.0048 | 0.0 | WRONG_SCRIPT_LATIN:4, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| fp32_forced_vad | 0.9357 | 0.0 | WRONG_SCRIPT_LATIN:6, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| int8_auto_vad | 1.2051 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:5, EMPTY:0 |
| openai_forced | 1.277 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| openai_auto | 1.2298 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:1, REPETITION:1, EMPTY:1 |

## kn (kn_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | ಸರಳ ಸುವಾಸನೆ ಭಕ್ಷ್ಯಗಳನ್ನು ಒದಗಿಸುವ ಜಾವಾನೀಸ್ ಪಾಕಪದ್ಧತಿಯನ್ನು ಈಗ ದ್ವೀಪಸಮೂಹದಾದ್ಯಂತ ವ್ಯಾಪಕವಾಗಿ ಬಳಸಲಾಗುತ್ತದೆ. ಜಾವಾನೀಸ್‌ನ ನೆಚ್ಚಿನ ಮಸಾಲೆಗಳು ಕಡಲೆಕಾಯಿ, ಮೆಣಸಿನಕಾಯಿ, ಸಕ್ಕರೆ (ಅದರಲ್ಲೂ ವಿಶೇಷವಾಗಿ ಜಾವಾ ತೆಂಗಿನಕಾಯಿ ಸಕ್ಕರೆ) ಮತ್ತು ವಿವಿಧ ಸುಗಂಧಿತ ಮಸಾಲೆಗಳಿಂದ ಕೂಡಿರುತ್ತದೆ. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | So, let's go back to the next question. | Sarala Swa, Bakshara, Nwada Giswa, Javani, Spaka Padadiyano, Yiga, Dvpa Samu, Dadeyanta, Bapakavagi, Bada Salagutade. Javani isna nitshi na masaale ralu kadale kai, Menasinakai, Sakkarai, Braket nili Adaralu, Vishishivaagi, Javataengi na kai Sakkarai, Mattu, Mavida, Sugandhita masaale randakudade. | Sarala Swa, Bakshara, Nwada Giswa, Javani, Spaka Paddatiya, Yiga, Dvpa Samu, Dadhiyanta, Bapakabaaghi, Bada Salagutari. Javani isna nitshi na masaale rulokadale kaayi, Menasinakaayi, Sakkarai, Braket nili Adaralu, Vishishivaayi, Javatengi na kaayi Sakkarai, Mattu, Mavida, Sugandhita masaale rulinda kudiluttari. |
| 02 | ಸ್ಥಳಕ್ಕೆ ಸಲ್ಲಬೇಕಾದ ಎಲ್ಲ ಘನತೆ, ಸಮಗ್ರತೆ ಮತ್ತು ಗೌರವವನ್ನು ದಯವಿಟ್ಟು ನೀಡಿ. ಹೋಲೋಕಾಸ್ಟ್ ಅಥವಾ  ನಾಜಿಗಳ ಬಗ್ಗೆ ಯಾವುದೇ ಜೋಕ್‌ಗಳನ್ನು ಮಾಡಬೇಡಿ. | nda kha sala be kaada ylga gha rata samagrati matu gaurau wa no daivit ni ni hula cost atta wa nazi ga la baki yau dei jom ka la no mada beidi | alak ke sala be kaada yalla agarate samagrathe matu gaurovanu daivituniri hola cost ata vaanasi galabakhe yawati jom galanu madabidi | nda kha sala be kaada ylga gha rata samagrati matu gaurau wa no daivit ni ni hula cost atta wa nazi ga la baki yau dei jom ka la no mada beidi | If you don't like this, you can't get it. You can't get it. | Stalaq ke salaveqchaada sh��all aade g ;) Tola caste atrav Anaj digr laba kai kh Surprise | Stanleyka salla vekaada six mar cuộc conjugole |
| 03 | ದೇಶದ ಪ್ರಸಿದ್ಧ ಗಾಯಕರು ಭಜನೆ ಅಥವಾ ಭಕ್ತಿಗೀತೆಗಳನ್ನು ಹಾಡಿ ಶ್ರೀ ಶ್ಯಾಮ್ ಅವರ ಪಾದ ಕಮಲಗಳಿಗೆ ಅರ್ಪಿಸಿದರು. | ndeshwara prasit da gaye karo bhajhane atamabhakti ki kigaran nuhadi ṣrijhavara pada kamalagaliki | Today should proceed to guide the project by ndawabhati ndawabhati ndawabhati ndawabhati ndawabhati ndawabhati nd | ndeshwara prasit da gaye karo bhajhane atamabhakti ki kigaran nuhadi ṣrijhavara pada kamalagaliki | [Speaking in English] | durch chudert prasil lugar lead | Today should appreciate the я�� bell çıktı You |
| 04 | ಚಳಿಗಾಲ ಅತ್ಯಂತ ಶೀತವಾಗಿರುತ್ತದೆ : ಉಷ್ಣಾಂಶವು ಹೆಪ್ಪುಗಟ್ಟಿಸುವಕಿಂತಲೂ ಕೆಳಗೆ ಹೋಗದಿದ್ದರೂ, ಗಾಳಿ ಮತ್ತು ತೇವಾಂಶ ಕೂಡಿ ಥರ್ಮಾಮೀಟರ್  ತೋರಿಸುವುದಕ್ಕಿಂತಲೂ ಶೀತವೆನಿಸುತ್ತದೆ. | hyayayayayayayayayayayayayayayayayayayayayayayayayayaya | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyah | hyayayayayayayayayayayayayayayayayayayayayayayayayayaya | We have been working with the government for the last few years. We have been working with the government for the first time in the world. | Bless us Harum kalia Please hope everyone will pass我们 mesquite We wish you enjoyed this tutorial with us blessings and happiness | Shut Below Something In την Pradesh The 7th increase in development and the retirement union. Should it give porting new guarantee where these restrictions in books sector should plants like newly sitzt canes achieve common salt |
| 05 | ಗಾಳಿಯಲ್ಲಿ ಮೊದಲು ಹಾರಿದ ಜೀವಿಗಳೆಂದರೆ ಅವು ಕೀಟಗಳು. ಅವುಗಳ ಹಾರಾಟದ ಸಾಮರ್ಥ್ಯದಿಂದಾಗಿ ಶತ್ರುಗಳಿಂದ ಸುಲಭವಾಗಿ ತಪ್ಪಿಸಿಕೊಳ್ಳಲು ಮತ್ತು ಆಹಾರ ಮತ್ತು ಸಂಗಾತಿಗಳನ್ನು ಹೆಚ್ಚು ಪರಿಣಾಮಕಾರಿಯಾಗಿ ಹುಡುಕಲು ಸಹಾಯ ಮಾಡಿತು. | hhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhār | Galianli, Modano Hari da Jivi galenderu aur ki tevelu aur gala haraat da samarthya da indagi sattru galenderu aur ki tepe sekolalu mattu, ahara matu sanggaat galenderu hai chu pari namakari ayyavdukalu sahaya maditu | hhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhār | In the name of God, we are going to teach you the word of God. | & bath of the lunches Next planted the house with friend Thanks for watching please ask & sharehtru house with friends & commentThe thoughts & suggestions Letter of Mahasatma & also message бл &ometers of here & know Mawar Aqui | genau要 делать一下 Then we will start done First we will put this mixture in aimes Renngo is ready So lets begin |
| 06 | ಕಾಲಾನಂತರದಲ್ಲಿ, ಹೊಸ ತಲೆಮಾರಿನವರು ತಮ್ಮ ಹೊಸ ಪರಿಸರಕ್ಕೆ ಹೊಂದಿಕೊಳ್ಳಲು ಪ್ರಾರಂಭಿಸಿದಾಗ, ಅವರು ಇತರೆ ಸಮುದಾಯವರ ಕಡೆಗೆ ವಿಶ್ವಾಸದಿಂದ ನೋಡುವುದು ತೀರಾ ಕಡಿಮೆ. | alāyam enteradali khosāyat al-amārīna vrutam khosā parisarakwanda kuru loprāyārambhītāga. | ndi kolaananananananananananananananananananananananananananananananananananananananananananananananananananananananananananananananananan | alāyam enteradali khosāyat al-amārīna vrutam khosā parisarakwanda kuru loprāyārambhītāga. | [speaking in foreign language] | [EMPTY] | [EMPTY] |
| 07 | ಅದನ್ನು ಮಂಗಳವಾರ ಧರೆಗೆ ಉರುಳಿಸಲು ನಿರ್ಧರಿಸಲಾಗಿತ್ತು, ಆದರೆ ತುರ್ತು ನ್ಯಾಯಾಲಯದ ತೀರ್ಪಿನ ಬಳಿಕ ಅದು ಹಾಗೆಯೇ ಬಿಡಲಾಗಿದೆ. | nda namamala varadharae ge aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nda namamala varadharae ge aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur | I am going to tell you about the music of the music. | NAMAMAMAMAMAM��면叫 tipping | A.D.A.S.A.D. A.D.A.D. A.D.A.D. A.D. A.D. |
| 08 | ಗಾಯಾಳುಗಳನ್ನು ಆಸ್ಪತ್ರೆಗೆ ಸಾಗಿಸಿದ ನಂತರ ಹೋರಾಟ ನಿಲ್ಲಿಸಿದಾಗ, ಉಳಿದ 40 ಕೈದಿಗಳು ಹೊಲದಲ್ಲಿಯೇ ಉಳಿದು ತಮ್ಮ ಕಿರುಕೋಣೆಗಳಿಗೆ ಮರಳಲು ನಿರಾಕರಿಸಿದರು. | ndo aaspatre ke saagis ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily | ndo aaspatre ke saagis ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily | ndo aaspatre ke saagis ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily | I would like to ask you to answer the question. | 히isode 11Birth Diary | yarhu gradna ms 43 saashion siv 편antara harata nilisidaga uli Dunalvat to kaido gerna haradali yeet uli distantam ma surkur updatesgali immera loni rakha di shidaro |
| 09 | ವೈಜ್ಞಾನಿಕ ವಿಧಾನದ ಮೂಲಕ ಜಗತ್ತು ಹೇಗೆ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತದೆ ಎಂಬುದನ್ನು ಕಂಡುಹಿಡಿಯುವುದು ವಿಜ್ಞಾನದ ಮುಖ್ಯ ಉದ್ದೇಶವಾಗಿದೆ. ಈ ವಿಧಾನವು ಬಹುತೇಕ ವೈಜ್ಞಾನಿಕ ಸಂಶೋಧನೆಗಳಿಗೆ ಮಾರ್ಗದರ್ಶನ ನೀಡುತ್ತದೆ. | ndo hir yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu | Waigna nika vidhana da mulaka, jagant hui kari nirvaisutta de, embudha nukand hui dhyudu, vigna nada mukya ude shavagide, i vidhana mu, bhahutte ka waigna nika sam chodha nagale, maar ka dashana niruttade. | ndo hir yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu | The reason why the people who were in the world were the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the world, and the ones who were in the | Waignana nika vidhana da mola ka jagan tuhege ka rianirvaisutta de embuta nukandu hid iyudu vignana da mukya udeshava gide iy vidhana bu bahuthega waignana ka sam chodha negalege maar ka dashana riruttade | Waignana nika vidhana da mola ka jagan tuhege ka rianirvaisutta de embuta nukandu hid iyudu vignana da mukya udeshava gide iy vidhana bu bahuthega waignana ka sam chodha negalege maar ka dashana riruttade |
| 10 | ಈ ಆಕರ್ಷಕ ಹಳ್ಳಿಯಲ್ಲಿ ಅಡ್ಡಾಡಲು ಅರ್ಧ ಗಂಟೆ ಸಮಯ ತೆಗೆದುಕೊಳ್ಳುತ್ತದೆ. | [EMPTY] | ertradi lhoh ertradi lhoh ertradi lhoh ertradi lhoh ertradi | [EMPTY] | This is a very good place for the people to understand the meaning of the word. | lakar sugar hardly ali attardalo ardagante Same Ayaeticety | 여기서 society shops had such a bigger area, similarity with the Turismo vehicles, |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 1.0446 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:1 |
| int8_forced_full | 1.0116 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:5, EMPTY:0 |
| fp32_forced_vad | 1.0446 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:1 |
| int8_auto_vad | 1.113 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |
| openai_forced | 1.0096 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:1 |
| openai_auto | 1.0922 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:1 |

## ml (ml_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | പോയിന്റ് മരിയനും ഫെയർ‌മോണ്ടും തമ്മിലുള്ള ദൂരം ഒറ്റപ്പെട്ട ബാക്ക്‌വുഡ് ഭൂപ്രദേശങ്ങളിലൂടെ കടന്നുപോകുന്ന ബഫല്ലോ-പിറ്റ്സ്ബർഗ് ഹൈവേയിലെ ഏറ്റവും വെല്ലുവിളി നിറഞ്ഞ ഡ്രൈവിംഗ് സാഹചര്യങ്ങൾ സൃഷ്ടിക്കുന്നു. | 4.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 | 4.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5. | 4.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 | Point Marie. | Point Marienum Paramondum Tamilullah Durum Ochta Petta Backwood Boopra Deshingali Lode Kadannupoguna Buffalo Pittsburgh High Vale Eighte boom Belluvil in Iranya driving Sahaja Rengals Risticunum | Point Marienum Paramondum Tamilullah Durum Ochta Petta Backwood Boopra Deshingali Lode Kadannupoguna Buffalo Pittsburgh High Vale Eighte boom Belluvil in Iranya driving Sahaja Rengals Risticunum |
| 02 | ഈ തത്വങ്ങള്‍ അഭിപ്രായപ്പെടുന്നത് ചില നിശ്ചിത ആവശ്യങ്ങള്‍ കൂടാതെ/അല്ലെങ്കില്‍ സ്വപങ്ങളുള്ളവരാണ്‌ അത് ആന്തരികവല്‍ക്കരിക്കുകയും പ്രായപൂര്‍ത്തിയിലേയ്ക്ക് എത്തിച്ചേരുകയും ചെയ്യൂ എന്നാണ്‌. | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahya | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahya | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahya | This is the first question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of the question of | 步icism huritetu song disin | Now i'm going to make the Z prefabmi see this place with soil rinsing and Darце In addition to his pattern The length could be the size on the fabric The cloth should be placedLD Range for the car While i'm cleaning these plants, i can see the color of this spot |
| 03 | മെട്രിക് സമ്പ്രദായത്തിന്റെ ഉപയോഗം, ഏകാധിപത്യത്തിൽ നിന്ന് പ്രജാധിപത്യത്തിലേക്കുള്ള മാറ്റം, ദേശീയത, രാജ്യം ഭരണാധികാരിക്കല്ല മറിച്ച്  ജനങ്ങൾക്ക് അവകാശപ്പെട്ടതാണ് എന്നതുപോലെയുള്ള സാമൂഹികവും രാഷ്ട്രീയവുമായ ധാരാളം പ്രഭാവങ്ങൾ അവിടെ ഉണ്ട്. | METIC Sambrathayetinde uba yoghum. 1.5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5- | Mithyxam brother this day eo game 家 家 家 家 家 家 家 家 家 家 家 家 家 家 家 家 家 | METIC Sambrathayetinde uba yoghum. 1.5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5- | I would like to say that this is a great opportunity for us to be able to live in this country. We are going to be able to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in this country. We are going to live in | METRIC CBDI THING鼓 & adh<\|id\|>i STI carali pedithil 수나�itat Brajathi tents 3rd floor 4th floor SIGH Seben is the kireka US TAD 11 floors Sin Ganes saud Ganes Kalpur 12and Kind P Clint PARD P Laugur PY PY Amt Piyas | 就ivid的 genocide 一屯其她們的目的 grooming 于她們娃娃扎、與她們的面臨 就參加了後悟指入女孩 女孩就 fused 女孩都有治療痢疼痢疼 |
| 04 | പ്രഖ്യാപനത്തെ യുഎസ് പ്രസിഡന്റ് ജോർജ്ജ് ഡബ്ല്യു ബുഷ് സ്വാഗതം ചെയ്തു. | Pracca, benete, USPresident George W Bush Swagdam, cheidoo | Prank ya benete US pressurring George W Bush Swagdam Cheydu | Pracca, benete, USPresident George W Bush Swagdam, cheidoo | [MUSIC] | Prakya Benete, US presserian George W. Bush, Swagadam Cheydu | . . . . . . . . . . |
| 05 | അവയ്ക്ക് മികച്ച രാത്രിക്കാഴ്ചയുള്ളതിനാല്‍ വളരെ നന്നായി കാണാനാകും, കൂടാതെ വളരെപ്പെട്ടെന്ന് നീങ്ങുകയും ചെയ്യും. ഒസെലോട്ടുകള്‍ അവയുടെ ഇരയെ പിടിക്കുന്നത് ചുറ്റുപാടുകളില്‍ അലിഞ്ഞുചേര്‍ന്ന് അവയ്ക്കുമേല്‍ ചാടിവീണാണ്‌. | 2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2 | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | 2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2 | So, what we need to do is to get rid of the pain and pain. | athrikar  golf ndff fought with them from where were we talking to. Now, let us know. Its so important to our people at trinity boat. | estão our children sharing a few important things. We have already talked for many more years. |
| 06 | നമുക്ക് പരിസ്ഥിതിയുമായി കൂടുതൽ സൗഹാർദ്ദപരമായി ജീവിച്ച് തുടങ്ങാനും പരിസ്ഥിതി പ്രസ്ഥാനത്തിൽ ചേരുവാനും കഴിയും, ഒരു പരിധി വരെ ഭാവിയിലെ കഷ്ടപ്പാടുകൾ കുറയ്ക്കുന്നതിനായി പ്രവർത്തിക്കാനും നമുക്ക് സാധിക്കും. | Namuka Parasithi My Coordal Sauhara Paramai Jevi Chituranganam Parasithi Prasithi Prasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Paras | Namuka Paris 3Mai Kourdsal Sauhara Paris 3Mai JV-2 Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3M | Namuka Parasithi My Coordal Sauhara Paramai Jevi Chituranganam Parasithi Prasithi Prasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Paras | I am going to share my community with you. | Namoka parisedew imported eONseWhardáveis Vishweedi parisyadhi prasthaaan thil cheerywanum padhi previous Qurken which several es means | 5. Barsana gives 1 Pacegon. Wearing |
| 07 | വീടിനകത്തുതന്നെ കഴിയാനും എയർകണ്ടീഷണറുകൾ ഓഫ് ചെയ്യാനും പൈപ്പ് വെള്ളം കുടിക്കരുതെന്നും പ്രാദേശിക അധികൃതർ പ്ലാന്റിന് സമീപമുള്ള താമസക്കാർക്ക് മുന്നറിയിപ്പ് നൽകുന്നു. | VDN-Eir Foundation will offer change in 5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4 | We will be in our third NECD, Air Conditioner, All-Fuchioner, 5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4 | VDN-Eir Foundation will offer change in 5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4 | We are going to do the air conditioners of the 5th week. | Veekdina? Meekdina? Öerkundation dovul osse Jayanem Bhai! in P Study Pradesh is obviously bout the pratay | VidnaGhtuthanya Becauseантya Osi Chin�� Han Mm Praa Deshi Whats When He's Dinner I'm Minds your 그래 In The cursed Summa insta |
| 08 | ഭക്ഷിക്കാനുള്ള മൃഗങ്ങളെ (ഇരകളെ) ഓസിലോട്ടുകൾ ഘ്രാണശക്തിയിലൂടെ പിന്തുടർന്ന് കണ്ടെത്തുകയാണെന്ന് ശാസ്ത്രജ്ഞർ കരുതുന്നു,അവ എവിടെയാണ് ഉള്ളതെന്ന് മണത്ത് കണ്ടുപിടിക്കുന്നു. | ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor nd | ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor | ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor nd | In the name of the language, in the name of the language, in the name of the language, in the name of the language. | Bakshi kaanulam ragangale iregale ousil otugal kranashaktilode pindodarna kandetugayanena nd | observe how to cook because of the sound and singing. |
| 09 | വിപുലമായി ഉൽപാദിപ്പിക്കപ്പെടുന്ന സമാന ഉൽപന്നങ്ങളേക്കാൾ നൂതനമാണെങ്കിൽ പോലും, കരകൗശല ഉൽപന്നങ്ങളെ പുരാവസ്തുക്കളായാണ് നിർവ്വചിക്കാറുള്ളത്. | ndi pkppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | ndi pkppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp | I am going to say that I am going to be a good person. I am going to say that I am going to be a good person. I am going to say that I am going to be a good person. | Biburamai, Pulpadi Pickepadna, Samana Pulpennangalei, Kalt, Noodana Manegilam, Noodana Manegil Pollam, Kara Kaushara Pulpennangalei, Puraavastukalai, Narva Jikkarulada. | கனிச் செல்லாமுமே thanூமானில் நானங்கே suctionகிழும்括lish நான் செல்லாமே thanூமானில் நானட்டையுமா? நveck்மும் தோல்சிகத்திரும் பீர்கள். செல்லாமே. இத識க் கேண்டி ஒருவில் 9時 நாளிகுவர் அவன்... |
| 10 | എൻഎസ്എ എന്ന നിലയിൽ, 1978-ലെ ക്യാമ്പ് ഡേവിഡ് അക്കോർഡ്സ്; 1970-കളുടെ അവസാനത്തോടെ യുഎസ്-ചൈന ബന്ധം സാധാരണ നിലയിലാക്കൽ; 1979-ലെ ഇറാൻ ബന്ധി പ്രശ്നത്തിലേക്ക് നയിച്ച ഇറാനിയൻ വിപ്ലവം; 1979-ൽ ഉണ്ടായ അഫ്ഗാനിസ്ഥാനിലെ സോവിയറ്റ് അധിനിവേശം എന്നിവ പോലുള്ള ലോക കാര്യങ്ങൾ നയതന്ത്രപരമായി കൈകാര്യം ചെയ്യുന്നതിന് അദ്ദേഹം കാർട്ടറെ സഹായിച്ചു. | NSEE NELEIL, IRETI THORLIRITY ADVATE 80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80- | NSEE NELEIL, IRETI THORLIRITY ADVATE 80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80- | NSEE NELEIL, IRETI THORLIRITY ADVATE 80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80- | N-S-A-N-E-L-I-R-T-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E-R-T-E | NSA NNLail irethi tola irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethirethi irethi irethi irethi irethi irethi | NSA NNLail irethi tola irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethirethi irethi irethi irethi irethi irethi irendi irendi irendi irerdi irethi irethi  underlying irethi irethi irethi irethi irendi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi irethi |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.9758 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| int8_forced_full | 0.9535 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:5, EMPTY:0 |
| fp32_forced_vad | 0.9758 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| int8_auto_vad | 1.314 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:1, REPETITION:4, EMPTY:0 |
| openai_forced | 1.0 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:1, REPETITION:1, EMPTY:0 |
| openai_auto | 1.16 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |

## mr (mr_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | एखादी संस्था नाविन्यपूर्ण होण्यापूर्वी, त्यातील प्रमुखांनी नाविन्यतेची एक संस्कृती निर्माण केली पाहिजे तसेच ज्ञानाची देवाण-घेवाण आणि संस्थात्मक अध्ययन केले पाहिजे. | y'all just sound start 9.9 per hour per week. | y'all sown start 9-9 per hour ne'a pru y y'all pru y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y | y'all just sound start 9.9 per hour per week. | I think this is the sound start now, we need to improve it. That's the problem, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound, we need to increase the sound | forn calling Hiya cuti sas saun. froniti kiz hu Frankly Mahy Chet kiz huฉri salvat | Ya kali selesą-STAA na Конnyap ruisierenmente, diatil pranicana na W lettie X NCK dan Japan MARK supported |
| 02 | तुम्ही अंधारामध्ये पिरॅमिड्स पाहू शकता आणि शो सुरू होण्यापूर्वी शांतपणे त्यांना न्याहाळू शकता. | nda ramadhepepir emir spauhshakta dani shou suro neapuru shantapun ni tennani haru shakta | nda ramad de peremid spau sakta dani sosur onyapur nda pene tennan ni haru sakta | nda ramadhepepir emir spauhshakta dani shou suro neapuru shantapun ni tennani haru shakta | [speaking in foreign language] | миэndaro Amadae Baba Pushakta Dh Normallyaking Bha everybody and | adorska |
| 03 | वैज्ञानिक असा विचार करतात की ऑसेलोट प्राण्यांना खाण्यासाठी (भक्ष्य म्हणून), ते जमिनीवर कोठे आहेत हे पाहण्यासाठी त्यांचा पाठलाग आणि शोध त्यांच्या वास, गंधाद्वारे करतात. | Vadna ne kasavi charkarath ki, ose load pranayana khane sati baksya manun. Thes zaminyaur kote a heth hei panya sati thensa parla gane heth shoth ten chav, vas gandhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh | Vaidna ni kasavichar karthat ki, ose loot pranayan na khane sati baksya manul. thet zaminyaur kote a heth hei panya sati thetan sapadla gane heth, kote henshyao, was gandhath wa rekath thet. | Vadna ne kasavi charkarath ki, ose load pranayana khane sati baksya manun. Thes zaminyaur kote a heth hei panya sati thensa parla gane heth shoth ten chav, vas gandhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh | So, we will start with the process of the process of the process of the process. This is the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the process of the | ordinate kasa vichor kar tat ki,藡 Lok pra nyan nahal hanya sathi. . Quiz zamin niro kote nahit. . processes pane sathitthanya palaganine,  statistically tshoh t ten chav costly thundha 2. | Waidna ni kasavichar karthad ki, oselot prananna khane sati baksha manuna. Te zaminyaur kutya hate hai pane sati tanta patla gane hai, šot ten chao, was gandhadaare karthad. |
| 04 | एखाद्या संकल्पनेवर आधारित जर सुती घालवायची असेल तर अंतर्गत जल वाहतूक ही उतम आहे. | 1. Sankal Panevar Adharid Zarsuti, Ghalwaiji Asil, Teranthar Gajjal Vahatu, Hiyutam Ahi. | 1. Sankal Panevar Adharid Zarsuti, Ghalwaiji Asil, Teran Terga Jalwahatu, Hiyutam Ahi. | 1. Sankal Panevar Adharid Zarsuti, Ghalwaiji Asil, Teranthar Gajjal Vahatu, Hiyutam Ahi. | This is the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the name of the | 1. Sankal pane var adhari zarsuti, khalwai chiasil, teraanterga jal vahatu hi uttam ahi. | 1. Sankal pane var adhari zarsuti, khalwai chiasil, teraanterga jal vahatu hi uttam ahi. |
| 05 | पॉइंट मॅरिअन आणि फेअरमॉंट मधील क्षेत्र, विलग असणाऱ्या वन्यक्षेत्रामधून वारंवार जाणाऱ्या बफेलो-पिट्सबर्ग हायवेवर आव्हानात्मक परिस्थिती निर्माण करते. | Point Warrior, namely F-E-R-Mode Model, etc. Willagas scenario, W-E-N-X-E-R-Mode, and W-E-R-Mode. W-E-R-Mode, W-E-R-Mode, and W-E-R-Mode. | Point Warrior, namely F-E-R-Mode Model, etc. Willagas scenario, W-E-N-X-E-R-Mode, and W-E-R-Mode. W-E-R-Mode, W-E-R-Mode, and W-E-R-Mode. | Point Warrior, namely F-E-R-Mode Model, etc. Willagas scenario, W-E-N-X-E-R-Mode, and W-E-R-Mode. W-E-R-Mode, W-E-R-Mode, and W-E-R-Mode. | Point Morion is a remote model, which is a very good model for the world. It is a very good model for the world. It is a very good model for the world. It is a very good model for the world. It is a very good model for the world. It is very good for the world. It is very good for the world. | Pipaian coresen 99mden 97mden Ina Kmat | Point Morion, any P.R. Mod, Modil, etc. Willagas, Narayal, Wenakshhe, Wenakshhe, Narayal, Wenakshhe, Wenakshhe, Narayal, Bafel, Pilsberg, Heavyware, Avarnatmokta, Diti Nivankarte. |
| 06 | क्रुगर नॅशनल पार्क (केएनपी) दक्षिण आफ्रिकेच्या उत्तर-पूर्वेला आहे आणि त्याच्या पूर्वेला मोझांबिक, उत्तरेला झिंबाब्वे आणि दक्षिण सीमेला क्रोकोडाईल नदी आहे. | Kruger National Park KMP, Dakshan Africa, Uttar Purvilla. Anintyachap Purvilla, Mujambik Uttarila, Jimbaapoi, Anint, Dakshan Similla, Krugodil Nadia. | Kruger National Park K&P, Dr. Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila | Kruger National Park KMP, Dakshan Africa, Uttar Purvilla. Anintyachap Purvilla, Mujambik Uttarila, Jimbaapoi, Anint, Dakshan Similla, Krugodil Nadia. | The National Park, K&P, is the National Park of the National Park. The National Park is the National Park of the National Park. The National Park is the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park of the National Park | Kruger National Park KMP, Dakshan Africaja Uttarpur Villaayani Tya Chapur Villa, Mojambik Uttar Villa Jimbapayani Dakshan Semiilla Krukodayani Nadiyahi. | Kruger National Park KMP, Dakshan Africaja Uttarpur Villaayani Tya Chapur Villa, Mojambik Uttar Villa Jimbapayani Dakshan Semiilla Krukodayani Nadiyahi. |
| 07 | विज्ञान आता दर्शविते की या प्रचंड कार्बन अर्थव्यवस्थेने गेल्या 2 दशलक्ष वर्षांपासून मानवी उत्क्रांतीला पाठिंबा देणाऱ्या जीवावरणाच्या 1 स्थिर स्थितीत बदल घडवून आणला आहे. | Vidya nata deshwitiki ja praccin ka rabun artavivosti nige da dun dhukshilokshil, voshya pasun manvi utkanti la par timbadi enara, jiva urna ca ekstiristiti buddhul garunanlai. | Within annata dish with the key apprecent of the carbon earth, we will stay in the get a doing introduction. | Vidya nata deshwitiki ja praccin ka rabun artavivosti nige da dun dhukshilokshil, voshya pasun manvi utkanti la par timbadi enara, jiva urna ca ekstiristiti buddhul garunanlai. | [speaking in foreign language] | mins du s | Vid南 expectories о熱 submissions inertia Bien<\|si\|>ато to bare nlin |
| 08 | ऑलिम्पिक पदक विजेता कॉमन वेल्थ खेळामध्ये  100m आणि 200m मुक्त शैली आणि 3 रिले मध्ये पोहणार होता, परंतु त्याच्या तक्रारी मुळे त्याची शारीरिक तंदुरुस्ती शंकास्पद वाटली. | Alambic 5-4-4,000wm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/ | Alambic Padak Vijaytha common wild drama december, 2-3-3-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4- | Alambic 5-4-4,000wm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/ | The column is a common value of 12m and 13m. But we have to get the same value as the standard condition. | Alampik Padak Vijeta Common World Khelemade Shambar Mitarani 2-3 meter Mukta Shelyani 3-4 meter ponarota 1-2 meter ponarota 1-2 meter ponarota 1-2 meter ponarota | [EMPTY] |
| 09 | प्राचीन रोमन जेवणामध्ये अमेरिका किंवा आशिया तून नंतरच्या शतकामध्ये युरोप मध्ये आलेलं अन्न समाविष्ट करणे शक्य नव्हते. | Pratchin Roman J.V.A.M.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A | Pratchin Roman J.V.A.M.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A. | Pratchin Roman J.V.A.M.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A | The reason for this is that the world is in America. The world is in Europe. The world is in Europe. | Prachi in Roman Jona Madhi, America kima Asia, Tune nantearcha shataka Madhi, Europe Madhi, Aalella, Anna, Zamaavishta karne shakkenalte. | Prachi in Roman Jona Madhya, America kima Asia, Tune nantearcha shataka Madhya, Europe Madhya, Aleyla, Anna, Zamaavishta karne shakkenalte. |
| 10 | जेव्हा कार धडकली तेव्हा जरी घरामध्ये 3 लोक होते, त्यापैकी कोणीही जखमी झाले नाही. | では | では, では, では, では, では, では, では, では, では, では, では | では | The people who are not allowed to go to the house, who are not allowed to go to the house. | industri, | جو وہ کارد درک لتواتزری گرام جاتین لوگ خودتے آپ پیکی کو نہیں زکمیز لنہی۔ |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.9767 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| int8_forced_full | 1.0124 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| fp32_forced_vad | 0.9767 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| int8_auto_vad | 1.6973 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:6, EMPTY:0 |
| openai_forced | 0.9704 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |
| openai_auto | 0.9786 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:1, REPETITION:0, EMPTY:1 |

## gu (gu_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | તીબેટનો બૌદ્ધવાદ બુદ્ધનાં ઉપદેશો ઉપર આધારિત છે, પરંતુ તેનો પ્રેમના મહાયાન પંથ દ્વારા અને ભારતીય યોગની ઘણી બધી પ્રયુક્તિઓ દ્વારા વિસ્તાર કરવામાં આવ્યો છે. | 3.5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4- | 3.5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5 | 3.5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4- | We are going to play the game with the 3rd and 5th of June. | Tibet NO BOTWAD BODINAU Danishoparadaritce Perantu Teno Premna Mahayan Pant Dhar anebarti Letina ghaneb Sadi Prayuktiv Adwaravistar Kalaman Waviyoce | Tabit Nub вот Vadbudnaod stow paradarit spray into tiro Premium Mahayan Pandh dwarā. tän collab with Vaatu Yoginiya Gandhi byji |
| 02 | તમે પ્રવાહમાંથી બહાર આવો પછી, પાછા તરવું એ સામાન્ય રીતે હોય તેના કરતાં વધારે મુશ્કેલ નથી. | So, May Prabhmaathi bahar oud 5-5-5-5-5-5-5-5-5-5-5-5-5 | So, May Prabhmaathi bahar oud 5-5-5-5-5-5-5-5-5-5-5-5-5 | So, May Prabhmaathi bahar oud 5-5-5-5-5-5-5-5-5-5-5-5-5 | So, I will try to get the opportunity to get the opportunity to go to the hospital. | Got the مج | So, P Creativeマacter layer is also around 6.5 mAh due to trembling |
| 03 | પુરુષોના સીટીંગ સુપર -જીમાં મેક્સિકોના અર્લી વેલાસ્ક્યુઝે પંદરમાં ક્રમે  પૂર્ણ કર્યું હતું.  પુરુષોના સ્ટેન્ડીંગ સુપર-જીમાં  ન્યૂઝીલેન્ડના આદમ હોલે નવમા ક્રમે પૂર્ણ કર્યું હતું. | Purushona Streetings Supergeema Mexico N' Ali Velas Q. Is a Pandarma Krami Puna Karyu Hathu Purushona Streetings Supergeema New Zealand N' Adam Holendau Krami Puna Karyu Hathu | Purushona Streetings Supergeema Mexico N' Ali Velas Q. Is a Pandarma Krami Puna Karyu Hathu Purushona Streetings Supergeema New Zealand N' Adam Holendau Krami Puna Karyu Hathu | Purushona Streetings Supergeema Mexico N' Ali Velas Q. Is a Pandarma Krami Puna Karyu Hathu Purushona Streetings Supergeema New Zealand N' Adam Holendau Krami Puna Karyu Hathu | For us, we are studying Superjima. We are going to study with the various skills of the Pandarmakrami Pune Kariyu. For us, we are studying Superjima, New Zealand, Adam Holendau, and we are studying Pune Kariyu. | Purushona Streeting Superjima, Mexiko Na Ali, Velast Qoze Panderma Kramepuna Kariyuhatu, Purushona Stending Superjima, Nuzilaina Adam Hole Nauma Kramepuna Kariyuhatu. | Pura-shona-streeting super-gima, Mexiko na Ali, Velast Q. Zepanderma Kramepuna Karyuhatu, Pura-shona-standing super-gima, New Zealand na Adam Hole na Oma Kramepuna Karyuhatu. |
| 04 | આ ઉદ્યાન 19,500 કિલોમીટર વર્ગનું અંતર કાપે છે અને તેનું વિભાજન 14 અલગ અલગ ઇકોઝોનમાં કરવામાં આવ્યું છે, જે દરેક વન્યજીવોને ટેકો આપે છે. | Audio & Ovenessagear 500 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/ | Audio & 5-10 km/h, 4-10 km/h, 4-10 km/h, 4-10 km/h, 4-10 km/h, 5-10 km/h, 5-10 km/h, 5-10 km/h, 5-10 km/h, 5-10 km | Audio & Ovenessagear 500 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/ | Today, we will be talking about 5-10 km of work on the work of the company. We will be talking about the work of the company, and the work of the company will be the first to be the first to be the first to be the first to be the first to be the first to be the first to | Alien  Latino 5 transgender 5-100客 months Spirituality  diam 對 Six chiad pipe 엠 vältz 15-100kmV�� ndir ka ape�리gesch ndir kp tindu況明 ndir kp bha sin Eek personalہ........ ..... | Augד áting organic Azure 500 km nearvak concert Artinge rekape se gan tanu vi bajan Chaoud debak, línea-galak & lonaka noise arva Avyote |
| 05 | કૃપા કરીને સાઇટ સાથે તેની યોગ્યતા મુજબ સંપૂર્ણ ગૌરવ, વિચારપૂર્વકતા અને માનથી વર્તન કરો. કત્લેઆમ અથવા નાઝીઓ વિશે રમૂજ ન કરો. | Krupakarini 177, then yogiyatam, Punjab samboot agarov, which are poor work, and manatee, and kao kao kao kao kao kao kao kao kao kao kao kao kao ka | Krupakarini 177, then yogiyatam, Punjab samboot agarov, which are poor work, and manatee, and kao kao kao kao kao kao kao kao kao kao kao kao kao ka | Krupakarini 177, then yogiyatam, Punjab samboot agarov, which are poor work, and manatee, and kao kao kao kao kao kao kao kao kao kao kao kao kao ka | I hope you guys have enjoyed this video. | Karomidahal illnessataal saati te'no yogiathan mujim sapada gauro vichare purwav caane mada timartan caro Kathleam Athuanaziya fir sirab仲na caro | 分어 8000 agreeör. 4K kerbe chember 2. 3. 4. 4. 8K kerbe . 4 корГ apple gerber 2. |
| 06 | "ટિપ્પણી કરવા કહેવામાં આવ્યુ ત્યરે મિલરે કહ્યું, ""માઇક ને સાંભળવામાં બહુ વાર લાગે છે.. હું તૈયાર થઈ રહ્યો હતો તેથી તે શું કહી રહ્યો હતો તે હું નહોતો સાંભળી રહ્યો.""" | Tipo dikar va kehva ma avyuttiare. Millorek kahyum, Mike ni samharva ma bahu var la vhe, mutayar thayra hohoto. Tehti desu kohirayohoto, tehun auto samhirayoh. | tigpani karva kehbama avyuttiare millare ka hiyu mightni samharva ma bahuwar la vhe hiyu mutayar thayra hiyu hoto titi tisung hiyra yoh hoto tiyung 1-2 sambri rayu | Tipo dikar va kehva ma avyuttiare. Millorek kahyum, Mike ni samharva ma bahu var la vhe, mutayar thayra hohoto. Tehti desu kohirayohoto, tehun auto samhirayoh. | I will tell you what I will do. | 직 punishment karvaki名 avrutiał Mein singkatom mait artistaindir samarvarva ma bahuvar love jey briefly Thiyarak hwat salah yaiti sitthusu, mga ishnu Havinghwat otu doingyutu Same Briramdh nuovo | 对ividad geliyorоду特na giftsiter |
| 07 | "તેની સાપેક્ષ સુલભતા સાથે ""ટિંબકટુ"" નો ઉપયોગ વિદેશી, દૂરની જમીનો માટે રૂપક તરીકે થયો છે. " | 10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10- | 10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10- | 10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10- | I will show you the next 2 minutes. | Tenis Apex soluppy ta satre tim bak tuna Upio Yogi Deshidur ni Jaminomate Rupak tariket Therey Chee | 1080p隨sten sinh Lets see |
| 08 | ફિલિપાઇન્સ જીતવામાં યુ.એસ. સામ્રાજ્યવાદના ખર્ચા ફિલિપિનો લોકોએ પોતે આપ્યા હતા. | Philippine Jthama US Samarajya Vadna, Kharcha Philippine & Lokoi Pote Apiato | Philippine Jthama US Samarajya Vadna, Kharcha Philippine & Lokkoi Poteapia. | Philippine Jthama US Samarajya Vadna, Kharcha Philippine & Lokoi Pote Apiato | Philippines, which we have already mentioned in the US, will be the Philippines and people will be there. | Philipیں situation, z workout various society's mobile calls Galaxy Prebbe. | Filippines ZIXUS Samura Z�ión Caritta Philip nO Loco 8📹 |
| 09 | તે ચેતવણી આપે છે કે કોઈ બાંહેધરી આપી ન શકે કે અત્યારના સમયે ઇરાકમાં કોઈ પણ ગતિવિધિ સાંપ્રદાયિક યુદ્ધ, વધતી હિંસા અથવા સંપૂર્ણ અંધાધૂંધી તરફનો ઝોક અટકાવશે. | etc. | etc. | etc. | So, if you have any questions, please write in the comments section. | . . | vegetables are also complimentaryitude |
| 10 | નવા રાજ્યના પ્રાચીન ઇજિપ્શિયનો તેમના પૂર્વજોના જે સ્મારકોથી અભિભૂત થતાં તે એ સમયે એક હજાર વર્ષ કરતાં જૂનાં હતાં. | Nava Rajya na Prachin Egyptian, Timna Purva, Jna Jna, Jna Marakoti, Abibut, Thata, T, Eta, Eta, Eta, Eta, Eta, Eta, Eta, Eta, Eta, | Navaraj jena praccene Egyptiano. Timna purva jeno, jessmarra kohti. Nowi budh. Hata. Tee, ee samai, ee ka jahar vrshkatta juna hata. | Nava Rajya na Prachin Egyptian, Timna Purva, Jna Jna, Jna Marakoti, Abibut, Thata, T, Eta, Eta, Eta, Eta, Eta, Eta, Eta, Eta, Eta, | Now, the question is, is it a question? | Navaraj jaina praccin Egyptian oh Te traveler Noch, jase Shah Sar might ki koti sorry ibud 특 Atsisa mai Beans | Nawar are Giana Prachin Egyptian Hatayou |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.97 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| int8_forced_full | 0.9696 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| fp32_forced_vad | 0.97 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| int8_auto_vad | 1.0588 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| openai_forced | 0.9958 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_auto | 0.9337 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |

## pa (pa_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | ਉਪ-ਸੱਭਿਆਚਾਰ ਦੇ ਮੈਂਬਰ ਅਕਸਰ ਸ਼ੈਲੀ ਦੀ ਇੱਕ ਖ਼ਾਸ ਅਤੇ ਸੰਕੇਤਕ ਵਰਤੋਂ ਰਾਹੀਂ ਆਪਣੀ ਸਦੱਸਤਾ ਦਾ ਸੰਕੇਤ ਦਿੰਦੇ ਹਨ, ਜਿਸ ਵਿੱਚ ਫੈਸ਼ਨ, ਸ਼ਿਸ਼ਟਤਾ ਅਤੇ ਗੁਪਤ ਭਾਸ਼ਾ ਸ਼ਾਮਲ ਹੁੰਦੀ ਹੈ। | ndo s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s | ndihann jiswitch fashion | ndo s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s | I am going to say that the members of the community are doing their best to be the first member of the community. | OpoSecias Rediyimastes Ü прав груpe | 欲 google he 언버ğim.. othari Channeli de ikk kr hathi sote kthil va r crah |
| 02 | ਖੰਭਾਂ ਦੀ ਸੰਰਚਨਾ ਤੋਂ ਪਤਾ ਲਗਦਾ ਹੈ ਕਿ ਉਨ੍ਹਾਂ ਦੀ ਵਰਤੋਂ ਫਲਾਈਟ ਵਿੱਚ ਨਹੀਂ ਕੀਤੀ ਗਈ ਸੀ, ਬਲਕਿ ਤਾਪਮਾਨ ਨਿਯਮ ਜਾਂ ਪ੍ਰਦਰਸ਼ਨ ਲਈ ਕੀਤੀ ਗਈ ਸੀ। ਸ਼ੋਧ ਕਰਤਾ ਨੇ ਸੁਝਾਅ ਦਿੱਤਾ ਹੈ ਕਿ ਭਾਵੇਂ ਇਹ ਇਕ ਯੁਵਾ ਡਾਇਨਾਸੂਰ ਦੀ ਪੂਛ ਹੋਵੇ, ਨਮੂਨਾ ਬਾਲਗ ਪੰਛੀ ਦਾ ਖੰਭ ਦਿਖਾਉਂਦਾ ਹੈ ਅਤੇ ਬੋਟ ਦਾ ਹੇਠਲਾ ਨਹੀਂ। | nda diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya di | kambadis, surchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina, | nda diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya di | The reason why they don't like it is because they don't like it. So, when you start to show them, you will see that there is a huge difference in the universe. | kembadis searchnato pata lagda haki ona di garto flight which nahi ki ti gasi balke tap maan neum japar darshan lei ki ti gasi showd kertane sujaaditta ke pami ekis yuba dinasurdi pujh hovi namanna balak panchida khambdi di khanda hai a te botda hitla nahi | Kambadis searchna to pata lagda khaki, ona di garto flight which nahi ki ti gaisi. Balke, Tapmaan Nium, Jappar darshan lei ki ti gaisi, show the karta ne sujaadita ke pami. A. Iki jubar dinasurdi puj hovi. Namunna balak panchida kambadi konda hai, ate bota da hitla nahi. |
| 03 | ਰੋਗੀ ਨਾਈਜੀਰੀਆ ਗਿਆ ਸੀ, ਜਿੱਥੇ ਈਬੋਲਾ ਵਾਇਰਸ ਦੇ ਕੁਝ ਮਾਮਲੇ ਸਾਹਮਣੇ ਆਏ ਹਨ। | Rokih Najidya Yasi, which is the Ebola virus that we have received from the virus. | Rokih Najidya Yasi, which is the Ebola virus that we have received from the virus. | Rokih Najidya Yasi, which is the Ebola virus that we have received from the virus. | Rokin Nijidya Yasi, who has been the most important in the world. | Rotates Kendi idiyai idiyasi | Rokin Najratingar Yaasu which was truly an biggest trás of a nation Autchy Month. |
| 04 | ਮੇਰੇ ਲਈ ਇਸਦਾ ਕੋਈ ਮਤਲਬ ਨਹੀਂ ਸੀ; ਇਹ ਸਹੀ ਨਹੀਂ ਸੀ। | meraele is da koi matlabnaisi isaineisi | 美食 | meraele is da koi matlabnaisi isaineisi | Miri is the Koi Matlabnaisi and is here in Aisi | Merinlai is da koi matlabnaisi iya sai naisi | My girl is the mother of Unidos. |
| 05 | ਰਿਪੋਰਟ ਖੁੱਲ੍ਹੀ ਬਹਿਸ ਅਤੇ ਸੰਯੁਕਤ ਰਾਸ਼ਟਰ ਵਿੱਚ ਮੱਧ ਪੂਰਵ ਪ੍ਰਤੀ ਨੀਤੀ ਲਈ ਸਹਿਮਤੀ ਨਿਰਮਾਣ ਦੀ ਅਪੀਲ ਨਾਲ ਸ਼ੁਰੂ ਹੁੰਦੀ ਹੈ। | ndiqoorulib asatis interasu whichumatulul prati, netilei senti ni mandi pinal surumati | ndi4bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | ndiqoorulib asatis interasu whichumatulul prati, netilei senti ni mandi pinal surumati | The port is fully best, it is inter-ashtu which is not fully ready. | sporty쪽ж motherboard bi-quid Dж | This dish is really flowery cha prati na �usse mak Devati 30日 |
| 06 | ਇਹ ਸਿਧਾਂਤ ਦੱਸਦੇ ਹਨ ਕਿ ਲੋਕਾਂ ਦੀਆਂ ਕੁੱਝ ਜ਼ਰੂਰੀ ਲੋੜਾਂ ਅਤੇ/ਜਾਂ ਇੱਛਾਵਾਂ ਹੁੰਦੀਆਂ ਹਨ ਜਿਹਨਾਂ ਦੀ ਪੂਰਤੀ ਕੀਤੀ ਜਾਂਦੀ ਹੈ ਕਿਉਂਕਿ ਉਹ ਜਵਾਨੀ ਵਿੱਚ ਵੱਧ ਹੁੰਦੀਆਂ ਹਨ। | nda zentro kadee kuzur i banae jai chha ma merae jenae kuzur i kuzur i kuzur i kuzur i kuzur i kuzur i | nda zentro kadee kuzur i banae jai chha ma merae jenae kuzur i kuzur i kuzur i kuzur i kuzur i kuzur i | nda zentro kadee kuzur i banae jai chha ma merae jenae kuzur i kuzur i kuzur i kuzur i kuzur i kuzur i | I don't know what to do with this. | Easi jagam dahzin tem Nirno kejanyam i bisher courses ezearing еzlichkeit imbo id Id id id | ایسی دانگاز انتلوک دی پس روڑی لنا تے جائی چاہوں گیا نی جنہی بورتی کی چاہم دیکھو پیو جوانی بچ باتی مدرہنا |
| 07 | ਖਾਰੇ-ਪਾਣੀ ਦੇ ਮਗਰਮੱਛ ਸਮੁੰਦਰ ਵਿੱਚ ਸਰਗਰਮੀ ਨਾਲ ਨਹੀਂ ਰਹਿੰਦੇ, ਉਹਨਾਂ ਦਾ ਪ੍ਰਮੁੱਖ ਵਸੇਬਾ ਰਾਕਹੈਮਪਟਨ ਤੋਂ ਉੱਤਰੀ ਨਦੀਂ ਦੇ ਜਵਾਰ ਦਹਾਨਿਆਂ ਵਿੱਚ ਹੁੰਦਾ ਹੈ। | Karipani de magarmaat samundar, which started, garminal, neerandai, onnada prumuk, mashebba, rap, habitant, tuy, butrinadidejjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj | Karipani de magarmaat samundar, which started, garminal, neerande, onnada prumuk, mashebba, rap, habitant, oi, butrin, naidejjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj | Karipani de magarmaat samundar, which started, garminal, neerandai, onnada prumuk, mashebba, rap, habitant, tuy, butrinadidejjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj | I don't know the name of the family. I don't know the name of the family. I don't know the name of the family. I don't know the name of the family. | Khare panide Magar Wald sem� contrôle gezgra cultre the village is not a | Qarving кап리 Karmy Nalku SEMP |
| 08 | ਪ੍ਰਾਚੀਨ ਮਿਸਰ ਦੇ ਨਵੇਂ ਰਾਜ ਆਪਣੇ ਪੁਰਾਣੇ ਸਮਾਰਕਾਂ ‘ਤੇ ਹੈਰਾਨ ਸਨ, ਜਿਹੜੇ ਉਸ ਸਮੇਂ ਹਜਾਰਾਂ ਸਾਲਾਂ ਤੋਂ ਵੀ ਵੱਧ ਪੁਰਾਣੇ ਸਨ। | Prachir Mr. Deen Name Raj apune Puranis Marka Tehhar Ansan, Jede Usamhe Hazaara Sallatu Vy Vud Puranisam. | Prathchir Mr. Deu Raj, Pranis, Marga, Tehhar Anson, Jede Usamheh Zara Sallatovi, Vath Pranisam. | Prachir Mr. Deen Name Raj apune Puranis Marka Tehhar Ansan, Jede Usamhe Hazaara Sallatu Vy Vud Puranisam. | Prachir, Mr. Deen, Name Raj, Ape, Puranis, Marka, Teh, Haran, Sun, Jede Usam, Haza, Sallatu, Vy, Bhaat, Puranisam. | Pra reinforcing Ji De abdominalary | Prath year wyrently under stage � radiator 중��öyle 聊 columns . |
| 09 | ਕ੍ਰਿਸਮਸ ਇਸਾਈ ਮੱਤ ਦੀਆਂ ਸਭ ਤੋਂ ਮਹੱਤਵਪੂਰਨ ਛੁੱਟੀਆਂ ਵਿੱਚੋਂ ਇੱਕ ਹੈ, ਅਤੇ ਇਸਨੂੰ ਈਸਾ ਮਸੀਹ ਦੇ ਜਨਮਦਿਨ ਦੇ ਰੂਪ ਵਿੱਚ ਮਨਾਇਆ ਜਾਂਦਾ ਹੈ। | Christmas issai Mathidya  sabhtu Mathipulan chutya 北chon ikh hai ate esnu esam sih de janam dandar opubh janam dandar opubh janam ai | !!!! | Christmas issai Mathidya  sabhtu Mathipulan chutya 北chon ikh hai ate esnu esam sih de janam dandar opubh janam dandar opubh janam ai | Krishna is not a mother, so all the children are small, and all the children are small. So, this is the first time we have seen this, we have seen this, we have seen this. | Christmas sisay madwidi ya, Sambtu matu pulkyun first thy har GOD Az oina manusiday jealom den ag dark | prismesseee Sai果தiyya SubhyMANDARIN Mw service,<\|hi\|>agmore root po于ahan Banaya. |
| 10 | ਦੇਸ਼ ਭਰ ਦੇ ਮਸ਼ਹੂਰ ਗਾਇਕਾਂ ਨੇ ਸ਼੍ਰੀ ਸ਼ਿਆਮ ਦੇ ਚਰਨਾਂ ਵਿੱਚ ਭਜਨ, ਜਾਂ ਭਗਤੀ ਗੀਤ ਭੇਟ ਕੀਤੇ। | 我會不會覺得很喜歡的角度的角度,我就會不會覺得很喜歡的角度 | 我會不會覺得很喜歡的角度的角度,我就會不會覺得很喜歡的角度 | 我會不會覺得很喜歡的角度的角度,我就會不會覺得很喜歡的角度 | I will be able to show you the channel's content. | 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 北市 | [EMPTY] |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 1.0169 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| int8_forced_full | 0.9982 | 0.0 | WRONG_SCRIPT_LATIN:7, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |
| fp32_forced_vad | 1.0169 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| int8_auto_vad | 0.9448 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| openai_forced | 1.0916 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |
| openai_auto | 0.9297 | 0.0 | WRONG_SCRIPT_LATIN:8, SPECIAL_TOKEN:1, REPETITION:0, EMPTY:1 |

## ur (ur_pk)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | آپ اہرام کو تاریکی میں دیکھ سکتے ہیں اور آپ ان کو شو شروع ہونے سے پہلے کے سکون میں بھی دیکھ سکتے ہیں۔ | اگر ان کو اران کو تاری کمی دیکھتے ہیں اور ان کو شو شو شو شو شو شو شو شو شو شو شو شو شو شو شو | آپ ارام کو تاری کی مدیق ستتے ہیں اور ان کو شوش او مرنسے پہلے کے سکوان میں مدیق ستتے ہیں | اگر ان کو اران کو تاری کمی دیکھتے ہیں اور ان کو شو شو شو شو شو شو شو شو شو شو شو شو شو شو شو | I am going to give it a chance to say it in English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or English or | آپ اہرام کو تاری کیمے دیکھ سکتے ہیں اور ان کو شوشو مانے سے پہلے گے سکو مے بھی دیکھ سکتے ہیں | you want to tell a hero whose song is he and his lover hypwantay madam within us was at home the love on his share |
| 02 | یہ پارک 19،500 کلومیٹر رقبے پر محیط ہے اور یہ 14 مختلف ماحولیاتی علاقوں میں تقسیم کیا ہوا ہے، جن میں سے ہر علاقہ ایک الگ جنگلی حیات کے لیے سازگار ہے۔ | یہ پار کنی سا جات محاولی آتی لہم ایک ایک جنیے آتی لہم ایک جنیے آتی لہم ایک جنیے آتی لہم ایک کار ساہ | یہ پار کنی ساہد محاولی آتی لہکومت ایک ساہد ہموائے جن میں ساہد ایک یا لن جن لہے آتی لہکار ساہد ہے | یہ پار کنی سا جات محاولی آتی لہم ایک ایک جنیے آتی لہم ایک جنیے آتی لہم ایک جنیے آتی لہم ایک کار ساہ | This is the first time I have been in the hospital for 5 years now. I have been in the hospital for a while now. | یہ پار کنی سادات ماد سکلومیٹر رکب پر موھی تھی چاہدام خطلی خمحاولی آتی لہکومی تقصیم ہوئے جن میں سے ہر ایک یعلاق جنگلی ہے آت کے لیے کار ساتھ ہے | 預備al Theaterover 2-3 km入格itter包 1 kilo duke Kar5 |
| 03 | پوائنٹ میرین اور فیئرماؤنٹ کے درمیان پھیلاؤ کے سبب بفیلو-پٹسبرگ ہائیوے پر الگ تھلگ اندرونی جنگلی علاقے کے درمیان سے کثرت سے گزرتے ہوئے گاڑی چلانا مشکل بھرا کام ہوتا ہے۔ | ویٹمیلین اور فیر موٹ کے درمیان پر لہو کے سبب بفلو پیس پر حائی بر ارکھتے لن جنگی لاب کو لہتے کے درمیان سے کسرات سے گزرتے ہوئے گا ریزلانا موشکل برکام ہوتا | اور موضع کی درمیان پر لہو کے سبب بفلوپیس برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائ | ویٹمیلین اور فیر موٹ کے درمیان پر لہو کے سبب بفلو پیس پر حائی بر ارکھتے لن جنگی لاب کو لہتے کے درمیان سے کسرات سے گزرتے ہوئے گا ریزلانا موشکل برکام ہوتا | and we are going to meet the people who are in the same place. | موانٹ میلین اور فیر موانٹ کے درمیان پر لوگ کے سبب بفلو پسٹر بر حيبے بر ارکتا لگدر درمی جنگل جنگی لقق کے درمیان سے کسرد سے گزرتے ہوئے گا ریزلانا مشکل برق آموطہ ہے | موانٹ میلین اور فیر موانٹ کے درمیان پر لوگ کے سبب بفلو پسٹر بر حيبے بر ارکتا لگدر درمی جنگل جنگی لقق کے درمیان سے کسرد سے گزرتے ہوئے گا ریزلانا مشکل برق آموطہ ہے |
| 04 | ذیلی ثقافت کے ارکان اکثر اپنی رکنیت کا اشارہ امتیازی اور علامتی طرز، جسمیں فیشن، خاص اسلوب اور بول چال کا استعمال کرتے ہوئے کرتے ہیں۔ | دیالی سکاتھت کے ارکان اچھر اپنی رکھنے کا اشارہ ابدیازی اور ارامتی ترس دس میں فیحشان خ | ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ا | دیالی سکاتھت کے ارکان اچھر اپنی رکھنے کا اشارہ ابدیازی اور ارامتی ترس دس میں فیحشان خ | I will show you the recipe of your friends, friends, and relatives. This is my fashion, princess, rub and ball-towel. | دیالی سکافت کے ارکان اچھر اپنی رکھنے کئی شارہ اب دیالی اور رام تیترس دسمے فیشن خاص اس روب اور بول چوال کس تمال کرتے ہوئے کرتے ہیں | دیالی سکافت کے ارکان اچھر اپنی رکھنے کئی شارہ اب دیالی اور رام تیترس دسمے فیشن خاص اس روب اور بول چوال کس تمال کرتے ہوئے کرتے ہیں |
| 05 | مجھے معلوم نہیں آیا آپ نے محسوس کیا ہے یا نہیں، اس ملک میں سنٹرل امریکا سے آئی زیادہ تر اشیاء ڈیوٹی فری ہیں۔ | مجمالوں نہیں آئے آپ نے محسموس کیا یہاں نہیں ہے اس مطمل سنٹر لمدی کا سعیل عائیزیادتر اشیادی ایوٹی | یہ مالوں نہیں آئے آپ نہیں محسنی ہے اس مورت میں سنٹرونوں کیا سے آئیزیادہ ترہ شیر دیوی تیوی تیوی تیوی تیوی | مجمالوں نہیں آئے آپ نے محسموس کیا یہاں نہیں ہے اس مطمل سنٹر لمدی کا سعیل عائیزیادتر اشیادی ایوٹی | I don't know if you have any questions or any. I'm a student in this country. I'm a student in this country. I'm a student in this country. I'm a student in this country. I'm a student in this country. I'm a student in this country | مجھے مالوں نہیں آیا آپ نے محسمس کی آمیانے ہیں اس مورک میں سنٹر لم دیکھا سین ایزیادہ تراشی آدیوٹیفری ہے | In française Mahadeemin Niya Mahadeemin Shadeema |
| 06 | وقت گزرنے کے ساتھ ساتھ، جیسا کہ نئی آبادی اپنے نئے ماحول میں ڈھلتی ہے، وہ آہستہ آہستہ دیگر آبادی کی طرح کم دکھنے لگتے ہیں۔ | ہوا ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ای | ہوا ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ای | ہوا ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ای | I am very happy that you are not talking about this thing. I am very happy that you are about to keep them healthy. | بقاروزہ نے کہ ساتھ ساتھ جسکین لہیہ باتیغی اپنے منا як ہون 고양ہ سھجہ دکہ contexto ج going باتی اس الکم دیکھی ändلکتے | złabad pozen는데요 saat chari somebody acts in neya bathing ofkin vai waholman hukti hai財rada, Hari Baba Dicky is the inheritreddedic 你有沒有這兒呢? |
| 07 | اس کے بنسبت ناقابل رسائی پر مشتمل "ٹمبٹو" کا نام غیر ملکی، دور دراز علاقے کے استعارے کے طور پر استعمال ہوتا تھا۔ | اس کی بنسبتنا کبلے رسائے پر مستمل تمتو کنام غير ملکی دور درازی لا | اس کی بنسبتنا کبلی رسائی پر نوشتمل تم تو کنام غير ملکی دور درازی ل | اس کی بنسبتنا کبلے رسائے پر مستمل تمتو کنام غير ملکی دور درازی لا | This is the number of people who have been able to receive the number of names of the multidoulda-razi-la-kis-ta-ri-tor-per- | اس کی بنیسپتنا کبلے رسائی پر مستمل تم تو کنام گائر ملکی دور طرازی لاکے کیستارے کے طور پر استمان ہستارے ہیں | Neel wolfite会tal jaunahu marna00 |
| 08 | کسی بھی ترمیمات یا درخواستوں کو ٹریول ایجنٹ کی معرفت ارسال کیا جائے، نہ کہ براہ راست ہوٹل کو۔ | کسی بھی ترمیماتی در خوستوکورe Travel Agent کے مارفف اٹسال کے جائے ناک | کسی بھی ترمیماتی در خوستوکورe travel agent کے مارففات اٹسال کے جائے ناک | کسی بھی ترمیماتی در خوستوکورe Travel Agent کے مارفف اٹسال کے جائے ناک | If you have any questions or questions, please go to the channel and write the name of the question. | کسی بیترنی ماتیہ در خوستوکور رہل ایک ان کے مارفت ارصال کے اجائے نکہ برائرہ سھٹل کو۔ | کسی بھی ترمیماتی اضہ خوال تکور پر بھی ایٹن کے مارفات اٹسال کے اجائے ناکہ برائرہ سھٹل کو |
| 09 | این ایچ کے نے یہ بھی رپورٹ کیا کہ نائی گاٹا پری فیکچر میں قائم کشیوازاکی کریوا نیوکلیئر پاور پلانٹ معمول کے انداز میں کام کررہا تھا۔ | ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک | ایک ایک نیر برقائق یہاں جائم کسیوزاقی قریوان نکلر بارط مامل کیا دازمی کام کر رہا تھا | ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک | In the end, the new Gata Prefecture, which was a great popular plant called Keshwazaki, which was working in the plant. | این اچکے نے یہ بریپورٹ جائے گئی گئٹہ پریفکٹر میں کائم کشوہ زاکی قریوان نکلر پاور پلاٹ ممول کنداز میں کام کر رہا تھا | n h.ka neewuri sport jage 9 vasa pre guradiya kai, keshwa zagi Kriwan new Kluar Power Plant Mammul. kAndais mmi kyan kaum kar ra-ha bann mit. |
| 10 | سیال مواد کی مقدار کی بنیاد پر کوئی سالن "خشک" یا "گیلی" ہوسکتا ہے۔ | سیل مبات کی مقضار کی گنیات پر کوئی ساللن خوش پیئی اگیلی | سیل مبات کی مقضار کی گنیان پر کوئی ساللان خوش پیا گیلی ہو سطال | سیل مبات کی مقضار کی گنیات پر کوئی ساللن خوش پیئی اگیلی | I am also a mother of the people of the world, and I am also a mother of the people of the world. | سیل مبات کی میکودار کی گنیا پر کوئی سالن خوش پیہ گینی ہو ستالی | سيا الماباتظ مقدار كبونيات فرقيت صالط harmless发ط |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 0.5386 | 0.9789 | WRONG_SCRIPT_LATIN:1, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:0 |
| int8_forced_full | 0.5456 | 0.9797 | WRONG_SCRIPT_LATIN:1, SPECIAL_TOKEN:0, REPETITION:3, EMPTY:0 |
| fp32_forced_vad | 0.5386 | 0.9789 | WRONG_SCRIPT_LATIN:1, SPECIAL_TOKEN:0, REPETITION:4, EMPTY:0 |
| int8_auto_vad | 1.2068 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:0 |
| openai_forced | 0.3449 | 0.9783 | WRONG_SCRIPT_LATIN:1, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_auto | 0.7469 | 0.3786 | WRONG_SCRIPT_LATIN:7, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |

## or (or_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | ହାତୀ ଓ ଜିରାଫ୍‌ ଭଳି ପଶୁମାନଙ୍କର, କାର୍ ଏବଂ ଭଲ ଦେଖାଯାଉଥିବା ମାନକ ସରଞ୍ଜାମକୁ ନିକଟରୁ ଦେଖିବା ପାଇଁ ପାଖକୁ ଆସିବାର ପ୍ରବୃତ୍ତି ଥାଏ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | I'm going to show you the next video. | [WHISPER_TOKEN_MISSING] | 1.2 g of salt 2.5 g of salt 2.5 g of salt 1.5 g of salt 1.5 g of salt |
| 02 | ଦୟାକରି ଏହି ସ୍ଥାନକୁ ଯଥାର୍ଥ ସାଧୁତା, ଗମ୍ଭୀରତା ଏବଂ ସମ୍ମାନର ସହିତ ବିଚାର କରନ୍ତୁ। ହୋଲକୋଷ୍ଟ କିମ୍ବା ନାଜିମାନଙ୍କ ବିଷୟରେ ଥଟ୍ଟା କରନ୍ତୁ ନାହିଁ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [speaking in foreign language] | [WHISPER_TOKEN_MISSING] | Therefore, when we overwhelmingly accept ourselves and indeed accept ourselves to be accept ourselves to stay faithful with a person. We have always wanted to make it clear when this is assets. So, when we feel at this moment, we are very satisfied |
| 03 | 108 ପ୍ରକାର ଛପନ ଭୋଗ (ହିନ୍ଦୁ ଧର୍ମରେ, 56 ପ୍ରକାର ଖାଦ୍ୟ ସାମଗ୍ରୀ, ଯଥା, ମିଠା, ଫଳ, ବାଦାମ, ବ୍ୟଞ୍ଜନ, ଇତ୍ୟାଦି ଯାହା ଭଗବାନଙ୍କୁ ଅର୍ପଣ କରାଯାଏ) ବାବା ଶ୍ୟାମଙ୍କୁ ଅର୍ପଣ କରଯାଇଥିଲା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | So, after the preparation of the food, we will prepare the food for the children. | [WHISPER_TOKEN_MISSING] | Rholes 394.4% |
| 04 | ବର୍ତ୍ତମାନ ସମଗ୍ର ଆର୍ଚିପେଲାଗୋରେ ବହୁଳ ଭାବରେ ଉପଲବ୍ଧ, ଜାଭାନିଜ୍ ରୋଷେଇ ସମ୍ବନ୍ଧୀୟ ବ୍ୟବସ୍ଥାରେ କେବଳ ଋତୁ ଅନୁସାରେ ଖାଦ୍ୟର ଏକ ଦୀର୍ଘ ବୈଶିଷ୍ଟ୍ୟ ରହିଛି, ଯାହାକି ଜାଭାନିଜ୍ ମାନଙ୍କର ଚିନାବାଦାମ, ଲଙ୍କା, ଚିନି (ବିଶେଷକରି ଜାଭାନିଜ୍ ମାନଙ୍କର ନଡ଼ିଆ ଚିନି) ଏବଂ ବିଭିନ୍ନ ସୁଗନ୍ଧିତ ମସଲା ଗୁଡିକୁ ମିଶାଇ ସ୍ଵାଦ ଦେଇଥାନ୍ତି। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | And the first thing we have to do is to make sure that the group members are not in the same position as the group members. | [WHISPER_TOKEN_MISSING] | transitionsato 5 nd 16 archi snacks jaba mini trossi sambandiam bago starce cellul kzavanish jamad mountain etc autres jaba mini jamad propagation jaba madam marut she saw then suddenly nd |
| 05 | ପ୍ରାରମ୍ଭରେ ପୋଷାକ ପୂର୍ବର ବାଇଜାଣ୍ଟାଇନ ସଂସ୍କୃତି ଦ୍ୱାରା ବହୁଳ ଭାବରେ ପ୍ରଭାବିତ ହୋଇଥିଲା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | (speaking in foreign language) | [WHISPER_TOKEN_MISSING] | [EMPTY] |
| 06 | ସଂଶୋଧନ ହେବା ପୂର୍ବରୁ ଆର୍ଟିକଲଗୁଡିକ ସମସ୍ତ ରାଜ୍ୟମାନଙ୍କ ଦ୍ଵାରା ସର୍ବସମ୍ମତିର ଆବଶ୍ୟକ ଥିଲା ଏବଂ ରାଜ୍ୟ ମାନେ କେନ୍ଦ୍ର ସରକାରଙ୍କୁ ଏତେ ହାଲୁକା ଭାବେ ନେଲେ ଯେ ସେମାନଙ୍କର ପ୍ରତିନିଧି ମାନେ ପ୍ରାୟତଃ ଅନୁପସ୍ଥିତ ଥିଲେ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [speaking in foreign language] | [WHISPER_TOKEN_MISSING] | ZSam画 sahно-cheapat system su kya seamaAPPLAUSE |
| 07 | ରବିନ୍ ଉଥାପ୍ପା 11 ଟି ଚୌକା ଓ 2 ଛକା ମାରି 41 ଟି ବଲରେ 70 ରନ୍ କରିଥିଲେ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | and the same thing is the same. | [WHISPER_TOKEN_MISSING] | Roughinn hot thuck hat taking, cocoa van, |
| 08 | ଗୀର୍ଜାଘର ପରିସରରେ, ଅନେକଗୁଡ଼ିଏ ସମାଧି ଉପରେ କପୋତର ଦୃଷ୍ଟି ଆକର୍ଷକ ମାର୍ବଲ ମୂର୍ତ୍ତି ଅଛି। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | The journey is completed. | [WHISPER_TOKEN_MISSING] | Girjagana poshara dee O ne guri samantha ddos tierraore Ca Mackir G說ksam motheraneun tin sixt were todo sugi nukara MSD qes aunas Malgool multiridges |
| 09 | ଆରବମାନେ ମଧ୍ୟ ଇସଲାମକୁ ଦେଶକୁ ଆଣିଥିଲେ, ଏବଂ ଏହା କୋମୋରସ୍ ଏବଂ ମାୟୋଟେରେ ଏକ ପରିବ୍ୟାପ୍ତ ହୋଇଥିଲା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | (speaking in foreign language) | [WHISPER_TOKEN_MISSING] | today I will talk about Man Ecofiction A film of 2017 |
| 10 | ଇରାନରେ ଉଭୟ ନାଗରିକ ଓ ସୈନ୍ୟବାହିନୀ ପାଇଁ ଥିବା ବିମାନଗୁଡ଼ିକ ପୁରୁଣା ହୋଇଥିବା ଓ ସେଥିପାଇଁ ରକ୍ଷଣାବେକ୍ଷଣର ଅଭାବ ଥିବା ଯୋଗୁଁ ସେଠାରେ ବିମାନ ଦୁର୍ଘଟଣା ଏକ ସାଧାରଣ ଘଟଣା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | We then do a point in our body. | [WHISPER_TOKEN_MISSING] | eventle uboy nako dicoriumos Ai na banyi fa reality baimin nako dicovenuch na hoji te ba wo si pi 세�tycznie sue fin bhi ko Соin keenbael ka nu baich instructed bumps e ba重aihan tibahi ka cowk Liebe so way nako keluar vee bi meneGoodu mureek Coay nako si le bi maine Oke markings coo.. |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | None | None | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:10 |
| int8_forced_full | None | None | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:10 |
| fp32_forced_vad | None | None | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:10 |
| int8_auto_vad | 0.9457 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:0 |
| openai_forced | None | None | WRONG_SCRIPT_LATIN:0, SPECIAL_TOKEN:0, REPETITION:0, EMPTY:10 |
| openai_auto | 1.21 | 0.0 | WRONG_SCRIPT_LATIN:9, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:1 |

## as (as_in)

| # | reference | app INT8+VAD forced | INT8 full | FP32+VAD forced | INT8 auto | OpenAI forced | OpenAI auto |
|---|---|---|---|---|---|---|---|
| 01 | এতিয়াও আমাৰ আশী শতাংশ দ্ৰব্যৰ কৰ টাৰিফৰ যোগেদি চেণ্ট্ৰেল আমেৰিকান দেশসমূহসমূহত প্ৰদান কৰা হৈছিল। আমি আপোনাৰ যত্ম লওঁ। | では では では では では では では では では では では では では では では では では では では では では では では では では | ethio アマラхihotang和reboard call the reference of the Central American Day Homehold product アニアプラジオッノ llow | では では では では では では では では では では では では では では では では では では では では では では では では では | and the American Day Homehold of the Republic of California, Central American Day Homehold, and the American Homehold of the Republic of California. | ethio AMO дней come on Prudan Khirens시죠, amii jáppnerm generally | A matomi Central America Technical Fund employ US |
| 02 | বিজ্ঞানে এতিয়া ইংগিত দিছে যে এই বিশাল কাৰ্বন অৰ্থনীতিয়ে আমাৰ জীৱমণ্ডলৰ স্থিৰ অৱস্থাৰ পৰিৱৰ্তন আনিছে যিটো অৱস্থায়ে মানৱ সভ্যতাক বিগত ২ মিলিয়ন বছৰৰ পৰা  সমৰ্থন কৰি আহিছে। | ndmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmd | ndmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmd | ndmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmd | The first part is the English language, which is the first part of the Japanese language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English language. The first part is the English | Mikka Netya Ingrid Dashajinate a pickle carbon or thin Amar Zib Monterード Sthiro Obosta WohopTHIN ANATA | 餓chnittinal pesa 50 sec 1 kg 100 ml |
| 03 | ২০১০ৰ সাধাৰণ নিৰ্বাচনৰ পূৰ্বে ১,৪০০ গৰাকী ব্যক্তিৰ ভোটগ্ৰহণ কৰা হয়, ২০০৮ চনৰ পৰা অষ্ট্ৰেলিয়া গণতান্ত্ৰিক দেশ হোৱাটো নিবিচৰা লোকৰ সংখ্যা ৮ শতাংশ বৃদ্ধি পালে। | 其实, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, | 果是非常有趣的 | 其实, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, | This is the daughter of her personal group. She has a very good character for her group. She is a personal personality. She is a very healthy and healthy group. | nam併 Most of the forest is located at the time of the lake In a wave of wind When such perch blossoms or black spots They will take the bait and catch the fish for Geröha Let us know the story of the Thorab predestined What would you like to go and taste the perch? thigh deep thigh in southern area of the river | Koha Insene Damon Ngoha Sariha Kle whenever Bowrow |
| 04 | ছাফাৰি অভিযানসমূহ চাগে আফ্ৰিকান পৰ্য্যটনৰ আটাইতকৈ ডাঙৰ আকৰ্ষণ আছিল আৰু বহুতক আকৰ্ষিত কৰিছিল। | 其實我非常喜歡,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以 | 再次 後面 再次 再次 再次 再次 再次 再次 再次 再次 再次 再次 | 其實我非常喜歡,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以 | So, for example, if you have any questions, please contact me. | 到時候ieważ子以下午 都夫 開始這樣就不知道 何了時間到學生 | Israel S Delete |
| 05 | বহুতো সামাজিক আৰু ৰাজনৈতিক প্ৰভাৱ আছে, যেনে মেট্ৰিক ব্যৱস্থাৰ ব্যৱহাৰ কৰা, নিৰপেক্ষবাদৰ পৰা গণতন্ত্ৰলৈ পৰিৱৰ্তন হোৱা, জাতীয়তাবাদ আৰু এইটো বিশ্বাস যে দেশ ইয়াৰ জনতাৰ বাবেহে, কোনো 1টা শাসকৰ বাবে নহয়। | 物2,我們要先開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k. | Bohutu, how much is it? I have a 3k, I will start the world for a new topic, but other for another totally important one. The 3rd of the 3rd is the 4th of the 3rd is the 4th of the 3rd. | 物2,我們要先開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k. | I will do how I was able to do this. I will start the work for a new topic. I will start the work for another 3rd time. | 常常常添會 Unfortunately. | K mai marinaykte sa wifi Osaka Lama O |
| 06 | "কিছুমান প্ৰতিবেদনে অধিকাৰীক মূল্যক আঠত ৰাখি দিলে , আৰু অধিকাৰীক প্ৰতিবেদনে অনুমোদন জনালে যে ৩০ জনলৈকে আহত হৈ গল; কিন্তু শেষ সংখ্যা এতিয়ালৈকে জ্ঞাত নহয় ।""" | ndiqari mullog at hoot rakeh n e ndiqari prote ve do ne ono moton journali jeh 3 januari ke a hoot hul gul kintu hh hul kintu hh hul kintu hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh h | ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndi | ndiqari mullog at hoot rakeh n e ndiqari prote ve do ne ono moton journali jeh 3 januari ke a hoot hul gul kintu hh hul kintu hh hul kintu hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh h | and they are not allowed to do so. | kisman protebvedon ndi karik mullog atho trakhidine arho ndi karik protebvedon ndi ono mothan jonaljheh 3 jonolikhe ahod hulgol ndi hulgheh hulgheh hulgheh hulgheh hulgheh | Arum Surant Heekhoi |
| 07 | মেজৰকেন ব্যঞ্জন মেডিটেৰাণীয়ানৰ বাকীবিলাক ক্ষেত্ৰৰ নিচিনা ৰুটি, শাক-পাচলি আৰু মাংসৰ(বিশেষকৈ গাহৰি মাংস) ওপৰত আধাৰিত, আৰু ইয়াত জলফাইৰ তেল ব্যৱহাৰ কৰা হয়। | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | Mesertgen Vgen Meditori general and Bacchipi lab kept in the journality hack, personally I am also hoping to help you get the whole project. I hope you had some thoughts, we will be very glad. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | The major convention is the media community and the community has a speciality and we are going to be a part of the social media community. | Maisorne can viendha tune medit ne顆 Ketrol isiner Ande hak Masorne can be bad Parsali 나릇bell kcal namik korot Atta | Mes Urkin retourner Naturally Heading Agency After mending aover. implant COVID has triggered theublian Arts against unphiltorial disease. traumaillah |
| 08 | প্ৰাচীন ৰোমাণ আহাৰসমূহত শেষৰ শতিকাবোৰত আমেৰিকা বা এছিয়াৰ পৰা ইউৰোপলৈ অহা খাদ্যসমূহ অন্তৰ্ভূক্ত হ’ব নোৱাৰে। | Prasind Roman, Hacor, Hothika, Latin America, etc., etc., etc. | 曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲 | Prasind Roman, Hacor, Hothika, Latin America, etc., etc., etc. | The person who is in the world is the real world of the world. | jo jsmthrkкеxxxhrh単时间Nmd | Pracín pocketsotikä Borot. Proceduir la |
| 09 | নতুন পৰিবেশত বিভিন্নধৰণৰ উপায় আৰু বিভিন্ন প্ৰতিযোগী আছে, গতিকে নতুন মানুহক বেলেগ বেলেগ বিশেষত্বৰ প্ৰয়োজন হ'ব আৰু তেওঁলোকে আগতে পোৱাতকৈ শক্তিশালী প্ৰতিযোগী গ্ৰহণ কৰিব লাগিব। | we have been not on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been | [EMPTY] | we have been not on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been | We have been in the world for a long time. We have been in the world for a long time. | Notun pori ve khot bivin na dhanan u pai arom bivin na protizogi asa Koti ke no to n manohawk belek belek belek behe hot tor prayasin hobo arom Teo la ke agotya baut koi hotti hali protizogi brohon kori mo lagi | Nothun Puri ve khot bivindh harnar u pai arum bivindh prothizogi asa Koti ke Nothun Manohawk belek belek belek belek belek belek belek belek hot tor prasin hobo arum Teolak ke agotya poat koi hotti hali prothizogi brohon kori mo lagi |
| 10 | পৰবৰ্তী, কিছুমান ঘোঁৰাৰ গাদী, বিশেষভাৱে ইংলিছ ঘোঁৰাৰ গাদীত সুৰক্ষা দণ্ডিকা থাকে যিয়ে এজন ঘোঁৰাচালক পাছলৈ পৰি যোৱাৰ সময়ত ধৰি ৰাখিবৰ বাবে গাদীৰ লগত এডাল ৰেকাবী থাকে। | 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名 | Poroborthe human koorahar gadim, behedhabe English koorahar gadim,Poroborok ka dondikahake ghe,Resum koorahar salo,Pasoloi,Porizawa khumoi,Horiakim,Borbabe,Gadil logoth edal,Recapithake | 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名 | and we will be going to the next one. | speciesuras shupens of government Human anger professor The all very moves we are focused on the private energy. | Coronary blackpetten N LEierz Coronary blackpetten the rural eyes of women Rosaational were the leaders in pink |

| variant | mean CER | script ratio | garbage flags |
|---|---|---|---|
| app_int8_forced_vad | 1.0569 | 0.0 | WRONG_SCRIPT_LATIN:7, SPECIAL_TOKEN:0, REPETITION:7, EMPTY:0 |
| int8_forced_full | 1.0043 | 0.0 | WRONG_SCRIPT_LATIN:6, SPECIAL_TOKEN:0, REPETITION:2, EMPTY:1 |
| fp32_forced_vad | 1.0569 | 0.0 | WRONG_SCRIPT_LATIN:7, SPECIAL_TOKEN:0, REPETITION:7, EMPTY:0 |
| int8_auto_vad | 1.1625 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |
| openai_forced | 1.0518 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |
| openai_auto | 0.983 | 0.0 | WRONG_SCRIPT_LATIN:10, SPECIAL_TOKEN:0, REPETITION:1, EMPTY:0 |

#### Exact Kotlin-matching app-path re-run

This re-run sets `tail_paddings=1000`, uses `language=""` for true auto-detection, uses the exact
20 ms VAD ZCR denominator, and never calls the decoder after a VAD rejection. The recognised
config was read back from `recognizer.config.model_config.whisper` and printed as
`forced tail=1000, auto tail=1000, auto language=''` for every language (log
`deliverables/diagnosis9/whisly_exact_verification.txt`). Copies:
`deliverables/diagnosis9/diagnosis9_exact_report.md`, `diagnosis9_exact_results.jsonl`.
# DIAGNOSIS9 exact-config raw outputs

Config verified from `recognizer.config.model_config.whisper`:
`tail_paddings=1000`, `language=<forced code>` or `''` for auto, `task='transcribe'`.
VAD: exact Kotlin port (ZCR denominator count-1), 20 ms frames, 300 ms pre-roll,
700 ms hangover; decoder called only when app VAD passes.

## en

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| en_us-01 | When you call someone who is thousands of miles away, you are using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. | When you call someone who is thousands of miles away, you're using a satellite. |
| en_us-02 | Now widely available throughout the archipelago, Javanese cuisine features an array of simply seasoned dishes, the predominant flavorings the Javanese favor being peanuts, chillies, sugar (especially Javanese coconut sugar) and various aromatic spices. | Now widely available throughout the Archaeopalago, Japanese cuisine, features, and array of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chilies, sugar, especially Japanese coconut. | Now widely available throughout the Archaeopelago, Japanese cuisine features an array of simply seasoned dishes, the predominant flavorings, the Japanese favorite being peanuts, chilies, sugar, especially Japanese coconut sugar and various aromatic spices. | Now widely available throughout the Archaeopalago, Japanese cuisine, features, and array of simply seasoned dishes. The predominant flavorings, the Japanese favorite being peanut, chilies, sugar, especially Japanese coconut. |
| en_us-03 | The U.N. also hopes to finalize a fund to help countries affected by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affect it by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affected by global warming to cope with the impacts. | The UN also hopes to finalize a fund to help countries affect it by global warming to cope with the impacts. |
| en_us-04 | Then, Lakkha Singh took the lead in singing the bhajans. | [VAD_REJECTED] | Then lock the things to delete and sing the "passions" | [VAD_REJECTED] |
| en_us-05 | The major religion in Moldova is Orthodox Christian. | [VAD_REJECTED] | The major religion in Wildova is Orthodox Christian. | [VAD_REJECTED] |
| en_us-06 | The East African Islands are in the Indian Ocean off the eastern coast of Africa. | [VAD_REJECTED] | The East African Islands are in the Indian Ocean off the eastern coast of Africa. | [VAD_REJECTED] |
| en_us-07 | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. | Some festivals have special camping areas for families with young children. |
| en_us-08 | The Tibetan Buddhism is based on the teachings of Buddha, but were extended by the mahayana path of love and by a lot of techniques from Indian Yoga. | [VAD_REJECTED] | But to begin, but Buddhism is based on the teachings of Buddhism, but we're extended by the Mayana Path of Love and by a lot of techniques from Indian Yoga. | [VAD_REJECTED] |
| en_us-09 | Pronunciation is relatively easy in Italian since most words are pronounced exactly how they are written | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. | From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written. |
| en_us-10 | In this dynamic transport shuttle everyone is somehow connected with, and supporting, a transport system based on private cars. | [VAD_REJECTED] | In this dynamic transport shuttle, everyone is sure connected with and supporting a transport system based on private cars. | [VAD_REJECTED] |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.5 | EMPTY=5 |
| int8_forced_full_exact | 0.5 |  |
| int8_auto_vad_exact | 0.5 | EMPTY=5 |

## hi

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| hi_in-01 | मेजरकेन खाना, भूमध्य सागर में समान क्षेत्रों की तरह, रोटी, सब्जियों और मांस (विशेष रूप से सूअर का मांस) पर आधारित है, और जैतून के तेल का उपयोग करता है। | Major kinkanam pohumadheesagar mein zaman shatrum, guitar roti, subjunior and mass, Vishesh Roop se sur kamas par adharyath hai. And jatun ke tel kao pi okreta hai. | Major Kinkan Amp Humadyasagar mein Saman Shaitroom ki Tarar roti, Subjiya & Mass Vishayesh Roop se sur kamas par Adarith. And jeetoon ke tel kao piop kar tae hai. | Major kinkanam pohumadheesagar mein zaman shatrum, guitar roti, subjunior and mass, Vishesh Roop se sur kamas par adharyath hai. And jatun ke tel kao pi okreta hai. |
| hi_in-02 | वैज्ञानिकों का मानना है कि ओसेलोट्स गंध के द्वारा जानवरों का पीछा करते और खाते (शिकार) हैं, यह सूँघते हुए कि वे ज़मीन पर कहाँ रहे होंगे। | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda |
| hi_in-03 | आर्मंड वर्सास ने कहा, मैंने अपनी बहन और उसकी दोस्त को खो दिया, और रास्ते में व्हीलचेयर में दो दिव्यांग लोग थे, और लोग उनपर उछल रहे थे और उन्हें धक्का दे रहे थे”। | And I said, "I am not a person and a person who has been a child and I am a whole child and I am a child." And people were very happy and they were very happy. | I am the person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am a person who used to say that I am | And I said, "I am not a person and a person who has been a child and I am a whole child and I am a child." And people were very happy and they were very happy. |
| hi_in-04 | कुतूहल गाँव में आधे घंटे टहलना समय की हानि नहीं है। | [VAD_REJECTED] | kutu hallgaume adekantik dahalna  savay ki haanim nahi hai | [VAD_REJECTED] |
| hi_in-05 | जब घायलों को लड़ाई बंद होने के बाद अस्पताल ले जाया गया, तो लगभग 40 अन्य बचे हुए कैदी यार्ड में रुके और अपनी कोठरियों में लौटने से इनकार कर दिया. | nd when people say that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know | र ा ा ा ा ा ा ा ा ा ा ा ा ा ा ा ा | nd when people say that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know that they are going to know |
| hi_in-06 | पूर्व हाउस स्पीकर न्यूट गिंग्रिच, टेक्सास के गर्वनर रिक पैरी, और कांग्रेसवुमन मिशेल बैचमैन ने क्रमश: चौथा, पाँचवाँ और छठा स्थान हासिल किया. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | Pure House speaker newed GreenKitch Taxi's Governor Rick Barry and Congresswoman Michelle Batchman Negramsha Chathapachho and Chathasthan Hashiri. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| hi_in-07 | रॉबिन उथप्पा ने केवल 41 गेंदों में 11 चौकों और 2 छक्कों की मदद से 70 रन बना कर पारी का सर्वोच्च स्कोर बनाया. | ndo chakongi madad sasaran bana kar pari kar servo qi school | Robbenootappan 1-1-1-2-1-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2-2- | ndo chakongi madad sasaran bana kar pari kar servo qi school |
| hi_in-08 | यदि आप सर्दियों में आर्कटिक या अंटार्कटिक क्षेत्रों में जाते हैं, तो आप ध्रुवीय रात का अनुभव करेंगे, जिसका अर्थ है कि सूरज क्षितिज से ऊपर नहीं उगता है। | now we will start the interview with the other 3rd episode | now we will start the interview with the other 3rd of the interview | now we will start the interview with the other 3rd episode |
| hi_in-09 | अभी भी वे यह पता लगाने की कोशिश कर रहे हैं कि क्रैश कितना बड़ा था और धरती किस तरह से प्रभावित होगी. | nd bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | nd bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | nd bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb |
| hi_in-10 | समय के साथ, जैसे-जैसे नई आबादी नए माहौल में ढलती जाती है, वह भी दूसरी आबादी की तरह अनदेखी करना शुरू कर देती है. | 7.JSNN ABDI NAMAHOLMADDAL TYJATI HURBI, DUG SRI ABDI KITARH, UNDICI KURNASHU KARDDI. | 7.JSNN ABDI NAMAHOLMADDAL TYJATI HURBI, DUG SRI ABDI KITARH, UNDICI KURNASHU KARDDI. | 7.JSNN ABDI NAMAHOLMADDAL TYJATI HURBI, DUG SRI ABDI KITARH, UNDICI KURNASHU KARDDI. |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| int8_forced_full_exact | 0.1 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| int8_auto_vad_exact | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |

## bn

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| bn_in-01 | বাঁধের উপর 100 ফুট চওড়া একটি জায়গা থেকে জল ছিটকে বেরোচ্ছে। | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| bn_in-02 | যদিও অধিকাংশ চিহ্ন শুধুমাত্র কাতালানে নির্দেশ করে কারণ এটি আইন এর মাধ্যমে প্রথম দাপ্তরিক ভাষা হিসাবে প্রতিষ্ঠিত হয়। | 3.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4. | 3.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4. | 3.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4.4. |
| bn_in-03 | স্থানীয় কার্যক্রমের উদাহরণগুলোর মধ্যে শিকার করা, মাছ ধরা, ফটোগ্রাফি করা, পাখি দেখা, এবং পার্কগুলোতে ঘোরা ও বাস্তুতন্ত্র সম্পর্কিত তথ্য নিয়ে পড়াশোনা করা অন্তর্ভুক্ত রয়েছে। | !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! | !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! | !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! |
| bn_in-04 | নতুন প্রজন্ম প্রাচীন মিশরীয়রদের স্মৃতিস্তম্ভগুলো দেখে অবাক হয়েছিলেন, যা এক হাজার বছরেরও বেশি পুরানো ছিল। | Not only the project is a mission of the 3rd, Istanbul, the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of | Note 1 Projion Mode Projion Missionary Delta 3.Estanbo-Gulow BK-OBAC-Ecylinder 1.2.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0. | Not only the project is a mission of the 3rd, Istanbul, the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of the region of |
| bn_in-05 | আপনি প্রথম বার বিদেশে গেলে, সেখানকার মানুষকে সম্ভবত ধৈর্যশীল ও সহানুভূতিশীল মনে হবে, কারণ তারা জানে যে নতুন দেশে ভ্রমণকারীদের মানিয়ে নেওয়ার দরকার হয়। | nda mnus kishamababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | In my story, I started with the second mother of the mother of the mother. She was born in the same age. She was born in the same age. |
| bn_in-06 | গবেষকদের মতামত অনুযায়ী, এটি একটি অল্প বয়সি ডায়নোসরের লেজ হলেও, নমুনায় ছোট পাখি নয় বরং প্রাপ্তবয়স্কদের পালকই দেখা গিয়েছে। | Gaby Shaktir Motamaad Unujai, AD-1.0.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 | Gaby Shock 3-Motamak 1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1. | Gaby Shaktir Motamaad Unujai, AD-1.0.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 |
| bn_in-07 | আপনি বুঝেছেন কিনা জানি না তবে মধ্য আমেরিকা থেকে এই দেশে বেশিরভাগ পণ্য শুল্কমুক্ত ভাবে এসেছে। | ndo iibe shiir ba kono shun kono shun kono shun kono shiir ba kono shun kono shiir ba kono shun kono shiir ba kono shiir ba | ndo iibe shiir ba kono shun kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono kono | ाी ी ी ी ी ी ी |
| bn_in-08 | বিজ্ঞানীরা মনে করেন যে ওসেলোটগুলি অনুসরণ কর এবং গন্ধ দ্বারা খাওয়া (শিকার) করে, যেখানে তারা মাটিতে চলার সময় ঘ্রাণ নিয়ে প্রাণীদের খুঁজে। | ndo da da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da | ndo da da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da | ndo da da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da ndo da |
| bn_in-09 | জাতিসংঘ বৈশ্বিক উষ্ণতা দ্বারা আক্রান্ত দেশগুলোকে এর প্রভাব মোকাবেলা করতে সাহায্য করার জন্য তহবিল চূড়ান্ত করার ব্যাপারেও আশা করে । | ndoooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo | ndo dhjati shang boshi kusnu tada rapranto dej gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dhj gulakhe ndo dh | ndoooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo |
| bn_in-10 | বিজ্ঞানীরা জানাচ্ছেন সংঘর্ষের ফলে সৃষ্ট বিস্ফোরণ ছিল বিপুল। | We will be in the next few days. | We will be in the next few days. | We will be in the next few days. |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=8 |
| int8_forced_full_exact | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=8 |
| int8_auto_vad_exact | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=7 |

## ta

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| ta_in-01 | இந்த விதிகள் திருத்தப்படுவதற்கு முன்னர் அனைத்து மாநிலங்களின் ஒருமித்த ஒப்புதலையும் கோரியது மற்றும் பெரும்பாலும் அவர்களின் பிரதிநிதிகள் அங்கு இல்லாததால் மாநில அரசு மத்திய அரசை மிகவும் சாதாரணமாக எடுத்துக் கொண்டது. | இந்த விதிருத்தைப் போகுத்துக்கும் திருத்துக்கும் திருத்துக்கும் திருத்துக்கும் திருந்தும் வர | இந்த விதிருத்தைப் போகுத்துக்கும் அனைத்தும் மனில்லைகள் ஒரும் இது உப்புதலைக் கோரியது மற்றும் பெரு | In the video, 3rd of the 4th of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the |
| ta_in-02 | "கருத்து கேட்டபோது, ""விசாரணையின் போது மைக் அதிகளவு பேசுகிறார்.. நான் விசாரணைக்காக தயாராகி கொண்டிருந்ததால் அவர் கூறியதை நான் கேட்கவில்லை"", என்று மில்லர் கூறினார். " | கருத்துக்கேட்ட போகு, விசாரும் என்போது மைக்காதிக்கால் பேசிக்கிறார். நான் வ | கருத்துக்குடைய போகு, மைக்கும் காதில்லையால் பேசிக்குரார். நான் முதல் என்னைக் | ndo kata bode misar ni bode maikadika ala vpsegarar ndo misar ni kaga tayaragi kundirandadalalalalalalalalalalalalalalalalalalalalalalalalal |
| ta_in-03 | பூச்சிகளே காற்றில் பறந்த முதல் விலங்குகள். அவற்றின் பறக்கும் திறன்  பகைவர்களிடமிருந்து எளிதாகத் தப்பவும் உணவு மற்றும் இணைகளை திறமையாகக் கண்டறியவும் உதவுகிறது. | போசுகலைக் காட்டி, பார்ந்து முதல் வில்லாம் ஒரு அவர் திரும் பார்க்கும் பிரும் பார் | போசிக்குளைக் காட்டில் பார்ந்து முதல் வில் வில் வில் வில் வில் வில் வில் வில் வில் வ | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| ta_in-04 | சுற்றுச்சூழலுடன் நாம் மிகவும் இணக்கமாக வாழத் தொடங்கலாம், சுற்றுச்சூழல் இயக்கத்தில் நாம் சேரலாம், எதிர்கால பாதிப்புகளை ஓரளவுக்குக் குறைப்பதற்காக நம்மால் செயல்பாட்டாளர்களாகச் செயல்படமுடியும். | சுற்றுவிட்டுக்கும் நம்மிக்குவும் இருக்கமாக வாலாத்துக்கொண்டும் நம் ச | இது விலையும் வாலையும் வாலையும் வாலையும் வாலையும் வாலையும் வாலையும் வாலைய | சுற்றுவிட்டுக்கும் நம்மிக்குவும் இருக்கமாக வாலாத்துக்கொண்டும் நம் ச |
| ta_in-05 | பல மாவட்டங்கள் சிறிய, வசதியான பலமான,  ஜப்பானிய கோஸ்டார் பேருந்துகளால் சேவை செய்யப்படுகின்றன. | வருமாட்டுங்கள் செய்துவில் வருமானே, ன் பான் என்று கோ | பலமாவுடன் செய்யவில் வருமானே, ன் பார்னியே, கோச்டார் ப | வருமாட்டுங்கள் செய்துவில் வருமானே, ன் பான் என்று கோ |
| ta_in-06 | கிழக்கு ஆப்பிரிக்கத் தீவுகள் இந்தியப் பெருங்கடலில் ஆப்பிரிக்காவின் கிழக்குக் கரையோரமாக உள்ளது. | அப்படிக்கத் திவு வலிந்து என்று எல்லாப் பிருக | அப்படிக்கத் திவு வலிந்து என்று எல்லாப் பிருக | அப்படிக்கத் திவு வலிந்து என்று எல்லாப் பிருக |
| ta_in-07 | புதிய ராஜ்ஜியத்தின் புராதன எகிப்தியர்கள், தங்களின் முன்னோர்கள் கட்டிய நினைவுச் சின்னங்களைக் கண்டு வியந்தனர், அப்போதே அவை ஆயிரம் வருடங்களுக்கு மேல் பழமையானவை. | புதிராத்து என்னுடைய புராத்தான் என்று தங்குரிம் தெரியம் தெர | புதிராத்து என்னுடைய புராத்தானேன் என்று தங்குரின்னும் என்று தன | ndovi ndovi ndovi ndovi ndovi ndovi ndovi ndovi ndovi ndovi ndovi ndovi nd |
| ta_in-08 | தொடக்கத்தில், கிழக்கின் பைசண்ட்டைன் கலாச்சாரத்தால் ஆடைகளில் பெரிய தாக்கம் ஏற்பட்டது. | தொடுக்கு தில்கின் பயசந்தான் கலாத்தால் ஆனைகளைப் பிரியதாக் கமிர்பிர்ப்பத்து. தொடுக்கு தில்கின் பயசந்தான் க | திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்கும் திருக்க | தொடுக்கு தில்கின் பயசந்தான் கலாத்தால் ஆனைகளைப் பிரியதாக் கமிர்பிர்ப்பத்து. தொடுக்கு தில்கின் பயசந்தான் க |
| ta_in-09 | உண்மையான மக்கள் தொகை எப்பொழுதும் மாறப்போவதில்லை முந்தைய காலத்தை போன்று அவர்களுக்கு இன்று தழுவல்கள் தேவைபடுகின்றது. | உன்னை எனக்கு மக்கல் தோவையையைப்பு மாற்போதுலை முந்த | உன்னை எனக்கு மக்கல் தவையையைப்புல் மாருப் போகுதலை முந்தை | உன்னை எனக்கு மக்கல் தோவையையைப்பு மாற்போதுலை முந்த |
| ta_in-10 | பல பொதுவான அமைப்புகள் (உதாரணமாக ஏபிஎஸ் வகை அமைப்புகள்) இந்த இயல்பு விகிதத்திற்கு சமம் அல்லது கிட்டத்தட்ட அருகிலிருப்பது. | இந்த எல்லும் விக்குத்தத்துக்கு சம்மும் அல்லுக்கு எடுத்து தட்டையும் | இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந்த இந் | இந்த எல்லும் விக்குத்தத்துக்கு சம்மும் அல்லுக்கு எடுத்து தட்டையும் |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 1.0 |  |
| int8_forced_full_exact | 1.0 | REPETITION=4 |
| int8_auto_vad_exact | 0.6 | REPETITION=3, WRONG_SCRIPT_LATIN=4 |

## te

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| te_in-01 | చాలా సందర్భాల్లో, విదేశాల్లో ఒక గ్యాప్ ఇయర్ కోర్సులో చేరడం వల్ల మీ స్వంత దేశంలో తిరిగి ఉన్నత విద్యకు వెళ్లే మీ అవకాశాలను మెరుగుపరుచుకోవచ్చు. | ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndrbalu ndr | Ciao! | Y ahora vamos a hablar de la video. |
| te_in-02 | అది నాకు అర్థం కాలేదు. ఖచ్చితంగా న్యాయం కాదు. | dzин õ õ õ õ õ õ õ | ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ | Adina kartan galadu, kacitan kan yang saya melakukan. |
| te_in-03 | Point Marion మరియు Fairmont మధ్య సాగిన Buffalo-Pittsburgh రహదారిలో చాలా సవాలుగా ఉండే డ్రైవింగ్ పరిస్థితులు ఉంటాయి, ఇది నిర్మాణుష్యమైన  backwoods భూభాగం గుండా వెళుతుంది. | nd mario 4 nd magya sagna bhafelho pitsper gratharilu caalas avalga on de driving parisitilu guntai nd remanushe maina backwards booba gun gunta veilutun ni | nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 nd mario 4 | nd mario 4 nd magya sagna bhafelho pitsper gratharilu caalas avalga on de driving parisitilu guntai nd remanushe maina backwards booba gun gunta veilutun ni |
| te_in-04 | ఆర్థోడాక్స్  క్రిస్టియన్ మోల్డోవాలో ప్రధాన మతంగా ఉంది. | õõõõõõõõõõõõõõõõõõõõõõõõ | õõõõõõõõõõõõõõõõõõõõõõõõ | R2.x Christian Mildo value predana matanga on the |
| te_in-05 | "నేను నా సోదరిని ఇంకా ఆమె స్నేహితుడిని కోల్పోయాను, నా మార్గంలో వీల్‌చైర్‌లలో ఇద్దరు వికలాంగులు ఉన్నారు, అందరు వ్యక్తులు దూకుతూ వారిని నెడుతున్నారు"" అని Armand Versace చెప్పారు." | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ わりゃ | ndooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo |
| te_in-06 | వేల మైళ్ల దూరంలో ఉన్న ఎవరినైనా మీరు కాల్ చేసినప్పుడు, మీరు ఒక శాటిలైట్ ని ఉపయోగిస్తున్నారు. | uğu | uğu | Welcome to the second round of the year. |
| te_in-07 | దయచేసి ఈ ప్రదేశాన్ని తగిన విధంగా గౌరవించి, పవిత్రంగా భావించండి. Holocaust లేదా Nazis గురించి వేళాకోళం చేయకండి. | uğucoge kleta nase sgurin chmela column chm | !!!! | uğucoge kleta nase sgurin chmela column chm |
| te_in-08 | కీటకాలు గాలిలోకి తీసుకువెళ్ళే మొదటి జంతువులు. వాటి ఎగిరే సామర్థ్యం శత్రువులను మరింత సులభంగా తప్పించుకోవడానికి, ఆహారం మరియు సహచరులను మరింత సమర్థవంతంగా కనుగొనడంలో సహాయపడింది. | õeeta kaalu garalu kathis kovelle moda igeanthu ulivati õeegre saamadem sithrul on marintusulabanga tappinch kova daanik ki? | õeeta kaalu garalu kathis kovelle moda igeanthu ulivati õeegre saamadem sithrul on marintusulabanga tappinch kova daanik ki? | ndo ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee ee e |
| te_in-09 | ఇది సాధారణ ఆచారంగా మారింది, కానీ ఇనుము బండ్ల యొక్క చెక్క చక్రాలపై మరింత అరుగుదల కలిగించింది. | õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ | õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ | õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ õ |
| te_in-10 | ఉపసంస్కృతి యొక్క సభ్యులు తమ సభ్యత్వాన్ని ఒక విభిన్న మరియు ఆధునికత, పద్దతులు, మరియు యాస భాషలో ఉండే సంకేత రూప శైలితో తెలియపరుస్తారు. | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | õõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõõ | upa sam skuti yaka sub u lhu, upa sam gituani uka vi bintna mario adunikata pathalu mario yasabashalau ne saan keta ru pa shai lhu te liya paru staro |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=6 |
| int8_forced_full_exact | 0.0 | REPETITION=3, TOO_SHORT=2, WRONG_SCRIPT_LATIN=4 |
| int8_auto_vad_exact | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=9 |

## kn

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| kn_in-01 | ಸರಳ ಸುವಾಸನೆ ಭಕ್ಷ್ಯಗಳನ್ನು ಒದಗಿಸುವ ಜಾವಾನೀಸ್ ಪಾಕಪದ್ಧತಿಯನ್ನು ಈಗ ದ್ವೀಪಸಮೂಹದಾದ್ಯಂತ ವ್ಯಾಪಕವಾಗಿ ಬಳಸಲಾಗುತ್ತದೆ. ಜಾವಾನೀಸ್‌ನ ನೆಚ್ಚಿನ ಮಸಾಲೆಗಳು ಕಡಲೆಕಾಯಿ, ಮೆಣಸಿನಕಾಯಿ, ಸಕ್ಕರೆ (ಅದರಲ್ಲೂ ವಿಶೇಷವಾಗಿ ಜಾವಾ ತೆಂಗಿನಕಾಯಿ ಸಕ್ಕರೆ) ಮತ್ತು ವಿವಿಧ ಸುಗಂಧಿತ ಮಸಾಲೆಗಳಿಂದ ಕೂಡಿರುತ್ತದೆ. | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| kn_in-02 | ಸ್ಥಳಕ್ಕೆ ಸಲ್ಲಬೇಕಾದ ಎಲ್ಲ ಘನತೆ, ಸಮಗ್ರತೆ ಮತ್ತು ಗೌರವವನ್ನು ದಯವಿಟ್ಟು ನೀಡಿ. ಹೋಲೋಕಾಸ್ಟ್ ಅಥವಾ  ನಾಜಿಗಳ ಬಗ್ಗೆ ಯಾವುದೇ ಜೋಕ್‌ಗಳನ್ನು ಮಾಡಬೇಡಿ. | nda kha sala be kaada ylga gha rata samagrati matu gaurau wa no daivit ni ni hula cost atta wa nazi ga la baki yau dei jom ka la no mada beidi | alak ke sala be kaada yalla agarate samagrathe matu gaurovanu daivituniri hola cost ata vaanasi galabakhe yawati jom galanu madabidi | nda kha sala be kaada ylga gha rata samagrati matu gaurau wa no daivit ni ni hula cost atta wa nazi ga la baki yau dei jom ka la no mada beidi |
| kn_in-03 | ದೇಶದ ಪ್ರಸಿದ್ಧ ಗಾಯಕರು ಭಜನೆ ಅಥವಾ ಭಕ್ತಿಗೀತೆಗಳನ್ನು ಹಾಡಿ ಶ್ರೀ ಶ್ಯಾಮ್ ಅವರ ಪಾದ ಕಮಲಗಳಿಗೆ ಅರ್ಪಿಸಿದರು. | ndeshwara prasit da gaye karo bhajhane atamabhakti ki kigaran nuhadi ṣrijhavara pada kamalagaliki | Today should proceed to guide the project by ndawabhati ndawabhati ndawabhati ndawabhati ndawabhati ndawabhati nd | ndeshwara prasit da gaye karo bhajhane atamabhakti ghii karan nuhadi ndeshwara ndeshwara ndeshw |
| kn_in-04 | ಚಳಿಗಾಲ ಅತ್ಯಂತ ಶೀತವಾಗಿರುತ್ತದೆ : ಉಷ್ಣಾಂಶವು ಹೆಪ್ಪುಗಟ್ಟಿಸುವಕಿಂತಲೂ ಕೆಳಗೆ ಹೋಗದಿದ್ದರೂ, ಗಾಳಿ ಮತ್ತು ತೇವಾಂಶ ಕೂಡಿ ಥರ್ಮಾಮೀಟರ್  ತೋರಿಸುವುದಕ್ಕಿಂತಲೂ ಶೀತವೆನಿಸುತ್ತದೆ. | hyayayayayayayayayayayayayayayayayayayayayayayayayayaya | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyah | hyayayayayayayayayayayayayayayayayayayayayayayayayayaya |
| kn_in-05 | ಗಾಳಿಯಲ್ಲಿ ಮೊದಲು ಹಾರಿದ ಜೀವಿಗಳೆಂದರೆ ಅವು ಕೀಟಗಳು. ಅವುಗಳ ಹಾರಾಟದ ಸಾಮರ್ಥ್ಯದಿಂದಾಗಿ ಶತ್ರುಗಳಿಂದ ಸುಲಭವಾಗಿ ತಪ್ಪಿಸಿಕೊಳ್ಳಲು ಮತ್ತು ಆಹಾರ ಮತ್ತು ಸಂಗಾತಿಗಳನ್ನು ಹೆಚ್ಚು ಪರಿಣಾಮಕಾರಿಯಾಗಿ ಹುಡುಕಲು ಸಹಾಯ ಮಾಡಿತು. | hhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhārāhār | Galianli, Modano Hari da Jivi galenderu aur ki tevelu aur gala haraat da samarthya da indagi sattru galenderu aur ki tepe sekolalu mattu, ahara matu sanggaat galenderu hai chu pari namakari ayyavdukalu sahaya maditu | hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hārīdha ji v hā |
| kn_in-06 | ಕಾಲಾನಂತರದಲ್ಲಿ, ಹೊಸ ತಲೆಮಾರಿನವರು ತಮ್ಮ ಹೊಸ ಪರಿಸರಕ್ಕೆ ಹೊಂದಿಕೊಳ್ಳಲು ಪ್ರಾರಂಭಿಸಿದಾಗ, ಅವರು ಇತರೆ ಸಮುದಾಯವರ ಕಡೆಗೆ ವಿಶ್ವಾಸದಿಂದ ನೋಡುವುದು ತೀರಾ ಕಡಿಮೆ. | alāyam enteradali khosāyat al-amārīna vrutam khosā parisarakwanda kuru loprāyārambhītāga. | ndi kolaananananananananananananananananananananananananananananananananananananananananananananananananananananananananananananananananan | alāyā m |
| kn_in-07 | ಅದನ್ನು ಮಂಗಳವಾರ ಧರೆಗೆ ಉರುಳಿಸಲು ನಿರ್ಧರಿಸಲಾಗಿತ್ತು, ಆದರೆ ತುರ್ತು ನ್ಯಾಯಾಲಯದ ತೀರ್ಪಿನ ಬಳಿಕ ಅದು ಹಾಗೆಯೇ ಬಿಡಲಾಗಿದೆ. | nda namamala varadharae ge aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur aur | nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda nda | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| kn_in-08 | ಗಾಯಾಳುಗಳನ್ನು ಆಸ್ಪತ್ರೆಗೆ ಸಾಗಿಸಿದ ನಂತರ ಹೋರಾಟ ನಿಲ್ಲಿಸಿದಾಗ, ಉಳಿದ 40 ಕೈದಿಗಳು ಹೊಲದಲ್ಲಿಯೇ ಉಳಿದು ತಮ್ಮ ಕಿರುಕೋಣೆಗಳಿಗೆ ಮರಳಲು ನಿರಾಕರಿಸಿದರು. | ndo aaspatre ke saagis ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily | ndo aaspatre ke saagis ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily si daga ndo hara tandily | では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では |
| kn_in-09 | ವೈಜ್ಞಾನಿಕ ವಿಧಾನದ ಮೂಲಕ ಜಗತ್ತು ಹೇಗೆ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತದೆ ಎಂಬುದನ್ನು ಕಂಡುಹಿಡಿಯುವುದು ವಿಜ್ಞಾನದ ಮುಖ್ಯ ಉದ್ದೇಶವಾಗಿದೆ. ಈ ವಿಧಾನವು ಬಹುತೇಕ ವೈಜ್ಞಾನಿಕ ಸಂಶೋಧನೆಗಳಿಗೆ ಮಾರ್ಗದರ್ಶನ ನೀಡುತ್ತದೆ. | ndo hir yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu yu | Waigna nika vidhana da mulaka, jagant hui kari nirvaisutta de, embudha nukand hui dhyudu, vigna nada mukya ude shavagide, i vidhana mu, bhahutte ka waigna nika sam chodha nagale, maar ka dashana niruttade. | わゆгnanika vidhana-the-mulaka では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では では |
| kn_in-10 | ಈ ಆಕರ್ಷಕ ಹಳ್ಳಿಯಲ್ಲಿ ಅಡ್ಡಾಡಲು ಅರ್ಧ ಗಂಟೆ ಸಮಯ ತೆಗೆದುಕೊಳ್ಳುತ್ತದೆ. | [EMPTY] | ertradi lhoh ertradi lhoh ertradi lhoh ertradi lhoh ertradi | [EMPTY] |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | EMPTY=1, REPETITION=4, WRONG_SCRIPT_LATIN=9 |
| int8_forced_full_exact | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=10 |
| int8_auto_vad_exact | 0.0 | EMPTY=1, REPETITION=5, WRONG_SCRIPT_LATIN=8 |

## ml

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| ml_in-01 | പോയിന്റ് മരിയനും ഫെയർ‌മോണ്ടും തമ്മിലുള്ള ദൂരം ഒറ്റപ്പെട്ട ബാക്ക്‌വുഡ് ഭൂപ്രദേശങ്ങളിലൂടെ കടന്നുപോകുന്ന ബഫല്ലോ-പിറ്റ്സ്ബർഗ് ഹൈവേയിലെ ഏറ്റവും വെല്ലുവിളി നിറഞ്ഞ ഡ്രൈവിംഗ് സാഹചര്യങ്ങൾ സൃഷ്ടിക്കുന്നു. | 4.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 | 4.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5. | 4.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 |
| ml_in-02 | ഈ തത്വങ്ങള്‍ അഭിപ്രായപ്പെടുന്നത് ചില നിശ്ചിത ആവശ്യങ്ങള്‍ കൂടാതെ/അല്ലെങ്കില്‍ സ്വപങ്ങളുള്ളവരാണ്‌ അത് ആന്തരികവല്‍ക്കരിക്കുകയും പ്രായപൂര്‍ത്തിയിലേയ്ക്ക് എത്തിച്ചേരുകയും ചെയ്യൂ എന്നാണ്‌. | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahya | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahya | hyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahyahya |
| ml_in-03 | മെട്രിക് സമ്പ്രദായത്തിന്റെ ഉപയോഗം, ഏകാധിപത്യത്തിൽ നിന്ന് പ്രജാധിപത്യത്തിലേക്കുള്ള മാറ്റം, ദേശീയത, രാജ്യം ഭരണാധികാരിക്കല്ല മറിച്ച്  ജനങ്ങൾക്ക് അവകാശപ്പെട്ടതാണ് എന്നതുപോലെയുള്ള സാമൂഹികവും രാഷ്ട്രീയവുമായ ധാരാളം പ്രഭാവങ്ങൾ അവിടെ ഉണ്ട്. | METIC Sambrathayetinde uba yoghum. 1.5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5- | Mithyxam brother this day eo game 家 家 家 家 家 家 家 家 家 家 家 家 家 家 家 家 家 | METIC Sambrathayetinde uba yoghum. 1.5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5- |
| ml_in-04 | പ്രഖ്യാപനത്തെ യുഎസ് പ്രസിഡന്റ് ജോർജ്ജ് ഡബ്ല്യു ബുഷ് സ്വാഗതം ചെയ്തു. | Pracca, benete, USPresident George W Bush Swagdam, cheidoo | Prank ya benete US pressurring George W Bush Swagdam Cheydu | Pra quem abenetei USPresident George W Bush, suagradam que eu e do |
| ml_in-05 | അവയ്ക്ക് മികച്ച രാത്രിക്കാഴ്ചയുള്ളതിനാല്‍ വളരെ നന്നായി കാണാനാകും, കൂടാതെ വളരെപ്പെട്ടെന്ന് നീങ്ങുകയും ചെയ്യും. ഒസെലോട്ടുകള്‍ അവയുടെ ഇരയെ പിടിക്കുന്നത് ചുറ്റുപാടുകളില്‍ അലിഞ്ഞുചേര്‍ന്ന് അവയ്ക്കുമേല്‍ ചാടിവീണാണ്‌. | 2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2 | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | 2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2.2 |
| ml_in-06 | നമുക്ക് പരിസ്ഥിതിയുമായി കൂടുതൽ സൗഹാർദ്ദപരമായി ജീവിച്ച് തുടങ്ങാനും പരിസ്ഥിതി പ്രസ്ഥാനത്തിൽ ചേരുവാനും കഴിയും, ഒരു പരിധി വരെ ഭാവിയിലെ കഷ്ടപ്പാടുകൾ കുറയ്ക്കുന്നതിനായി പ്രവർത്തിക്കാനും നമുക്ക് സാധിക്കും. | Namuka Parasithi My Coordal Sauhara Paramai Jevi Chituranganam Parasithi Prasithi Prasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Paras | Namuka Paris 3Mai Kourdsal Sauhara Paris 3Mai JV-2 Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3Mai Paris 3M | Namuka Parasithi My Coordal Sauhara Paramai Jevi Chituranganam Parasithi Prasithi Prasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Parasithi Paras |
| ml_in-07 | വീടിനകത്തുതന്നെ കഴിയാനും എയർകണ്ടീഷണറുകൾ ഓഫ് ചെയ്യാനും പൈപ്പ് വെള്ളം കുടിക്കരുതെന്നും പ്രാദേശിക അധികൃതർ പ്ലാന്റിന് സമീപമുള്ള താമസക്കാർക്ക് മുന്നറിയിപ്പ് നൽകുന്നു. | VDN-Eir Foundation will offer change in 5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4 | We will be in our third NECD, Air Conditioner, All-Fuchioner, 5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4 | VDN-Eir Foundation will offer change in 5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4 |
| ml_in-08 | ഭക്ഷിക്കാനുള്ള മൃഗങ്ങളെ (ഇരകളെ) ഓസിലോട്ടുകൾ ഘ്രാണശക്തിയിലൂടെ പിന്തുടർന്ന് കണ്ടെത്തുകയാണെന്ന് ശാസ്ത്രജ്ഞർ കരുതുന്നു,അവ എവിടെയാണ് ഉള്ളതെന്ന് മണത്ത് കണ്ടുപിടിക്കുന്നു. | ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor nd | ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor | ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor ndoor nd |
| ml_in-09 | വിപുലമായി ഉൽപാദിപ്പിക്കപ്പെടുന്ന സമാന ഉൽപന്നങ്ങളേക്കാൾ നൂതനമാണെങ്കിൽ പോലും, കരകൗശല ഉൽപന്നങ്ങളെ പുരാവസ്തുക്കളായാണ് നിർവ്വചിക്കാറുള്ളത്. | ndi pkppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | ndi pkppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp |
| ml_in-10 | എൻഎസ്എ എന്ന നിലയിൽ, 1978-ലെ ക്യാമ്പ് ഡേവിഡ് അക്കോർഡ്സ്; 1970-കളുടെ അവസാനത്തോടെ യുഎസ്-ചൈന ബന്ധം സാധാരണ നിലയിലാക്കൽ; 1979-ലെ ഇറാൻ ബന്ധി പ്രശ്നത്തിലേക്ക് നയിച്ച ഇറാനിയൻ വിപ്ലവം; 1979-ൽ ഉണ്ടായ അഫ്ഗാനിസ്ഥാനിലെ സോവിയറ്റ് അധിനിവേശം എന്നിവ പോലുള്ള ലോക കാര്യങ്ങൾ നയതന്ത്രപരമായി കൈകാര്യം ചെയ്യുന്നതിന് അദ്ദേഹം കാർട്ടറെ സഹായിച്ചു. | NSEE NELEIL, IRETI THORLIRITY ADVATE 80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80- | NSEE NELEIL, IRETI THORLIRITY ADVATE 80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80- | NSEE NELEIL, IRETI THORLIRITY ADVATE 80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80-80- |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 |
| int8_forced_full_exact | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=9 |
| int8_auto_vad_exact | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 |

## mr

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| mr_in-01 | एखादी संस्था नाविन्यपूर्ण होण्यापूर्वी, त्यातील प्रमुखांनी नाविन्यतेची एक संस्कृती निर्माण केली पाहिजे तसेच ज्ञानाची देवाण-घेवाण आणि संस्थात्मक अध्ययन केले पाहिजे. | y'all just sound start 9.9 per hour per week. | y'all sown start 9-9 per hour ne'a pru y y'all pru y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y y | Ya, ini sudah sudah mengenai perhubung. |
| mr_in-02 | तुम्ही अंधारामध्ये पिरॅमिड्स पाहू शकता आणि शो सुरू होण्यापूर्वी शांतपणे त्यांना न्याहाळू शकता. | nda ramadhepepir emir spauhshakta dani shou suro neapuru shantapun ni tennani haru shakta | nda ramad de peremid spau sakta dani sosur onyapur nda pene tennan ni haru sakta | nda ramadepir emir spauh saktadani sosurone apurushanatopanittenan ni haru saktad |
| mr_in-03 | वैज्ञानिक असा विचार करतात की ऑसेलोट प्राण्यांना खाण्यासाठी (भक्ष्य म्हणून), ते जमिनीवर कोठे आहेत हे पाहण्यासाठी त्यांचा पाठलाग आणि शोध त्यांच्या वास, गंधाद्वारे करतात. | Vadna ne kasavi charkarath ki, ose load pranayana khane sati baksya manun. Thes zaminyaur kote a heth hei panya sati thensa parla gane heth shoth ten chav, vas gandhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh | Vaidna ni kasavichar karthat ki, ose loot pranayan na khane sati baksya manul. thet zaminyaur kote a heth hei panya sati thetan sapadla gane heth, kote henshyao, was gandhath wa rekath thet. | Vadna ne kasavi charkarath ki, ose load pranayana khane sati baksya manun. Thes zaminyaur kote a heth hei panya sati thensa parla gane heth shoth ten chav, vas gandhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh |
| mr_in-04 | एखाद्या संकल्पनेवर आधारित जर सुती घालवायची असेल तर अंतर्गत जल वाहतूक ही उतम आहे. | 1. Sankal Panevar Adharid Zarsuti, Ghalwaiji Asil, Teranthar Gajjal Vahatu, Hiyutam Ahi. | 1. Sankal Panevar Adharid Zarsuti, Ghalwaiji Asil, Teran Terga Jalwahatu, Hiyutam Ahi. | 1. Sankal Panevar Adharid Zarsuti, Ghalwaiji Asil, Teranthar Gajjal Vahatu, Hiyutam Ahi. |
| mr_in-05 | पॉइंट मॅरिअन आणि फेअरमॉंट मधील क्षेत्र, विलग असणाऱ्या वन्यक्षेत्रामधून वारंवार जाणाऱ्या बफेलो-पिट्सबर्ग हायवेवर आव्हानात्मक परिस्थिती निर्माण करते. | Point Warrior, namely F-E-R-Mode Model, etc. Willagas scenario, W-E-N-X-E-R-Mode, and W-E-R-Mode. W-E-R-Mode, W-E-R-Mode, and W-E-R-Mode. | Point Warrior, namely F-E-R-Mode Model, etc. Willagas scenario, W-E-N-X-E-R-Mode, and W-E-R-Mode. W-E-R-Mode, W-E-R-Mode, and W-E-R-Mode. | Point Warrior, namely F-Ere mode, model, etc. Willow, SNR, W-NX, etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc., etc |
| mr_in-06 | क्रुगर नॅशनल पार्क (केएनपी) दक्षिण आफ्रिकेच्या उत्तर-पूर्वेला आहे आणि त्याच्या पूर्वेला मोझांबिक, उत्तरेला झिंबाब्वे आणि दक्षिण सीमेला क्रोकोडाईल नदी आहे. | Kruger National Park KMP, Dakshan Africa, Uttar Purvilla. Anintyachap Purvilla, Mujambik Uttarila, Jimbaapoi, Anint, Dakshan Similla, Krugodil Nadia. | Kruger National Park K&P, Dr. Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila Hector Purwila | Kruger National Park KMP, Dakshan Africa, Uttar Purvilla. Anintyachap Purvilla, Mujambik Uttarila, Jimbaapoi, Anint, Dakshan Similla, Krugodil Nadia. |
| mr_in-07 | विज्ञान आता दर्शविते की या प्रचंड कार्बन अर्थव्यवस्थेने गेल्या 2 दशलक्ष वर्षांपासून मानवी उत्क्रांतीला पाठिंबा देणाऱ्या जीवावरणाच्या 1 स्थिर स्थितीत बदल घडवून आणला आहे. | Vidya nata deshwitiki ja praccin ka rabun artavivosti nige da dun dhukshilokshil, voshya pasun manvi utkanti la par timbadi enara, jiva urna ca ekstiristiti buddhul garunanlai. | Within annata dish with the key apprecent of the carbon earth, we will stay in the get a doing introduction. | vidnya nata deshwi te ki yapre chindakarabun artaviu stinni gedeun do kshilokshil, wasya pasun manmi utkantila partin bha dienara jiva urna ca ekstiristitit be del gharun alai |
| mr_in-08 | ऑलिम्पिक पदक विजेता कॉमन वेल्थ खेळामध्ये  100m आणि 200m मुक्त शैली आणि 3 रिले मध्ये पोहणार होता, परंतु त्याच्या तक्रारी मुळे त्याची शारीरिक तंदुरुस्ती शंकास्पद वाटली. | Alambic 5-4-4,000wm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/sm/ | Alambic Padak Vijaytha common wild drama december, 2-3-3-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4- | Alambic 5.4.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9.9. |
| mr_in-09 | प्राचीन रोमन जेवणामध्ये अमेरिका किंवा आशिया तून नंतरच्या शतकामध्ये युरोप मध्ये आलेलं अन्न समाविष्ट करणे शक्य नव्हते. | Pratchin Roman J.V.A.M.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A | Pratchin Roman J.V.A.M.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A.A. | Pratch인 Roman J.V.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M.A.M |
| mr_in-10 | जेव्हा कार धडकली तेव्हा जरी घरामध्ये 3 लोक होते, त्यापैकी कोणीही जखमी झाले नाही. | では | では, では, では, では, では, では, では, では, では, では, では | جوہ وقارد رکلتے وقت ذریق غرام مجتین لوک خودتے اپ |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | TOO_SHORT=1, WRONG_SCRIPT_LATIN=9 |
| int8_forced_full_exact | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| int8_auto_vad_exact | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=9 |

## gu

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| gu_in-01 | તીબેટનો બૌદ્ધવાદ બુદ્ધનાં ઉપદેશો ઉપર આધારિત છે, પરંતુ તેનો પ્રેમના મહાયાન પંથ દ્વારા અને ભારતીય યોગની ઘણી બધી પ્રયુક્તિઓ દ્વારા વિસ્તાર કરવામાં આવ્યો છે. | 3.5-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4- | 3.5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5 | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| gu_in-02 | તમે પ્રવાહમાંથી બહાર આવો પછી, પાછા તરવું એ સામાન્ય રીતે હોય તેના કરતાં વધારે મુશ્કેલ નથી. | So, May Prabhmaathi bahar oud 5-5-5-5-5-5-5-5-5-5-5-5-5 | So, May Prabhmaathi bahar oud 5-5-5-5-5-5-5-5-5-5-5-5-5 | 所以,我们一起玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩玩 |
| gu_in-03 | પુરુષોના સીટીંગ સુપર -જીમાં મેક્સિકોના અર્લી વેલાસ્ક્યુઝે પંદરમાં ક્રમે  પૂર્ણ કર્યું હતું.  પુરુષોના સ્ટેન્ડીંગ સુપર-જીમાં  ન્યૂઝીલેન્ડના આદમ હોલે નવમા ક્રમે પૂર્ણ કર્યું હતું. | Purushona Streetings Supergeema Mexico N' Ali Velas Q. Is a Pandarma Krami Puna Karyu Hathu Purushona Streetings Supergeema New Zealand N' Adam Holendau Krami Puna Karyu Hathu | Purushona Streetings Supergeema Mexico N' Ali Velas Q. Is a Pandarma Krami Puna Karyu Hathu Purushona Streetings Supergeema New Zealand N' Adam Holendau Krami Puna Karyu Hathu | Pura-Symonastrichting Supergeema Mexico Valley Velastrius de panderma krami puna kariyu hathu Pura-Symonastrichting Supergeema New Zealand na Adam Holendau ma krami puna kariyu hathu |
| gu_in-04 | આ ઉદ્યાન 19,500 કિલોમીટર વર્ગનું અંતર કાપે છે અને તેનું વિભાજન 14 અલગ અલગ ઇકોઝોનમાં કરવામાં આવ્યું છે, જે દરેક વન્યજીવોને ટેકો આપે છે. | Audio & Ovenessagear 500 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/ | Audio & 5-10 km/h, 4-10 km/h, 4-10 km/h, 4-10 km/h, 4-10 km/h, 5-10 km/h, 5-10 km/h, 5-10 km/h, 5-10 km/h, 5-10 km | Audio & Ovenessagear 500 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/h, 4.5 km/ |
| gu_in-05 | કૃપા કરીને સાઇટ સાથે તેની યોગ્યતા મુજબ સંપૂર્ણ ગૌરવ, વિચારપૂર્વકતા અને માનથી વર્તન કરો. કત્લેઆમ અથવા નાઝીઓ વિશે રમૂજ ન કરો. | Krupakarini 177, then yogiyatam, Punjab samboot agarov, which are poor work, and manatee, and kao kao kao kao kao kao kao kao kao kao kao kao kao ka | Krupakarini 177, then yogiyatam, Punjab samboot agarov, which are poor work, and manatee, and kao kao kao kao kao kao kao kao kao kao kao kao kao ka | Krupal Karin 1737, now we are going to start the work on the management and the content. |
| gu_in-06 | "ટિપ્પણી કરવા કહેવામાં આવ્યુ ત્યરે મિલરે કહ્યું, ""માઇક ને સાંભળવામાં બહુ વાર લાગે છે.. હું તૈયાર થઈ રહ્યો હતો તેથી તે શું કહી રહ્યો હતો તે હું નહોતો સાંભળી રહ્યો.""" | Tipo dikar va kehva ma avyuttiare. Millorek kahyum, Mike ni samharva ma bahu var la vhe, mutayar thayra hohoto. Tehti desu kohirayohoto, tehun auto samhirayoh. | tigpani karva kehbama avyuttiare millare ka hiyu mightni samharva ma bahuwar la vhe hiyu mutayar thayra hiyu hoto titi tisung hiyra yoh hoto tiyung 1-2 sambri rayu | ndi pani kar vah keh vah mah avu tehre mingle kehre mingle kehre mingle kehre mingle kehre mingle kehre mingle kehre mingle kehre mingle kehre mingle keh |
| gu_in-07 | "તેની સાપેક્ષ સુલભતા સાથે ""ટિંબકટુ"" નો ઉપયોગ વિદેશી, દૂરની જમીનો માટે રૂપક તરીકે થયો છે. " | 10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10- | 10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10- | 10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10-10- |
| gu_in-08 | ફિલિપાઇન્સ જીતવામાં યુ.એસ. સામ્રાજ્યવાદના ખર્ચા ફિલિપિનો લોકોએ પોતે આપ્યા હતા. | Philippine Jthama US Samarajya Vadna, Kharcha Philippine & Lokoi Pote Apiato | Philippine Jthama US Samarajya Vadna, Kharcha Philippine & Lokkoi Poteapia. | Philippine Jthama US Samaraj Jhavadna, Kharcha Philippine & Lokoi Poteapiahato |
| gu_in-09 | તે ચેતવણી આપે છે કે કોઈ બાંહેધરી આપી ન શકે કે અત્યારના સમયે ઇરાકમાં કોઈ પણ ગતિવિધિ સાંપ્રદાયિક યુદ્ધ, વધતી હિંસા અથવા સંપૂર્ણ અંધાધૂંધી તરફનો ઝોક અટકાવશે. | etc. | etc. | iretma koi punn gati video samprodai youtube.insaatva sampeunandadu.tv.jokat kalsai |
| gu_in-10 | નવા રાજ્યના પ્રાચીન ઇજિપ્શિયનો તેમના પૂર્વજોના જે સ્મારકોથી અભિભૂત થતાં તે એ સમયે એક હજાર વર્ષ કરતાં જૂનાં હતાં. | Nava Rajya na Prachin Egyptian, Timna Purva, Jna Jna, Jna Marakoti, Abibut, Thata, T, Eta, Eta, Eta, Eta, Eta, Eta, Eta, Eta, Eta, | Navaraj jena praccene Egyptiano. Timna purva jeno, jessmarra kohti. Nowi budh. Hata. Tee, ee samai, ee ka jahar vrshkatta juna hata. | ndovera jena praccion Egyptiano, tmna purva jeno jesmarako theta t ea samae, ea khajar wa shkatta juna hata |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| int8_forced_full_exact | 0.0 | REPETITION=2, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| int8_auto_vad_exact | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=8 |

## pa

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| pa_in-01 | ਉਪ-ਸੱਭਿਆਚਾਰ ਦੇ ਮੈਂਬਰ ਅਕਸਰ ਸ਼ੈਲੀ ਦੀ ਇੱਕ ਖ਼ਾਸ ਅਤੇ ਸੰਕੇਤਕ ਵਰਤੋਂ ਰਾਹੀਂ ਆਪਣੀ ਸਦੱਸਤਾ ਦਾ ਸੰਕੇਤ ਦਿੰਦੇ ਹਨ, ਜਿਸ ਵਿੱਚ ਫੈਸ਼ਨ, ਸ਼ਿਸ਼ਟਤਾ ਅਤੇ ਗੁਪਤ ਭਾਸ਼ਾ ਸ਼ਾਮਲ ਹੁੰਦੀ ਹੈ। | ndo s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s s | ndihann jiswitch fashion | -19th birthday member Axar Shali di 1 kaas ati sanki thikvertur Rahi, apni, sadastata sanki dundahan just which fashion, sista ati gruph tasha shamal hundi |
| pa_in-02 | ਖੰਭਾਂ ਦੀ ਸੰਰਚਨਾ ਤੋਂ ਪਤਾ ਲਗਦਾ ਹੈ ਕਿ ਉਨ੍ਹਾਂ ਦੀ ਵਰਤੋਂ ਫਲਾਈਟ ਵਿੱਚ ਨਹੀਂ ਕੀਤੀ ਗਈ ਸੀ, ਬਲਕਿ ਤਾਪਮਾਨ ਨਿਯਮ ਜਾਂ ਪ੍ਰਦਰਸ਼ਨ ਲਈ ਕੀਤੀ ਗਈ ਸੀ। ਸ਼ੋਧ ਕਰਤਾ ਨੇ ਸੁਝਾਅ ਦਿੱਤਾ ਹੈ ਕਿ ਭਾਵੇਂ ਇਹ ਇਕ ਯੁਵਾ ਡਾਇਨਾਸੂਰ ਦੀ ਪੂਛ ਹੋਵੇ, ਨਮੂਨਾ ਬਾਲਗ ਪੰਛੀ ਦਾ ਖੰਭ ਦਿਖਾਉਂਦਾ ਹੈ ਅਤੇ ਬੋਟ ਦਾ ਹੇਠਲਾ ਨਹੀਂ। | nda diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya diya di | kambadis, surchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina,-ruchina, | Kabadis, Sarcina,-5th Alagda, khaki, Ona Dev,-5th Alagda, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, khaki, |
| pa_in-03 | ਰੋਗੀ ਨਾਈਜੀਰੀਆ ਗਿਆ ਸੀ, ਜਿੱਥੇ ਈਬੋਲਾ ਵਾਇਰਸ ਦੇ ਕੁਝ ਮਾਮਲੇ ਸਾਹਮਣੇ ਆਏ ਹਨ। | Rokih Najidya Yasi, which is the Ebola virus that we have received from the virus. | Rokih Najidya Yasi, which is the Ebola virus that we have received from the virus. | Rocky Najidia Yasi, which is the Ebola virus that we have received from the virus. |
| pa_in-04 | ਮੇਰੇ ਲਈ ਇਸਦਾ ਕੋਈ ਮਤਲਬ ਨਹੀਂ ਸੀ; ਇਹ ਸਹੀ ਨਹੀਂ ਸੀ। | meraele is da koi matlabnaisi isaineisi | 美食 | miren la y esta coima, la me dice, y esta y la dice |
| pa_in-05 | ਰਿਪੋਰਟ ਖੁੱਲ੍ਹੀ ਬਹਿਸ ਅਤੇ ਸੰਯੁਕਤ ਰਾਸ਼ਟਰ ਵਿੱਚ ਮੱਧ ਪੂਰਵ ਪ੍ਰਤੀ ਨੀਤੀ ਲਈ ਸਹਿਮਤੀ ਨਿਰਮਾਣ ਦੀ ਅਪੀਲ ਨਾਲ ਸ਼ੁਰੂ ਹੁੰਦੀ ਹੈ। | ndiqoorulib asatis interasu whichumatulul prati, netilei senti ni mandi pinal surumati | ndi4bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | iree iree iree iree iree iree iree iree iree iree iree iree iree iree iree |
| pa_in-06 | ਇਹ ਸਿਧਾਂਤ ਦੱਸਦੇ ਹਨ ਕਿ ਲੋਕਾਂ ਦੀਆਂ ਕੁੱਝ ਜ਼ਰੂਰੀ ਲੋੜਾਂ ਅਤੇ/ਜਾਂ ਇੱਛਾਵਾਂ ਹੁੰਦੀਆਂ ਹਨ ਜਿਹਨਾਂ ਦੀ ਪੂਰਤੀ ਕੀਤੀ ਜਾਂਦੀ ਹੈ ਕਿਉਂਕਿ ਉਹ ਜਵਾਨੀ ਵਿੱਚ ਵੱਧ ਹੁੰਦੀਆਂ ਹਨ। | nda zentro kadee kuzur i banae jai chha ma merae jenae kuzur i kuzur i kuzur i kuzur i kuzur i kuzur i | nda zentro kadee kuzur i banae jai chha ma merae jenae kuzur i kuzur i kuzur i kuzur i kuzur i kuzur i | ایس دان دازن تلوکدیں کو ضروری بناتے جائے چاہوں گیا میں جنانے بورتے کی چاہوں گے چاہ |
| pa_in-07 | ਖਾਰੇ-ਪਾਣੀ ਦੇ ਮਗਰਮੱਛ ਸਮੁੰਦਰ ਵਿੱਚ ਸਰਗਰਮੀ ਨਾਲ ਨਹੀਂ ਰਹਿੰਦੇ, ਉਹਨਾਂ ਦਾ ਪ੍ਰਮੁੱਖ ਵਸੇਬਾ ਰਾਕਹੈਮਪਟਨ ਤੋਂ ਉੱਤਰੀ ਨਦੀਂ ਦੇ ਜਵਾਰ ਦਹਾਨਿਆਂ ਵਿੱਚ ਹੁੰਦਾ ਹੈ। | Karipani de magarmaat samundar, which started, garminal, neerandai, onnada prumuk, mashebba, rap, habitant, tuy, butrinadidejjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj | Karipani de magarmaat samundar, which started, garminal, neerande, onnada prumuk, mashebba, rap, habitant, oi, butrin, naidejjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj | Kariminal 9.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 |
| pa_in-08 | ਪ੍ਰਾਚੀਨ ਮਿਸਰ ਦੇ ਨਵੇਂ ਰਾਜ ਆਪਣੇ ਪੁਰਾਣੇ ਸਮਾਰਕਾਂ ‘ਤੇ ਹੈਰਾਨ ਸਨ, ਜਿਹੜੇ ਉਸ ਸਮੇਂ ਹਜਾਰਾਂ ਸਾਲਾਂ ਤੋਂ ਵੀ ਵੱਧ ਪੁਰਾਣੇ ਸਨ। | Prachir Mr. Deen Name Raj apune Puranis Marka Tehhar Ansan, Jede Usamhe Hazaara Sallatu Vy Vud Puranisam. | Prathchir Mr. Deu Raj, Pranis, Marga, Tehhar Anson, Jede Usamheh Zara Sallatovi, Vath Pranisam. | پرچیر مصر دے نمے راج اپنے پرانیس مارکاتے حرانسن جدی اسمے ہزارہ سالات ہوئی برانیسن |
| pa_in-09 | ਕ੍ਰਿਸਮਸ ਇਸਾਈ ਮੱਤ ਦੀਆਂ ਸਭ ਤੋਂ ਮਹੱਤਵਪੂਰਨ ਛੁੱਟੀਆਂ ਵਿੱਚੋਂ ਇੱਕ ਹੈ, ਅਤੇ ਇਸਨੂੰ ਈਸਾ ਮਸੀਹ ਦੇ ਜਨਮਦਿਨ ਦੇ ਰੂਪ ਵਿੱਚ ਮਨਾਇਆ ਜਾਂਦਾ ਹੈ। | Christmas issai Mathidya  sabhtu Mathipulan chutya 北chon ikh hai ate esnu esam sih de janam dandar opubh janam dandar opubh janam ai | !!!! | Christmas, this Imaath India, Subhtom, Hath Appuyaan Chutiya, Vijayam 1.1, аte this nu isam CD, Jannam Dandar opu, which means, Jannam Dandar opu, which means, Jann |
| pa_in-10 | ਦੇਸ਼ ਭਰ ਦੇ ਮਸ਼ਹੂਰ ਗਾਇਕਾਂ ਨੇ ਸ਼੍ਰੀ ਸ਼ਿਆਮ ਦੇ ਚਰਨਾਂ ਵਿੱਚ ਭਜਨ, ਜਾਂ ਭਗਤੀ ਗੀਤ ਭੇਟ ਕੀਤੇ। | 我會不會覺得很喜歡的角度的角度,我就會不會覺得很喜歡的角度 | 我會不會覺得很喜歡的角度的角度,我就會不會覺得很喜歡的角度 | 可以看到你喜歡我的角度的小朋友的話我就會不會給你 |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| int8_forced_full_exact | 0.0 | REPETITION=1, TOO_SHORT=2, WRONG_SCRIPT_LATIN=7 |
| int8_auto_vad_exact | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=7 |

## ur

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| ur_pk-01 | آپ اہرام کو تاریکی میں دیکھ سکتے ہیں اور آپ ان کو شو شروع ہونے سے پہلے کے سکون میں بھی دیکھ سکتے ہیں۔ | اگر ان کو اران کو تاری کمی دیکھتے ہیں اور ان کو شو شو شو شو شو شو شو شو شو شو شو شو شو شو شو | آپ ارام کو تاری کی مدیق ستتے ہیں اور ان کو شوش او مرنسے پہلے کے سکوان میں مدیق ستتے ہیں | أم أرامك وطريكي من ديكساته وأنك وشوش ومعنس أحليكي سكون من ديكساته |
| ur_pk-02 | یہ پارک 19،500 کلومیٹر رقبے پر محیط ہے اور یہ 14 مختلف ماحولیاتی علاقوں میں تقسیم کیا ہوا ہے، جن میں سے ہر علاقہ ایک الگ جنگلی حیات کے لیے سازگار ہے۔ | یہ پار کنی سا جات محاولی آتی لہم ایک ایک جنیے آتی لہم ایک جنیے آتی لہم ایک جنیے آتی لہم ایک کار ساہ | یہ پار کنی ساہد محاولی آتی لہکومت ایک ساہد ہموائے جن میں ساہد ایک یا لن جن لہے آتی لہکار ساہد ہے | 4.5 km/h. 4.5 km/h. 4.5 km/h. 4.5 km/h. 4.5 km/h. 4.5 km/h. 4.5 km/h. |
| ur_pk-03 | پوائنٹ میرین اور فیئرماؤنٹ کے درمیان پھیلاؤ کے سبب بفیلو-پٹسبرگ ہائیوے پر الگ تھلگ اندرونی جنگلی علاقے کے درمیان سے کثرت سے گزرتے ہوئے گاڑی چلانا مشکل بھرا کام ہوتا ہے۔ | ویٹمیلین اور فیر موٹ کے درمیان پر لہو کے سبب بفلو پیس پر حائی بر ارکھتے لن جنگی لاب کو لہتے کے درمیان سے کسرات سے گزرتے ہوئے گا ریزلانا موشکل برکام ہوتا | اور موضع کی درمیان پر لہو کے سبب بفلوپیس برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائی برحائ | ویٹمیلین اور فیر موٹ کے درمیان پر لہو کے سبب بفلو پیس پر حائی بر ارکھتے لن جنگی لاب کو لہتے کے درمیان سے کسرات سے گزرتے ہوئے گا ریزلانا موشکل برکام ہوتا |
| ur_pk-04 | ذیلی ثقافت کے ارکان اکثر اپنی رکنیت کا اشارہ امتیازی اور علامتی طرز، جسمیں فیشن، خاص اسلوب اور بول چال کا استعمال کرتے ہوئے کرتے ہیں۔ | دیالی سکاتھت کے ارکان اچھر اپنی رکھنے کا اشارہ ابدیازی اور ارامتی ترس دس میں فیحشان خ | ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ا | دیالی سکاتھت کے ارکان اچھر اپنی رکھنے کا اشارہ ابدیازی اور ارامتی ترس دس میں فیحشان خ |
| ur_pk-05 | مجھے معلوم نہیں آیا آپ نے محسوس کیا ہے یا نہیں، اس ملک میں سنٹرل امریکا سے آئی زیادہ تر اشیاء ڈیوٹی فری ہیں۔ | مجمالوں نہیں آئے آپ نے محسموس کیا یہاں نہیں ہے اس مطمل سنٹر لمدی کا سعیل عائیزیادتر اشیادی ایوٹی | یہ مالوں نہیں آئے آپ نہیں محسنی ہے اس مورت میں سنٹرونوں کیا سے آئیزیادہ ترہ شیر دیوی تیوی تیوی تیوی تیوی | Maja Malu is not meh, I am a mahsmos, I am a mahsmos, I am a mahsmos, I am a mahsmos, I am a mahsmos, I am a mahsmos, I am |
| ur_pk-06 | وقت گزرنے کے ساتھ ساتھ، جیسا کہ نئی آبادی اپنے نئے ماحول میں ڈھلتی ہے، وہ آہستہ آہستہ دیگر آبادی کی طرح کم دکھنے لگتے ہیں۔ | ہوا ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ای | ہوا ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ای | ہوا ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ایسٹی ای |
| ur_pk-07 | اس کے بنسبت ناقابل رسائی پر مشتمل "ٹمبٹو" کا نام غیر ملکی، دور دراز علاقے کے استعارے کے طور پر استعمال ہوتا تھا۔ | اس کی بنسبتنا کبلے رسائے پر مستمل تمتو کنام غير ملکی دور درازی لا | اس کی بنسبتنا کبلی رسائی پر نوشتمل تم تو کنام غير ملکی دور درازی ل | Eski ben ispehna kabili de sae per nostemil tamtoo ka naangar multi do utaraz ilaki kestari ketor per stemalu |
| ur_pk-08 | کسی بھی ترمیمات یا درخواستوں کو ٹریول ایجنٹ کی معرفت ارسال کیا جائے، نہ کہ براہ راست ہوٹل کو۔ | کسی بھی ترمیماتی در خوستوکورe Travel Agent کے مارفف اٹسال کے جائے ناک | کسی بھی ترمیماتی در خوستوکورe travel agent کے مارففات اٹسال کے جائے ناک | کسی بھی ترمیماتی در خوستوکورe Travel Agent کے مارفف اٹسال کے جائے ناک |
| ur_pk-09 | این ایچ کے نے یہ بھی رپورٹ کیا کہ نائی گاٹا پری فیکچر میں قائم کشیوازاکی کریوا نیوکلیئر پاور پلانٹ معمول کے انداز میں کام کررہا تھا۔ | ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک ایک | ایک ایک نیر برقائق یہاں جائم کسیوزاقی قریوان نکلر بارط مامل کیا دازمی کام کر رہا تھا | NHK has a new report that has a new data prefecture in the game, Kesheva Zaki Kriwan nuclear power plant Mahmool Khandaz has been working in the game. |
| ur_pk-10 | سیال مواد کی مقدار کی بنیاد پر کوئی سالن "خشک" یا "گیلی" ہوسکتا ہے۔ | سیل مبات کی مقضار کی گنیات پر کوئی ساللن خوش پیئی اگیلی | سیل مبات کی مقضار کی گنیان پر کوئی ساللان خوش پیا گیلی ہو سطال | Szeri elmavát kimi, kodárki, gonyát, perkő szállán, hogy fos, hogy a gilího |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.9789 | REPETITION=4, WRONG_SCRIPT_LATIN=1 |
| int8_forced_full_exact | 0.9797 | REPETITION=3, WRONG_SCRIPT_LATIN=1 |
| int8_auto_vad_exact | 0.4789 | REPETITION=3, WRONG_SCRIPT_LATIN=6 |

## or

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| or_in-01 | ହାତୀ ଓ ଜିରାଫ୍‌ ଭଳି ପଶୁମାନଙ୍କର, କାର୍ ଏବଂ ଭଲ ଦେଖାଯାଉଥିବା ମାନକ ସରଞ୍ଜାମକୁ ନିକଟରୁ ଦେଖିବା ପାଇଁ ପାଖକୁ ଆସିବାର ପ୍ରବୃତ୍ତି ଥାଏ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | 3-4-4-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5-5 |
| or_in-02 | ଦୟାକରି ଏହି ସ୍ଥାନକୁ ଯଥାର୍ଥ ସାଧୁତା, ଗମ୍ଭୀରତା ଏବଂ ସମ୍ମାନର ସହିତ ବିଚାର କରନ୍ତୁ। ହୋଲକୋଷ୍ଟ କିମ୍ବା ନାଜିମାନଙ୍କ ବିଷୟରେ ଥଟ୍ଟା କରନ୍ତୁ ନାହିଁ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | 『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『『 |
| or_in-03 | 108 ପ୍ରକାର ଛପନ ଭୋଗ (ହିନ୍ଦୁ ଧର୍ମରେ, 56 ପ୍ରକାର ଖାଦ୍ୟ ସାମଗ୍ରୀ, ଯଥା, ମିଠା, ଫଳ, ବାଦାମ, ବ୍ୟଞ୍ଜନ, ଇତ୍ୟାଦି ଯାହା ଭଗବାନଙ୍କୁ ଅର୍ପଣ କରାଯାଏ) ବାବା ଶ୍ୟାମଙ୍କୁ ଅର୍ପଣ କରଯାଇଥିଲା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | So, today, I will talk about the future of our future. |
| or_in-04 | ବର୍ତ୍ତମାନ ସମଗ୍ର ଆର୍ଚିପେଲାଗୋରେ ବହୁଳ ଭାବରେ ଉପଲବ୍ଧ, ଜାଭାନିଜ୍ ରୋଷେଇ ସମ୍ବନ୍ଧୀୟ ବ୍ୟବସ୍ଥାରେ କେବଳ ଋତୁ ଅନୁସାରେ ଖାଦ୍ୟର ଏକ ଦୀର୍ଘ ବୈଶିଷ୍ଟ୍ୟ ରହିଛି, ଯାହାକି ଜାଭାନିଜ୍ ମାନଙ୍କର ଚିନାବାଦାମ, ଲଙ୍କା, ଚିନି (ବିଶେଷକରି ଜାଭାନିଜ୍ ମାନଙ୍କର ନଡ଼ିଆ ଚିନି) ଏବଂ ବିଭିନ୍ନ ସୁଗନ୍ଧିତ ମସଲା ଗୁଡିକୁ ମିଶାଇ ସ୍ଵାଦ ଦେଇଥାନ୍ତି। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | Borderman Samumurra RcPella Goree Bokulabhaburriu Bolloptho. Jabha Mijrusai Samu Bungi Baba's 3-4-2-3-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4-4- |
| or_in-05 | ପ୍ରାରମ୍ଭରେ ପୋଷାକ ପୂର୍ବର ବାଇଜାଣ୍ଟାଇନ ସଂସ୍କୃତି ଦ୍ୱାରା ବହୁଳ ଭାବରେ ପ୍ରଭାବିତ ହୋଇଥିଲା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | Prahramdure Posape kurbaru, Baalijanta insan scripti dorakobu, babae Prabhito, Uitila |
| or_in-06 | ସଂଶୋଧନ ହେବା ପୂର୍ବରୁ ଆର୍ଟିକଲଗୁଡିକ ସମସ୍ତ ରାଜ୍ୟମାନଙ୍କ ଦ୍ଵାରା ସର୍ବସମ୍ମତିର ଆବଶ୍ୟକ ଥିଲା ଏବଂ ରାଜ୍ୟ ମାନେ କେନ୍ଦ୍ର ସରକାରଙ୍କୁ ଏତେ ହାଲୁକା ଭାବେ ନେଲେ ଯେ ସେମାନଙ୍କର ପ୍ରତିନିଧି ମାନେ ପ୍ରାୟତଃ ଅନୁପସ୍ଥିତ ଥିଲେ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | !!!! |
| or_in-07 | ରବିନ୍ ଉଥାପ୍ପା 11 ଟି ଚୌକା ଓ 2 ଛକା ମାରି 41 ଟି ବଲରେ 70 ରନ୍ କରିଥିଲେ। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | xt ball |
| or_in-08 | ଗୀର୍ଜାଘର ପରିସରରେ, ଅନେକଗୁଡ଼ିଏ ସମାଧି ଉପରେ କପୋତର ଦୃଷ୍ଟି ଆକର୍ଷକ ମାର୍ବଲ ମୂର୍ତ୍ତି ଅଛି। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | ndirjagara poisara de anne guri samadu pude popotara dusti akarsaka marbal mutti ochid |
| or_in-09 | ଆରବମାନେ ମଧ୍ୟ ଇସଲାମକୁ ଦେଶକୁ ଆଣିଥିଲେ, ଏବଂ ଏହା କୋମୋରସ୍ ଏବଂ ମାୟୋଟେରେ ଏକ ପରିବ୍ୟାପ୍ତ ହୋଇଥିଲା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd |
| or_in-10 | ଇରାନରେ ଉଭୟ ନାଗରିକ ଓ ସୈନ୍ୟବାହିନୀ ପାଇଁ ଥିବା ବିମାନଗୁଡ଼ିକ ପୁରୁଣା ହୋଇଥିବା ଓ ସେଥିପାଇଁ ରକ୍ଷଣାବେକ୍ଷଣର ଅଭାବ ଥିବା ଯୋଗୁଁ ସେଠାରେ ବିମାନ ଦୁର୍ଘଟଣା ଏକ ସାଧାରଣ ଘଟଣା। | [WHISPER_TOKEN_MISSING] | [WHISPER_TOKEN_MISSING] | irent ve u boy naka riko sa ni bae ni bae ti bae bi manokurika boduna hoi ti bae u settipayro kranabe kranarobap ti bae ju kun settari bi manokur kranar i |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | None | EMPTY=10 |
| int8_forced_full_exact | None | EMPTY=10 |
| int8_auto_vad_exact | 0.0 | REPETITION=1, TOO_SHORT=1, WRONG_SCRIPT_LATIN=7 |

## as

| # | reference | app_int8_forced_vad_exact | int8_forced_full_exact | int8_auto_vad_exact |
|---|---|---|---|---|
| as_in-01 | এতিয়াও আমাৰ আশী শতাংশ দ্ৰব্যৰ কৰ টাৰিফৰ যোগেদি চেণ্ট্ৰেল আমেৰিকান দেশসমূহসমূহত প্ৰদান কৰা হৈছিল। আমি আপোনাৰ যত্ম লওঁ। | では では では では では では では では では では では では では では では では では では では では では では では では では | ethio アマラхihotang和reboard call the reference of the Central American Day Homehold product アニアプラジオッノ llow | ethio, Amara Hihotang和 Draper called Tariffulsukity Central American Day Homehold, Prudang Kura Hesil Ami A Pular Zotna Long. |
| as_in-02 | বিজ্ঞানে এতিয়া ইংগিত দিছে যে এই বিশাল কাৰ্বন অৰ্থনীতিয়ে আমাৰ জীৱমণ্ডলৰ স্থিৰ অৱস্থাৰ পৰিৱৰ্তন আনিছে যিটো অৱস্থায়ে মানৱ সভ্যতাক বিগত ২ মিলিয়ন বছৰৰ পৰা  সমৰ্থন কৰি আহিছে। | ndmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmd | ndmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmdmd | Bikka has been in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in India in |
| as_in-03 | ২০১০ৰ সাধাৰণ নিৰ্বাচনৰ পূৰ্বে ১,৪০০ গৰাকী ব্যক্তিৰ ভোটগ্ৰহণ কৰা হয়, ২০০৮ চনৰ পৰা অষ্ট্ৰেলিয়া গণতান্ত্ৰিক দেশ হোৱাটো নিবিচৰা লোকৰ সংখ্যা ৮ শতাংশ বৃদ্ধি পালে। | 其实, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, では, | 果是非常有趣的 | ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical ertical |
| as_in-04 | ছাফাৰি অভিযানসমূহ চাগে আফ্ৰিকান পৰ্য্যটনৰ আটাইতকৈ ডাঙৰ আকৰ্ষণ আছিল আৰু বহুতক আকৰ্ষিত কৰিছিল। | 其實我非常喜歡,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以在 African people,所以 | 再次 後面 再次 再次 再次 再次 再次 再次 再次 再次 再次 再次 | Aquí african por una vez que tengo la gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran gran |
| as_in-05 | বহুতো সামাজিক আৰু ৰাজনৈতিক প্ৰভাৱ আছে, যেনে মেট্ৰিক ব্যৱস্থাৰ ব্যৱহাৰ কৰা, নিৰপেক্ষবাদৰ পৰা গণতন্ত্ৰলৈ পৰিৱৰ্তন হোৱা, জাতীয়তাবাদ আৰু এইটো বিশ্বাস যে দেশ ইয়াৰ জনতাৰ বাবেহে, কোনো 1টা শাসকৰ বাবে নহয়। | 物2,我們要先開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k.物2,我們要開始玩k. | Bohutu, how much is it? I have a 3k, I will start the world for a new topic, but other for another totally important one. The 3rd of the 3rd is the 4th of the 3rd is the 4th of the 3rd. | この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3人は、この3 |
| as_in-06 | "কিছুমান প্ৰতিবেদনে অধিকাৰীক মূল্যক আঠত ৰাখি দিলে , আৰু অধিকাৰীক প্ৰতিবেদনে অনুমোদন জনালে যে ৩০ জনলৈকে আহত হৈ গল; কিন্তু শেষ সংখ্যা এতিয়ালৈকে জ্ঞাত নহয় ।""" | ndiqari mullog at hoot rakeh n e ndiqari prote ve do ne ono moton journali jeh 3 januari ke a hoot hul gul kintu hh hul kintu hh hul kintu hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh hh h | ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndiqari mullo ndi | nd he is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is a man who is |
| as_in-07 | মেজৰকেন ব্যঞ্জন মেডিটেৰাণীয়ানৰ বাকীবিলাক ক্ষেত্ৰৰ নিচিনা ৰুটি, শাক-পাচলি আৰু মাংসৰ(বিশেষকৈ গাহৰি মাংস) ওপৰত আধাৰিত, আৰু ইয়াত জলফাইৰ তেল ব্যৱহাৰ কৰা হয়। | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | Mesertgen Vgen Meditori general and Bacchipi lab kept in the journality hack, personally I am also hoping to help you get the whole project. I hope you had some thoughts, we will be very glad. | The major convention is the media community and the community has a speciality and we are going to be a part of the social media community. |
| as_in-08 | প্ৰাচীন ৰোমাণ আহাৰসমূহত শেষৰ শতিকাবোৰত আমেৰিকা বা এছিয়াৰ পৰা ইউৰোপলৈ অহা খাদ্যসমূহ অন্তৰ্ভূক্ত হ’ব নোৱাৰে। | Prasind Roman, Hacor, Hothika, Latin America, etc., etc., etc. | 曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲曲 | para hacer un román a ver cómo recordó la América Latina para irlo en el mundo. |
| as_in-09 | নতুন পৰিবেশত বিভিন্নধৰণৰ উপায় আৰু বিভিন্ন প্ৰতিযোগী আছে, গতিকে নতুন মানুহক বেলেগ বেলেগ বিশেষত্বৰ প্ৰয়োজন হ'ব আৰু তেওঁলোকে আগতে পোৱাতকৈ শক্তিশালী প্ৰতিযোগী গ্ৰহণ কৰিব লাগিব। | we have been not on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been on our own, we have been | [EMPTY] | نو تن پوری وقت بھی بھی نوان اور اپائے رو بھی بھی نو براتیز ہو گیا ہے۔ تو تکے نو تن پوری وقت بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی بھی ب |
| as_in-10 | পৰবৰ্তী, কিছুমান ঘোঁৰাৰ গাদী, বিশেষভাৱে ইংলিছ ঘোঁৰাৰ গাদীত সুৰক্ষা দণ্ডিকা থাকে যিয়ে এজন ঘোঁৰাচালক পাছলৈ পৰি যোৱাৰ সময়ত ধৰি ৰাখিবৰ বাবে গাদীৰ লগত এডাল ৰেকাবী থাকে। | 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名字 名 | Poroborthe human koorahar gadim, behedhabe English koorahar gadim,Poroborok ka dondikahake ghe,Resum koorahar salo,Pasoloi,Porizawa khumoi,Horiakim,Borbabe,Gadil logoth edal,Recapithake | ndo iiwara.com/sumanngharagadim.bhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhbhb |

| variant | mean script ratio | flags |
|---|---:|---|
| app_int8_forced_vad_exact | 0.0 | REPETITION=7, WRONG_SCRIPT_LATIN=7 |
| int8_forced_full_exact | 0.0 | EMPTY=1, REPETITION=2, WRONG_SCRIPT_LATIN=6 |
| int8_auto_vad_exact | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=8 |


### 5.2 Failure classification

Counts below use the exact-config app path (bundled INT8 + forced language + Kotlin-equivalent
VAD). `PLAIN_MISRECOGNITION` means plausible script and length but CER > 0.25; `PLAUSIBLE` means
CER <= 0.25. Classes can overlap (for example a Latin-script loop is both `WRONG_SCRIPT` and
`REPETITION_LOOP`). Full per-clip raw text is in §5.1.
## App-path exact failure classes

| language | class | count | clip IDs |
|---|---|---:|---|
| en | EMPTY | 5 | en_us-04, en_us-05, en_us-06, en_us-08, en_us-10 |
| en | PLAUSIBLE | 5 | en_us-01, en_us-02, en_us-03, en_us-07, en_us-09 |
| en | VAD_OR_TOKEN_REJECT | 5 | en_us-04, en_us-05, en_us-06, en_us-08, en_us-10 |
| hi | EMPTY | 1 | hi_in-04 |
| hi | REPETITION_LOOP | 3 | hi_in-02, hi_in-05, hi_in-06 |
| hi | VAD_OR_TOKEN_REJECT | 1 | hi_in-04 |
| hi | WRONG_SCRIPT | 9 | hi_in-01, hi_in-02, hi_in-03, hi_in-05, hi_in-06, hi_in-07, hi_in-08, hi_in-09, hi_in-10 |
| bn | PLAIN_MISRECOGNITION | 2 | bn_in-02, bn_in-03 |
| bn | REPETITION_LOOP | 4 | bn_in-01, bn_in-04, bn_in-07, bn_in-08 |
| bn | WRONG_SCRIPT | 8 | bn_in-01, bn_in-04, bn_in-05, bn_in-06, bn_in-07, bn_in-08, bn_in-09, bn_in-10 |
| ta | PLAIN_MISRECOGNITION | 10 | ta_in-01, ta_in-02, ta_in-03, ta_in-04, ta_in-05, ta_in-06, ta_in-07, ta_in-08, ta_in-09, ta_in-10 |
| te | PLAIN_MISRECOGNITION | 3 | te_in-04, te_in-05, te_in-10 |
| te | REPETITION_LOOP | 3 | te_in-01, te_in-02, te_in-09 |
| te | TOO_SHORT | 1 | te_in-06 |
| te | WRONG_SCRIPT | 6 | te_in-01, te_in-02, te_in-03, te_in-06, te_in-07, te_in-08 |
| kn | EMPTY | 1 | kn_in-10 |
| kn | REPETITION_LOOP | 4 | kn_in-01, kn_in-07, kn_in-08, kn_in-09 |
| kn | WRONG_SCRIPT | 9 | kn_in-01, kn_in-02, kn_in-03, kn_in-04, kn_in-05, kn_in-06, kn_in-07, kn_in-08, kn_in-09 |
| ml | PLAIN_MISRECOGNITION | 2 | ml_in-01, ml_in-05 |
| ml | REPETITION_LOOP | 2 | ml_in-06, ml_in-08 |
| ml | WRONG_SCRIPT | 8 | ml_in-02, ml_in-03, ml_in-04, ml_in-06, ml_in-07, ml_in-08, ml_in-09, ml_in-10 |
| mr | TOO_SHORT | 1 | mr_in-10 |
| mr | WRONG_SCRIPT | 9 | mr_in-01, mr_in-02, mr_in-03, mr_in-04, mr_in-05, mr_in-06, mr_in-07, mr_in-08, mr_in-09 |
| gu | PLAIN_MISRECOGNITION | 2 | gu_in-01, gu_in-07 |
| gu | REPETITION_LOOP | 3 | gu_in-04, gu_in-05, gu_in-10 |
| gu | TOO_SHORT | 1 | gu_in-09 |
| gu | WRONG_SCRIPT | 8 | gu_in-02, gu_in-03, gu_in-04, gu_in-05, gu_in-06, gu_in-08, gu_in-09, gu_in-10 |
| pa | PLAIN_MISRECOGNITION | 1 | pa_in-10 |
| pa | REPETITION_LOOP | 3 | pa_in-01, pa_in-02, pa_in-06 |
| pa | WRONG_SCRIPT | 9 | pa_in-01, pa_in-02, pa_in-03, pa_in-04, pa_in-05, pa_in-06, pa_in-07, pa_in-08, pa_in-09 |
| ur | PLAIN_MISRECOGNITION | 5 | ur_pk-03, ur_pk-04, ur_pk-05, ur_pk-07, ur_pk-10 |
| ur | REPETITION_LOOP | 4 | ur_pk-01, ur_pk-02, ur_pk-06, ur_pk-09 |
| ur | WRONG_SCRIPT | 1 | ur_pk-08 |
| or | EMPTY | 10 | or_in-01, or_in-02, or_in-03, or_in-04, or_in-05, or_in-06, or_in-07, or_in-08, or_in-09, or_in-10 |
| or | VAD_OR_TOKEN_REJECT | 10 | or_in-01, or_in-02, or_in-03, or_in-04, or_in-05, or_in-06, or_in-07, or_in-08, or_in-09, or_in-10 |
| as | REPETITION_LOOP | 7 | as_in-01, as_in-03, as_in-04, as_in-06, as_in-07, as_in-09, as_in-10 |
| as | WRONG_SCRIPT | 7 | as_in-02, as_in-04, as_in-05, as_in-06, as_in-07, as_in-08, as_in-09 |

Exact-config app-path garbage/unusable: **125 / 130**

## Forced vs true auto language

| language | forced==auto | auto detected `result.lang` values | auto mean CER |
|---|---:|---|---:|
| en | 10/10 | en, en, en, None, None, None, en, None, en, None | 0.0836 |
| hi | 10/10 | hi, hi, hi, None, hi, hi, hi, hi, hi, hi | 1.0581 |
| bn | 8/10 | bn, bn, bn, bn, hi, bn, hi, bn, bn, bn | 1.2048 |
| ta | 6/10 | ml, si, ml, ta, ta, ta, ml, ta, ta, ta | 0.8226 |
| te | 3/10 | es, id, te, si, ml, ml, te, si, te, hi | 0.9434 |
| kn | 4/10 | si, kn, si, si, ml, si, ml, ml, si, kn | 0.9466 |
| ml | 9/10 | ml, ml, ml, pt, ml, ml, ml, ml, ml, ml | 0.9773 |
| mr | 3/10 | id, si, mr, mr, ml, mr, bo, ml, hi, ur | 1.006 |
| gu | 2/10 | ml, bn, si, gu, hi, bn, bn, bn, si, bn | 0.9276 |
| pa | 0/10 | hi, hi, hi, es, te, ur, hi, ur, hi, zh | 0.9922 |
| ur | 4/10 | ar, hi, ur, ur, hi, ur, hi, ur, hi, hu | 0.7642 |
| or | 0/10 | bn, bn, bn, bn, bn, bn, bn, bn, bn, si | 0.9722 |
| as | 0/10 | bn, bn, hi, es, ja, hi, en, es, ur, bn | 1.3265 |

## Exact VAD decisions

| language | rejected | clip IDs |
|---|---:|---|
| en | 5/10 | en_us-04 (0.005676,0ms), en_us-05 (0.007538,0ms), en_us-06 (0.0047,0ms), en_us-08 (0.006195,0ms), en_us-10 (0.009033,60ms) |
| hi | 1/10 | hi_in-04 (0.007111,0ms) |
| bn | 0/10 |  |
| ta | 0/10 |  |
| te | 0/10 |  |
| kn | 0/10 |  |
| ml | 0/10 |  |
| mr | 0/10 |  |
| gu | 0/10 |  |
| pa | 0/10 |  |
| ur | 0/10 |  |
| or | 0/10 |  |
| as | 0/10 |  |
Special-token evidence that the first harness predicate missed: bracketed annotations other than
`[MUSIC]` also occur — `[SUMMING]`, `[SOMI]`, `[speaking in foreign language]`, `[Speaking in English]` —
and language tags such as `<|ur|>`, `<|id|>`, `<|si|>`, `<|hi|>`. The firewall must therefore strip
all bracketed annotation tokens and all `<|...|>` tokens, not just the literal `[MUSIC]`.

### 5.3 Same-model reference and first divergence

Measured comparisons (all 13 measured languages, 10 clips each):

| Comparison | Result |
|---|---|
| Bundled INT8 vs same architecture FP32 ONNX, identical forced language and VAD segment | **10/10 byte-identical text in every language** (130/130 clips). INT8 quantization is not the cause of the garbage. |
| VAD segment vs whole clip, forced language | Near-identical CER (e.g. Hindi 1.0581 vs 1.1258; Tamil 0.7304 vs 0.7428; Urdu 0.5386 vs 0.5456). VAD truncation is not the primary cause. VAD *false reject* is a separate bug on 6 quiet clips (§5.2). |
| `tail_paddings=-1` vs `1000` | No text change in the exact re-run for any clip. Not the cause. |
| Forced language vs true auto language | Forced is required. Auto misidentified every Odia clip as Bengali/Sinhala, every Assamese clip as Bengali/Hindi/Spanish/Japanese/English/Urdu, and 10/10 Punjabi clips as Hindi/Telugu/Urdu/Spanish/Chinese. Forced output is equal to auto output only where auto happened to detect the same language. |
| sherpa-onnx bundled INT8 vs official OpenAI `tiny.pt`, forced language, same WAVs | Both fail at the same rate for every Indic target (mean CER roughly 0.70–1.28; English is the exception at ≈0.09), with the same classes (Latin transliteration, repetition loops, digits/token loops). Exact text matches are rare (0–2/10 per language). The failure is therefore **model capacity / language coverage**, not Android, ONNX Runtime, or a Java/Kotlin binding bug. |
| First divergence | There is no earlier divergence to find: the reference model itself produces the same garbage classes from the same PCM. The first observable divergence is *decoding strategy / tokenizer post-processing* (e.g. Tamil stays in Tamil script in sherpa output while OpenAI mixes Latin transliteration; sherpa emits `[SUMMING]`/`[SOMI]` on tonal audio), and that divergence is downstream of the model's wrong tokens. |

Conclusion: Whisper Tiny is **not a viable STT model for the Indic targets** under the required
thresholds. Keeping it would require the garbage firewall to reject nearly every result. No model
change is made in this diagnosis step; model selection is a later, explicitly gated step.

### 5.4 Adversarial audio
# DIAGNOSIS9 adversarial summary

Source: `C:\dev\Whisly-eval\adversarial_results.jsonl` (40 cases).
Signals: deterministic digital silence, deterministic white noise, a **synthetic tonal
music proxy** (chord plus percussive noise bursts; real recorded music is UNMEASURED),
and pinned FLEURS Tamil speech forced to Hindi.

| category | n | VAD passed | decoder called | false accept | garbage text |
|---|---:|---:|---:|---:|---:|
| silence | 10 | 0 | 0 | 0 | 0 |
| white_noise | 10 | 0 | 0 | 0 | 0 |
| music_proxy | 10 | 10 | 10 | 10 | 5 |
| cross_lang_ta_as_hi | 10 | 10 | 10 | 10 | 9 |

## Raw outputs (forced-Hindi app path)

| category | file | VAD | `result.lang` | raw text | flags |
|---|---|---|---|---|---|
| silence | `silence_01.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_02.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_03.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_04.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_05.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_06.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_07.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_08.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_09.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_10.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| white_noise | `white_noise_01.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_02.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_03.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_04.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm | |
| white_noise | `white_noise_05.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_06.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_07.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_08.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_09.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_10.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| music_proxy | `tonal_proxy_01.wav` | True | hi | [SUMMING] | WRONG_SCRIPT_LATIN |
| music_proxy | `tonal_proxy_02.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_03.wav` | True | hi | [SOMI] [SOMI] [SOMI] [SOM | WRONG_SCRIPT_LATIN |
| music_proxy | `tonal_proxy_04.wav` | True | hi | 播 | TOO_SHORT |
| music_proxy | `tonal_proxy_05.wav` | True | hi | 播 | TOO_SHORT |
| music_proxy | `tonal_proxy_06.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_07.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_08.wav` | True | hi | [SUMMING] | WRONG_SCRIPT_LATIN |
| music_proxy | `tonal_proxy_09.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_10.wav` | True | hi | 播播播播播播 |  |
| cross_lang_ta_as_hi | `01_10105741974673023255.wav` | True | hi | In the video, 3rd of the 4th of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | இந்த வide directamente தங்க்கு அந்த மனுவர உங்கள் ஒரு Theres夢ером traverse அல்லவில் ஒப்பு தெலிக்கோ வளையே அந்தாவிகள் எதை தெரித்து அரச் செய்த வீக்கூமிக்கம் ஒரு மாக வேன்தை சாதார்க்காக Kurtகள். | |
| cross_lang_ta_as_hi | `02_10229295588311841863.wav` | True | hi | Karuhtu keta bode, visar nein bode, maikadika ala vupesi girar. Naan visar nekagata yaaragi kondirandadala verkuriya, nekagla indrimillar kuriya. | WRONG_SCRIPT_LATIN |
|  |  | OpenAI auto `lang=si` | | Kharruti kita bodi,irsearni in bodi um toldung alabupese Alright nest means kagahtayarak harrim Villageirendhada labour ishiihira team are duties and cattlelus Kabine may loose the breed – 1 min padde , a shield of villagers from Kananinday | |
| cross_lang_ta_as_hi | `03_10232827384701487425.wav` | True | hi | Pochika le kartri paranna mudal bilan global our dream parakon puran parai versu dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ml` | | ポ attempting to take Argyllarobil in South Korea.. In pavayver case, its possible for the concept of water Similarly, here areok afar | |
| cross_lang_ta_as_hi | `04_10256197264036976693.wav` | True | hi | Sudhu Chuludan, we may have come and have had a lot of trouble. Sudhu Chuludan, we may have come and have had a lot of trouble. Sudhu Chuludan, we may have had a lot of trouble. We may have had a lot of | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | கணம் வேண்டுமாகசென்றாலாடே செய்து時</v 있다, இரவுசைக் கணமாகசென்றால் வாடில் அச்சுண்டாவரும் சிலப் பாராகலியும். | |
| cross_lang_ta_as_hi | `05_10270431525105329521.wav` | True | hi | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | பலமாவட்டங்கு சிரியே வசதியானே வலமானே, ஏப்பானேனே, கோச்டார் பிருந்துகளால் செய்வி செய்வி செய்வியும் படிக்கிறேன் என்று என்று நன்று நன்று? | |
| cross_lang_ta_as_hi | `06_10339165458009681545.wav` | True | hi | whereas you have been a good idea to become a good idea to become a good idea to become a good idea to become a good idea to become a good idea to become a good idea | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=it` | | Further che av doctrine ilmbieto e ai bewvolt��aria e non v possiamo rinetterna con gli sigli | |
| cross_lang_ta_as_hi | `07_10409567983529131284.wav` | True | hi | Poori Rajithin Pradana, Githyag, Tangarim Manuarul, Kattin, Neem Cheneyag, Kandivin, Neem Naap, Pooriya, Hyram, | WRONG_SCRIPT_LATIN |
|  |  | OpenAI auto `lang=ta` | | சாதானா, அல்லவிய காழந்துவைப் புர்த்தேன் பொறியவர் தங்கிற்கு aroundக்கிழக்கிறீர்கள் மனைந்து இன்று 10-10秒 plusacağல் என்று கணBirகவ가ய்வில் எப்பு ஜிரம் தற்கிறோம் என்றுகொல்லிவிண்டுப் பார்கிற்கு என்றுger- தங்கிれないக்கொள்ளிக்குந்து நினையை பிருக்க்கம் | |
| cross_lang_ta_as_hi | `08_10442244724198740907.wav` | True | hi | 3.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 |  |
|  |  | OpenAI auto `lang=ta` | | திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். | |
| cross_lang_ta_as_hi | `09_10461942282842981790.wav` | True | hi | unmeya na makaltova yappu'l darapovo dali, Monday, Kala-te, Bundra, Avali-leki, Indri, Talu | WRONG_SCRIPT_LATIN |
|  |  | OpenAI auto `lang=ta` | | உன்னை எனக்கு மக்கல் தயவையையையைப் போக்குத்துள்ளை முந்தையை கால்திக் கூறுவிட்டுகள் இந்த தளவுக்குள்ள வித்தேவும் படிக்கிறீர்கள் என்ன? | |
| cross_lang_ta_as_hi | `10_10514055212061494969.wav` | True | hi | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | பலகத்து வாணமாகே போது வாங்கே அமைப்புக்கொள்ளும் இந்த எல்லுவே விக்கத்துக்கு சம்மும் அல்லது கிட்டத்தாட்டே அடிக்கில் இருக்கிறது பலகத்து | |
Real recorded music is **UNMEASURED**: the tonal proxy is a deterministic synthetic chord plus
percussive noise, so the 10/10 false-accept rate applies to that proxy only. The cross-language
false-accept rate (10/10 Tamil clips forced to Hindi) is measured on real pinned FLEURS speech.

## 6. Build verification

No production code was changed in this diagnosis step. The last authoritative build remains the
Step 2 build (`BUILD SUCCESSFUL in 45s`, `51 actionable tasks: 22 executed, 28 from cache,
1 up-to-date`), log `C:\Users\danse\AppData\Local\Temp\opencode\whisly_build_step2_final_retry.txt`.
A fresh `.\gradlew.bat clean assembleDebug testDebugUnitTest` will be run and pasted here if any
production code changes during this step.

## 7. UNMEASURED (phone/human evidence still required)

- On-device WER/CER and garbage rate through the real `AudioRecord` → VAD → native AAR path.
- Device model, Android version, ABI, RAM; cold-start model load time; peak RAM; latency.
- Clean-install airplane-mode proof for ML Kit typed translation.
- TTS voice availability and round-trip WER for every target locale.
- Native-speaker transcript and translation references / chrF++.
- Play Console reported compressed size.

Exact user steps for these items are in `docs/DEVICE_EVIDENCE_STEPS.md` and will be repeated in the
final report for this step.

## 8. Raw logs and pasted output

All generated artifacts and logs are copied verbatim to `whisly/deliverables/diagnosis9/`:

- `diagnosis9_report.md` / `diagnosis9_results.jsonl` — first 6-way matrix, 130 clips.
- `diagnosis9_exact_report.md` / `diagnosis9_exact_results.jsonl` — authoritative app-path re-run.
- `adversarial_summary.md` / `adversarial_results.jsonl` — 40 adversarial cases.
- `fleurs_manifest.json` — 130 pinned clips with WAV SHA-256 and references.
- `whisly_fetch_fleurs.txt`, `whisly_pilot.txt`, `whisly_run_diagnosis.txt`,
  `whisly_exact_verification.txt`, `whisly_adversarial.txt`, `whisly_eval_pip_freeze.txt`.

Exact stdout excerpts (copied from those log files):

```
en_us: tsv_rows=394 indexed=394
en_us: clips=10 scanned=10
...
MANIFEST C:\dev\Whisly-eval\fleurs_manifest.json clips=130
```

```
FROM_WHISPER_DOC:
        ...
          language:
            The spoken language in the audio file. Example values: en, de, zh,
            jp, fr. See https://github.com/openai/whisper/blob/main/whisper/tokenizer.py#L10
            for all possible values. Note that for non-multilingual models, the
            only valid value is 'en'.
```

```
en english
hi hindi
bn bengali
ta tamil
te telugu
kn kannada
ml malayalam
mr marathi
gu gujarati
pa punjabi
ur urdu
or MISSING
as assamese
sat MISSING
```

```
=== en: forced tail=1000, auto tail=1000, auto language=''
...
=== or: forced tail=N/A, auto tail=1000, auto language=''
...
RESULTS=C:\dev\Whisly-eval\diagnosis9_exact_results.jsonl REPORT=C:\dev\Whisly-eval\diagnosis9_exact_report.md clips=130
```

```
silence              silence_01.wav               vad=False zcr_ok out='VAD_REJECT::MICROPHONE_MUTED'
white_noise          white_noise_01.wav           vad=False zcr_ok out='VAD_REJECT::SPEECH_NOT_DETECTED'
music_proxy          tonal_proxy_01.wav           vad=True zcr_ok out='[SUMMING]'
...
cross_lang_ta_as_hi  10_10514055212061494969.wav  vad=True zcr_ok out='nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd'
RESULTS=C:\dev\Whisly-eval\adversarial_results.jsonl cases=40
```

The shell reported `Exit code: 1` for the Python harness runs because sherpa-onnx writes native
C++ warnings to stderr (`Only waves less than 30 seconds are supported...`) and `whisper` writes a
`torch.load(weights_only=False)` FutureWarning; PowerShell surfaces redirected stderr as errors.
The result files were written completely (130 and 40 rows) as shown above.

## 9. Current official capability limits (verified 2026-11-23)

These are fetched from Google's live documentation, not inferred from app code.

### 9.1 ML Kit on-device translation

Source: <https://developers.google.com/ml-kit/language/translation/translation-language-support>
(fetched as Markdown, 2026-11-23). The returned table includes `bn` Bengali, `gu` Gujarati,
`hi` Hindi, `kn` Kannada, `mr` Marathi, `ta` Tamil, `te` Telugu, `ur` Urdu and `en` English.
It does **not** include `ml` Malayalam, `pa` Punjabi, `or` Odia, `as` Assamese or `sat` Santali.

| App language | Translation tag | Verified? |
|---|---|---|
| `en`, `hi`, `bn`, `ta`, `te`, `kn`, `mr`, `gu`, `ur` | same BCP-47 code | YES — present in official table |
| `ml`, `pa`, `or`, `as`, `sat` | `NONE` | YES — absent from official table |

Consequence: translation for those five languages must be registered as `NONE` (typing and speech
still work); there will be no fake transliteration and no online fallback.

### 9.2 ML Kit on-device language identification

Source: <https://developers.google.com/ml-kit/language/identification/langid-support>
(fetched as Markdown, 2026-11-23). The returned table includes `en`, `hi`, `bn`, `ta`, `te`,
`kn`, `ml`, `mr`, `gu`, `pa`, `ur`. It does **not** include `or`, `as` or `sat`.

Consequence: LID tag is `NONE` for `or`, `as`, `sat`; the Whisper `result.lang` value and the
user's selected language remain the only signals there, and a mismatch cannot be arbitrated by
ML Kit LID.

### 9.3 Whisper tokenizer language codes

Measured from the pinned `openai-whisper==20240930` tokenizer in the evaluation venv:

```
en english
hi hindi
bn bengali
ta tamil
te telugu
kn kannada
ml malayalam
mr marathi
gu gujarati
pa punjabi
ur urdu
or MISSING
as assamese
sat MISSING
```

Consequence: the forced-language mode used by `SherpaOnnxSttEngine.kt` cannot express Odia or
Santali with this tokenizer. Those languages must be marked STT `UNAVAILABLE` until a verified
tokenizer/code exists; they must not be silently forced to another language.

### 9.4 Google Play size limits

Source: <https://support.google.com/googleplay/android-developer/answer/9859372?hl=en>
(fetched as Markdown, 2026-11-23). Exact table returned:

| App component | App download size limit |
|---|---|
| Base module | 500MB |
| Individual feature modules | 500MB |
| Individual asset packs | 1.5GB |
| Cumulative total for all modules and install-time asset packs | 4GB |
| Cumulative total for asset packs delivered on-demand or fast-follow | 30GB |

The same page states: limits are **compressed download size** as calculated by Play Console;
apps larger than 1 GB must target API 21+; users on mobile data see a non-blocking large-size
dialog above 200 MB; legacy APK-only publishing is limited to 100 MB APK. Measured Step 2
artifacts: release AAB `87,599,467` bytes, bundletool `MIN,MAX` `81881902,82429589` bytes for
`arm64-v8a`. Both are below the 500 MB base-module limit; **Play Console reported size remains
UNMEASURED** (no console account in scope).

## 10. Source-level capability mismatches found while preparing the firewall

These are **source inspections**, not runtime measurements. Exact locations are recorded so the fix
can be verified later; no production code has been changed for them yet.

| Mismatch | Evidence |
|---|---|
| Registry allows listening for `or` and `sat`, but the decoder has no Whisper token for either. | `CapabilityRegistry.kt:299,359` (`canListen=true`); `SherpaOnnxSttEngine.kt:181-184` (`WHISPER_LANGUAGE_CODES` omits both); `WhislyOfflineSpeechRecognizer.kt:121-124` gates only on the registry. Result: microphone opens, then `UNSUPPORTED_SOURCE_LANGUAGE` is returned after the user speaks. |
| Registry blocks listening for `ur`, `ne`, `sa`, `sd`, although the bundled decoder supports them. | `CapabilityRegistry.kt:380,399,417,553` (`canListen=false`); `SherpaOnnxSttEngine.kt:181-184` includes all four. |
| Registry claims `canIdentify=true` / `mtSupported=true` for `or`, `as`, `sat`, but ML Kit supports neither LID nor translation for those codes. | `CapabilityRegistry.kt:300-301,323-324,358-359`; verified official tables in §9.1/§9.2; actual route is `RealOnDeviceTranslationService.kt:33-46,117-118`. |
| The conversation path fabricates `confidence = outcome.confidence ?: 1.0f` when the engine reports none. | `WhislyViewModel.kt:714`. The engine intentionally reports `null` (`SherpaOnnxSttEngine.kt:91-93`). This invented value must be removed. |
| Two independent mutable stage owners exist. | `WhislyViewModel._pipelineStage` (158-159, 24 write sites) and `SpeechToSpeechPipeline._currentStage` (41-46), copied at 601/818/911. |
| Transcript text is rewritten before confirmation. | `WhislyViewModel.sanitizeTranscript` (395-404) trims, capitalizes and collapses whitespace; conversation path skips it and passes raw text at 713. A single firewall must apply identical, measured cleanup to both. |
| Microphone orb disablement is driven by banner string sniffing (`"pack"`, `"download"`), not by error type. | `MicrophoneButton.kt:88`; required user-facing strings are not produced by that path. |

A full call-site inventory (STT, translation, TTS, language lists, Room entities) was produced by
source exploration and is available in this session's log; the items above are the ones that block
the measured firewall work and are recorded here for traceability.

## 11. End-of-step language coverage (Step 1 diagnosis)

Garbage counts are the exact-config app path from §5.2 (10 pinned FLEURS dev clips per language).
A clip counts once even if it has several failure classes. MT/LID are the official table values
from §9, not app measurements. TTS and all device metrics are `UNMEASURED` (§7). No language has
been dropped; no model has been changed.

| Language | Code | STT clips | Garbage | STT tier (measured) | ML Kit MT | ML Kit LID | TTS |
|---|---|---:|---:|---|---|---|---|
| English | `en` | 10 | 5/10 (all VAD false-rejects) | `UNAVAILABLE` (VAD bug fixable) | YES | YES | UNMEASURED |
| Hindi | `hi` | 10 | 9/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Bengali | `bn` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Tamil | `ta` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Telugu | `te` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Kannada | `kn` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Malayalam | `ml` | 10 | 10/10 | `UNAVAILABLE` | `NONE` | YES | UNMEASURED |
| Marathi | `mr` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Gujarati | `gu` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Punjabi | `pa` | 10 | 10/10 | `UNAVAILABLE` | `NONE` | YES | UNMEASURED |
| Urdu | `ur` | 10 | 10/10 | `UNAVAILABLE` | YES | YES | UNMEASURED |
| Odia | `or` | 10 | 10/10 (no Whisper token) | `UNAVAILABLE` | `NONE` | `NONE` | UNMEASURED |
| Assamese | `as` | 10 | 10/10 | `UNAVAILABLE` | `NONE` | `NONE` | UNMEASURED |
| Santali | `sat` | 0 | — | `UNAVAILABLE` / `UNMEASURED` (§3.1) | `NONE` | `NONE` | UNMEASURED |

`UNAVAILABLE` here means Whisper Tiny does not meet the Step 3 `VERIFIED`/`BETA` thresholds;
typing still works. No tier was lowered to make the model pass.

### Questions only you can answer before Step 2

1. **Santali corpus access (blocked, 3 attempts made):** provide an `HF_TOKEN` with accepted
   access to `ai4bharat/IndicVoices` (`santali/valid`, CC BY 4.0), **or** approve a different
   license-clean Santali speech source. Without this, `sat` stays `UNAVAILABLE` and `UNMEASURED`.
2. **Measurement device:** no phone is attached (`adb devices` unavailable). Provide a device with
   USB debugging, or run `docs/DEVICE_EVIDENCE_STEPS.md` yourself and paste the outputs. On-device
   WER/CER, garbage rate, latency, RAM, cold start and TTS voice availability otherwise stay
   `UNMEASURED`.
3. **Model change approval:** the measured evidence rules out keeping Whisper Tiny for speech.
   May I proceed to a replacement-model *evaluation* (no download or bundling yet), restricted to
   models with a clear permissive license and a measured fit under the Play limit? No model will be
   bundled, fine-tuned or purchased without your explicit approval.
4. **Voice path:** is the temporary, clearly labelled Android system TTS acceptable until the
   neural-voice bake-off, or do you want voices selected first? GPL/non-commercial voices are
   excluded unless you explicitly approve them.
5. **No language removal:** I will not drop Odia, Assamese, Malayalam, Punjabi or Santali. Confirm
   you want them kept as typing-only/`UNAVAILABLE` rather than removed.
