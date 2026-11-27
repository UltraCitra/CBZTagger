# CBZTagger

[English](README.md) | **한국어**

**CBZTagger**는 안드로이드에서 완전 오프라인으로 동작하는 **CBZ(ZIP) 전용 만화 메타데이터(`ComicInfo.xml`) 편집 및 ZIP 압축 관리 앱**입니다.

> [!NOTE]
> **AI 활용 개발 안내**  
> 이 프로젝트는 생성형 AI와의 협업을 통해 설계·구현·테스트·문서화된 결과물입니다.  
> 단위 테스트와 실기기 검증을 거쳤으나, 중요한 만화 아카이브(`.cbz`)를 대량 편집·재압축·리네임하기 전에는 원본 파일을 백업해 두시는 것을 권장합니다.

---

## 주요 기능

### 1. 완전 오프라인 · 안전한 로컬 파일 처리
- **`INTERNET` 권한 없음**: 네트워크 연결 없이 기기 내부 저장소에서만 동작합니다.
- **Android SAF(Storage Access Framework)** 기반 폴더 및 개별 `.cbz` 파일 열기 지원.
- 기존 `ComicInfo.xml`에 포함된 앱 미지원 확장 태그도 수정 시 손실 없이 보존합니다.
- **한국어 · 영어 다국어 지원**: 설정에서 `시스템 기본` / `한국어` / `English`를 즉시 전환할 수 있으며 Android 13+ 앱별 언어 설정과도 연동됩니다.

### 2. 메타데이터 (`ComicInfo.xml`) 편집
- **기본 정보**: 시리즈(`Series`), 번호(`Number`), 전체(`Count`), 볼륨(`Volume`), 제목(`Title`), 출판사/임프린트, 발행일(연/월/일), 장르, 언어 코드, 읽기 방향(`Manga`), 흑백 여부, 줄거리(`Summary`), 메모(`Notes`).
  - `시리즈`와 `번호`가 입력된 경우 버튼 한 번으로 `{series} {number}` 형식 제목 자동 입력.
  - 파일명에서 시리즈·권호·볼륨·연도 자동 추출(한국어 `권/화/부/편` 및 영문 패턴 지원).
  - 읽기 방향(`Manga`) 드롭다운 선택 (`Yes`, `YesAndRightToLeft`, `No`).
- **상세 · 크레딧**: 그림·글작가 통합 입력(기본) 및 `그림·글 다름` 체크박스로 `Penciller`/`Writer` 분리 입력, 잉커/채색/식자/표지작가/편집자 및 추가 분류 정보 편집.
- **미저장 변경사항 보호**: 저장하지 않은 변경사항이 있을 때 뒤로가기 시 확인 팝업(`저장 후 나가기` / `저장하지 않고 나가기` / `계속 편집`) 표시.

### 3. 페이지 관리 · 북마크 · 미리보기
- **페이지 속성 편집**: `FrontCover`, `InnerCover`, `Story`, `BackCover`, `Advertisement`, `DoublePage` 등 개별 및 일괄 지정.
  - 일괄 속성 편집 모드: 다중 체크박스 선택, 시작~끝 범위 선택, 빈 페이지 일괄 `Story` 지정.
  - 우측 빠른 이동 스크롤바 및 현재 페이지 표시(`#페이지 / 전체`) 지원.
- **북마크 관리**:
  - 개별 페이지 길게 누르기로 북마크 추가·수정·삭제.
  - 상단 `북마크 지정` 창에서 여러 페이지 북마크 일괄 관리.
- **페이지 미리보기**:
  - 좌우 스와이프 및 양쪽 화살표 버튼으로 이전/다음 페이지 이동.
  - 설정에서 `Manga` 태그 읽기 방향(`Yes` / `YesAndRightToLeft` 시 우→좌) 연동 ON/OFF 가능.
  - 설정에서 페이지 목록 썸네일 표시 ON/OFF 가능(별도 캐시 미사용).

### 4. ZIP 압축 단계 제어 및 일괄 작업
- **압축 단계 설정**:
  - **`기존 유지`**: 기존 이미지 압축 방식을 그대로 두고 `ComicInfo.xml`만 빠르게 갱신.
  - **`0 ~ 9` 단계**: `0`(무압축 보관 / Store) ~ `9`(Deflate 최대 압축) 선택 가능.
- **라이브러리 다중 선택 일괄 작업** (항목 길게 누르기):
  - **일괄 압축**: 선택한 여러 CBZ를 지정한 압축 단계(0~9)로 일괄 재압축.
  - **일괄 태그 편집**: 시리즈·출판사·장르·작가·연도·언어·읽기 방향 일괄 적용.
  - **일괄 파일명 파싱**: 파일명에서 메타데이터 추출 후 저장.
  - **일괄 리네임**: 템플릿(`{series}`, `{issue}`, `{volume}`, `{year}`, `{title}` 등), 0-패딩, 빈 괄호 정리, 단권(`Series` 없음) 시 `{title}` 사용 옵션 및 접기/펼치기 미리보기 지원.

### 5. 외부 이미지 폴더 → CBZ 불러오기
- `<설정된 외부 경로>/<폴더명>/<이미지 파일들>` 구조의 폴더를 **`<폴더명>.cbz`** 로 묶어 현재 CBZ 작업 폴더(또는 지정 폴더)로 가져옵니다.
- **원본 이미지 파일명 유지** + 자연 정렬 순서로 패키징하며, 이후 메타데이터 편집 시 재압축할 수 있도록 **무압축 보관(`ZIP_STORED`)**으로 빠르게 생성합니다.
- 원본 폴더 내 기존 `ComicInfo.xml`이 있으면 그대로 포함하며, 설정에서 **불러오기 성공 후 원본 폴더 삭제** 옵션(기본 꺼짐)을 선택할 수 있습니다.

---

## 시스템 요구 사양

| 항목 | 최소 사양 | 권장 사양 |
| --- | --- | --- |
| **OS** | Android 8.0 (Oreo / API 26) 이상 | Android 11 ~ 15 (`targetSdk = 35`) |
| **CPU 아키텍처** | 제한 없음 (순수 Kotlin/JVM) | 제한 없음 |
| **네트워크** | 불필요 (완전 오프라인) | 불필요 |
| **RAM** | — | 4GB 이상 |
| **저장소 여유 공간** | 편집할 단일 CBZ 크기의 2배 이상 | 1~2GB 이상 (대용량 CBZ 작업 시) |

---

## 빌드 방법

### 요구 환경
- **JDK 17**
- **Android SDK** (`compileSdk = 35`, `minSdk = 26`)
- **Gradle 8.11.1** (포함된 Gradle Wrapper `./gradlew` 사용 가능)

### 단위 테스트 및 APK 빌드
```bash
# 단위 테스트 실행
./gradlew testDebugUnitTest

# Debug APK 빌드
./gradlew assembleDebug

# Release APK 빌드
./gradlew assembleRelease
```

> Release 서명 빌드를 하려면 프로젝트 루트에 `keystore.properties`를 작성하세요(`.gitignore`로 제외됨):
> ```properties
> storeFile=your-release-key.jks
> storePassword=YOUR_STORE_PASSWORD
> keyAlias=YOUR_KEY_ALIAS
> keyPassword=YOUR_KEY_PASSWORD
> ```

---

## 프로젝트 구조

```text
app/src/main/java/com/cbztagger/app/
├── MainActivity.kt               # 진입점, SAF 런처, 뒤로가기/이탈 확인, 화면 전환 애니메이션
├── core/                         # Android 비의존 순수 Kotlin 코어 로직 (JUnit 테스트 대상)
│   ├── CbzArchive.kt             # CBZ 읽기/쓰기 및 ZIP 압축 단계(기존 유지 / 0~9) 재압축
│   ├── ComicInfo.kt              # ComicInfo.xml 데이터 모델 및 일괄 패치
│   ├── ComicInfoXml.kt           # ComicInfo.xml 파서·직렬화 (미지원 태그 보존)
│   ├── ExternalFolderImporter.kt # 외부 이미지 폴더 → 무압축(Store) <폴더명>.cbz 패키징
│   ├── FilenameParser.kt         # 한국어/영문 파일명 파싱
│   ├── FileRenamer.kt            # 템플릿 기반 파일명 생성 및 단권 {title} 처리
│   ├── PageBatchHelper.kt        # 페이지 일괄 속성 편집·범위 선택·북마크 동기화
│   └── PagePreviewNavigation.kt  # 미리보기 좌우 이동 및 Manga RTL 방향 계산
├── data/                         # 영구 설정, 언어 헬퍼 및 저장소
│   ├── AppLocaleHelper.kt        # 앱 내 한국어/영어 언어 전환 (API 26~35)
│   ├── AppPreferences.kt         # SharedPreferences 동기 저장(.commit())
│   └── SafCbzRepository.kt       # Android SAF 문서 접근, 에디터 세션, 외부 폴더 스캔/임포트
└── ui/
    ├── MainViewModel.kt          # 앱 상태 관리 및 비동기 작업 처리
    ├── screens/                  # 화면 Composable (Library, Editor, Settings)
    ├── dialogs/                  # 기능별 팝업 (ExternalImport, PageBatchDialogs)
    ├── components/               # 재사용 UI 컨트롤 (CompressionControls, FastScrollbar, LocalizedLabels, TagInputControls)
    └── theme/                    # Material 3 라이트/다크/다이나믹 테마
```

---

## 기술 스택
- **Kotlin** · **Jetpack Compose (Material 3)**
- **AndroidX Lifecycle / ViewModel / Activity Compose / DocumentFile**
- **Kotlin Coroutines**
- **JUnit 4** (코어 로직 단위 테스트)

---

## 라이선스

이 프로젝트는 **MIT License** 하에 배포됩니다. 자세한 내용은 [LICENSE](./LICENSE)를 참고하세요.
