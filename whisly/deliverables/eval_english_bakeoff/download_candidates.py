"""Download pinned, public, non-gated sherpa-onnx Whisper English ONNX candidates.

Evaluation only. Nothing is copied into the Android app or bundle.
"""
from __future__ import annotations

import hashlib
import shutil
from pathlib import Path

from huggingface_hub import hf_hub_download

REPOS = {
    "base.en": (
        "csukuangfj/sherpa-onnx-whisper-base.en",
        "59eea950fc76df2453efb57e6c0fd334548e8ffe",
    ),
    "small.en": (
        "csukuangfj/sherpa-onnx-whisper-small.en",
        "d9533f69affd85061aee349af7fea5cb2996dbbe",
    ),
}
DEST_ROOT = Path(r"C:\dev\Whisly-eval\models")


def main() -> None:
    for name, (repo, sha) in REPOS.items():
        dest = DEST_ROOT / name
        dest.mkdir(parents=True, exist_ok=True)
        print(f"=== {name} repo={repo} revision={sha} ===", flush=True)
        for fname in (
            f"{name}-encoder.int8.onnx",
            f"{name}-decoder.int8.onnx",
            f"{name}-tokens.txt",
        ):
            cached = Path(hf_hub_download(repo_id=repo, filename=fname, revision=sha))
            target = dest / fname
            if not target.exists():
                shutil.copy2(cached, target)
            digest = hashlib.sha256(target.read_bytes()).hexdigest()
            print(f"{fname}\t{target.stat().st_size}\t{digest}", flush=True)


if __name__ == "__main__":
    main()
