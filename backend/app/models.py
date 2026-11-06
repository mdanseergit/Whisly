from datetime import datetime, timezone
import uuid
from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    BigInteger,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()


def utc_now():
    return datetime.now(timezone.utc)


def new_uuid() -> str:
    return str(uuid.uuid4())


class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=new_uuid)
    email = Column(String(255), unique=True, nullable=False, index=True)
    hashed_password = Column(String(255), nullable=False)
    role = Column(String(50), nullable=False, default="ORG_ADMIN")  # SYSTEM_ADMIN, ORG_ADMIN, AUDITOR
    is_active = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class Organization(Base):
    __tablename__ = "organizations"

    id = Column(String(36), primary_key=True, default=new_uuid)
    name = Column(String(255), nullable=False)
    org_code = Column(String(64), unique=True, nullable=False, index=True)
    tier = Column(String(50), nullable=False, default="ENTERPRISE_OFFLINE")
    max_devices = Column(Integer, nullable=False, default=250)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    members = relationship("OrganizationMember", back_populates="organization", cascade="all, delete-orphan")
    devices = relationship("Device", back_populates="organization")
    licenses = relationship("License", back_populates="organization")


class OrganizationMember(Base):
    __tablename__ = "organization_members"
    __table_args__ = (
        UniqueConstraint("organization_id", "user_id", name="uq_org_member"),
    )

    id = Column(String(36), primary_key=True, default=new_uuid)
    organization_id = Column(String(36), ForeignKey("organizations.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    member_role = Column(String(50), nullable=False, default="ORG_ADMIN")
    joined_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    organization = relationship("Organization", back_populates="members")


class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=new_uuid)
    device_fingerprint = Column(String(128), unique=True, nullable=False, index=True)
    organization_id = Column(String(36), ForeignKey("organizations.id", ondelete="SET NULL"), nullable=True, index=True)
    app_version = Column(String(32), nullable=False)
    registered_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    last_seen_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    organization = relationship("Organization", back_populates="devices")


class License(Base):
    __tablename__ = "licenses"

    id = Column(String(36), primary_key=True, default=new_uuid)
    license_key = Column(String(128), unique=True, nullable=False, index=True)
    organization_id = Column(String(36), ForeignKey("organizations.id", ondelete="CASCADE"), nullable=False, index=True)
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="SET NULL"), nullable=True, index=True)
    status = Column(String(32), nullable=False, default="ACTIVE")  # ACTIVE, EXPIRED, REVOKED
    expires_at = Column(DateTime(timezone=True), nullable=False)
    activated_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    organization = relationship("Organization", back_populates="licenses")


class LanguagePack(Base):
    __tablename__ = "language_packs"

    pack_id = Column(String(128), primary_key=True)
    language_code = Column(String(16), nullable=False, index=True)
    indic_trans_tag = Column(String(32), nullable=False)
    english_name = Column(String(64), nullable=False)
    native_name = Column(String(64), nullable=False)
    task_type = Column(String(16), nullable=False, index=True)  # STT, MT, TTS
    is_shared_resource = Column(Boolean, nullable=False, default=False)
    tts_neural_supported = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    versions = relationship("ModelVersion", back_populates="language_pack", cascade="all, delete-orphan")


class ModelVersion(Base):
    __tablename__ = "model_versions"
    __table_args__ = (
        UniqueConstraint("pack_id", "version", name="uq_pack_version"),
        Index("ix_model_versions_pack_version", "pack_id", "version"),
    )

    id = Column(String(36), primary_key=True, default=new_uuid)
    pack_id = Column(String(128), ForeignKey("language_packs.pack_id", ondelete="CASCADE"), nullable=False)
    version = Column(String(32), nullable=False)
    s3_object_key = Column(String(512), nullable=False)
    download_url = Column(String(512), nullable=False)
    sha256_checksum = Column(String(64), nullable=False)
    file_size_bytes = Column(BigInteger, nullable=False)
    required_runtime = Column(String(64), nullable=False, default="ONNX Runtime Mobile >= 1.18")
    min_memory_mb = Column(Integer, nullable=False, default=384)
    license_name = Column(String(128), nullable=False)
    attribution = Column(String(255), nullable=False)
    manifest_signature_hex = Column(String(128), nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    language_pack = relationship("LanguagePack", back_populates="versions")


class ModelRelease(Base):
    __tablename__ = "model_releases"

    id = Column(String(36), primary_key=True, default=new_uuid)
    release_tag = Column(String(64), unique=True, nullable=False, index=True)
    model_version_id = Column(String(36), ForeignKey("model_versions.id", ondelete="CASCADE"), nullable=False)
    channel = Column(String(32), nullable=False, default="STABLE")  # STABLE, ENTERPRISE, BETA
    is_published = Column(Boolean, nullable=False, default=False)
    release_notes = Column(Text, nullable=True)
    published_by = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    published_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class CorrectionSubmission(Base):
    __tablename__ = "correction_submissions"

    id = Column(String(36), primary_key=True, default=new_uuid)
    source_lang = Column(String(16), nullable=False, index=True)
    target_lang = Column(String(16), nullable=False, index=True)
    original_stt_text = Column(Text, nullable=False)
    corrected_stt_text = Column(Text, nullable=False)
    model_translation = Column(Text, nullable=False)
    corrected_translation = Column(Text, nullable=True)
    user_consented = Column(Boolean, nullable=False)
    submitted_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(String(36), primary_key=True, default=new_uuid)
    actor_id = Column(String(36), nullable=True, index=True)
    action = Column(String(128), nullable=False, index=True)
    target_resource = Column(String(255), nullable=False)
    sanitized_metadata = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
