#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Osmium v2.3.3 strings: keep-count keys x9 locales + accurate count wording."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"
LOCALES = ["values", "values-zh", "values-de", "values-es", "values-fr",
           "values-hi", "values-ja", "values-ko", "values-ru"]
LANG = {"values": "en", "values-zh": "zh", "values-de": "de",
        "values-es": "es", "values-fr": "fr", "values-hi": "hi",
        "values-ja": "ja", "values-ko": "ko", "values-ru": "ru"}


def esc(s: str) -> str:
    return (s.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace("'", "\\'")
             .replace("\n", "\\n"))


NEW_KEYS = {
"auto_backup_keep_label": {
 "en": "Backups to keep", "zh": "备份文件数量",
 "de": "Zu behaltende Backups", "es": "Copias a conservar",
 "fr": "Sauvegardes à conserver", "hi": "रखे जाने वाले बैकअप",
 "ja": "保持するバックアップ数", "ko": "보관할 백업 수",
 "ru": "Хранить копий"},
"auto_backup_keep_desc": {
 "en": "Older backups are deleted beyond this count (max 10)",
 "zh": "超出数量后自动删除最早的备份（最多 10 份）",
 "de": "Über diese Anzahl hinaus werden ältere Backups gelöscht (max. 10)",
 "es": "Las copias más antiguas se borran al superar este número (máx. 10)",
 "fr": "Au-delà de ce nombre, les sauvegardes les plus anciennes sont supprimées (max. 10)",
 "hi": "इस संख्या से अधिक होने पर पुराने बैकअप हटा दिए जाते हैं (अधिकतम 10)",
 "ja": "この数を超えた古いバックアップは削除されます（最大10）",
 "ko": "이 개수를 초과하면 오래된 백업이 삭제됩니다(최대 10)",
 "ru": "Свыше этого числа старые копии удаляются (макс. 10)"},
"auto_backup_keep_picker_title": {
 "en": "Choose how many to keep", "zh": "选择保留数量",
 "de": "Anzahl wählen", "es": "Elige cuántas conservar",
 "fr": "Choisir combien conserver", "hi": "कितनी रखनी हैं चुनें",
 "ja": "保持する数を選択", "ko": "보관할 개수 선택",
 "ru": "Выберите, сколько хранить"},
}

PRIVACY_OLD_NEW = {
 "en": ("the 5 newest are kept and older ones are pruned",
        "a configurable number are kept (default 5, max 10) and older ones are pruned"),
 "zh": ("保留最新 5 份并清理更旧的备份",
        "按设置保留最近若干份（默认 5 份，最多 10 份）并清理更旧的备份"),
 "de": ("die 5 neuesten bleiben, ältere werden gelöscht",
        "eine einstellbare Anzahl bleibt (Standard 5, max. 10), ältere werden gelöscht"),
 "es": ("se conservan las 5 más recientes y se borran las antiguas",
        "se conserva una cantidad configurable (por defecto 5, máx. 10) y se borran las antiguas"),
 "fr": ("les 5 plus récentes sont conservées et les anciennes supprimées",
        "un nombre configurable est conservé (5 par défaut, 10 max.) et les anciennes sont supprimées"),
 "hi": ("सबसे नई 5 फ़ाइलें रखी जाती हैं और पुरानी हटाई जाती हैं",
        "सेट की गई संख्या तक फ़ाइलें रखी जाती हैं (डिफ़ॉल्ट 5, अधिकतम 10) और पुरानी हटाई जाती हैं"),
 "ja": ("最新5件を残して古いものは削除されます",
        "設定した件数を残して（デフォルト5、最大10）古いものは削除されます"),
 "ko": ("최신 5개만 남기고 오래된 파일은 삭제됩니다",
        "설정한 개수(기본 5, 최대 10)만 남기고 오래된 파일은 삭제됩니다"),
 "ru": ("хранятся 5 последних, более старые удаляются",
        "хранится настраиваемое число копий (по умолчанию 5, максимум 10), более старые удаляются"),
}

MANUAL_OLD_NEW = {
 "en": ("the 5 newest auto-backups are kept and older ones are pruned",
        "a configurable number of auto-backups are kept (default 5, max 10) and older ones are pruned"),
 "zh": ("自动保留最新 5 份并清理更旧的备份",
        "按设置自动保留最近若干份（默认 5 份，最多 10 份）并清理更旧的备份"),
 "de": ("die 5 neuesten bleiben erhalten, ältere werden gelöscht",
        "eine einstellbare Anzahl bleibt erhalten (Standard 5, max. 10), ältere werden gelöscht"),
 "es": ("se conservan las 5 más recientes y se borran las antiguas",
        "se conserva una cantidad configurable (por defecto 5, máx. 10) y se borran las antiguas"),
 "fr": ("les 5 plus récentes sont conservées et les anciennes supprimées",
        "un nombre configurable est conservé (5 par défaut, 10 max.) et les anciennes sont supprimées"),
 "hi": ("सबसे नई 5 फ़ाइलें रखी जाती हैं और पुरानी हटा दी जाती हैं",
        "सेट की गई संख्या तक फ़ाइलें रखी जाती हैं (डिफ़ॉल्ट 5, अधिकतम 10) और पुरानी हटा दी जाती हैं"),
 "ja": ("最新5件を残して古いものは削除されます",
        "設定した件数を残して（デフォルト5、最大10）古いものは削除されます"),
 "ko": ("최신 5개만 남기고 오래된 파일은 삭제됩니다",
        "설정한 개수(기본 5, 최대 10)만 남기고 오래된 파일은 삭제됩니다"),
 "ru": ("хранятся 5 последних, более старые удаляются",
        "хранится настраиваемое число копий (по умолчанию 5, максимум 10), более старые удаляются"),
}


def replace_in_key(content, key, old, new):
    pattern = re.compile(
        r'(<string name="' + re.escape(key) + r'">)(.*?)(</string>)',
        re.DOTALL,
    )
    def repl(m):
        value = m.group(2)
        assert old in value, f"{key}: phrase not found: {old[:40]}"
        return m.group(1) + value.replace(old, esc(new), 1) + m.group(3)
    assert pattern.search(content), f"key not found: {key}"
    return pattern.sub(repl, content, count=1)


for folder in LOCALES:
    path = f"{BASE}/{folder}/strings.xml"
    content = open(path, encoding="utf-8").read()
    lang = LANG[folder]

    old, new = PRIVACY_OLD_NEW[lang]
    content = replace_in_key(content, "privacy_body", old, new)
    old, new = MANUAL_OLD_NEW[lang]
    content = replace_in_key(content, "manual_auto_backup_body", old, new)

    entries = [
        f'<string name="{k}">{esc(NEW_KEYS[k][lang])}</string>'
        for k in NEW_KEYS
    ]
    block = "\n    " + "\n    ".join(entries)
    idx = content.rfind("</resources>")
    content = content[:idx] + block + "\n" + content[idx:]

    open(path, "w", encoding="utf-8").write(content)
    print(f"OK {folder} ({lang})")

print("ALL DONE")
