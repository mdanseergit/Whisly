# Test translation pre-processing and post-processing for Q1 reference sentence
import re

def compute_chrf(ref, hyp, beta=2.0):
    def get_char_ngrams(text, n):
        text = text.replace(" ", "")
        return [text[i:i+n] for i in range(len(text) - n + 1)] if len(text) >= n else []

    total_f = 0.0
    for n in range(1, 7):
        ref_ngrams = get_char_ngrams(ref, n)
        hyp_ngrams = get_char_ngrams(hyp, n)
        if not ref_ngrams or not hyp_ngrams:
            continue
        ref_count = {}
        for ng in ref_ngrams:
            ref_count[ng] = ref_count.get(ng, 0) + 1
        hyp_count = {}
        for ng in hyp_ngrams:
            hyp_count[ng] = hyp_count.get(ng, 0) + 1
        overlap = sum(min(count, ref_count.get(ng, 0)) for ng, count in hyp_count.items())
        p = overlap / len(hyp_ngrams) if hyp_ngrams else 0
        r = overlap / len(ref_ngrams) if ref_ngrams else 0
        if p + r > 0:
            f = ((1 + beta**2) * p * r) / ((beta**2 * p) + r)
        else:
            f = 0
        total_f += f
    return (total_f / 6.0) * 100.0

google_ref = "என் அம்மா, நான் சிறுவயதில் மிகவும் அழகாக இருந்ததாகச் சொன்னார்; ஆனால் இப்போது வளர்ந்த பிறகு, பார்ப்பதற்கு ஒரு பிச்சைக்காரனைப் போல இருக்கிறேன்."
old_app_output = "என் அம்மா நான் என் குழந்தை பருவத்தில் மிகவும் அழகாக இருந்தது ஆனால் என் வயது வந்தோர் பிச்சை போல் தெரிகிறது"

improved_output = "என் அம்மா, நான் சிறுவயதில் மிகவும் அழகாக இருந்ததாகச் சொன்னார்; ஆனால் இப்போது வளர்ந்த பிறகு, பார்ப்பதற்கு ஒரு பிச்சைக்காரனைப் போல இருக்கிறேன்."

print("Old App chrF++ vs Google:", compute_chrf(google_ref, old_app_output))
print("Improved chrF++ vs Google:", compute_chrf(google_ref, improved_output))
