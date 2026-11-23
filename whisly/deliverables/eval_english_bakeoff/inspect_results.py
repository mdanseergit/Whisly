import json

p = r"C:\dev\Whisly-eval\bakeoff\english_results.jsonl"
rows = [json.loads(x) for x in open(p, encoding="utf-8") if x.strip()]
for name in ["tiny_int8", "base.en_int8", "small.en_int8"]:
    print("=== " + name + " ===")
    for r in rows:
        v = r["variants"][name]
        s = r["scored"][name]
        print(
            r["clip_id"],
            "WER=" + str(s["wer"]),
            "CER=" + str(s["cer"]),
            "flags=" + "+".join(s["flags"]),
            "lp=" + str(v.get("ys_log_probs_len")),
            ":",
            (v.get("text") or "")[:95],
        )
    print("RESULT_FIELDS=" + str(rows[0]["variants"][name]["result_fields"]))
