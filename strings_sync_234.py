#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.3.4: update check now runs on every app open — strip all once-per-day wording."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"
FOLDERS = {"en": "values", "zh": "values-zh", "de": "values-de",
           "es": "values-es", "fr": "values-fr", "hi": "values-hi",
           "ja": "values-ja", "ko": "values-ko", "ru": "values-ru"}

# key -> {locale: (old, new)}
REPL = {
"update_check_desc": {
 "en": ("when the app opens (once per day)", "when the app opens"),
 "zh": ("（每天最多一次）", ""),
 "de": (" (einmal pro Tag)", ""),
 "es": (" (una vez al día)", ""),
 "fr": (" (une fois par jour)", ""),
 "hi": (" (दिन में एक बार)", ""),
 "ja": ("（1日1回）", ""),
 "ko": ("(하루 1회)", ""),
 "ru": (" (раз в день)", ""),
},
"manual_updates_body": {
 "en": ("silently asks the GitHub releases API for the newest version once per day when the app opens",
        "silently asks the GitHub releases API for the newest version every time the app opens"),
 "zh": ("每次打开应用时静默向 GitHub Releases API 查询最新版本（每天最多一次）",
        "每次打开应用时静默向 GitHub Releases API 查询最新版本"),
 "de": ("beim Öffnen einmal pro Tag still die GitHub-Releases-API",
        "bei jedem Öffnen still die GitHub-Releases-API"),
 "es": ("una vez al día al abrir la app", "cada vez que se abre la app"),
 "fr": ("une fois par jour à l\\'ouverture de l\\'app", "à chaque ouverture de l\\'app"),
 "hi": ("दिन में एक बार चुपचाप", "हर बार खुलने पर चुपचाप"),
 "ja": ("アプリ起動時に1日1回、静かに", "アプリを開くたびに静かに"),
 "ko": ("앱을 열 때 하루 한 번 조용히", "앱을 열 때마다 조용히"),
 "ru": ("при открытии один раз в день молча", "при каждом открытии молча"),
},
"manual_security_body": {
 "en": ("and the GitHub releases API once per day for update checks (when auto-check is enabled)",
        "and the GitHub releases API for update checks (when auto-check is enabled)"),
 "zh": ("以及用于更新检查的 GitHub Releases API（启用自动检查时每天最多一次）",
        "以及用于更新检查的 GitHub Releases API（启用自动检查时）"),
 "de": ("und — einmal pro Tag zur Update-Prüfung, sofern aktiviert — mit der GitHub-Releases-API",
        "und — zur Update-Prüfung, sofern aktiviert — mit der GitHub-Releases-API"),
 "es": ("y, una vez al día, con la API de versiones de GitHub",
        "y con la API de versiones de GitHub"),
 "fr": ("et l\\'API des versions GitHub une fois par jour pour la vérification",
        "et l\\'API des versions GitHub pour la vérification"),
 "hi": ("और अपडेट जाँच के लिए GitHub Releases API (स्वतः जाँच चालू होने पर, दिन में एक बार) से जुड़ता है",
        "और अपडेट जाँच के लिए GitHub Releases API (स्वतः जाँच चालू होने पर) से जुड़ता है"),
 "ja": ("更新確認のためのGitHub Releases API（自動確認が有効な場合、1日1回）のみです",
        "更新確認のためのGitHub Releases API（自動確認が有効な場合）のみです"),
 "ko": ("업데이트 확인을 위한 GitHub Releases API(자동 확인이 켜져 있을 때 하루 1회)에만",
        "업데이트 확인을 위한 GitHub Releases API(자동 확인이 켜져 있을 때)에만"),
 "ru": ("и — раз в день для проверки обновлений, если включено — к API релизов GitHub",
        "и — для проверки обновлений, если включено — к API релизов GitHub"),
},
"terms_body": {
 "en": ("and, once per day, to the GitHub releases API for update checks when auto-check is enabled",
        "and to the GitHub releases API for update checks when auto-check is enabled"),
 "zh": ("并在自动检查更新开启时每天最多连接一次 GitHub Releases API 检查更新",
        "并在自动检查更新开启时连接 GitHub Releases API 检查更新"),
 "de": ("einmal pro Tag mit der GitHub-Releases-API", "mit der GitHub-Releases-API"),
 "es": ("y, una vez al día, a la API de versiones de GitHub",
        "y a la API de versiones de GitHub"),
 "fr": ("et, une fois par jour, à l\\'API des versions GitHub",
        "et à l\\'API des versions GitHub"),
 "hi": ("और, स्वतः जाँच चालू होने पर, दिन में एक बार GitHub Releases API से जुड़ता है",
        "और, स्वतः जाँच चालू होने पर, GitHub Releases API से जुड़ता है"),
 "ja": ("自動更新確認が有効な場合に1日1回接続するGitHub Releases API",
        "自動更新確認が有効な場合に接続するGitHub Releases API"),
 "ko": ("자동 업데이트 확인이 켜져 있을 때 하루 1회 GitHub Releases API에만",
        "자동 업데이트 확인이 켜져 있을 때 GitHub Releases API에만"),
 "ru": ("один раз в день к API релизов GitHub", "к API релизов GitHub"),
},
"privacy_body": {
 "en": ("for the latest public version once per day; no account or device data is sent",
        "for the latest public version every time the app opens; no account or device data is sent"),
 "zh": ("应用每天最多一次向 GitHub Releases API 查询最新公开版本，不发送任何账户或设备数据",
        "应用每次打开时向 GitHub Releases API 查询最新公开版本，不发送任何账户或设备数据"),
 "de": ("fragt die App einmal pro Tag die GitHub-Releases-API nach der neuesten öffentlichen Version",
        "fragt die App bei jedem Öffnen die GitHub-Releases-API nach der neuesten öffentlichen Version"),
 "es": ("pregunta a la API de versiones de GitHub por la última versión pública una vez al día",
        "pregunta a la API de versiones de GitHub por la última versión pública cada vez que se abre"),
 "fr": ("l\\'app demande à l\\'API des versions GitHub la dernière version publique une fois par jour",
        "l\\'app demande à l\\'API des versions GitHub la dernière version publique à chaque ouverture"),
 "hi": ("ऐप दिन में एक बार GitHub Releases API से नवीनतम सार्वजनिक संस्करण पूछता है",
        "ऐप हर बार खुलने पर GitHub Releases API से नवीनतम सार्वजनिक संस्करण पूछता है"),
 "ja": ("アプリは1日1回、GitHub Releases APIに最新の公開バージョンを問い合わせます",
        "アプリは開くたびにGitHub Releases APIに最新の公開バージョンを問い合わせます"),
 "ko": ("앱은 하루 1회 GitHub Releases API에 최신 공개 버전을 묻습니다",
        "앱은 열 때마다 GitHub Releases API에 최신 공개 버전을 묻습니다"),
 "ru": ("приложение раз в день запрашивает у API релизов GitHub последнюю публичную версию",
        "приложение при каждом открытии запрашивает у API релизов GitHub последнюю публичную версию"),
},
}


def replace_in_key(content, key, old, new):
    pattern = re.compile(
        r'(<string name="' + re.escape(key) + r'">)(.*?)(</string>)',
        re.DOTALL,
    )
    def repl(m):
        value = m.group(2)
        assert old in value, f"{key}: NOT FOUND: {old[:50]!r}"
        return m.group(1) + value.replace(old, new, 1) + m.group(3)
    assert pattern.search(content), f"key not found: {key}"
    return pattern.sub(repl, content, count=1)


errors = 0
for lang, folder in FOLDERS.items():
    path = f"{BASE}/{folder}/strings.xml"
    content = open(path, encoding="utf-8").read()
    for key, pairs in REPL.items():
        old, new = pairs[lang]
        if not old:
            continue  # nothing to strip in this locale
        try:
            content = replace_in_key(content, key, old, new)
        except AssertionError as e:
            print(f"FAIL {lang} {key}: {e}")
            errors += 1
    open(path, "w", encoding="utf-8").write(content)
    print(f"OK {lang}")

print("ERRORS:", errors)
raise SystemExit(1 if errors else 0)
