# Glyph Charge design system

The screen follows Nothing OS 5.0. The system is monochrome, uses the device typefaces,
and moves in short ease-out steps. It uses platform APIs only.

## 1. Tokens

Defined in `ui/GlyphTokens.kt` and `res/values/colors.xml`.

### Color

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `surface` | `#F5F5F5` | `#000000` | Page background |
| `ink` | `#0A0A0A` | `#FFFFFF` | Primary text, lit dots |
| `ink_muted` | `#6E6E6E` | `#8C8C8C` | Micro labels, values, secondary text |
| `ink_faint` | `#A0A0A0` | `#5C5C5C` | Unlit dots, chevron |
| `hairline` | 10% black | 12% white | Dividers and rules |
| `switch_track_off` | 14% black | 14% white | Switch track, off |
| `accent` | `#D71921` | `#FF453A` | One problem state only |

Color is an event. The app uses accent only when notifications are off.
All other states use opacity.

### Type

| Role | Face | Size | Weight | Tracking |
| --- | --- | --- | --- | --- |
| Level | Geist | 72 sp | 200 | -0.04 em |
| Percent sign | Geist | 20 sp | 300 | 0 |
| Row label | Geist | 14.5 sp | 400 | 0 |
| Status value | Geist | 13 sp | 400 | 0 |
| Micro label | Geist Mono | 10 sp | 400 | 0.16 em |
| Value | Geist Mono | 11 sp | 400 | 0.06 em |
| Details | Geist Mono | 11 sp | 400 | 0 |

The app requests `sans-serif`, `geist-mono` and fixed Geist weights from the system.
Nothing OS 5.0 maps `sans-serif` to Geist. No font files ship in the APK.
On a device without Geist Mono the app falls back to `monospace`.

Do not use NDot55 for interface text. The brand rules reserve it for the logotype
and product names.

### Space and shape

- Base unit: 8 dp.
- Screen margin: 20 dp.
- Section gap: 28 dp.
- Row height: 56 dp minimum.
- Control radius: 6 dp. Surface radius: 8 dp.
- No pill buttons. Only the switch is a pill.

## 2. Components

### GlyphGaugeView

Twelve dots on a circle, filled clockwise to the battery level.
This is the hero element and the only expressive moment.

- Dot radius: 2.9% of the view.
- Ring radius: half the view minus 2.5 dot radii.
- Unlit dot: 13% ink.
- Lit dot: 100% ink.
- Leading dot: soft radial glow, 2.4 s sine pulse, only while the glyph is live.
- Paused or idle: the whole ring fades to 55% and the pulse stops.

### NothingSwitch

44 x 26 dp. Off is a quiet track with an ink knob. On is an ink track with a surface knob.
The knob slides in 180 ms with `(0.2, 0, 0, 1)`. A clock tick haptic marks the change.
The view reports itself as `android.widget.Switch` to accessibility services.

### Section

A micro label, a row count, a hairline, then rows separated by hairlines.
The count is the Nothing habit of showing the system.

### StatusCell

A micro caption, a 4 dp dot, and a value. The dot is ink when the state is true
and faint when it is false.

### Footer theme toggle

The footer ends with a `DAY` or `NIGHT` text button in the micro label style.
The label shows the current theme. A tap stores the opposite theme in `settings`
and recreates the screen. Until the first tap the screen follows the system.
The 48 dp hit area stays centered on the label.

## 3. States

| Condition | Gauge | State label | Glyph cell |
| --- | --- | --- | --- |
| Monitor off | Dim | MONITOR OFF | OFF |
| Charging, face gate open | Lit, pulsing | CHARGING | ON |
| Charging, face up | Dim | CHARGING | PAUSED |
| Not charging | Dim | NOT CHARGING | OFF |
| Battery full | Dim | FULL | OFF |

## 4. Motion

| Element | Duration | Curve |
| --- | --- | --- |
| Screen enter | 320 ms, 40 ms stagger | ease-out cubic |
| Gauge sweep | 620 ms | ease-out cubic |
| Dot reveal | 560 ms, staggered by index | ease-out cubic |
| Switch | 180 ms | `(0.2, 0, 0, 1)` |
| State text | 200 ms | ease-out |
| Details expand | 200 ms | `AutoTransition` |

No spring. No bounce. No parallax.

## 5. Accessibility

- The gauge has a live content description: "Battery 68 percent".
- The switch exposes a checked state and a click action.
- Rows are focusable and clickable.
- Text contrast: ink on surface is above 7:1 in both themes.
- Micro labels are 10 sp. They are captions, never the only carrier of meaning.

## 6. Files

```
ui/GlyphTokens.kt      tokens, typefaces, easing
ui/GlyphGaugeView.kt   the dot ring
ui/NothingSwitch.kt    the toggle
ui/NothingUi.kt        labels, rows, section, status cell
MainActivity.kt        screen assembly and state
```
