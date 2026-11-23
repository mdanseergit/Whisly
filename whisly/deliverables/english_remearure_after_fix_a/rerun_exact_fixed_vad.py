"""Exact-config STT re-measurement after fix (a), using final_vad for the gate.

Same decoder config as run_exact_verification.py (tail_paddings=1000, forced language,
task=transcribe, no decode after VAD reject). The only change is that the app-path gate is
prototype_vad_v2.final_vad instead of the old app_vad. This script also rebuilds the
pre-roll/voiced/hangover PCM segment using the production thresholds.
"""
from __future__ import annotations

import hashlib
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

import soundfile as sf

from prototype_vad import (
    BANDS_HZ, BAND_MIN_FRACTION, FRAME, HANGOVER, HANN, MAX_NORM_GAIN,
    MAX_SPEECH_FLATNESS, MIN_SPEECH_FLATNESS, MIN_SPEECHLIKE_RATIO,
    NOISE_MAX, NOISE_MIN, NORM_TARGET_PEAK, PREROLL, SILENCE_PEAK_FLOOR,
    SR, START_ABS, START_MUL, STOP_ABS, STOP_MUL, TOTAL_BAND_HZ, ZCR_MAX,
    FREQS, TOTAL_IDX, BAND_IDX,
)
from prototype_vad_v2 import global_accept
from run_diagnosis import (
    INT8, flags, make_rec, script_stats, sherpa_decode, to_pcm16,
)
from summarize_exact import classify

MANIFEST = Path(r"C:\dev\Whisly-eval\fleurs_manifest.json")
OUT = Path(r"C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_results.jsonl")
REPORT = Path(r"C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_report.md")
SUMMARY = Path(r"C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_summary.md")


def mean(xs):
    xs = [x for x in xs if x is not None]
    return round(statistics.mean(xs), 4) if xs else None


def final_vad_segment(pcm) -> dict:
    """Return fixed-VAD decision plus the exact pre-roll/voiced/hangover segment."""
    f = pcm.astype("float32") / 32768.0
    peak = float(max(abs(f))) if len(f) else 0.0
    if len(f) == 0 or peak < SILENCE_PEAK_FLOOR:
        return {
            "passed": False, "reason": "NO_INPUT_OR_MUTED", "voiced_ms": 0,
            "peak": round(peak, 6), "segment": pcm[:0],
        }
    gain = min(MAX_NORM_GAIN, NORM_TARGET_PEAK / max(peak, 1e-9))
    fn = (f * gain).clip(-1.0, 1.0)
    frames = max(1, (len(pcm) + FRAME - 1) // FRAME)
    rms = [0.0] * frames
    zcr = [0.0] * frames
    bands = [0] * frames
    flat = [0.0] * frames
    for k in range(frames):
        seg = fn[k * FRAME:min((k + 1) * FRAME, len(fn))]
        if not len(seg):
            continue
        if len(seg) < FRAME:
            seg = (seg, (0.0,), (FRAME - len(seg),))
            seg = __import__("numpy").concatenate(seg).astype("float32")
        energy = float((seg.astype("float64") ** 2).mean())
        rms[k] = math.sqrt(energy)
        zcr[k] = float(((seg[1:] >= 0) != (seg[:-1] >= 0)).sum()) / max(1, len(seg) - 1)
        spectrum = abs(__import__("numpy").fft.rfft(seg * HANN, n=512)) ** 2
        total = float(spectrum[TOTAL_IDX].sum()) + 1e-20
        bands[k] = sum(1 for idx in BAND_IDX if float(spectrum[idx].sum()) >= BAND_MIN_FRACTION * total)
        band = spectrum[TOTAL_IDX] + 1e-20
        flat[k] = float(__import__("numpy").exp(__import__("numpy").log(band).mean()) / band.mean())

    q = max(1, frames // 4)
    floor = float(__import__("numpy").mean(__import__("numpy").sort(rms)[:q]))
    floor = min(NOISE_MAX, max(NOISE_MIN, floor))
    start_thr = max(START_ABS, floor * START_MUL)
    stop_thr = max(STOP_ABS, floor * STOP_MUL)
    active_cut = 0.25 * max(rms)

    in_speech, hang, voiced, first, last = False, 0, 0, -1, -1
    active_frames = speechlike_frames = 0
    for k in range(frames):
        spectral_ok = bands[k] >= 1 and MIN_SPEECH_FLATNESS <= flat[k] <= MAX_SPEECH_FLATNESS
        if rms[k] >= active_cut and zcr[k] <= ZCR_MAX:
            active_frames += 1
            if spectral_ok:
                speechlike_frames += 1
        energy_start = rms[k] >= start_thr and zcr[k] <= ZCR_MAX
        energy_stop = rms[k] >= stop_thr and zcr[k] <= ZCR_MAX
        if not in_speech:
            if energy_start and spectral_ok:
                in_speech, hang, voiced = True, HANGOVER, voiced + 1
                if first < 0:
                    first = k
                last = k
        else:
            if energy_stop and spectral_ok:
                voiced += 1
                hang = HANGOVER
                last = k
            elif hang > 0:
                hang -= 1
                last = k
            else:
                in_speech = False

    ratio = speechlike_frames / max(1, active_frames)
    base_passed = voiced * 20 >= 120 and speechlike_frames >= 6 and ratio >= MIN_SPEECHLIKE_RATIO
    ok, reason, ffeat, pfeat = global_accept(pcm)
    pre = 0
    if first >= 0:
        pre = min(PREROLL, first) if first > 0 else 0
    s = max(0, first - pre) * FRAME if first >= 0 else 0
    e = min(len(pcm), (last + 1) * FRAME) if last >= 0 else 0
    segment = pcm[s:e] if e > s else pcm[:0]
    return {
        "passed": bool(base_passed and ok), "reason": reason if not ok else "",
        "voiced_ms": voiced * 20, "peak": round(peak, 6),
        "gain": round(gain, 2), "floor": round(floor, 6),
        "speechlike_ratio": round(ratio, 3),
        "global_reason": reason,
        "active": ffeat["active"], "flat_med": ffeat["flat_med"],
        "band2_frac": ffeat["band2_frac"], "band3_frac": ffeat["band3_frac"],
        "harmonicity_med": pfeat["harmonicity_med"],
        "syllable": ffeat["syllable"],
        "segment_samples": int(len(segment)),
        "segment_sha256": hashlib.sha256(segment.tobytes()).hexdigest(),
        "segment": segment,
    }


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    by = defaultdict(list)
    for row in manifest:
        by[row["code"]].append(row)

    results = []
    for code, rows in by.items():
        token_ok = code not in ("or", "sat")
        rec_forced = make_rec(INT8, code) if token_ok else None
        print(f"=== {code}: fixed-VAD exact forced STT ===", flush=True)
        for row in rows:
            audio, sr = sf.read(row["wav_path"], dtype="float32", always_2d=True)
            pcm = to_pcm16(audio[:, 0], sr)
            vad = final_vad_segment(pcm)
            segment = vad.pop("segment")
            vad_ok = bool(vad["passed"]) and len(segment) > 0
            if token_ok and vad_ok:
                variant = sherpa_decode(rec_forced, segment)
            else:
                err = ("WHISPER_TOKEN_MISSING" if not token_ok
                       else "VAD_REJECTED" if not vad["passed"] else "VAD_NO_SEGMENT")
                variant = {"text": "", "error": err}
            results.append({
                "clip_id": row["clip_id"], "code": code, "reference": row["transcript"],
                "wav_sha256": row["wav_sha256"], "vad": vad,
                "variants": {"app_int8_forced_vad_fixed": variant},
            })
            print(f"  {row['clip_id']}: {variant.get('text','')[:80]!r}", flush=True)
        del rec_forced
        with OUT.open("w", encoding="utf-8") as fh:
            for r in results:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    lines = [
        "# Exact-config STT after fix (a)", "",
        "Decoder config unchanged from `diagnosis9_exact_results.jsonl`: bundled Whisper Tiny INT8,",
        "`task=transcribe`, `tail_paddings=1000`, forced language (no auto-detect), no decode after",
        "VAD reject. Only the VAD gate/segment changed to the measured `final_vad`.",
        "",
    ]
    for code, rs in by.items():
        subset = [r for r in results if r["code"] == code]
        lines += [f"## {code}", "", "| # | reference | app_int8_forced_vad_fixed |",
                  "|---|---|---|"]
        for r in subset:
            v = r["variants"]["app_int8_forced_vad_fixed"]
            txt = v.get("text") or f"[{v.get('error','EMPTY')}]"
            lines.append("| " + " | ".join([r["clip_id"], r["reference"].replace("|", "\\|"),
                                            txt.replace("|", "\\|").replace("\n", " ")]) + " |")
        lines.append("")

    OUT.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in results), encoding="utf-8")
    REPORT.write_text("\n".join(lines), encoding="utf-8")

    summary = [
        "# Exact-config STT summary after fix (a)", "",
        f"Source: `{OUT}` ({len(results)} rows).", "",
        "## Per-language app path", "",
        "| language | decoded | mean CER | mean script ratio | flags | garbage/unusable |",
        "|---|---:|---:|---:|---|---:|",
    ]
    by_code = defaultdict(list)
    for r in results:
        by_code[r["code"]].append(r)
    total_garbage = 0
    for code, rs in by_code.items():
        cers, scripts, fc, garbage = [], [], Counter(), 0
        for r in rs:
            v = r["variants"]["app_int8_forced_vad_fixed"]
            txt = v.get("text", "")
            classes, bad, cer = classify(r, "app_int8_forced_vad_fixed")
            garbage += bad
            cers.append(cer)
            if txt:
                scripts.append(script_stats(txt, code)["script_ratio"])
                for f in flags(txt, code):
                    fc[f] += 1
            else:
                fc["EMPTY"] += 1
        total_garbage += garbage
        summary.append(
            f"| {code} | {sum(c is not None for c in cers)}/10 | {mean(cers)} | {mean(scripts)} | " +
            ", ".join(f"{k}={v}" for k, v in sorted(fc.items())) +
            f" | {garbage}/10 |"
        )
    summary += ["", f"Total garbage/unusable after fix (a): **{total_garbage}/{len(results)}**", ""]
    summary += ["## VAD decisions", "", "| language | rejected | reason | segment samples (min-max) |",
                "|---|---:|---|---:|"]
    for code, rs in by_code.items():
        bad = [r for r in rs if not r["vad"]["passed"]]
        segs = [r["vad"]["segment_samples"] for r in rs]
        summary.append(f"| {code} | {len(bad)}/10 | " +
                       ", ".join(f"{r['clip_id']}={r['vad'].get('global_reason','')}" for r in bad) +
                       f" | {min(segs) if segs else 0}-{max(segs) if segs else 0} |")
    SUMMARY.write_text("\n".join(summary), encoding="utf-8")
    print(f"RESULTS={OUT} REPORT={REPORT} SUMMARY={SUMMARY} garbage={total_garbage}/{len(results)}", flush=True)


if __name__ == "__main__":
    main()
