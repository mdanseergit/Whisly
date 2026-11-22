# Whisly Splash Screen Forensic Diagnosis Report (Phase 0)

**Date**: 2026-11-06  
**Application**: Whisly (`com.aistudio.whisly.kxmpzq`)  
**Scope**: Splash Screen, Cold-Start Window Theming, Asset Pipeline & Branding Alignment  

---

## 1. Executive Summary & Problem Description

When Whisly is launched cold on a physical device, the splash screen displays visual anomalies reported by the user:
1. **Opaque Grey/White Textured Box**: A hard-edged box is visible against the dark background.
2. **Horizontal Cropping of Logo Artwork**: The orange/red/gold "W" ribbon is cropped at the left and right edges.
3. **Non-Square, Distorted Aspect Ratio**: The splash icon is wide (~2.12:1) rather than a centered 1:1 icon conforming to standard Android splash requirements.
4. **Jagged Edges & Upscaling Halos**: Edges exhibit visible jaggedness and high-contrast fringe artifacts.
5. **Missing "WHISLY" Wordmark**: The typography and brand identity ("WHISLY") are completely absent from the initial launch sequence.

This report documents the forensic pixel measurements of every asset in the repository, the AndroidManifest / themes / drawables wiring, the `androidx.core.splashscreen` lifecycle, and the root causes of each defect.

---

## 2. Asset Inventory & Forensic Measurements

Every image asset in the repository was inspected using Python / Pillow / NumPy pixel-level instrumentation:

| Asset Name | Relative Path | Resolution | Color Mode | Aspect Ratio | Alpha Channel | Content Bounding Box | Ribbon Bounding Box | Touches Canvas Edge? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`whisly logo.png`** | `whisly logo.png` (Root) | **1254 × 1254** | **RGB** | **1.000 (1:1)** | None (Solid black bg) | `[124, 121]` to `[1129, 1109]` (1006 × 989 px) | `[235, 438]` to `[1053, 794]` (819 × 357 px) | **False** (Uncropped; 121–144px margins) |
| **`whisly_logo_2acce0a0.png`** | `whisly/public/assets/aistudio/...` | **1254 × 1254** | **RGB** | **1.000 (1:1)** | None (Solid black bg) | `[124, 121]` to `[1129, 1109]` (1006 × 989 px) | `[235, 438]` to `[1053, 794]` (819 × 357 px) | **False** (SHA-256 matches root file) |
| **`whisly_logo.png`** | `whisly/app/src/main/res/drawable/...` | 512 × 512 | RGBA | 1.000 (1:1) | 100% Opaque (No alpha) | `[0, 0]` to `[511, 511]` (512 × 512 px) | `[47, 157]` to `[465, 343]` (419 × 187 px) | **True** (Entire 512×512 filled with `#FFF9F5`) |
| **`whisly_launcher_fg.png`** | `whisly/app/src/main/res/drawable/...` | 512 × 512 | RGBA | 1.000 (1:1) | 100% Opaque (No alpha) | `[0, 0]` to `[511, 511]` (512 × 512 px) | `[47, 157]` to `[465, 343]` (419 × 187 px) | **True** (Identical SHA-256 to `whisly_logo.png`) |
| **`whisly_logo_transparent.png`** | `whisly/app/src/main/res/drawable/...` | 512 × 512 | RGBA | 1.000 (1:1) | Transparent (Cutout) | `[46, 157]` to `[465, 354]` (420 × 198 px) | `[47, 157]` to `[465, 343]` (419 × 187 px) | **False** (Isolated ribbon cutout; no tile) |
| **`ic_launcher.png`** | `whisly/app/src/main/res/mipmap-xxxhdpi/...` | 192 × 192 | RGBA | 1.000 (1:1) | 100% Opaque | `[0, 0]` to `[191, 191]` (192 × 192 px) | `[18, 59]` to `[174, 128]` (157 × 70 px) | **True** (Opaque cream background) |

### Key Diagnostic Findings on Assets:
1. **The Authentic Source Artwork Exists at 1254 × 1254 px**:
   - `whisly logo.png` (and `whisly_logo_2acce0a0.png`, SHA-256: `2acce0a00f72f9786f2cda480c56557f6202a7740b880c8f65d25c626b739ddb`) is the authentic high-resolution master.
   - It contains the **complete, uncropped rounded tile** (squircle with corner radius ~215 px, dimensions 1006 × 989 px) centered within the 1254 × 1254 canvas.
   - Inside the rounded tile sits the **pristine 3D orange/red/gold W ribbon** (width: 819 px, height: 357 px). The ribbon has 111 px clearance on the left and 76 px clearance on the right before reaching the tile edge.
   - **Crucial Limitation**: The background outside the tile is **solid black** (`RGB [0, 0, 0]` to `[1, 1, 1]`), not transparent alpha.
2. **The Flawed Cutout `whisly_logo_transparent.png`**:
   - Width: 420 px, Height: 198 px (Aspect ratio: 2.12:1).
   - This asset was created by crudely chromakeying out the `#FFF9F5` background of `whisly_logo.png`.
   - **Defect Evidence**: It contains **18,621 semi-transparent border pixels** with mean RGB `[235, 241, 247]`. When rendered against a dark background (`#1A1412`), these pixels form an ugly light-grey jagged halo.
   - The rounded tile is entirely absent.

---

## 3. Splash Theming & Manifest Wiring

### AndroidManifest.xml:
```xml
<application
    android:theme="@style/Theme.MyApplication" ...>
    <activity
        android:name=".MainActivity"
        android:theme="@style/Theme.Whisly.Starting" ...>
```
The cold-start window theme is explicitly set to `@style/Theme.Whisly.Starting`.

### Theme Specifications:
- **`values/themes.xml` (Light Mode)**:
  ```xml
  <style name="Theme.Whisly.Starting" parent="Theme.MyApplication">
      <item name="android:windowBackground">@drawable/bg_splash_whisly</item>
      <item name="android:windowSplashScreenBackground" tools:targetApi="31">@color/whisly_light_bg</item>
      <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="31">@drawable/ic_splash_whisly_logo</item>
      <item name="android:windowSplashScreenIconBackgroundColor" tools:targetApi="31">@android:color/transparent</item>
      <item name="android:windowSplashScreenAnimationDuration" tools:targetApi="31">250</item>
  </style>
  ```
- **`values-night/themes.xml` (Dark Mode)**:
  ```xml
  <style name="Theme.Whisly.Starting" parent="Theme.MyApplication">
      <item name="android:windowBackground">@drawable/bg_splash_whisly</item>
      <item name="android:windowSplashScreenBackground" tools:targetApi="31">@color/whisly_dark_bg</item>
      <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="31">@drawable/ic_splash_whisly_logo</item>
      <item name="android:windowSplashScreenIconBackgroundColor" tools:targetApi="31">@android:color/transparent</item>
      <item name="android:windowSplashScreenAnimationDuration" tools:targetApi="31">250</item>
  </style>
  ```

### Drawables Analysis:
1. **`bg_splash_whisly.xml` (Pre-Android 12 window background fallback)**:
   ```xml
   <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
       <item android:drawable="@color/whisly_light_bg" />
       <item
           android:width="160dp"
           android:height="160dp"
           android:drawable="@drawable/whisly_logo_transparent"
           android:gravity="center" />
   </layer-list>
   ```
   - **Bug**: Hardcoded to `@color/whisly_light_bg` (`#FFF9F5`) in `res/drawable/`! Even when running on a dark theme device on pre-API 31, it flashes cream/white background.
2. **`ic_splash_whisly_logo.xml` (API 31+ SplashScreen icon)**:
   ```xml
   <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
       <item
           android:width="160dp"
           android:height="160dp"
           android:drawable="@drawable/whisly_logo_transparent"
           android:gravity="center" />
   </layer-list>
   ```
   - Directly references `@drawable/whisly_logo_transparent`.

---

## 4. SplashScreen API Lifecycle & Compose Handoff Analysis

### `MainActivity.kt`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    val coldStartTimestamp = SystemClock.uptimeMillis()
    super.onCreate(savedInstanceState)
    ...
    configureSplashScreen(splashScreen, coldStartTimestamp)
    ...
    setContent {
        WhislyTheme(darkTheme = uiState.isDarkTheme) {
            WhislyAppRoot(viewModel = whislyViewModel)
        }
    }
}
```
`configureSplashScreen` registers an exit animation:
```kotlin
val containerFadeOut = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)
val iconScaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.05f)
val iconScaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.05f)
val iconFadeOut = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
```
Duration is 250ms with `PathInterpolator(0.4f, 0f, 0.2f, 1f)`.

### Compose Handoff Gap:
In `WhislyNavigation.kt`:
```kotlin
fun WhislyAppRoot(
    viewModel: WhislyViewModel,
    startInTranslateDirectly: Boolean = true
) {
    ...
    NavHost(
        navController = navController,
        startDestination = if (startInTranslateDirectly) WhislyRoutes.TRANSLATE else WhislyRoutes.SPLASH,
        ...
```
Because `startInTranslateDirectly` was set to `true`, **the Compose `WhislySplashScreen` composable was completely bypassed**!
In `WhislySplashScreen` (defined in `SplashAndOnboardingScreen.kt`), there is code designed to display `WhislyLogo(size = 140.dp, cornerRadius = 28.dp)` and the text `"WHISLY"`. But because navigation directly loads `TranslateScreen`, users saw only the system window splash (`ic_splash_whisly_logo.xml` containing `whisly_logo_transparent.png`).

---

## 5. Root Cause Analysis of Reported Defects

### Defect 1: Opaque Grey/White Textured Box Visible Against Dark Background
- **Root Cause**: `whisly_logo.png` had a solid `#FFF9F5` rectangular background. In `whisly_logo_transparent.png`, anti-aliased edge pixels between the ribbon and the cream background were left with partial opacity and high RGB values (`mean [235, 241, 247]`). Furthermore, in `bg_splash_whisly.xml`, `@color/whisly_light_bg` was hardcoded, causing a bright rectangular flash or contrast box against dark system mode.

### Defect 2: Orange "W" Ribbon Cropped at Left and Right Edges
- **Root Cause**: Android 12+ (API 31+) `SplashScreen` places the `windowSplashScreenAnimatedIcon` drawable inside a circular viewport mask of diameter 160dp (up to 192dp). Because `whisly_logo_transparent.png` is an ultra-wide ribbon (420 × 198 px, aspect ratio 2.12:1) scaled to a 160dp × 160dp box, its width spanned nearly the entire box while the circular crop clipped both the far-left and far-right tips of the ribbon.

### Defect 3: Image is Wide, Not Square; Off-Center
- **Root Cause**: The raw ribbon has dimensions 419 × 187 px (2.24:1 ratio). Without its square rounded-tile container (which is 1006 × 989 px, 1.01:1 square), the icon violates the 1:1 square splash requirement.

### Defect 4: Jagged Edges / Low-Resolution Cutout Look
- **Root Cause**: Scaling up a 420 × 198 px cutout to fill high-density viewports (e.g. xxxhdpi screens require ~576px) caused pixel interpolation blur and exposed the 18,621 fringe pixels.

### Defect 5: Missing "WHISLY" Wordmark
- **Root Cause**: Twofold:
  1. `Theme.Whisly.Starting` did not define `android:windowSplashScreenBrandingImage` (which provides a standard bottom branding slot on Android 12+).
  2. In Compose, `startInTranslateDirectly = true` bypassed `WhislySplashScreen`, so the brand header never performed the smooth transition with the "WHISLY" wordmark.

---

## 6. Source Asset Assessment & Phase 1 Decision Gate

Under **Ground Rule 4**:
> *"If no clean, uncropped, transparent version of the logo exists anywhere in the repository, do not guess and do not regenerate it with AI. Stop, list every candidate asset you found with exact dimensions and bounding boxes, show me what each looks like, and ask me to provide the clean source. If a clean source does exist, tell me where it is, prove it is uncropped, and proceed."*

And **Ground Rule 2**:
> *"Do NOT redraw, recolor, trace, regenerate or "improve" the logo. Use only my original artwork. Cropping, scaling, padding and alpha cleanup of the real source are allowed. AI-generating a replacement is forbidden."*

### Assessment:
1. **No pre-existing transparent raster or vector asset of the rounded tile exists in the repo.**
2. **However, `whisly logo.png` (1254 × 1254 px) at the project root is the 100% authentic, uncropped master artwork.**
   - It contains the complete rounded tile (1006 × 989 px) and the uncropped 3D orange ribbon (819 × 357 px).
   - Its background is pure black (`[0, 0, 0]` to `[1, 1, 1]`).
   - Through mathematically exact, lossless alpha extraction (segmenting the outer black background while preserving 100% of the authentic tile, bevel, rim lighting, and ribbon), a pristine, square, transparent splash icon conforming to Android 12+ 288dp/192dp specs can be produced.

We are ready to present this diagnosis to the user and request approval to proceed with Phase 2 using the master asset `whisly logo.png`.

