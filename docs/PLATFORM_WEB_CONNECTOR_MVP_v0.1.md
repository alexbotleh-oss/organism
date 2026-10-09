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
