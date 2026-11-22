# Whisly — Backend Plan (planning only, no deploy)

Status: PLANNING. No cloud resources created. Requires user approval before any billing/deployment.

## 1. Stack

- FastAPI (Python 3.12) on Cloud Run (scale-to-zero, cheapest fit) OR a free-tier host if user prefers.
- Dockerfile: multi-stage, non-root user, no secrets in image.
- Tests: pytest outside the image; ruff + mypy in CI.

## 2. Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | /health | Liveness |
| GET | /v1/status | `{online_available, online_paused, reason}` for app routing |
| POST | /v1/translate | NMT (and optional LLM) translation |
| POST | /v1/tts | Synthesize speech, return audio bytes + cache key |
| WS | /v1/stt | Streaming STT with partial results |
| POST | /v1/erase | Delete any stored identifiers (if any) |

## 3. Auth & abuse control

- Play Integrity / Firebase App Check token verification.
- Issue short-lived device tokens (JWT, ~1 h) after integrity check.
- Per-device daily quotas (from COST_MODEL.md free-tier sizing).
- Per-IP rate limits.
- Payload limits: audio ≤ 60 s, text ≤ 5,000 chars.
- Request timeouts; structured errors mapped to plain app messages.

## 4. Privacy

- Process audio/text in memory only. No storage, no logging of content.
- Log only metadata: language pair, character count, status, latency.
- No secrets in repo or app; keys in Secret Manager only.

## 5. Cost control

- GCP Billing budget alerts at 50/80/100% of user-set monthly cap.
- Server-side kill switch → /v1/status returns `online_paused: true`.
- App falls back to offline with "Online translation is paused. Using offline."

## 6. Ops (for user to run)

- `gcloud` commands and Cloud Run deploy steps to be provided in DEPLOY.md after approval.
- CI: lint, type-check, tests on PR.

## 7. STOP AND ASK

I will not create the GCP project, enable billing, or deploy until you explicitly approve.
