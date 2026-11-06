# WHISLY — PHASE 9 BACKEND CONTROL PLANE AUDIT (`BACKEND_AUDIT.md`)

## 1. Scope Audited
- `/backend/Dockerfile`
- `/backend/requirements.txt`
- `/backend/schema.sql` (10 PostgreSQL tables + indexes)
- `/backend/app/models.py` (SQLAlchemy 2.0 ORM models)
- `/backend/app/main.py` (FastAPI Control Plane endpoints)

---

## 2. Issues Identified & Remediation Applied

| File | Issue Found | Severity | Fix Applied |
| :--- | :--- | :--- | :--- |
| `/backend/Dockerfile` | Single-stage image running `uvicorn` as `root`, with no `HEALTHCHECK` instruction. | High (Security / Ops) | Rebuilt as a multi-stage `python:3.12-slim` Dockerfile (`builder` wheel stage + `runtime` stage), created unprivileged system user `whisly` (`UID 10001`), chowned `/srv/whisly`, set `USER whisly`, and added a `/health` `HEALTHCHECK`. |
| `/backend/app/main.py` | Missing `/health` and `/ready` top-level probes (only `/api/v1/health` existed); `LANGUAGES_CATALOGUE` and `MODEL_PACKS_STORE` contained `"VITS-Rasa 13-language neural voice verified"` notes that conflicted with honest labeling of the offline engine. | Medium (Ops & Honest Labeling) | Added `/health` and `/ready` endpoints alongside `/api/v1/health` and `/api/v1/ready`; updated catalogue descriptions to truthful capability notes; enforced strict environment variable secret handling (`WHISLY_MANIFEST_SECRET`) and consent gate (`HTTP 400` when `user_consented == False`). |
| `/backend/tests/` | No automated `pytest` suite existed in `/backend/tests/` to verify `/health`, `/ready`, manifest HMAC-SHA256 signing, RBAC headers, and `HTTP 400` rejection when `user_consented == false`. | High (Testing) | Created `/backend/tests/test_api.py` covering `/health`, `/ready`, `/api/v1/languages` (12 languages), `/api/v1/model-packs`, `/api/v1/feedback` (`400` when `user_consented=False`, `200` when `user_consented=True`), and RBAC enforcement on `/api/v1/admin/releases`. |
| `/backend/schema.sql` & `models.py` | Audited all 10 tables (`users`, `organizations`, `organization_members`, `devices`, `licenses`, `language_packs`, `model_versions`, `model_releases`, `correction_submissions`, `audit_logs`). `correction_submissions` enforces `CHECK (user_consented = TRUE)` at the SQL level in addition to the FastAPI `HTTP 400` guard. | Verified | Preserved schema and added `CheckConstraint("user_consented = true")` in `models.py`. |
