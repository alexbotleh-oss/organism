"""Small, explicit policy functions. No self-promotion by repetition or model consensus."""
from __future__ import annotations

HANDOFF_BLOCKED = {"TOMBSTONED", "REFUTED", "DEPRECATED", "CONFLICT", "QUARANTINED"}
EVIDENCE_KINDS = {"USER_REPORT", "ARTIFACT", "TEST", "EXECUTION", "EXTERNAL_SOURCE", "MODEL_REVIEW"}

def effective_status(claim_status: str, verification_status: str) -> str:
    """Keep claim lifecycle and verification independent; return a conservative display status."""
    cs = (claim_status or "CANDIDATE").upper()
    vs = (verification_status or "NONE").upper()
    if cs in HANDOFF_BLOCKED:
        return cs
    if vs in {"EXECUTED", "TEST_PASSED", "ARTIFACT_CONFIRMED", "EXTERNAL_CONFIRMED"}:
        return "VERIFIED"
    if vs in {"USER_REPORTED"}:
        return "REPORTED"
    if vs in {"MODEL_REVIEW"}:
        return "CANDIDATE"
    return "UNVERIFIED" if cs not in {"DECISION", "PREFERENCE", "USER_DIRECTIVE"} else cs

def may_enter_handoff(*, status: str, verification_status: str, scope_match: bool,
                      environment_match: bool, risk: str = "normal",
                      explicit_override: bool = False) -> tuple[bool, str]:
    """Return (allowed, reason). High-risk tasks exclude unverified claims by default."""
    s = (status or "CANDIDATE").upper()
    v = (verification_status or "NONE").upper()
    if s in HANDOFF_BLOCKED:
        return False, f"blocked_status:{s.lower()}"
    if explicit_override:
        return True, "explicit_user_override"
    if not scope_match:
        return False, "scope_mismatch"
    if not environment_match:
        return False, "environment_mismatch"
    verified = v in {"EXECUTED", "TEST_PASSED", "ARTIFACT_CONFIRMED", "EXTERNAL_CONFIRMED"}
    if (risk or "normal").lower() in {"high", "critical"} and not verified:
        return False, "high_risk_requires_verification"
    return True, "verified" if verified else "unverified_contextual_candidate"
