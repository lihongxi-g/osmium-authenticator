#!/usr/bin/env python3
"""Insert auto_backup_webdav_not_configured + auto_backup_fallback_local
after auto_backup_webdav_missing in all 9 string files. Idempotent."""
import re
import sys

# lang dir -> (not_configured, fallback_local)
TEXTS = {
    "values": (
        "No WebDAV server configured — unavailable. Set one up first.",
        "WebDAV not configured — using local backup instead.",
    ),
    "values-zh": (
        "没有配置 WebDAV 服务，无法使用，请先配置",
        "未配置 WebDAV，已改用本地备份",
    ),
    "values-es": (
        "No hay servidor WebDAV configurado — no disponible. Configúralo primero.",
        "WebDAV no configurado — se usará una copia local.",
    ),
    "values-ja": (
        "WebDAVサーバー未設定のため使用できません。先に設定してください。",
        "WebDAV未設定のため、ローカルバックアップを使用します。",
    ),
    "values-ko": (
        "WebDAV 서버가 설정되지 않아 사용할 수 없습니다. 먼저 설정하세요.",
        "WebDAV 미설정 — 로컬 백업으로 대체합니다.",
    ),
    "values-de": (
        "Kein WebDAV-Server konfiguriert — nicht verfügbar. Bitte zuerst einrichten.",
        "WebDAV nicht konfiguriert — lokale Sicherung wird verwendet.",
    ),
    "values-ru": (
        "Сервер WebDAV не настроен — недоступно. Сначала настройте его.",
        "WebDAV не настроен — используется локальная копия.",
    ),
    "values-fr": (
        "Aucun serveur WebDAV configuré — indisponible. Configurez-en un d'abord.",
        "WebDAV non configuré — sauvegarde locale utilisée.",
    ),
    "values-hi": (
        "कोई WebDAV सर्वर सेट नहीं है — उपलब्ध नहीं। पहले इसे सेट करें।",
        "WebDAV सेट नहीं है — लोकल बैकअप उपयोग किया जाएगा।",
    ),
}

ANCHOR = 'name="auto_backup_webdav_missing">'
NEW_NOT = '    <string name="auto_backup_webdav_not_configured">%s</string>'
NEW_FALLBACK = '    <string name="auto_backup_fallback_local">%s</string>'

base = "app/src/main/res"
for lang, (not_cfg, fallback) in TEXTS.items():
    path = f"{base}/{lang}/strings.xml"
    with open(path, encoding="utf-8") as f:
        content = f.read()
    if 'name="auto_backup_webdav_not_configured"' in content:
        print(f"skip {lang} (already present)")
        continue
    idx = content.index(ANCHOR)
    line_end = content.index("\n", idx) + 1
    insert = NEW_NOT % not_cfg + "\n" + NEW_FALLBACK % fallback + "\n"
    content = content[:line_end] + insert + content[line_end:]
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"OK   {lang}")
print("done")
