# Closet AI (native Android)

Kotlin + Jetpack Compose wardrobe app that:

- reads your full-length photo to work out build, proportions, skin undertone, best colours and fits
- auto-detects each clothing photo (T-shirt, polo, shirt, kurta, jeans, chinos, sneakers, watch…) plus its colour, pattern, fabric, fit and formality
- styles 3 outfits from **your own clothes** for any occasion, weather and vibe, with concrete styling tips
- logs what you wear so it avoids repeats, saves favourite looks, and tells you which few pieces to buy next

Everything (photos and wardrobe) is stored only on the phone. AI calls go straight from the phone to
Google Gemini or Anthropic Claude using **your own API key** (set it in ⚙ Settings inside the app).

## Get the APK — option A: GitHub builds it for you (no PC needed)

1. Create a new GitHub repository and push this folder to it (branch `main`).
2. The **Build APK** workflow runs automatically (~5 min). Open the repo's **Releases** page on your phone.
3. Download `ClosetAI.apk`, open it, allow "Install unknown apps" for your browser when asked, install.

Every new push creates a new release you can install over the old one (your data is kept).

## Option B: Android Studio

Open this folder in Android Studio (Ladybug or newer, JDK 17), let Gradle sync, plug in your phone with
USB debugging on, and press **Run**. Or `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## AI key

- **Gemini** (default): create a key at https://aistudio.google.com/apikey — it has a free tier.
  Default model `gemini-3.8-flash`; change it in Settings if Google renames models.
- **Claude**: key from https://console.anthropic.com/settings/keys, default model `claude-haiku-5-5`.

## Project layout

```
app/src/main/java/com/sanket/closetai/
  MainActivity.kt          entry point
  data/Models.kt           Item, Profile, Analysis, Look, WearLog + JSON
  data/Repo.kt             local storage + image import (EXIF-aware, downscaled JPEG)
  ai/AiClient.kt           Gemini / Claude REST calls with your key
  ai/Stylist.kt            prompts: detect clothes, analyse photo, outfits, wardrobe gaps
  ui/AppViewModel.kt       app state and actions
  ui/ClosetApp.kt          all screens (Wardrobe, Style me, Profile, Settings)
  ui/Theme.kt              dark gold theme
```
