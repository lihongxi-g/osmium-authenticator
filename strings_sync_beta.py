#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.3.4: Beta badge + mandatory background-permission warning, 9 locales."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"
FOLDERS = {"en": "values", "zh": "values-zh", "de": "values-de",
           "es": "values-es", "fr": "values-fr", "hi": "values-hi",
           "ja": "values-ja", "ko": "values-ko", "ru": "values-ru"}

BETA = {"en": "Beta", "zh": "Beta", "de": "Beta", "es": "Beta",
        "fr": "Beta", "hi": "Beta", "ja": "Beta", "ko": "Beta", "ru": "Beta"}

WARNING = {
 "en": 'Requires \\"Auto-start\\" and \\"Allow full background activity\\" in system battery / auto-start settings — otherwise scheduled backups may not run. Osmium does not drain battery in the background: it wakes only briefly, once per backup.',
 "zh": "需在系统设置中开启「允许自启动」与「完全允许后台行为」，否则定时自动备份可能无法执行。本应用不会在后台持续耗电，仅在备份时短暂启动一次，请放心。",
 "de": "Erfordert „Autostart“ und „Volle Hintergrundaktivität erlauben“ in den Systemeinstellungen (Akku/Autostart) — sonst laufen geplante Backups möglicherweise nicht. Osmium verbraucht im Hintergrund keinen Akku: Es wacht nur einmal pro Backup kurz auf.",
 "es": 'Requiere activar \\"Autoinicio\\" y \\"Permitir actividad en segundo plano completa\\" en los ajustes del sistema (batería/autoinicio); de lo contrario, las copias programadas podrían no ejecutarse. Osmium no consume batería en segundo plano: solo se despierta brevemente, una vez por copia.',
 "fr": "Nécessite d'activer « Démarrage automatique » et « Autoriser l'activité complète en arrière-plan » dans les réglages système (batterie/démarrage automatique) — sinon les sauvegardes planifiées peuvent ne pas s'exécuter. Osmium ne consomme pas de batterie en arrière-plan : il ne se réveille brièvement qu'une fois par sauvegarde.",
 "hi": 'सिस्टम सेटिंग्स (बैटरी/ऑटो-स्टार्ट) में \\"ऑटो-स्टार्ट\\" और \\"पूर्ण बैकग्राउंड गतिविधि की अनुमति\\" चालू करना ज़रूरी है — अन्यथा शेड्यूल किए गए बैकअप नहीं चल सकते। Osmium बैकग्राउंड में बैटरी नहीं खाता: यह केवल एक बार, बैकअप के समय थोड़ी देर के लिए जागता है।',
 "ja": "システム設定（バッテリー/自動起動）で「自動起動」と「完全なバックグラウンド動作を許可」を有効にする必要があります。有効でないと、予定された自動バックアップが実行されない場合があります。Osmiumはバックグラウンドで電池を消費しません。バックアップのたびに一度、短時間起動するだけです。",
 "ko": '시스템 설정(배터리/자동 시작)에서 \\"자동 시작\\"과 \\"완전한 백그라운드 동작 허용\\"을 켜야 합니다. 그렇지 않으면 예약된 백업이 실행되지 않을 수 있습니다. Osmium은 백그라운드에서 배터리를 소모하지 않습니다. 백업 시 한 번만 잠깐 깨어납니다.',
 "ru": "Требуется включить «Автозапуск» и «Разрешить полную фоновую активность» в системных настройках (батарея/автозапуск) — иначе запланированные копии могут не выполняться. Osmium не расходует заряд в фоне: он просыпается лишь ненадолго, один раз на каждую копию.",
}


def esc(s: str) -> str:
    return (s.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace("'", "\\'")
             .replace("\n", "\\n"))


for lang, folder in FOLDERS.items():
    path = f"{BASE}/{folder}/strings.xml"
    content = open(path, encoding="utf-8").read()
    entries = [
        f'<string name="auto_backup_beta_label">{esc(BETA[lang])}</string>',
        f'<string name="auto_backup_permission_warning">{esc(WARNING[lang])}</string>',
    ]
    block = "\n    " + "\n    ".join(entries)
    idx = content.rfind("</resources>")
    assert idx != -1, f"{folder}: no </resources>"
    content = content[:idx] + block + "\n" + content[idx:]
    open(path, "w", encoding="utf-8").write(content)
    print(f"OK {lang}")

print("DONE")
