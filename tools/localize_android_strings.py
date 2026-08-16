#!/usr/bin/env python3
"""Generate Android string resources for every language offered by LanguageActivity."""

from __future__ import annotations

import argparse
import concurrent.futures
import html
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
CACHE_PATH = Path("/tmp/a02_android_translation_cache.json")


@dataclass(frozen=True)
class LocaleSpec:
    resource_qualifier: str
    translation_code: str


LOCALES = {
    "de": LocaleSpec("de", "de"),
    "fr": LocaleSpec("fr", "fr"),
    "es": LocaleSpec("es", "es"),
    "it": LocaleSpec("it", "it"),
    "pt-BR": LocaleSpec("pt-rBR", "pt"),
    "nl": LocaleSpec("nl", "nl"),
    "sv": LocaleSpec("sv", "sv"),
    "nb": LocaleSpec("nb", "nb"),
    "da": LocaleSpec("da", "da"),
    "fi": LocaleSpec("fi", "fi"),
    "ja": LocaleSpec("ja", "ja"),
    "ko": LocaleSpec("ko", "ko"),
    "zh-Hant": LocaleSpec("b+zh+Hant", "zh-TW"),
    "pl": LocaleSpec("pl", "pl"),
}

# Compact translation models often misread context-free UI verbs (for example,
# Japanese "Continue" as "Contact"). Keep reviewed product vocabulary here so
# buttons and navigation remain natural while longer copy still comes from the
# translation engine.
MANUAL_OVERRIDES: dict[str, dict[str, str]] = {
    "de": {"Continue": "Weiter", "Saving…": "Wird gespeichert…"},
    "fr": {"Back": "Retour"},
    "es": {"My Album": "Mi álbum", "Draw with camera": "Dibujar con cámara"},
    "pt-BR": {"Welcome back": "Bem-vindo de volta"},
    "sv": {"Next": "Nästa", "Done": "Klar"},
    "pl": {"Back": "Wstecz"},
    "ja": {
        "Something went wrong": "問題が発生しました",
        "Choose Language": "言語を選択",
        "Select your preferred language": "使用する言語を選択してください",
        "Continue": "続ける",
        "Saving…": "保存中…",
        "Try again": "もう一度試す",
        "Retry": "再試行",
        "Selected": "選択済み",
        "Ad": "広告",
        "Close ad": "広告を閉じる",
        "Watch ad": "広告を見る",
        "Preparing ad…": "広告を準備中…",
        "Not now": "後で",
        "Welcome back": "おかえりなさい",
        "Settings": "設定",
        "My drawings": "マイ作品",
        "Loading drawings": "作品を読み込み中",
        "No drawings yet": "作品はまだありません",
        "Go Premium": "プレミアムにアップグレード",
        "Lifetime access": "永久アクセス",
        "Restore purchases": "購入を復元",
        "Preparing checkout…": "購入手続きを準備中…",
        "New drawing": "新しい作品",
        "Drawing created": "作品を作成しました",
        "Share drawing": "作品を共有",
        "Video saved": "動画を保存しました",
        "Draw from": "描画元",
        "My Gallery": "マイギャラリー",
        "Open AI Emoji Mix": "AI Emoji Mixを開く",
        "Open Web Browser": "Web Browserを開く",
        "Choose emoji below": "下から絵文字を選択",
        "Clear": "クリア",
        "Create": "作成",
        "Your Emoji Mix": "あなたのEmoji Mix",
        "Creating emoji mix": "Emoji Mixを作成中",
        "Generated emoji mix": "作成したEmoji Mix",
        "Save": "保存",
        "Copy": "コピー",
        "Share": "共有",
        "Draw This Emoji": "この絵文字を描く",
        "Create New": "新しく作成",
        "Image saved": "画像を保存しました",
        "Image copied": "画像をコピーしました",
        "Search images": "画像を検索",
        "Importing image…": "画像を読み込み中…",
        "Choose topic": "トピックを選択",
        "Home": "ホーム",
        "Learn": "学ぶ",
        "Profile": "プロフィール",
        "Setting": "設定",
        "Add an image": "画像を追加",
        "Camera": "カメラ",
        "Gallery": "ギャラリー",
        "Close": "閉じる",
        "Back": "戻る",
        "Complete": "完了",
        "Reset filters": "フィルターをリセット",
        "Filter": "フィルター",
        "Difficulty level": "難易度",
        "Drawing style": "描画スタイル",
        "Easy": "かんたん",
        "Medium": "ふつう",
        "Hard": "難しい",
        "Line Sketch": "線画",
        "Color": "カラー",
        "Trending": "トレンド",
        "No drawings found": "作品が見つかりません",
        "Search results": "検索結果",
        "Learn to draw": "描き方を学ぶ",
        "Categories": "カテゴリー",
        "Favorite": "お気に入り",
        "My Album": "マイアルバム",
        "Sketches": "スケッチ",
        "Time": "時間",
        "Select mode": "モードを選択",
        "Draw with camera": "カメラで描く",
        "Draw with screen": "画面で描く",
        "Draw now": "今すぐ描く",
        "Opacity": "不透明度",
        "Canvas": "キャンバス",
        "Hide": "非表示",
        "Show": "表示",
        "Lock": "ロック",
        "Flip": "反転",
        "Adjust": "調整",
        "Rotate": "回転",
        "Center": "中央",
        "Remove BG": "背景を削除",
        "Crop": "切り抜き",
        "Grid": "グリッド",
        "Zoom": "ズーム",
        "Flash": "フラッシュ",
        "Capture": "撮影",
        "Off": "オフ",
        "Front": "前面",
        "Guide": "ガイド",
        "Record": "録画",
        "Ratio": "比率",
        "Reset": "リセット",
        "Full": "全画面",
        "Good Job": "よくできました",
        "Take a photo": "写真を撮る",
        "Retake photo": "撮り直す",
        "Saved to My Album": "マイアルバムに保存しました",
        "Switch camera": "カメラを切り替える",
        "Back to Settings": "設定に戻る",
        "Previous step": "前のステップ",
        "Next": "次へ",
        "Done": "完了",
        "Help & FAQs": "ヘルプとよくある質問",
        "Privacy policy": "プライバシーポリシー",
        "Terms of use": "利用規約",
        "Not selected": "未選択",
        "Saved": "保存済み",
        "All": "すべて",
    },
    "ko": {
        "Something went wrong": "문제가 발생했습니다",
        "Choose Language": "언어 선택",
        "Select your preferred language": "사용할 언어를 선택하세요",
        "Continue": "계속",
        "Saving…": "저장 중…",
        "Try again": "다시 시도",
        "Retry": "재시도",
        "Selected": "선택됨",
        "Ad": "광고",
        "Close ad": "광고 닫기",
        "Watch ad": "광고 보기",
        "Preparing ad…": "광고 준비 중…",
        "Not now": "나중에",
        "Welcome back": "다시 오신 것을 환영합니다",
        "Settings": "설정",
        "My drawings": "내 그림",
        "Loading drawings": "그림 불러오는 중",
        "No drawings yet": "아직 그림이 없습니다",
        "Go Premium": "프리미엄 이용하기",
        "Lifetime access": "평생 이용",
        "Restore purchases": "구매 복원",
        "Preparing checkout…": "결제 준비 중…",
        "New drawing": "새 그림",
        "Drawing created": "그림을 만들었습니다",
        "Share drawing": "그림 공유",
        "Video saved": "동영상을 저장했습니다",
        "Draw from": "그리기 시작",
        "My Gallery": "내 갤러리",
        "Choose emoji below": "아래에서 이모지를 선택하세요",
        "Clear": "지우기",
        "Create": "만들기",
        "Your Emoji Mix": "나의 Emoji Mix",
        "Creating emoji mix": "Emoji Mix 만드는 중",
        "Generated emoji mix": "완성된 Emoji Mix",
        "Save": "저장",
        "Copy": "복사",
        "Share": "공유",
        "Draw This Emoji": "이 이모지 그리기",
        "Create New": "새로 만들기",
        "Image saved": "이미지를 저장했습니다",
        "Image copied": "이미지를 복사했습니다",
        "Search images": "이미지 검색",
        "Importing image…": "이미지 가져오는 중…",
        "Choose topic": "주제 선택",
        "Home": "홈",
        "Learn": "배우기",
        "Profile": "프로필",
        "Setting": "설정",
        "Add an image": "이미지 추가",
        "Camera": "카메라",
        "Gallery": "갤러리",
        "Close": "닫기",
        "Back": "뒤로",
        "Complete": "완료",
        "Reset filters": "필터 초기화",
        "Filter": "필터",
        "Difficulty level": "난이도",
        "Drawing style": "그림 스타일",
        "Easy": "쉬움",
        "Medium": "보통",
        "Hard": "어려움",
        "Line Sketch": "선 스케치",
        "Color": "컬러",
        "Trending": "인기",
        "No drawings found": "그림을 찾을 수 없습니다",
        "Search results": "검색 결과",
        "Learn to draw": "그리기 배우기",
        "Categories": "카테고리",
        "Favorite": "즐겨찾기",
        "My Album": "내 앨범",
        "Sketches": "스케치",
        "Time": "시간",
        "Select mode": "모드 선택",
        "Draw with camera": "카메라로 그리기",
        "Draw with screen": "화면으로 그리기",
        "Draw now": "지금 그리기",
        "Opacity": "불투명도",
        "Canvas": "캔버스",
        "Hide": "숨기기",
        "Show": "표시",
        "Lock": "잠금",
        "Flip": "뒤집기",
        "Adjust": "조정",
        "Rotate": "회전",
        "Center": "가운데",
        "Remove BG": "배경 제거",
        "Crop": "자르기",
        "Grid": "격자",
        "Zoom": "확대/축소",
        "Flash": "플래시",
        "Capture": "촬영",
        "Off": "끄기",
        "Front": "전면",
        "Guide": "가이드",
        "Record": "녹화",
        "Ratio": "비율",
        "Reset": "초기화",
        "Full": "전체 화면",
        "Good Job": "잘했어요",
        "Take a photo": "사진 촬영",
        "Retake photo": "다시 촬영",
        "Saved to My Album": "내 앨범에 저장했습니다",
        "Switch camera": "카메라 전환",
        "Back to Settings": "설정으로 돌아가기",
        "Previous step": "이전 단계",
        "Next": "다음",
        "Done": "완료",
        "Help & FAQs": "도움말 및 자주 묻는 질문",
        "Privacy policy": "개인정보처리방침",
        "Terms of use": "이용약관",
        "Not selected": "선택 안 함",
        "Saved": "저장됨",
        "All": "전체",
    },
    "zh-Hant": {
        "Something went wrong": "發生錯誤",
        "Choose Language": "選擇語言",
        "Select your preferred language": "選擇您偏好的語言",
        "Image Projector": "影像投影",
        "Project your masterpiece through AR technology": "透過 AR 技術投影您的傑作",
        "Project images onto paper using AR technology, so you can sketch with ease": "使用 AR 技術將影像投影到紙上，讓您輕鬆描繪",
        "Continue": "繼續",
        "Saving…": "儲存中…",
        "Try again": "再試一次",
        "Retry": "重試",
        "Selected": "已選取",
        "Ad": "廣告",
        "Close ad": "關閉廣告",
        "Watch ad": "觀看廣告",
        "Preparing ad…": "正在準備廣告…",
        "Not now": "暫時不要",
        "Welcome back": "歡迎回來",
        "Settings": "設定",
        "My drawings": "我的畫作",
        "Loading drawings": "正在載入畫作",
        "No drawings yet": "尚無畫作",
        "Go Premium": "升級 Premium",
        "Lifetime access": "永久使用",
        "Restore purchases": "恢復購買",
        "Preparing checkout…": "正在準備結帳…",
        "New drawing": "新增畫作",
        "Drawing created": "畫作已建立",
        "Share drawing": "分享畫作",
        "Video saved": "影片已儲存",
        "Draw from": "繪圖來源",
        "My Gallery": "我的圖庫",
        "Choose emoji below": "從下方選擇表情符號",
        "Clear": "清除",
        "Create": "建立",
        "Your Emoji Mix": "您的 Emoji Mix",
        "Creating emoji mix": "正在建立 Emoji Mix",
        "Generated emoji mix": "已建立 Emoji Mix",
        "Save": "儲存",
        "Copy": "複製",
        "Share": "分享",
        "Draw This Emoji": "繪製這個表情符號",
        "Create New": "重新建立",
        "Image saved": "圖片已儲存",
        "Image copied": "圖片已複製",
        "Search images": "搜尋圖片",
        "Importing image…": "正在匯入圖片…",
        "Choose topic": "選擇主題",
        "Home": "首頁",
        "Learn": "學習",
        "Profile": "個人資料",
        "Setting": "設定",
        "Add an image": "新增圖片",
        "Camera": "相機",
        "Gallery": "圖庫",
        "Close": "關閉",
        "Back": "返回",
        "Complete": "完成",
        "Reset filters": "重設篩選條件",
        "Filter": "篩選",
        "Difficulty level": "難度",
        "Drawing style": "繪圖風格",
        "Easy": "簡單",
        "Medium": "中等",
        "Hard": "困難",
        "Line Sketch": "線稿",
        "Color": "彩色",
        "Trending": "熱門",
        "No drawings found": "找不到畫作",
        "Search results": "搜尋結果",
        "Learn to draw": "學習繪畫",
        "Categories": "類別",
        "Favorite": "最愛",
        "My Album": "我的相簿",
        "Sketches": "草圖",
        "Time": "時間",
        "Select mode": "選擇模式",
        "Draw with camera": "使用相機繪圖",
        "Draw with screen": "使用螢幕繪圖",
        "Draw now": "立即繪圖",
        "Opacity": "不透明度",
        "Canvas": "畫布",
        "Hide": "隱藏",
        "Show": "顯示",
        "Lock": "鎖定",
        "Flip": "翻轉",
        "Adjust": "調整",
        "Rotate": "旋轉",
        "Center": "置中",
        "Remove BG": "移除背景",
        "Crop": "裁切",
        "Grid": "格線",
        "Zoom": "縮放",
        "Flash": "閃光燈",
        "Capture": "拍攝",
        "Off": "關閉",
        "Front": "前置",
        "Guide": "指南",
        "Record": "錄影",
        "Ratio": "比例",
        "Reset": "重設",
        "Full": "全螢幕",
        "Good Job": "做得好",
        "Take a photo": "拍照",
        "Retake photo": "重新拍照",
        "Saved to My Album": "已儲存至我的相簿",
        "Switch camera": "切換相機",
        "Back to Settings": "返回設定",
        "Previous step": "上一步",
        "Next": "下一步",
        "Done": "完成",
        "Help & FAQs": "說明與常見問題",
        "Privacy policy": "隱私權政策",
        "Terms of use": "使用條款",
        "Not selected": "未選取",
        "Saved": "已儲存",
        "All": "全部",
    },
}

PLURAL_QUANTITIES: dict[str, tuple[str, ...]] = {
    "de": ("one", "other"),
    "fr": ("one", "many", "other"),
    "es": ("one", "many", "other"),
    "it": ("one", "many", "other"),
    "pt-BR": ("one", "many", "other"),
    "nl": ("one", "other"),
    "sv": ("one", "other"),
    "nb": ("one", "other"),
    "da": ("one", "other"),
    "fi": ("one", "other"),
    "ja": ("other",),
    "ko": ("other",),
    "zh-Hant": ("other",),
    "pl": ("one", "few", "many", "other"),
}

SOURCE_FILES = (
    Path("app/src/main/res/values/strings.xml"),
    Path("feature/home/src/main/res/values/strings.xml"),
)

PROTECTED_PATTERNS = (
    re.compile(r"%(?:\d+\$)?[a-zA-Z]"),
    re.compile(r"com\.a02\.draw"),
    re.compile(
        r"AR Drawing|Emoji Mix|Google Play Billing|Google Play|Google Payments|"
        r"Google Mobile Ads|Google Images|Android System WebView|Android|Web Browser|"
        r"KiroAds|Firebase Analytics|AppsFlyer|Adjust|Meta|HTTPS|URI|VIP|"
        r"Jujutsu Kaisen|One Piece|Doraemon",
    ),
)
PROTECTED_PATTERN = re.compile(
    "|".join(f"(?:{pattern.pattern})" for pattern in PROTECTED_PATTERNS),
)


@dataclass(frozen=True)
class ResourceEntry:
    kind: str
    name: str
    values: tuple[tuple[str | None, str], ...]


def parse_entries(path: Path) -> list[ResourceEntry]:
    root = ET.parse(path).getroot()
    entries: list[ResourceEntry] = []
    for element in root:
        if element.tag not in {"string", "plurals"}:
            continue
        if element.attrib.get("translatable") == "false":
            continue
        name = element.attrib["name"]
        if element.tag == "string":
            entries.append(ResourceEntry("string", name, ((None, text_of(element)),)))
        else:
            values = tuple(
                (item.attrib["quantity"], text_of(item))
                for item in element.findall("item")
            )
            entries.append(ResourceEntry("plurals", name, values))
    return entries


def text_of(element: ET.Element) -> str:
    return "".join(element.itertext())


def protect(text: str) -> tuple[str, dict[str, str]]:
    replacements: dict[str, str] = {}

    def replace_match(match: re.Match[str]) -> str:
        token = f"ZXQ{len(replacements):03d}QXZ"
        replacements[token] = match.group(0)
        return token

    protected = text
    for pattern in PROTECTED_PATTERNS:
        protected = pattern.sub(replace_match, protected)
    return protected, replacements


def restore(text: str, replacements: dict[str, str]) -> str:
    restored = text
    for token, value in replacements.items():
        if token not in restored:
            raise ValueError(f"Translation dropped protected token {token}")
        restored = restored.replace(token, value)
    return restored


def request_translation(protected_source: str, target: str) -> str:
    payload = urllib.parse.urlencode(
        {"client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": protected_source},
    ).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(6):
        try:
            request = urllib.request.Request(
                TRANSLATE_URL,
                data=payload,
                headers={"User-Agent": "A02-Draw-localization/1.0"},
            )
            with urllib.request.urlopen(request, timeout=45) as response:
                body = json.load(response)
            return "".join(segment[0] for segment in body[0] if segment[0])
        except (OSError, ValueError, urllib.error.URLError, json.JSONDecodeError) as error:
            last_error = error
            time.sleep(min(2 ** attempt, 20))
    raise RuntimeError(
        f"Could not translate text to {target}: {protected_source[:80]}",
    ) from last_error


def translate_unit(source: str, target: str) -> str:
    protected, replacements = protect(source)
    return restore(request_translation(protected, target), replacements)


def translate(source: str, target: str) -> str:
    if not source.strip():
        return source

    # Google Translate may truncate very long legal strings and silently drop
    # protected placeholders. Android line breaks are literal ``\\n`` tokens,
    # so translate each paragraph independently and join with the exact source
    # separators. This also keeps headings and numbered policy sections intact.
    parts = re.split(r"((?:\\n)+)", source)
    translated_parts = [
        part if re.fullmatch(r"(?:\\n)+", part) or not part.strip() else translate_unit(part, target)
        for part in parts
    ]
    return "".join(translated_parts)


def translatable_units(source: str) -> list[str]:
    """Return independently translatable pieces while preserving Android line breaks."""
    return [
        part
        for part in re.split(r"((?:\\n)+)", source)
        if part.strip() and not re.fullmatch(r"(?:\\n)+", part)
    ]


def translate_batch(units: list[str], target: str) -> list[str]:
    protected_units: list[str] = []
    replacements_by_unit: list[dict[str, str]] = []
    for unit in units:
        protected, replacements = protect(unit)
        protected_units.append(protected)
        replacements_by_unit.append(replacements)

    separators = [f"ZXQA02SEP{index:03d}QXZ" for index in range(len(units) - 1)]
    joined_parts: list[str] = []
    for index, protected in enumerate(protected_units):
        joined_parts.append(protected)
        if index < len(separators):
            joined_parts.append(f"\n{separators[index]}\n")
    translated = request_translation("".join(joined_parts), target)

    translated_units = [translated]
    for separator in separators:
        next_units: list[str] = []
        for value in translated_units:
            if separator in value:
                left, right = value.split(separator, maxsplit=1)
                next_units.extend((left.rstrip("\n"), right.lstrip("\n")))
            else:
                next_units.append(value)
        translated_units = next_units
    if len(translated_units) != len(units):
        raise ValueError(f"Translation changed a batch separator for {target}")
    return [
        restore(value, replacements)
        for value, replacements in zip(translated_units, replacements_by_unit, strict=True)
    ]


def cache_key(target: str, source: str) -> str:
    return f"{target}\u0000{source}"


def load_cache() -> dict[str, str]:
    if not CACHE_PATH.exists():
        return {}
    return json.loads(CACHE_PATH.read_text(encoding="utf-8"))


def save_cache(cache: dict[str, str]) -> None:
    CACHE_PATH.write_text(
        json.dumps(cache, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )


def collect_source_texts(entries_by_file: dict[Path, list[ResourceEntry]]) -> list[str]:
    return list(
        dict.fromkeys(
            text
            for entries in entries_by_file.values()
            for entry in entries
            for _, text in entry.values
        ),
    )


def translate_all(
    entries_by_file: dict[Path, list[ResourceEntry]],
) -> dict[tuple[str, str], str]:
    cache = load_cache()
    source_texts = collect_source_texts(entries_by_file)
    missing_sources = {
        (locale.translation_code, source)
        for locale in LOCALES.values()
        for source in source_texts
        if cache_key(locale.translation_code, source) not in cache
    }
    missing_units = list(
        dict.fromkeys(
            (target, unit)
            for target, source in missing_sources
            for unit in translatable_units(source)
            if cache_key(target, unit) not in cache
        ),
    )
    batches: list[tuple[str, list[str]]] = []
    for target in dict.fromkeys(target for target, _ in missing_units):
        current: list[str] = []
        current_size = 0
        for unit_target, unit in missing_units:
            if unit_target != target:
                continue
            added_size = len(unit) + 24
            if current and current_size + added_size > 2_500:
                batches.append((target, current))
                current = []
                current_size = 0
            current.append(unit)
            current_size += added_size
        if current:
            batches.append((target, current))

    if batches:
        # One worker is intentional: the unofficial endpoint throttles large
        # bursts. Batching keeps the request count low without sacrificing a
        # resumable per-string cache.
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            futures = {
                executor.submit(translate_batch, units, target): (target, units)
                for target, units in batches
            }
            completed = 0
            for future in concurrent.futures.as_completed(futures):
                target, units = futures[future]
                values = future.result()
                for unit, value in zip(units, values, strict=True):
                    cache[cache_key(target, unit)] = value
                completed += 1
                save_cache(cache)
                print(f"Translated batch {completed}/{len(batches)}", flush=True)

    for target, source in missing_sources:
        parts = re.split(r"((?:\\n)+)", source)
        cache[cache_key(target, source)] = "".join(
            part
            if re.fullmatch(r"(?:\\n)+", part) or not part.strip()
            else cache[cache_key(target, part)]
            for part in parts
        )
    save_cache(cache)
    return {
        (locale_tag, source): cache[cache_key(locale.translation_code, source)]
        for locale_tag, locale in LOCALES.items()
        for source in source_texts
    }


def translate_all_offline(
    entries_by_file: dict[Path, list[ResourceEntry]],
) -> dict[tuple[str, str], str]:
    try:
        import argostranslate.translate  # type: ignore[import-not-found]
        from opencc import OpenCC  # type: ignore[import-not-found]
    except ImportError as error:
        raise SystemExit(
            "Offline localization needs argostranslate and opencc-python-reimplemented",
        ) from error

    del argostranslate, OpenCC  # Imports are validated here; workers load models.
    cache = load_cache()
    source_texts = collect_source_texts(entries_by_file)
    tasks: list[tuple[str, list[str]]] = []
    for locale in LOCALES.values():
        missing = [
            source
            for source in source_texts
            if cache_key(locale.translation_code, source) not in cache
        ]
        for start in range(0, len(missing), 25):
            tasks.append((locale.translation_code, missing[start : start + 25]))

    completed = 0
    total = sum(len(sources) for _, sources in tasks)
    if tasks:
        with concurrent.futures.ProcessPoolExecutor(max_workers=4) as executor:
            futures = [executor.submit(offline_translate_chunk, task) for task in tasks]
            for future in concurrent.futures.as_completed(futures):
                target, values = future.result()
                for source, translated in values.items():
                    cache[cache_key(target, source)] = translated
                completed += len(values)
                save_cache(cache)
                print(f"Offline translated {completed}/{total} strings", flush=True)
    save_cache(cache)
    return {
        (locale_tag, source): cache[cache_key(locale.translation_code, source)]
        for locale_tag, locale in LOCALES.items()
        for source in source_texts
    }


_OFFLINE_TRANSLATORS: dict[str, object] = {}


def offline_translate_chunk(task: tuple[str, list[str]]) -> tuple[str, dict[str, str]]:
    import argostranslate.translate  # type: ignore[import-not-found]
    from opencc import OpenCC  # type: ignore[import-not-found]

    target, sources = task
    argos_target = "zh" if target == "zh-TW" else target
    translator = _OFFLINE_TRANSLATORS.get(argos_target)
    if translator is None:
        installed = {
            language.code: language
            for language in argostranslate.translate.get_installed_languages()
        }
        translator = installed["en"].get_translation(installed[argos_target])
        _OFFLINE_TRANSLATORS[argos_target] = translator
    traditional_chinese = OpenCC("s2twp") if target == "zh-TW" else None
    values: dict[str, str] = {}
    for source in sources:
        translated_parts: list[str] = []
        for part in re.split(r"((?:\\n)+)", source):
            if re.fullmatch(r"(?:\\n)+", part) or not part.strip():
                translated_parts.append(part)
                continue
            protected, replacements = protect(part)
            try:
                translated = restore(
                    translator.translate(protected),  # type: ignore[attr-defined]
                    replacements,
                )
            except ValueError:
                # Some compact offline models occasionally drop synthetic
                # tokens. Translate only the text between protected values in
                # that rare case, then stitch placeholders/brands back exactly.
                translated = "".join(
                    segment
                    if PROTECTED_PATTERN.fullmatch(segment)
                    else translator.translate(segment)  # type: ignore[attr-defined]
                    for segment in re.split(f"({PROTECTED_PATTERN.pattern})", part)
                    if segment
                )
            if traditional_chinese is not None:
                translated = traditional_chinese.convert(translated)
            translated_parts.append(translated)
        values[source] = "".join(translated_parts)
    return target, values


def android_escape(text: str) -> str:
    escaped = html.escape(text, quote=False).replace("'", r"\'")
    return escaped


def normalize_translation(text: str, locale_tag: str) -> str:
    normalized = text.replace("...", "…")
    if locale_tag == "it":
        normalized = normalized.replace("passo passo", "passo dopo passo")
    return normalized


def render(
    entries: list[ResourceEntry],
    locale_tag: str,
    translations: dict[tuple[str, str], str],
) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    overrides = MANUAL_OVERRIDES.get(locale_tag, {})
    for entry in entries:
        if entry.kind == "string":
            source = entry.values[0][1]
            value = android_escape(
                normalize_translation(
                    overrides.get(source, translations[(locale_tag, source)]),
                    locale_tag,
                ),
            )
            lines.append(f'    <string name="{entry.name}">{value}</string>')
        else:
            lines.append(f'    <plurals name="{entry.name}">')
            sources_by_quantity = dict(entry.values)
            for quantity in PLURAL_QUANTITIES[locale_tag]:
                source = sources_by_quantity.get(quantity, sources_by_quantity["other"])
                value = android_escape(
                    normalize_translation(
                        overrides.get(source, translations[(locale_tag, source)]),
                        locale_tag,
                    ),
                )
                lines.append(f'        <item quantity="{quantity}">{value}</item>')
            lines.append("    </plurals>")
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def verify_placeholders(source: str, translated: str) -> None:
    placeholder = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
    if sorted(placeholder.findall(source)) != sorted(placeholder.findall(translated)):
        raise ValueError(f"Placeholder mismatch: {source!r} -> {translated!r}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify existing files only")
    parser.add_argument(
        "--offline",
        action="store_true",
        help="use locally installed Argos models instead of Google Translate",
    )
    args = parser.parse_args()

    entries_by_file = {path: parse_entries(path) for path in SOURCE_FILES}
    translations = (
        translate_all_offline(entries_by_file)
        if args.offline
        else translate_all(entries_by_file)
    )
    for locale_tag, locale in LOCALES.items():
        for source_file, entries in entries_by_file.items():
            output = source_file.parent.parent / f"values-{locale.resource_qualifier}" / "strings.xml"
            expected = render(entries, locale_tag, translations)
            for entry in entries:
                for _, source in entry.values:
                    verify_placeholders(source, translations[(locale_tag, source)])
            if args.check:
                if not output.exists() or output.read_text(encoding="utf-8") != expected:
                    raise SystemExit(f"Out-of-date localization: {output}")
            else:
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_text(expected, encoding="utf-8")
                print(f"Wrote {output}")


if __name__ == "__main__":
    main()
