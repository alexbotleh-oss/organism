# ORGANISM — ChatGPT Web connector MVP v0.1

**Status:** implementation committed; build/device verification pending  
**Date:** 2026-10-09  
**Mode:** official ChatGPT website inside Android WebView, with explicit user-controlled copy/paste

## Why this exists

The current Android chat path uses an authenticated HTTP request to `https://api.openai.com/v1/responses` and separately requests `/models`. The OAuth trace proves the login token was issued and stored, but the first reported model request returned `subscription_sharing_usage_limit_exceeded`. The user asked to try the official ChatGPT web interface to distinguish platform-UI behavior from the existing API-oriented path and make the actual prompt inspectable.

## MVP behavior

1. From the ORGANISM chat, the user writes a prompt and taps **Prepare request for ChatGPT Web**.
2. ORGANISM prepares one prompt containing the current Context Snapshot and the user's text. It stores a pending prompt locally and records a metadata-only `PLATFORM_WEB_PROMPT_PREPARED` event. The event explicitly says `PREPARED_NOT_SENT`; preparing/copying is not proof that ChatGPT received it.
3. The app opens `https://chatgpt.com/` inside a WebView. The user signs in there if needed, taps **Copy prepared request**, pastes it into ChatGPT, and presses Send themselves.
4. After ChatGPT responds, the user copies the response from the page, returns to ORGANISM, and taps **Paste response from ChatGPT Web**. ORGANISM stores the user question and copied response in the active session and marks the model output `NOT_VERIFIED`.
5. A separate menu item opens the official site without a prepared prompt.

## Explicit limitations

- This is a manual, user-controlled connector prototype, not DOM automation.
- ORGANISM does not inject JavaScript, read page content, inspect browser cookies/session storage, extract credentials, or click Send on the user's behalf.
- The app cannot prove that the user pasted/sent the exact prepared prompt or that copied text is the page's actual answer. The event therefore records user-confirmed clipboard import, not independently verified transport.
- ChatGPT may restrict or refuse authentication in an embedded WebView. If it does, do not bypass that protection; record the limitation and consider an official browser handoff with the same manual copy/paste flow.
- This does not bypass plan or usage limits. If the same account limit is exhausted, the official website may also refuse further requests.
- The existing API/OAuth path remains unchanged and is still the default **Send via ORGANISM → GPT** action.

## Data and privacy

- No OAuth token, password, cookie, raw URL query, or browser session data is read or logged by this feature.
- The prepared prompt may include selected memory/context. It remains in app-private preferences until the user imports a response or starts another prepared request; it must not be exported automatically.
- A copied response is stored only after the user explicitly taps the import button.
- Existing database, imported ChatGPT archive, and saved credentials are not reset, deleted, or migrated.
- Imported response content remains unverified; creating an answer does not automatically establish reusable experience.

## Acceptance checks

1. App compiles on the exact commit.
2. Chat Web mode opens the official HTTPS site and the manual controls remain usable.
3. Copy prompt produces the exact prepared text; no API `/responses` request is made by this action.
4. Pasting a copied response stores one user turn and one model output in the selected ORGANISM session, with source label `ChatGPT Web (manual copy)`.
5. The stored output is `NOT_VERIFIED`; no duplicate turn is created by re-opening the page.
6. API mode remains unchanged.
7. On-device login, clipboard flow, and WebView compatibility are explicitly tested and recorded separately from CI build status.

## Current status

Source code is committed on `fix/oauth-diagnostic-trace-20261009`; no CI result or physical-device test has yet been observed for this MVP. Do not call it verified until those checks complete.

## Multi-provider web connector direction (user decision, 2026-10-09)

The user explicitly chose web interfaces as the primary connection method for multiple model providers, while retaining API connectivity as an optional/parallel method. The first web providers to support are:

| Provider | Official web entry |
|---|---|
| Claude | https://claude.ai/ |
| DeepSeek | https://chat.deepseek.com/ |
| Qwen | https://chat.qwen.ai/ |
| Gemini | https://gemini.google.com/ |
| Yandex Alice | https://alice.yandex.ru/ |

This list is the user-provided scope, not a claim that all providers have already been tested or that every site permits embedded WebView authentication.

### Architecture requirements for the next phase

- Keep ORGANISM's own conversation history, context snapshot, memory, experience, provenance and safety rules provider-independent.
- Add a provider registry and separate web connector entries; do not hard-code provider-specific behavior into the core conversation/memory layer.
- Each web connector opens the provider's official site and uses explicit manual copy/paste unless a supported, user-authorized integration is separately designed. No DOM/script injection, cookie/session extraction, automated send, or authentication/usage-limit bypass.
- Keep the existing API/OAuth connector available as an alternative; do not silently switch a user's chosen transport.
- Record provider, transport (WEB_MANUAL or API), timestamps, prompt/response provenance, user-confirmed import, and verification status for each result. Mark manually imported responses NOT_VERIFIED until independently checked.
- Provide a provider-selection UI and per-provider status distinguishing OPENED, SIGNED_IN_BY_USER (only if user confirms), PROMPT_COPIED, RESPONSE_IMPORTED, and ERROR; do not label a site connected merely because its landing page opened.
- Treat availability, sign-in, and WebView compatibility as unverified until tested on device. If a provider blocks WebView or controls do not work, offer official external-browser handoff with the same manual transfer flow.
- Start with the smallest vertical slice: provider registry + Claude and DeepSeek manual web flow, validate data isolation and import, then add Qwen, Gemini and Alice using the same adapter contract. API path remains untouched by this web rollout.

### Verification boundary

A successful Android build proves compilation only. Each provider needs an on-device check for site loading, user sign-in, text entry, sending, response copy, and explicit import into the intended ORGANISM session. Never claim all five providers work based only on build success.