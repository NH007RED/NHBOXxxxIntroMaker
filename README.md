# 🎬 NH BOX Intro Maker Pro (4K / 1080p 60FPS HTML2Video Android Studio Project)

Complete native Android Studio application (`com.nhbox.intromaker`) that previews, customizes, and converts HTML5 Canvas Intro files into hardware-encoded **4K Ultra HD (3840x2160)** or **Full HD (1920x1080)** **60 FPS MP4 videos**.

## ✨ Key Features

1. **4 Pre-Bundled Ultra-Realistic Cinema Intros (with Live TMDB API Posters)**:
   - `app/src/main/assets/intros/netflix_nh_box_realistic_tmdb.html` (Netflix 3D Ribbon 'N' + Curved Cinema Arch + 260 Prism Beams + TMDB Wall)
   - `app/src/main/assets/intros/amazon_prime_nh_box_realistic_tmdb.html` (Amazon Prime 3D Orbiting TMDB Poster Carousel + 3D Beveled Text + Exact Smile Arrow)
   - `app/src/main/assets/intros/disney_plus_hotstar_nh_box_realistic_tmdb.html` (Disney+ Hotstar Shooting Star Parabolic Arch + Pixie Dust + TMDB Starlight Constellation)
   - `app/src/main/assets/intros/marvel_nh_box_realistic_tmdb.html` (Marvel Studios Live TMDB Poster Flipbook + Real TMDB Posters Inside 3D Extruded Letters + Red Box Slam)

2. **Import Any Custom `.html` Intro File**:
   - Tap **📂 Load HTML** to pick any HTML2Video `.html` file from your phone storage, or open any `.html` file directly from your File Manager.

3. **Live Brand Text & TMDB API Key Customizer**:
   - Change `"NH BOX"` to any brand name on the fly and customize the TMDB API key (`2e211dfda888f7cc55ce433d743f9bc3` pre-configured).

4. **Hardware-Accelerated Ultra HD MP4 Exporter (`Mp4VideoEncoder.java`)**:
   - Uses Android `MediaCodec` (`video/avc` H.264 Surface encoder at 45 Mbps for 4K / 18 Mbps for 1080p) + `MediaMuxer`.
   - Saves exported MP4 videos directly to `Movies/NHBOX_Intros` in your device gallery and provides a one-tap **📤 Share / Play** button.

## 🛠️ How to Build in Android Studio

1. Extract `NHBOX_Intro_Maker_Pro_Android_Studio_Project.zip`.
2. Open **Android Studio** (Hedgehog / Iguana / Jellyfish or newer) -> **Open** -> Select the extracted `NHBOXIntroMakerPro` folder.
3. Let Gradle sync automatically (uses Android Gradle Plugin 8.2.2, Compile SDK 34, Java 17).
4. Click **Run ▶** on any Android phone (Android 8.0+ / API 26+) or select **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
