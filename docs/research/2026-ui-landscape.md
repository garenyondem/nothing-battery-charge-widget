# Android 17 UI landscape and Nothing OS design rules

Date: 2026-11-05
Scope: pick a UI and animation approach for Glyph Charge, then redesign the app screen.

## 1. Platform direction

Android 17 released on 2026-06-16. Google now calls Android "Compose-first".

- All new Android UI APIs, libraries, tools and guidance target Jetpack Compose.
- Legacy `android.widget` views and View-based Jetpack libraries are in maintenance mode.
  This includes Fragments, RecyclerView and ViewPager.
- Material 3 Expressive is the current design system. It adds expressive motion,
  a wider shape scale and new component variants.
- Android 17 QPR1 adds more system blur, translucency and bouncy system motion.

Sources:
- https://developer.android.com/about/versions/17/blog-release
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://9to5google.com/2026/09/15/android-17-qpr1-pixel

Consequence: a new UI built on `android.widget` goes against the platform direction.
The trade-off is real and section 5 covers it.

## 2. Library survey

Versions checked on 2026-11-05.

| Library | Latest | What it gives | Cost | Fit for this app |
| --- | --- | --- | --- | --- |
| `androidx.compose` (BOM 2026.09.00) | 1.12.1 stable | Declarative UI, state animation, `AnimatedContent`, shared element transitions | About 3 to 4 MB in the APK. Needs the Compose compiler plugin. Breaks the "platform APIs only" rule. | High, if the rule changes |
| `androidx.compose.material3` | 1.4.0 stable | Material 3 Expressive components and motion | Pulls Compose. Its look is Google's, not Nothing's. | Medium. We would override most tokens. |
| Lottie (`com.airbnb.android:lottie`) | 6.x | After Effects vector animation | Needs AndroidX and AppCompat for the View variant. About 700 KB plus AppCompat. | Low. One screen does not need a motion authoring tool. |
| Haze (chrisbanes) | 1.x | Blur and glass modifiers for Compose | Needs Compose | Low. Platform `RenderEffect` gives blur without a dependency. |
| MotionLayout (`androidx.constraintlayout`) | 2.x | Declarative view motion and transitions | Needs AndroidX. Steep XML cost for one screen. | Low |
| `androidx.dynamicanimation` | 1.x | Spring physics | Small, but Nothing's motion is not spring based. | Low |

Popular does not mean necessary. This app has one screen and about eight controls.
A dependency would add megabytes to draw a ring of dots.

## 3. Nothing OS 5.0 design rules

Nothing OS 5.0 shipped in 2026. The official notes describe the current language.

- **Typeface:** Geist is the system typeface. Geist Mono carries data and labels.
- **Dot matrix:** the dot-matrix language stays, but Nothing uses it "more intentionally".
  It marks character. It does not carry body text.
- **Monochrome:** black, white and grey carry the hierarchy. Color is an event, not a default.
- **Spacing:** 8 px base unit. Group information clearly. Let empty space work.
- **Corners:** a small radius scale. Nothing uses 6 px for controls and 8 px for containers.
  Nothing avoids full-pill buttons and zero-radius flat surfaces.
- **Motion:** precise and short. 150 to 250 ms for micro motion. 300 to 400 ms for transitions.
  Nothing avoids spring and bounce easing.
- **Materials:** transparency and blur appear across Gallery and system controls in 5.0.

Sources:
- https://nothing.community/d/61730-nothing-os-50-new-features-explained
- https://nothing.community/d/61774-nothing-os-evolution
- https://nothing.wiki/nothing/brand_reference

Brand limits to respect:
- Do not use NDot55 outside the logotype and product names.
- Keep letter spacing at zero in the dot font.
- Do not add a brand color.

## 4. Device font inventory (verified with adb)

The test device is a Nothing Phone (3a) Pro on Nothing OS 5.0.
`/system/etc/ntfonts.xml` registers these families:

```
sans-serif            -> Geist (default, variable weight axis)
geist-light-fixed     -> Geist 300
geist-regular-fixed   -> Geist 400
geist-medium-fixed    -> Geist 500
geist-mono            -> Geist Mono
NDot55, NDot55All     -> dot-matrix logotype
NDot57, NDot57All     -> deprecated dot font
NDot77JPExtended
NType82-Headline, NType82-Regular
Lettera               -> mono label face
```

The app can request these families with `Typeface.create(name, style)` and no bundled font.
`android.graphics.fonts.SystemFonts.getAvailableFonts()` (API 29) tells the app
whether a family exists. On other devices the call falls back to the default face.

Result: the app can match Nothing OS 5.0 type exactly, with zero font files in the APK.

## 5. Decision

**Build a small, platform-only design system.** Do not add Compose, AndroidX or Lottie.

Reasons:

1. The screen is one settings page. Compose would add 3 to 4 MB to draw eight controls.
2. Material 3 Expressive would fight the Nothing look. We would replace nearly every token.
3. Nothing's motion is short and precise. Compose spring defaults are off-brand.
4. Platform APIs cover the whole design:
   - `Canvas` and `Path` draw the dot-matrix gauge.
   - `ValueAnimator` and `PathInterpolator` give the exact easing curves.
   - `RenderEffect` (API 31) gives real blur for the glass surfaces.
   - `SystemFonts` gives the device's Geist and dot fonts.
   - `HapticFeedbackConstants` gives the mechanical tick.
5. The project rule "platform APIs only" stays true.

This decision is reversible. The design system lives in one package.
A later move to Compose can keep the tokens and the drawing code.

## 6. Motion specification

| Element | Duration | Curve | Notes |
| --- | --- | --- | --- |
| Screen enter | 320 ms | ease-out cubic (0.05, 0.7, 0.1, 1) | Three groups, 40 ms stagger, 8 dp rise |
| Gauge sweep | 620 ms | ease-out cubic | Runs once on open and on level change |
| Dot appear | 240 ms | ease-out | 18 ms stagger per dot |
| Switch | 180 ms | (0.2, 0, 0, 1) | Knob slides, track inverts |
| Press | 120 ms | linear | 8 percent overlay, scale to 0.98 |
| State change | 200 ms | ease-out | Label cross-fade, no slide |
| Charging pulse | 2400 ms | sine | Opacity 100 to 55 percent, one dot at a time |

No spring. No bounce. No parallax.

## 7. Risks

- **Nothing font names are vendor specific.** Guard every lookup. Fall back to the
  default family and to a drawn dot grid.
- **`RenderEffect` needs API 31.** `minSdk` is 36, so this is safe.
- **Blur costs GPU time.** Use it on one small surface only. Do not blur the full screen.
- **The debug dump is useful.** Keep it, but move it behind a "Details" row.
