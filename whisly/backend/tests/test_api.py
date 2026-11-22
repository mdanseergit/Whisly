"""Unit and API contract tests for the Whisly FastAPI Control Plane."""
import asyncio
from fastapi import HTTPException
import pytest
from app.main import (
    CreateReleaseRequest,
    FeedbackSubmissionRequest,
    LANGUAGES_CATALOGUE,
    MODEL_PACKS_STORE,
    create_release,
    get_health,
    get_model_pack_manifest,
    get_ready,
    list_languages,
    sign_manifest_payload,
    submit_consented_feedback,
)


def test_health_and_ready_endpoints():
    health = asyncio.run(get_health())
    assert health["status"] == "healthy"
    assert health["mode"] == "control_plane_only_no_cloud_translation"

    ready = asyncio.run(get_ready())
    assert ready["status"] == "ready"
    assert ready["languages_count"] == 12
    assert ready["packs_count"] >= 5


def test_languages_catalogue_has_12_indian_languages_and_honest_santali_tts():
    langs = asyncio.run(list_languages())
    assert len(langs) == 12
    codes = {lang.code for lang in langs}
    assert {"ta", "bn", "hi", "te", "kn", "ml", "mr", "gu", "or", "as", "pa", "sat"} == codes

    santali = next(lang for lang in langs if lang.code == "sat")
    assert santali.tts_neural_supported is False


def test_manifest_signature_verification():
    pack = asyncio.run(get_model_pack_manifest("mt_indictrans2_200m_int8"))
    expected_sig = sign_manifest_payload(
        pack.pack_id,
        pack.version,
        pack.sha256_checksum,
        pack.file_size_bytes,
    )
    assert pack.signature_hex == expected_sig


def test_feedback_rejects_unconsented_submission_with_400():
    unconsented = FeedbackSubmissionRequest(
        source_lang="ta",
        target_lang="bn",
        original_stt_text="வணக்கம்",
        corrected_stt_text="வணக்கம்!",
        model_translation="নমস্কার",
        user_consented=False,
    )
    with pytest.raises(HTTPException) as exc_info:
        asyncio.run(submit_consented_feedback(unconsented))
    assert exc_info.value.status_code == 400

    consented = FeedbackSubmissionRequest(
        source_lang="ta",
        target_lang="bn",
        original_stt_text="வணக்கம்",
        corrected_stt_text="வணக்கம்!",
        model_translation="নমস্কার",
        user_consented=True,
    )
    res = asyncio.run(submit_consented_feedback(consented))
    assert res["status"] == "STORED_WITH_CONSENT"
    assert res["raw_audio_retained"] is False


def test_admin_release_creation_requires_system_admin():
    req = CreateReleaseRequest(
        release_tag="v2.1.1-test",
        pack_id="mt_indictrans2_200m_int8",
        version="2.1.1",
    )
    with pytest.raises(HTTPException) as exc_info:
        asyncio.run(create_release(req, role="ORG_ADMIN"))
    assert exc_info.value.status_code == 403
