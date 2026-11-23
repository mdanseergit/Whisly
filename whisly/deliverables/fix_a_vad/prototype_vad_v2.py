"""Final measured VAD prototype: normalize -> adaptive floor -> frame validity -> global speech-likeness.

Rules and thresholds are derived from the measured feature distributions in
whisly_vad_feature_separation.txt and whisly_vad_pitch_features.txt (130 pinned FLEURS
speech clips, 10 real CC0 music clips, 10 real CC0 noise clips). No threshold was lowered.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import soundfile as sf

from prototype_vad import fixed_vad
from vad_feature_separation import features as global_features
from vad_pitch_features import pitch_features

MANIFEST = Path(r"C:\dev\Whisly-eval\fleurs_manifest.json")
BAD_OLD = {"en_us-04", "en_us-05", "en_us-06", "en_us-08", "en_us-10", "hi_in-04"}


def global_accept(pcm: np.ndarray) -> tuple[bool, str, dict, dict]:
    f = global_features(pcm)
    p = pitch_features(pcm)
    burst = f["flux_med"] >= 500 and f["band3_frac"] >= 0.20

    if not (f["flat_med"] <= 0.14 and f["band3_frac"] <= 0.60 and
            f["band2_frac"] <= 0.94 and f["high_med"] <= 0.25 and
            f["centroid_med"] <= 2000):
        return False, "SPECTRAL_GLOBAL", f, p
    if p["harmonicity_med"] < 0.35:
        return False, "HARMONICITY", f, p
    temporal = (f["syllable"] >= 0.25 or burst or
                (p["pitch_change_rate"] >= 0.10 and p["harmonicity_med"] >= 0.50 and f["active"] >= 200))
    if not temporal:
        return False, "TEMPORAL", f, p
    if f["active"] < 30 and not (burst or p["harmonicity_med"] >= 0.60):
        return False, "SHORT_NON_SPEECH", f, p
    if f["band2_frac"] < 0.15 and f["flat_med"] >= 0.006:
        return False, "MUSIC_BAND2", f, p
    if f["flat_med"] >= 0.025 and f["band3_frac"] >= 0.20 and f["flux_med"] < 500 and f["zcr_med"] < 0.02:
        return False, "NOISE_FLAT_BAND3", f, p
    if f["flat_med"] >= 0.02 and f["zcr_med"] < 0.02 and f["flux_med"] < 500:
        return False, "NOISE_FLAT_ZCR", f, p
    return True, "", f, p


def final_vad(pcm: np.ndarray) -> tuple[dict, dict, dict]:
    base = fixed_vad(pcm)
    ok, reason, f, p = global_accept(pcm)
    return {**base, "passed": bool(base["passed"] and ok), "global_reason": reason}, f, p


def pcm_from(path: Path) -> np.ndarray:
    audio, sr = sf.read(str(path), dtype="float32", always_2d=True)
    return np.clip(np.rint(audio[:, 0].astype(np.float64) * 32768.0), -32768, 32767).astype(np.int16)


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    print("=== 6 previously rejected quiet speech clips ===")
    for row in manifest:
        if row["clip_id"] in BAD_OLD:
            v, _, _ = final_vad(pcm_from(Path(row["wav_path"])))
            print(f"{row['clip_id']:12s} passed={v['passed']} reason={v.get('global_reason','')}")

    print("=== all 130 pinned FLEURS clips ===")
    passed = 0
    for row in manifest:
        v, _, _ = final_vad(pcm_from(Path(row["wav_path"])))
        passed += bool(v["passed"])
    print(f"passed={passed}/130")

    print("=== synthetic adversarial ===")
    for p in sorted(Path(r"C:\dev\Whisly-eval\adversarial").glob("*.wav")):
        v, _, _ = final_vad(pcm_from(p))
        print(f"{p.name:20s} passed={v['passed']} reason={v.get('global_reason','')}")

    print("=== 20 real CC0 non-speech ===")
    real = json.loads(Path(r"C:\dev\Whisly-eval\real_nonspeech\manifest.json").read_text(encoding="utf-8"))
    for row in real:
        v, _, _ = final_vad(pcm_from(Path(row["local_path"])))
        print(f"{row['category']:5s} {Path(row['local_path']).name:70s} passed={v['passed']} reason={v.get('global_reason','')}")


if __name__ == "__main__":
    main()
