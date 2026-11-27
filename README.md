# CBZTagger

**English** | [한국어](README.ko.md)

**CBZTagger** is a fully offline Android app for editing **CBZ (ZIP) comic metadata (`ComicInfo.xml`)** and managing **ZIP compression**.

> [!NOTE]
> **AI-Assisted Development Notice**  
> This project was designed, implemented, tested, and documented in collaboration with generative AI.  
> While unit tests and on-device checks have been performed, backing up original comic archives (`.cbz`) is recommended before batch editing, recompressing, or renaming large collections.

---

## Key Features

### 1. Completely Offline & Safe Local File Handling
- **No `INTERNET` permission**: Operates strictly on local device storage with zero network access.
- **Android SAF (Storage Access Framework)** support for opening folders and individual `.cbz` files.
- **Preserves unsupported XML tags**: Any extra or custom tags in an existing `ComicInfo.xml` are preserved without loss when saving edits.
- **English & Korean UI**: Defaults to English for non-Korean locales and supports switching between `System`, `한국어`, and `English` directly in Settings (with Android 13+ per-app language integration).

### 2. Metadata (`ComicInfo.xml`) Editing
- **Basic Info**: Series, Number, Count, Volume, Title, Publisher/Imprint, Release Date (Year/Month/Day), Genre, Language ISO, Reading Direction (`Manga`), Black & White, Summary, and Notes.
  - One-tap `{series} {number}` title generation when Series and Number are filled in.
  - Automatic metadata extraction from filenames (supports both English and Korean volume/chapter patterns).
  - Dropdown selector for reading direction (`Yes`, `YesAndRightToLeft`, `No`).
- **Details & Credits**: Combined Author input by default, with a `Separate` checkbox to split `Penciller` (Art) and `Writer` (Story), plus Inker, Colorist, Letterer, Cover Artist, Editor, Story Arc, Series Group, Characters, Teams, Locations, Age Rating, Format, Scan Information, and Web link.
- **Unsaved Changes Protection**: Prompts (`Save & Leave` / `Discard & Leave` / `Keep Editing`) when navigating back with unsaved edits.

### 3. Page Management, Bookmarks & Preview
- **Page Attributes**: Individually or batch assign `FrontCover`, `InnerCover`, `Story`, `BackCover`, `Advertisement`, `DoublePage`, and more.
  - Batch edit mode with multi-select checkboxes, start–end range selection, and one-tap filling of untyped pages as `Story`.
  - Fast vertical scrollbar with live page indicator (`#page / total`) for quick navigation in long page lists.
- **Bookmarks**:
  - Long-press any page to add, edit, or delete a bookmark.
  - Manage multiple page bookmarks at once from the top `Bookmarks` dialog.
- **Page Preview**:
  - Navigate pages via horizontal swipe gestures or left/right arrow buttons.
  - Optional setting to follow the `Manga` tag reading direction (`Yes` / `YesAndRightToLeft` flips right-to-left).
  - Optional page list thumbnails in Settings (loaded on demand without caching).

### 4. ZIP Compression Control & Batch Tools
- **Compression Modes**:
  - **`Keep Original`**: Preserves existing image compression methods and quickly updates only `ComicInfo.xml`.
  - **`Compress (0–9)`**: Choose from `0` (Store / uncompressed) up to `9` (maximum Deflate compression).
- **Library Multi-Select Batch Operations** (long-press any item):
  - **Batch Compress**: Recompress selected CBZ archives at a chosen compression level (`0–9`).
  - **Batch Edit Tags**: Apply Series, Publisher, Genre, Author, Year, Language, and Reading Direction across multiple files.
  - **Batch Parse Filenames**: Extract metadata from filenames and save automatically.
  - **Batch Rename**: Template-based renaming (`{series}`, `{issue}`, `{volume}`, `{year}`, `{title}`, `{publisher}`), issue 0-padding, empty bracket/space cleanup, optional `{title}` fallback for one-shots (no `Series`), and expandable rename preview.

### 5. Import External Image Folders to CBZ
- Packs folders structured as `<configured path>/<folder name>/<image files>` into **`<folder name>.cbz`** in your current CBZ library folder (or any chosen destination folder).
- Preserves original image filenames in natural sort order and packs entries as **uncompressed Store (`ZIP_STORED`)** for fast import before later metadata editing or recompression.
- Includes any existing `ComicInfo.xml` found in the source folder, with an optional setting (off by default) to **delete original folders after successful import**.

---

## System Requirements

| Item | Minimum | Recommended |
| --- | --- | --- |
| **OS** | Android 8.0 (Oreo / API 26)+ | Android 11 – 15 (`targetSdk = 35`) |
| **CPU Architecture** | Any (pure Kotlin/JVM) | Any |
| **Network** | Not required (100% offline) | Not required |
| **RAM** | — | 4 GB+ |
| **Free Storage** | At least 2× the size of a single CBZ being edited | 1–2 GB+ (for large CBZ archives) |

---

## Building from Source

### Prerequisites
- **JDK 17**
- **Android SDK** (`compileSdk = 35`, `minSdk = 26`)
- **Gradle 8.11.1** (included Gradle Wrapper `./gradlew` supported)

### Run Tests & Build APKs
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

> To build a signed Release APK, create `keystore.properties` in the project root (excluded via `.gitignore`):
> ```properties
> storeFile=your-release-key.jks
> storePassword=YOUR_STORE_PASSWORD
> keyAlias=YOUR_KEY_ALIAS
> keyPassword=YOUR_KEY_PASSWORD
> ```

---

## Project Structure

```text
app/src/main/java/com/cbztagger/app/
├── MainActivity.kt               # Entry point, SAF launchers, back/unsaved dialogs, screen transitions
├── core/                         # Android-free pure Kotlin core logic (JUnit tested)
│   ├── CbzArchive.kt             # CBZ reader/writer and ZIP compression (Keep Original / 0–9)
│   ├── ComicInfo.kt              # ComicInfo.xml data model, author formatting, and batch patch
│   ├── ComicInfoXml.kt           # ComicInfo.xml parser/serializer (preserves unknown tags)
│   ├── ExternalFolderImporter.kt # External image folder → Store <folder>.cbz packager
│   ├── FilenameParser.kt         # English & Korean filename metadata parser
│   ├── FileRenamer.kt            # Template-based filename formatter & one-shot {title} handling
│   ├── PageBatchHelper.kt        # Batch page attributes, range selection, and bookmark sync
│   └── PagePreviewNavigation.kt  # Preview swipe/arrow navigation and Manga RTL direction logic
├── data/                         # Persistent settings, locale helper, and SAF repository
│   ├── AppLocaleHelper.kt        # In-app English/Korean locale switching (API 26–35)
│   ├── AppPreferences.kt         # Synchronous SharedPreferences storage (.commit())
│   └── SafCbzRepository.kt       # Android SAF document access, editor session, external folder import
└── ui/
    ├── MainViewModel.kt          # App state management and async file operations
    ├── screens/                  # Composable screens (Library, Editor, Settings)
    ├── dialogs/                  # Feature dialogs (ExternalImport, PageBatchDialogs)
    ├── components/               # Reusable UI controls (CompressionControls, FastScrollbar, LocalizedLabels, TagInputControls)
    └── theme/                    # Material 3 light/dark/dynamic color theme
```

---

## Tech Stack
- **Kotlin** · **Jetpack Compose (Material 3)**
- **AndroidX Lifecycle / ViewModel / Activity Compose / DocumentFile**
- **Kotlin Coroutines**
- **JUnit 4** (core logic unit tests)

---

## License

This project is licensed under the **MIT License**. See [LICENSE](./LICENSE) for details.
