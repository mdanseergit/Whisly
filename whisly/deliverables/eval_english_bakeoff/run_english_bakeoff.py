"""Evaluation-only Whisper English bake-off: tiny vs base.en vs small.en.

Mirror of the measured production path: final_vad (fix (a)) -> exact VAD segment ->
sherpa-onnx Whisper INT8 decode with task=transcribe, tail_paddings=1000, language=en,
16 kHz/80 features, PCM16/32768 scaling, no decode after VAD reject.

Nothing from this script is copied into the Android app or any bundle.
"""
from __future__ import annotations

import json
import os
import statistics
import sys
from pathlib import Path

import jiwer
import sherpa_onnx
import soundfile as sf

sys.path.insert(0, r"C:\dev\Whisly-eval")

from rerun_exact_fixed_vad import final_vad_segment
from run_diagnosis import flags, script_stats, to_pcm16
from summarize_exact import classify

MANIFEST = Path(r"C:\dev\Whisly-eval\fleurs_manifest.json")
BASELINE = Path(r"C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_results.jsonl")
OUT = Path(r"C:\dev\Whisly-eval\bakeoff\english_results.jsonl")
REPORT = Path(r"C:\dev\Whisly-eval\bakeoff\english_report.md")
SUMMARY = Path(r"C:\dev\Whisly-eval\bakeoff\english_summary.md")

MODELS = {
    "tiny_int8": (
        r"C:\Users\danse\AppData\Local\Temp\opencode\whisper\sherpa-onnx-whisper-tiny",
        "tiny",
    ),
    "base.en_int8": (r"C:\dev\Whisly-eval\models\base.en", "base.en"),
    "small.en_int8": (r"C:\dev\Whisly-eval\models\small.en", "small.en"),
}
NAMES = list(MODELS)


def make_rec(assets: str, prefix: str) -> sherpa_onnx.OfflineRecognizer:
    return sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=os.path.join(assets, f"{prefix}-encoder.int8.onnx"),
        decoder=os.path.join(assets, f"{prefix}-decoder.int8.onnx"),
        tokens=os.path.join(assets, f"{prefix}-tokens.txt"),
        task="transcribe",
        num_threads=2,
        debug=False,
        tail_paddings=1000,
        language="en",
    )


def decode(rec: sherpa_onnx.OfflineRecognizer, pcm) -> dict:
    import time

    t0 = time.perf_counter()
    samples = pcm.astype("float32") / 32768.0
    stream = rec.create_stream()
    stream.accept_waveform(16000, samples)
    rec.decode_stream(stream)
    r = stream.result
    return {
        "text": r.text.strip(),
        "lang": r.lang,
        "tokens": list(r.tokens),
        "ys_log_probs_len": len(r.ys_log_probs),
        "ys_log_probs_sample": [float(x) for x in r.ys_log_probs[:10]],
        "result_fields": [f for f in dir(r) if not f.startswith("_")],
        "latency_ms": round((time.perf_counter() - t0) * 1000, 1),
    }


def mean(xs):
    xs = [x for x in xs if x is not None]
    return round(statistics.mean(xs), 4) if xs else None


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    clips = [r for r in json.loads(MANIFEST.read_text(encoding="utf-8")) if r["code"] == "en"]
    print(f"English clips={len(clips)}", flush=True)

    recs = {}
    for name, (assets, prefix) in MODELS.items():
        print(f"LOAD {name} from {assets}", flush=True)
        recs[name] = make_rec(assets, prefix)

    rows = []
    for row in clips:
        audio, sr = sf.read(row["wav_path"], dtype="float32", always_2d=True)
        pcm = to_pcm16(audio[:, 0], sr)
        vad = final_vad_segment(pcm)
        segment = vad.pop("segment")
        vad_ok = bool(vad["passed"]) and len(segment) > 0
        variants = {}
        for name, rec in recs.items():
            if vad_ok:
                variants[name] = decode(rec, segment)
            else:
                reason = "VAD_REJECTED" if not vad["passed"] else "VAD_NO_SEGMENT"
                variants[name] = {"text": "", "error": reason}
        scored = {}
        for name, v in variants.items():
            txt = v.get("text", "")
            scored[name] = {
                "wer": round(jiwer.wer(row["transcript"], txt), 4) if txt else None,
                "cer": round(jiwer.cer(row["transcript"], txt), 4) if txt else None,
                "script": script_stats(txt, "en") if txt else None,
                "flags": flags(txt, "en") if txt else ["EMPTY"],
            }
        rows.append(
            {
                "clip_id": row["clip_id"],
                "code": "en",
                "reference": row["transcript"],
                "wav_sha256": row["wav_sha256"],
                "vad": vad,
                "variants": variants,
                "scored": scored,
            }
        )
        print(
            "  "
            + row["clip_id"]
            + " | "
            + " | ".join(f"{n}={variants[n].get('text', '')[:55]!r}" for n in NAMES)
            + " | lp="
            + ",".join(str(variants[n].get("ys_log_probs_len", "-")) for n in NAMES),
            flush=True,
        )

    OUT.write_text(
        "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8"
    )

    # Parity control: re-running tiny in this harness must equal the committed fix (a) result.
    baseline = {
        r["clip_id"]: r
        for r in (
            json.loads(line)
            for line in BASELINE.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    }
    parity = [
        r["clip_id"]
        for r in rows
        if baseline.get(r["clip_id"], {})
        .get("variants", {})
        .get("app_int8_forced_vad_fixed", {})
        .get("text", "")
        == r["variants"]["tiny_int8"].get("text", "")
    ]

    lines = [
        "# Whisper English bake-off — raw output",
        "",
        "Generated by `C:\\dev\\Whisly-eval\\bakeoff\\run_english_bakeoff.py`.",
        "Exact production config: `final_vad` segment (fix (a)), INT8 encoder+decoder,",
        "`task=transcribe`, `tail_paddings=1000`, `language=en`, 16 kHz/80 features,",
        "PCM16/32768 scaling, no decode after VAD reject. No text is edited before scoring.",
        "",
        f"Tiny in-process parity with committed `fix_a_vad_stt` results: **{len(parity)}/{len(rows)}**"
        + (f" (mismatch: {[r['clip_id'] for r in rows if r['clip_id'] not in parity]})" if len(parity) != len(rows) else ""),
        "",
        "| # | reference | " + " | ".join(NAMES) + " |",
        "|---|---|" + "---|" * len(NAMES),
    ]
    for r in rows:
        cells = [r["reference"]] + [
            r["variants"][n].get("text") or f"[{r['variants'][n].get('error', 'EMPTY')}]" for n in NAMES
        ]
        cells = [c.replace("|", "\\|").replace("\n", " ") for c in cells]
        lines.append("| " + " | ".join([r["clip_id"]] + cells) + " |")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")

    summary = [
        "# Whisper English bake-off — summary",
        "",
        f"Source: `{OUT}` ({len(rows)} pinned FLEURS `en_us` dev clips).",
        f"Tiny parity with committed fix (a) run: {len(parity)}/{len(rows)}.",
        "",
        "| model | decoded | mean WER | mean CER | mean script ratio | flags | garbage/10 | "
        "ys_log_probs present | mean latency ms (PC CPU) | INT8 bytes |",
        "|---|---:|---:|---:|---:|---|---:|---:|---:|---:|",
    ]
    sizes = {
        "tiny_int8": 12_937_772 + 89_855_401 + 816_730,
        "base.en_int8": 29_120_534 + 130_669_978 + 835_554,
        "small.en_int8": 112_442_483 + 262_223_042 + 835_554,
    }
    for name in NAMES:
        vals = [r["scored"][name] for r in rows]
        classes, garbage_count = [], 0
        for r in rows:
            cls, garbage, _ = classify(r, name)
            garbage_count += bool(garbage)
            classes.extend(cls)
        lp = [r["variants"][name].get("ys_log_probs_len", 0) for r in rows]
        summary.append(
            f"| {name} | {sum(v['wer'] is not None for v in vals)}/10 | {mean([v['wer'] for v in vals])} | "
            f"{mean([v['cer'] for v in vals])} | "
            f"{mean([v['script']['script_ratio'] for v in vals if v['script']])} | "
            + ", ".join(f"{k}={classes.count(k)}" for k in sorted(set(classes)))
            + f" | {garbage_count}/10 | {sum(1 for x in lp if x > 0)}/10 | "
            f"{mean([r['variants'][name].get('latency_ms') for r in rows])} | {sizes[name]:,} |"
        )
    summary += [
        "",
        "## Verdict rules (not lowered for this bake-off)",
        "",
        "- VERIFIED: WER <= 20-25%, garbage <= 1%, noise false-accept <= 2%, AND real per-token "
        "probability plus an explicitly measured acceptance threshold.",
        "- BETA: WER <= 40% with mandatory edit+confirm and a `Beta` label.",
        "- Otherwise UNAVAILABLE.",
        "- Noise false-accept is model-independent: the committed 20-clip CC0 corpus is rejected "
        "20/20 by the shared `final_vad` gate before any decoder runs.",
        "",
    ]
    SUMMARY.write_text("\n".join(summary) + "\n", encoding="utf-8")
    print(f"RESULTS={OUT} REPORT={REPORT} SUMMARY={SUMMARY} tiny_parity={len(parity)}/{len(rows)}", flush=True)


if __name__ == "__main__":
    main()
