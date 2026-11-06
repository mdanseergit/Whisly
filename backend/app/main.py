from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import os
from typing import List, Optional
from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field

app = FastAPI(
    title="Whisly Control Plane API",
    description=(
        "Privacy-first model catalogue, signed manifest distribution, enterprise license "
        "management, and opt-in correction synchronization. Never accepts unconsented voice or translation text."
    ),
    version="1.0.0",
)


def _get_manifest_signing_secret() -> str:
    secret = os.getenv("WHISLY_MANIFEST_SECRET", "").strip()
    if not secret:
        if os.getenv("WHISLY_ENV", "development").lower() == "production":
            raise RuntimeError("WHISLY_MANIFEST_SECRET environment variable must be set in production.")
        return "whisly-dev-hmac-signing-key-not-for-prod"
    return secret


def sign_manifest_payload(pack_id: str, version: str, sha256_checksum: str, file_size_bytes: int) -> str:
    secret = _get_manifest_signing_secret()
    msg = f"{pack_id}:{version}:{sha256_checksum}:{file_size_bytes}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()


class LanguageInfoSchema(BaseModel):
    code: str
    indic_trans_tag: str
    english_name: str
    native_name: str
    stt_supported: bool
    mt_supported: bool
    tts_neural_supported: bool
    tts_capability_note: str


class ModelPackManifestSchema(BaseModel):
    pack_id: str
    language_code: str
    task_type: str
    model_architecture: str
    version: str
    download_url: str
    sha256_checksum: str
    file_size_bytes: int
    required_runtime: str
    min_memory_mb: int
    license_name: str
    attribution: str
    dependencies: List[str]
    compatibility_info: str
    is_shared_resource: bool
    signature_hex: str


class DeviceRegisterRequest(BaseModel):
    device_fingerprint: str = Field(..., min_length=8, max_length=128)
    organization_code: str = Field(..., min_length=2, max_length=64)
    app_version: str = Field(..., min_length=1, max_length=32)


class LicenseActionRequest(BaseModel):
    device_id: str = Field(..., min_length=3, max_length=128)
    license_key: str = Field(..., min_length=6, max_length=128)


class FeedbackSubmissionRequest(BaseModel):
    source_lang: str = Field(..., min_length=2, max_length=16)
    target_lang: str = Field(..., min_length=2, max_length=16)
    original_stt_text: str = Field(..., min_length=1, max_length=2000)
    corrected_stt_text: str = Field(..., min_length=1, max_length=2000)
    model_translation: str = Field(..., min_length=1, max_length=2000)
    corrected_translation: Optional[str] = Field(default=None, max_length=2000)
    user_consented: bool


class CreateReleaseRequest(BaseModel):
    release_tag: str = Field(..., min_length=2, max_length=64)
    pack_id: str = Field(..., min_length=2, max_length=128)
    version: str = Field(..., min_length=1, max_length=32)
    channel: str = "STABLE"
    release_notes: str = ""


# Catalogue matching the 12 supported Indian languages in the Android registry
LANGUAGES_CATALOGUE: List[LanguageInfoSchema] = [
    LanguageInfoSchema(
        code="ta",
        indic_trans_tag="tam_Taml",
        english_name="Tamil",
        native_name="தமிழ்",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="bn",
        indic_trans_tag="ben_Beng",
        english_name="Bengali",
        native_name="বাংলা",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="hi",
        indic_trans_tag="hin_Deva",
        english_name="Hindi",
        native_name="हिन्दी",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="te",
        indic_trans_tag="tel_Telu",
        english_name="Telugu",
        native_name="తెలుగు",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="kn",
        indic_trans_tag="kan_Knda",
        english_name="Kannada",
        native_name="ಕನ್ನಡ",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="ml",
        indic_trans_tag="mal_Mlym",
        english_name="Malayalam",
        native_name="മലയാളം",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="mr",
        indic_trans_tag="mar_Deva",
        english_name="Marathi",
        native_name="मराठी",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="gu",
        indic_trans_tag="guj_Gujr",
        english_name="Gujarati",
        native_name="ગુજરાતી",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="or",
        indic_trans_tag="ory_Orya",
        english_name="Odia",
        native_name="ଓଡ଼ିଆ",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="as",
        indic_trans_tag="asm_Beng",
        english_name="Assamese",
        native_name="অসমীয়া",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="pa",
        indic_trans_tag="pan_Guru",
        english_name="Punjabi",
        native_name="ਪੰਜਾਬੀ",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=True,
        tts_capability_note="On-device & formant TTS supported",
    ),
    LanguageInfoSchema(
        code="sat",
        indic_trans_tag="sat_Olck",
        english_name="Santali",
        native_name="ᱥᱟᱱᱛᱟᱲᱤ",
        stt_supported=True,
        mt_supported=True,
        tts_neural_supported=False,
        tts_capability_note="Offline TTS unavailable for Santali (text translation only)",
    ),
]


def _build_pack(
    pack_id: str,
    lang: str,
    task: str,
    arch: str,
    version: str,
    size_bytes: int,
    license_name: str,
    attribution: str,
    deps: List[str],
    shared: bool,
) -> ModelPackManifestSchema:
    checksum = hashlib.sha256(f"{pack_id}:{version}:{size_bytes}".encode("utf-8")).hexdigest()
    sig = sign_manifest_payload(pack_id, version, checksum, size_bytes)
    return ModelPackManifestSchema(
        pack_id=pack_id,
        language_code=lang,
        task_type=task,
        model_architecture=arch,
        version=version,
        download_url=f"https://models.whisly.ai/v1/packs/{pack_id}.onnx.pack",
        sha256_checksum=checksum,
        file_size_bytes=size_bytes,
        required_runtime="ONNX Runtime Mobile >= 1.18",
        min_memory_mb=512 if shared else 384,
        license_name=license_name,
        attribution=attribution,
        dependencies=deps,
        compatibility_info="ARM64-v8a / armeabi-v7a Int8 quantized",
        is_shared_resource=shared,
        signature_hex=sig,
    )


MODEL_PACKS_STORE = {
    "mt_indictrans2_200m_int8": _build_pack(
        pack_id="mt_indictrans2_200m_int8",
        lang="indic_shared",
        task="MT",
        arch="Shared Indic Translation Pack",
        version="2.1.0",
        size_bytes=194_510_848,
        license_name="MIT License",
        attribution="AI4Bharat / IIT Madras",
        deps=["spm_indic_32k_vocab", "indic_nlp_normalizer"],
        shared=True,
    ),
    "stt_indicconformer_ta_v2": _build_pack(
        pack_id="stt_indicconformer_ta_v2",
        lang="ta",
        task="STT",
        arch="Tamil Speech Recognition Pack",
        version="2.1.0",
        size_bytes=64_487_424,
        license_name="CC-BY-4.0 / MIT",
        attribution="AI4Bharat",
        deps=["mel_filterbank_16khz"],
        shared=False,
    ),
    "tts_vits_rasa_ta_v1": _build_pack(
        pack_id="tts_vits_rasa_ta_v1",
        lang="ta",
        task="TTS",
        arch="Tamil Voice Synthesis Pack",
        version="2.1.0",
        size_bytes=43_830_272,
        license_name="CC-BY-4.0",
        attribution="AI4Bharat",
        deps=["indic_g2p_phonemizer_ta"],
        shared=False,
    ),
    "stt_indicconformer_bn_v2": _build_pack(
        pack_id="stt_indicconformer_bn_v2",
        lang="bn",
        task="STT",
        arch="Bengali Speech Recognition Pack",
        version="2.1.0",
        size_bytes=63_963_136,
        license_name="CC-BY-4.0 / MIT",
        attribution="AI4Bharat",
        deps=["mel_filterbank_16khz"],
        shared=False,
    ),
    "tts_vits_rasa_bn_v1": _build_pack(
        pack_id="tts_vits_rasa_bn_v1",
        lang="bn",
        task="TTS",
        arch="Bengali Voice Synthesis Pack",
        version="2.1.0",
        size_bytes=44_249_088,
        license_name="CC-BY-4.0",
        attribution="AI4Bharat",
        deps=["indic_g2p_phonemizer_bn"],
        shared=False,
    ),
}

RELEASES_STORE = [
    {
        "release_id": "rel_2026_11_indic_210",
        "release_tag": "v2.1.0-indic-offline",
        "pack_id": "mt_indictrans2_200m_int8",
        "version": "2.1.0",
        "channel": "STABLE",
        "is_published": True,
        "release_notes": "Shared Indic translation pack + Tamil/Bengali speech packs.",
    }
]


def verify_admin_role(x_whisly_role: Optional[str] = Header(default="ORG_ADMIN")) -> str:
    if x_whisly_role not in ("SYSTEM_ADMIN", "ORG_ADMIN"):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Insufficient role permissions for organization/system admin endpoint.",
        )
    return x_whisly_role


@app.get("/health")
@app.get("/api/v1/health")
async def get_health():
    return {
        "status": "healthy",
        "service": "whisly-control-plane",
        "version": "1.0.0",
        "mode": "control_plane_only_no_cloud_translation",
    }


@app.get("/ready")
@app.get("/api/v1/ready")
async def get_ready():
    catalogue_ready = len(LANGUAGES_CATALOGUE) == 12 and len(MODEL_PACKS_STORE) > 0
    if not catalogue_ready:
        raise HTTPException(status_code=503, detail="Control plane catalogue not ready")
    return {
        "status": "ready",
        "languages_count": len(LANGUAGES_CATALOGUE),
        "packs_count": len(MODEL_PACKS_STORE),
    }


@app.get("/api/v1/languages", response_model=List[LanguageInfoSchema])
async def list_languages():
    return LANGUAGES_CATALOGUE


@app.get("/api/v1/model-packs", response_model=List[ModelPackManifestSchema])
async def list_model_packs():
    return list(MODEL_PACKS_STORE.values())


@app.get("/api/v1/model-packs/{pack_id}", response_model=ModelPackManifestSchema)
async def get_model_pack(pack_id: str):
    pack = MODEL_PACKS_STORE.get(pack_id)
    if not pack:
        raise HTTPException(status_code=404, detail=f"Model pack '{pack_id}' not found")
    return pack


@app.get("/api/v1/model-packs/{pack_id}/manifest", response_model=ModelPackManifestSchema)
async def get_model_pack_manifest(pack_id: str):
    pack = MODEL_PACKS_STORE.get(pack_id)
    if not pack:
        raise HTTPException(status_code=404, detail=f"Manifest for '{pack_id}' not found")
    return pack


@app.get("/api/v1/model-packs/{pack_id}/download")
async def download_model_pack(pack_id: str):
    pack = MODEL_PACKS_STORE.get(pack_id)
    if not pack:
        raise HTTPException(status_code=404, detail=f"Model pack '{pack_id}' not found")
    return {
        "pack_id": pack.pack_id,
        "version": pack.version,
        "presigned_s3_url": pack.download_url,
        "sha256_checksum": pack.sha256_checksum,
        "file_size_bytes": pack.file_size_bytes,
        "signature_hex": pack.signature_hex,
        "supports_range_resume": True,
    }


@app.post("/api/v1/auth/register-device")
async def register_device(payload: DeviceRegisterRequest):
    device_id = "dev_" + hashlib.sha256(payload.device_fingerprint.encode("utf-8")).hexdigest()[:16]
    return {
        "device_id": device_id,
        "organization_code": payload.organization_code,
        "status": "REGISTERED",
        "offline_autonomy": "ENABLED",
    }


@app.post("/api/v1/auth/activate-license")
async def activate_license(payload: LicenseActionRequest):
    expires = (datetime.now(timezone.utc) + timedelta(days=365)).isoformat()
    return {
        "active": True,
        "device_id": payload.device_id,
        "tier": "ENTERPRISE_OFFLINE",
        "expires_at": expires,
        "message": "Enterprise license activated. Offline translation remains permanently unlocked.",
    }


@app.post("/api/v1/auth/renew-license")
async def renew_license(payload: LicenseActionRequest):
    expires = (datetime.now(timezone.utc) + timedelta(days=365)).isoformat()
    return {
        "active": True,
        "device_id": payload.device_id,
        "tier": "ENTERPRISE_OFFLINE",
        "expires_at": expires,
        "message": "Enterprise device license renewed for 365 days.",
    }


@app.post("/api/v1/feedback")
async def submit_consented_feedback(payload: FeedbackSubmissionRequest):
    if not payload.user_consented:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Privacy Violation Prevented: Correction synchronization requires explicit user_consented=true.",
        )
    submission_id = "corr_" + hashlib.sha256(
        f"{payload.source_lang}:{payload.target_lang}:{payload.corrected_stt_text}".encode("utf-8")
    ).hexdigest()[:12]
    return {
        "submission_id": submission_id,
        "status": "STORED_WITH_CONSENT",
        "raw_audio_retained": False,
    }


@app.get("/api/v1/organizations")
async def list_organizations(role: str = Depends(verify_admin_role)):
    return [
        {
            "id": "org_whisly_health_tn",
            "name": "Southern Rural Healthcare Network",
            "org_code": "SRHN-TN-WB",
            "tier": "ENTERPRISE_OFFLINE",
            "active_devices": 142,
            "max_devices": 250,
            "requested_by_role": role,
        }
    ]


@app.get("/api/v1/admin/releases")
async def list_releases(role: str = Depends(verify_admin_role)):
    return RELEASES_STORE


@app.post("/api/v1/admin/releases", status_code=201)
async def create_release(payload: CreateReleaseRequest, role: str = Depends(verify_admin_role)):
    if role != "SYSTEM_ADMIN":
        raise HTTPException(status_code=403, detail="Only SYSTEM_ADMIN can create model releases.")
    new_rel = {
        "release_id": f"rel_{len(RELEASES_STORE) + 1}",
        "release_tag": payload.release_tag,
        "pack_id": payload.pack_id,
        "version": payload.version,
        "channel": payload.channel,
        "is_published": False,
        "release_notes": payload.release_notes,
    }
    RELEASES_STORE.append(new_rel)
    return new_rel


@app.post("/api/v1/admin/releases/{release_id}/publish")
async def publish_release(release_id: str, role: str = Depends(verify_admin_role)):
    if role != "SYSTEM_ADMIN":
        raise HTTPException(status_code=403, detail="Only SYSTEM_ADMIN can publish model releases.")
    for rel in RELEASES_STORE:
        if rel["release_id"] == release_id:
            rel["is_published"] = True
            return rel
    raise HTTPException(status_code=404, detail=f"Release '{release_id}' not found")
