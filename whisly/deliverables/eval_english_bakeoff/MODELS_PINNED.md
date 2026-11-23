# Pinned candidate artifacts (evaluation only — not bundled)

Downloaded with `download_candidates.py` using `huggingface_hub.hf_hub_download(..., revision=<sha>)`.
Raw stdout/stderr: `download_candidates.log`. The SHA-256 values below were computed locally on the
downloaded files and match the LFS `oid` values from the pinned Hugging Face tree API.

Official model list: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/export-onnx.html
("Available models" table lists `base.en` and `small.en`; `.en` models support English only.)

## License evidence

| Artifact chain | License | Official source |
|---|---|---|
| OpenAI Whisper checkpoints (`base.en`, `small.en`) | MIT (Copyright (c) 2022 OpenAI) | https://raw.githubusercontent.com/openai/whisper/main/LICENSE |
| sherpa-onnx ONNX export tooling | Apache-2.0 | https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/LICENSE |

Export method documented by sherpa-onnx: `scripts/whisper/export-onnx.py`.
No GPL/non-commercial engine or voice is involved.

## `base.en`

- Repo: `csukuangfj/sherpa-onnx-whisper-base.en`
- Pinned commit: `59eea950fc76df2453efb57e6c0fd334548e8ffe`
- Gated: no. Public: yes.

| File | Bytes | SHA-256 |
|---|---:|---|
| `base.en-encoder.int8.onnx` | 29,120,534 | `ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823` |
| `base.en-decoder.int8.onnx` | 130,669,978 | `f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41` |
| `base.en-tokens.txt` | 835,554 | `306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930` |
| **INT8 total** | **160,626,066** | |

## `small.en`

- Repo: `csukuangfj/sherpa-onnx-whisper-small.en`
- Pinned commit: `d9533f69affd85061aee349af7fea5cb2996dbbe`
- Gated: no. Public: yes.

| File | Bytes | SHA-256 |
|---|---:|---|
| `small.en-encoder.int8.onnx` | 112,442,483 | `8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f` |
| `small.en-decoder.int8.onnx` | 262,223,042 | `710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb` |
| `small.en-tokens.txt` | 835,554 | `306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930` |
| **INT8 total** | **375,501,079** | |

## Bundled baseline (control, unchanged)

| File | Bytes |
|---|---:|
| `tiny-encoder.int8.onnx` | 12,937,772 |
| `tiny-decoder.int8.onnx` | 89,855,401 |
| `tiny-tokens.txt` | 816,730 |
| **INT8 total** | **103,609,903** |

The downloaded artifacts live in `C:\dev\Whisly-eval\models\` outside the repository.
No candidate file was copied into `whisly/app/src/main/assets/` or any AAB/APK.
