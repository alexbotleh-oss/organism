# ORGANISM — Android UI quality standard v1.0

**Status:** mandatory project-wide design and implementation gate
**Applies to:** every Activity, screen, dialog, bottom sheet, form, navigation surface, WebView wrapper, and APK release. This is not specific to the ChatGPT connector.

## Non-negotiable rule

Design every Android screen for the real usable viewport and real fingers before calling it ready. A control that exists in code but is clipped, hidden, overlapped, too small, or unreachable is a failed control. Never ship a screen merely because it compiles.

## Layout requirements

1. **Safe areas first.** Account for status bar, navigation bar (gesture and three-button modes), display cutouts, edge-to-edge behavior, and the IME/soft keyboard. Use current Android window-inset APIs appropriate to the app's View/Compose stack. Do not hard-code a single phone's pixel offsets or assume system bars are excluded from the window.
2. **Reachable actions.** Primary actions must remain visible and tappable when the keyboard opens. Use correct IME resize/pan/inset behavior, scroll containers where needed, and keep focus/input fields in view. No primary button may sit beneath navigation controls or outside the visible viewport.
3. **Touch targets.** Target at least **48 × 48 dp** for interactive controls, with about **8 dp** separation between adjacent targets unless a platform component provides a compliant touch area. Do not judge by visible icon size alone.
4. **Consistent spacing.** Use density-independent units (`dp`/`sp`), a deliberate spacing scale, balanced left/right margins, and consistent alignment. Typical phone content margins should be chosen consistently (often 16 dp) and adjusted when actual layout constraints require it.
5. **Clear hierarchy and composition.** Keep app bar, content, primary action, and navigation in predictable positions. Avoid giant instruction panels, redundant controls, crowded rows, or controls that force horizontal overflow. On narrow screens, wrap, scroll, or adapt instead of clipping.
6. **Keyboard-aware input.** Test empty, short, and multiline input; focus; submit; keyboard open/closed; and long text. The input and its action must not be hidden by the keyboard or navigation bar.
7. **Dialogs and WebViews.** Web-owned cookie/consent dialogs must remain dismissible; the app must not place its own fixed controls over them. A WebView must account for viewport changes, file chooser, scrolling, loading/error states, and app controls surrounding the page.
8. **Accessibility and readability.** Use readable `sp` text, sufficient contrast, meaningful labels/content descriptions, focus order, and support font scaling without losing essential actions.
9. **Adaptive behavior.** Consider small and large phones, portrait/landscape where supported, display cutouts, different navigation modes, and different font sizes. Do not optimize only for the developer's emulator/default screenshot.

## Required review before every APK release

- [ ] Every screen has consistent outer margins and top/bottom safe-area handling.
- [ ] No text, button, field, menu, or navigation item is obscured by system bars, cutouts, keyboard, dialogs, or another view.
- [ ] Every primary action is reachable by touch; interactive targets are at least 48 × 48 dp or have a verified equivalent touch area.
- [ ] Input screens are tested with the keyboard both open and closed; long content can scroll.
- [ ] Dialogs, empty/loading/error states, and cancellation paths remain usable.
- [ ] Screenshots or a screen-by-screen test record cover the affected screens.
- [ ] Build result is tied to the exact commit; device verification is reported separately from CI.
- [ ] Any known UI defect is recorded as a release blocker or explicitly documented limitation, not silently ignored.

## Evidence and release gate

A successful Gradle/CI build proves compilation only. It does not prove reachability, layout quality, or usability. For each release, record the exact commit and CI run; screens/device configurations actually checked; results and defects found; whether tested on a physical device or emulator; and next action for anything unverified.

If physical-device testing is unavailable, say so plainly and do not claim the UI is confirmed.

## Official reference sources

- Android Developers, **Lay out your app within window insets**: https://developer.android.com/develop/ui/views/layout/insets
- Android Developers, **Display content edge-to-edge in views**: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Android Developers, **Make apps more accessible (Views)**: https://developer.android.com/guide/topics/ui/accessibility/views/apps-views
- Android Accessibility Help, **Touch target size**: https://support.google.com/accessibility/android/answer/7101858

The Android platform evolves. Check the current official guidance when changing target SDK, window/inset behavior, navigation, or UI framework.