# Whisly — Cost Model (online mode)

Planning document only. No cloud resources have been created. Prices verified against official Google Cloud pages on 2026-11-21.

## 1. Verified unit prices (USD, as of 2026-11-21)

### Speech-to-Text (online STT)
- API: Cloud Speech-to-Text **V2** (Chirp 2 / Chirp 3). Source: cloud.google.com/speech-to-text/pricing.
- V2 Standard: $0.016 / audio-minute (first 500,000 min/account/month), then $0.010, $0.008, $0.004.
- V2 Dynamic Batch: $0.003 / audio-minute (higher latency, up to 24 h).
- V2 has **no** recurring free minutes (the 60 free min/month belong to V1 only).
- New accounts get up to $300 one-time trial credit.
- 1 second rounding per request; multi-channel billed per channel.

### Translation (online MT)
- API: Cloud Translation Advanced (v3) NMT. Source: cloud.google.com/products/translate/pricing.
- First 500,000 characters/month free (shared Basic+Advanced, applied as $10 credit).
- $20.00 / 1,000,000 characters above that.
- Translation LLM: $10.00 / 1M input + $10.00 / 1M output characters (no free tier).
- Adaptive Translation: $25.00 / 1M input + $25.00 / 1M output.
- Non-English pairs pivot through English in offline mode; online pairs go direct NMT (no pivot charge beyond characters).

### Text-to-Speech (online TTS)
- API: Cloud Text-to-Speech. Source: cloud.google.com/text-to-speech/pricing.
- Standard & WaveNet: $4 / 1M chars, free 4M chars/month (Standard) / 1M (WaveNet).
- Neural2: $16 / 1M chars, free 1M chars/month.
- Chirp 3: HD: $30 / 1M chars, free 1M chars/month.
- Studio: $160 / 1M chars. Instant custom voice: $60 / 1M chars.
- Gemini TTS: priced per token (25 tokens/sec audio), no free tier.
- Chirp 3: HD supports ta-IN, hi-IN, bn-IN, en-IN, en-US etc. (verified list).

### Offline (free, no billing)
- ML Kit on-device Translation: free, no key, models ~30 MB/language via Play Services RemoteModelManager.
- Supports 59 languages incl. en, ta, hi, bn, mr, te, kn, gu, ur, ml, pa, as, or (verified 2026-11-09).
- Non-English pairs pivot through English (quality caveat).

## 2. Cost per sentence (example)

Assume average sentence = 60 characters source, 60 characters target.

- STT (online, 5 s audio): 5/60 min × $0.016 = $0.00133 per sentence.
- MT (online, NMT, 60 chars): 60/1e6 × $20 = $0.00120 per sentence (free under 500k/mo).
- TTS (online, Chirp 3 HD, 60 chars): 60/1e6 × $30 = $0.00180 per sentence (free under 1M/mo).
- Total online per spoken sentence ≈ $0.00433 (after free tiers exhausted).

## 3. Per 1,000 users / month (assumption: 5 spoken sentences/day/user, 30 days)

- Sentences: 1,000 × 5 × 30 = 150,000 sentences/month.
- Audio: 150,000 × 5 s = 750,000 s = 12,500 min → STT $0.016 × 12,500 = $200.00.
- MT chars: 150,000 × 60 = 9,000,000 chars → above 500k free → 8.5M × $20/1M = $170.00.
- TTS chars: 150,000 × 60 = 9,000,000 chars → above 1M free → 8M × $30/1M = $240.00.
- Total ≈ $610.00 / month for 1,000 active users at 5 sentences/day.

Per-device daily quotas to stay inside free tiers:
- Free STT: none recurring on V2 → any online STT costs money. Recommend defaulting Auto mode to offline STT and using online STT only on user opt-in or when offline WER is poor.
- Free MT: 500k chars/month shared → ~8,333 sentences/month free across all users. Per device: ~8 sentences/day free.
- Free TTS: 1M chars/month for Chirp 3 HD → ~16,667 sentences/month free. Per device: ~16 sentences/day free.

## 4. Hard spending cap design

- Server-side kill switch in `/v1/status` returns `online_paused: true` when cumulative monthly cost ≥ cap (user-set, default suggest $50/month).
- App falls back to offline with message "Online translation is paused. Using offline."
- Per-device daily quotas sized from free tiers (above).
- Budget alerts at 50%, 80%, 100% of the monthly cap (GCP Billing budget alerts).
- Per-IP rate limits + payload limits (audio ≤ 60 s, text ≤ 5,000 chars) + request timeouts.

## 5. Offline cost

- $0. No network calls. ML Kit models downloaded once via Play Services (free). Verified by airplane-mode traffic capture (UNMEASURED — steps in DIAGNOSIS8.md §2).

## 6. UNMEASURED

- Real per-sentence latency, WER, chrF++ per engine/direction (needs device + native-speaker review).
- Actual audio durations and character counts in production (estimate only).
- Final monthly cap value (user to set).
