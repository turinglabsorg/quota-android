# Quota for Android design notes

The Android surfaces follow the macOS design system (`DESIGN.md` in the quota repository): same level colors, glyphs and copy. These notes cover what is specific to Android.

## Principles

- **The widget is the product.** Most of the time the numbers are read from the Home screen; the app is for pairing and details.
- **Color means state.** Green (`#34C759`) when more than 20% is left, orange (`#FF9500`) from 6% to 20%, red (`#FF3B30`) at 5% or less. Brand color (`#D97757`) only on the Claude glyph.
- **Material 3, system colors.** Dynamic color on Android 12+, default Material schemes before; Glance `GlanceTheme` colors in the widget so light, dark and themed launchers work.

## App

- Top app bar "Quota" with refresh and settings actions once paired; pull to refresh on the list.
- One card per account: glyph (18 dp) + provider name (`titleMedium` semibold) + plan pill (`labelMedium` on 8% onSurface), email (`bodySmall`); rows with window label (`bodyLarge`), value (`titleMedium` semibold, level color when low) + `left`/`used` suffix, a 6 dp rounded progress bar without stop indicator, reset countdown (`bodySmall`).
- Issues: warning icon (orange) + `bodySmall` text. "Updated …" footer under the list.
- Pairing: explanation, server field (URI keyboard), 8-digit code field (number keyboard, monospace, capped at 8 digits), full-width Pair button with progress, error text.
- Settings bottom sheet: remaining/used radio buttons, server address, "Add widget to Home screen" (pin request), "Unpair this device" in the error color.

## Widget

- Rounded (20 dp) `widgetBackground`, 14 dp padding; tapping opens the app.
- Bars follow the Mac menu bar (`AccountUsage.barWindows`): when an account reports a 5-hour session and a weekly (or monthly) window, it gets two stacked bars, session above, each with its own value and level color; otherwise one bar for the window closest to its limit.
- **Small** (2 × 2 cells and up): up to four rows of glyph (12 dp), short name (12 sp medium, 44 dp column), then the stacked 4 dp bars each followed by its value (11 sp bold).
- **Medium** (from 250 dp wide): 2 × 2 tiles: glyph + short name + `↻` countdown of the window closest to its limit (10 sp), then the stacked bars with 13 sp values.
- "Updated …" in 10 sp at the bottom, orange when older than 30 minutes. States: "Open Quota to pair", "Can't reach the Quota server."
