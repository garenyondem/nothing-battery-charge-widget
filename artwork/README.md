# Glyph Charge icon

## Design

The mark is a Glyph ring with 12 nodes. Nine nodes are lit, so the mark reads as 75% charge.
The three dim nodes show the remaining charge. The bolt shows charging.
The palette is near-black `#0B0B0C` and white `#FFFFFF`.

## Requirements

### Launcher icon

- Use an adaptive icon with a separate foreground and background.
- Size each layer to 108 x 108 dp.
- Keep the logo inside the 66 x 66 dp safe zone. The outer 18 dp is for masking and visual effects.
- Provide a monochrome layer for themed icons on Android 13 and later.
- Use vector layers. The app has `minSdk 36`, so density PNG files are not needed.
- Do not add a mask or a shadow. The launcher applies both.

### Splash screen

Android 12 and later show the launcher icon on the splash screen.
The icon must stay clear in the circular crop. The 66 dp safe zone also covers this crop.

### Google Play

- Use a 512 x 512 px PNG.
- Use a 32-bit image in sRGB.
- Keep the file at or below 1024 KB.
- Fill the square. Do not round the corners or add a shadow. Google Play applies both.

### Notifications

Use a white silhouette on a transparent background. The system tints it.

## Android assets

- `app/src/main/res/drawable/ic_launcher_background.xml`: adaptive background layer.
- `app/src/main/res/drawable/ic_launcher_foreground.xml`: adaptive foreground layer and monochrome layer.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: adaptive icon. It also declares the monochrome layer.
- `app/src/main/res/drawable/ic_glyph_mark.xml`: in-app header mark. The color follows the day and night theme.
- `app/src/main/res/drawable/ic_stat_glyph_charge.xml`: notification small icon. It is a white silhouette.

## Files

- `glyph-charge-icon.svg`: master with the background.
- `glyph-charge-mark.svg`: transparent master mark.
- `play-store-icon-512.png`: Google Play asset.
- `exports/ic_launcher-*.png`: 48, 72, 96, 144 and 192 px reference sizes.
- `exports/glyph-charge-mark-512.png`: transparent mark at 512 px.

The files in `exports` are reference sizes. The app does not package them.

## Regenerate

1. Install Pillow: `python -m pip install pillow`
2. Run the generator from the repository root: `python artwork/generate_icons.py`

The generator writes the Android vectors, the SVG masters and the PNG exports.
