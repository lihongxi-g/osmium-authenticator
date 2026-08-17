#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.3.4b: emphasize auto-start in warning; add grant-button + status strings (9 locales)."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"
FOLDERS = {"en": "values", "zh": "values-zh", "de": "values-de",
           "es": "values-es", "fr": "values-fr", "hi": "values-hi",
           "ja": "values-ja", "ko": "values-ko", "ru": "values-ru"}

OLD_WARNING = {
 "en": 'Requires \\"Auto-start\\" and \\"Allow full background activity\\" in system battery / auto-start settings — otherwise scheduled backups may not run. Osmium does not drain battery in the background: it wakes only briefly, once per backup.',
 "zh": "需在系统设置中开启「允许自启动」与「完全允许后台行为」，否则定时自动备份可能无法执行。本应用不会在后台持续耗电，仅在备份时短暂启动一次，请放心。",
 "de": "Erfordert „Autostart“ und „Volle Hintergrundaktivität erlauben“ in den Systemeinstellungen (Akku/Autostart) — sonst laufen geplante Backups möglicherweise nicht. Osmium verbraucht im Hintergrund keinen Akku: Es wacht nur einmal pro Backup kurz auf.",
 "es": 'Requiere activar \\"Autoinicio\\" y \\"Permitir actividad en segundo plano completa\\" en los ajustes del sistema (batería/autoinicio); de lo contrario, las copias programadas podrían no ejecutarse. Osmium no consume batería en segundo plano: solo se despierta brevemente, una vez por copia.',
 "fr": "Nécessite d\\'activer « Démarrage automatique » et « Autoriser l\\'activité complète en arrière-plan » dans les réglages système (batterie/démarrage automatique) — sinon les sauvegardes planifiées peuvent ne pas s\\'exécuter. Osmium ne consomme pas de batterie en arrière-plan : il ne se réveille brièvement qu\\'une fois par sauvegarde.",
 "hi": 'सिस्टम सेटिंग्स (बैटरी/ऑटो-स्टार्ट) में \\"ऑटो-स्टार्ट\\" और \\"पूर्ण बैकग्राउंड गतिविधि की अनुमति\\" चालू करना ज़रूरी है — अन्यथा शेड्यूल किए गए बैकअप नहीं चल सकते। Osmium बैकग्राउंड में बैटरी नहीं खाता: यह केवल एक बार, बैकअप के समय थोड़ी देर के लिए जागता है।',
 "ja": "システム設定（バッテリー/自動起動）で「自動起動」と「完全なバックグラウンド動作を許可」を有効にする必要があります。有効でないと、予定された自動バックアップが実行されない場合があります。Osmiumはバックグラウンドで電池を消費しません。バックアップのたびに一度、短時間起動するだけです。",
 "ko": '시스템 설정(배터리/자동 시작)에서 \\"자동 시작\\"과 \\"완전한 백그라운드 동작 허용\\"을 켜야 합니다. 그렇지 않으면 예약된 백업이 실행되지 않을 수 있습니다. Osmium은 백그라운드에서 배터리를 소모하지 않습니다. 백업 시 한 번만 잠깐 깨어납니다.',
 "ru": "Требуется включить «Автозапуск» и «Разрешить полную фоновую активность» в системных настройках (батарея/автозапуск) — иначе запланированные копии могут не выполняться. Osmium не расходует заряд в фоне: он просыпается лишь ненадолго, один раз на каждую копию.",
}

NEW_WARNING = {
 "en": 'Requires \\"Auto-start\\" and \\"Allow full background activity\\" in system settings. \\"Auto-start\\" is CRITICAL — without it, scheduled night backups will not run. Osmium does not drain battery in the background: it wakes only briefly, once per backup.',
 "zh": "需在系统设置中开启「允许自启动」与「完全允许后台行为」。其中「允许自启动」至关重要——未开启则夜间定时备份不会执行。本应用不会在后台持续耗电，仅在备份时短暂启动一次，请放心。",
 "de": "Erfordert „Autostart“ und „Volle Hintergrundaktivität erlauben“ in den Systemeinstellungen. „Autostart“ ist ENTSCHEIDEND — ohne ihn laufen geplante Nacht-Backups nicht. Osmium verbraucht im Hintergrund keinen Akku: Es wacht nur einmal pro Backup kurz auf.",
 "es": 'Requiere activar \\"Autoinicio\\" y \\"Permitir actividad en segundo plano completa\\" en los ajustes del sistema. \\"Autoinicio\\" es CRÍTICO: sin él, las copias nocturnas programadas no se ejecutarán. Osmium no consume batería en segundo plano: solo se despierta brevemente, una vez por copia.',
 "fr": "Nécessite d'activer « Démarrage automatique » et « Autoriser l'activité complète en arrière-plan » dans les réglages système. « Démarrage automatique » est CRITIQUE — sans lui, les sauvegardes nocturnes planifiées ne s'exécuteront pas. Osmium ne consomme pas de batterie en arrière-plan : il ne se réveille brièvement qu'une fois par sauvegarde.",
 "hi": 'सिस्टम सेटिंग्स (बैटरी/ऑटो-स्टार्ट) में \\"ऑटो-स्टार्ट\\" और \\"पूर्ण बैकग्राउंड गतिविधि की अनुमति\\" चालू करना ज़रूरी है। \\"ऑटो-स्टार्ट\\" अत्यंत महत्वपूर्ण है — इसके बिना रात के शेड्यूल बैकअप नहीं चलेंगे। Osmium बैकग्राउंड में बैटरी नहीं खाता: यह केवल एक बार, बैकअप के समय थोड़ी देर के लिए जागता है।',
 "ja": "システム設定（バッテリー/自動起動）で「自動起動」と「完全なバックグラウンド動作を許可」を有効にする必要があります。「自動起動」は非常に重要です。有効でないと、夜間の予定バックアップは実行されません。Osmiumはバックグラウンドで電池を消費しません。バックアップのたびに一度、短時間起動するだけです。",
 "ko": '시스템 설정(배터리/자동 시작)에서 \\"자동 시작\\"과 \\"완전한 백그라운드 동작 허용\\"을 켜야 합니다. \\"자동 시작\\"이 매우 중요합니다. 없으면 야간 예약 백업이 실행되지 않습니다. Osmium은 백그라운드에서 배터리를 소모하지 않습니다. 백업 시 한 번만 잠깐 깨어납니다.',
 "ru": "Требуется включить «Автозапуск» и «Разрешить полную фоновую активность» в системных настройках (батарея/автозапуск). «Автозапуск» КРИТИЧЕСКИ важен — без него ночные запланированные копии не выполняются. Osmium не расходует заряд в фоне: он просыпается лишь ненадолго, один раз на каждую копию.",
}

GRANT_BUTTON = {
 "en": "Allow background activity", "zh": "允许后台运行",
 "de": "Hintergrundaktivität erlauben",
 "es": "Permitir actividad en segundo plano",
 "fr": "Autoriser l'activité en arrière-plan",
 "hi": "बैकग्राउंड गतिविधि की अनुमति दें",
 "ja": "バックグラウンド動作を許可", "ko": "백그라운드 동작 허용",
 "ru": "Разрешить фоновую активность",
}

BG_GRANTED = {
 "en": "Background activity: allowed", "zh": "后台运行：已允许",
 "de": "Hintergrundaktivität: erlaubt",
 "es": "Actividad en segundo plano: permitida",
 "fr": "Activité en arrière-plan : autorisée",
 "hi": "बैकग्राउंड गतिविधि: अनुमत है",
 "ja": "バックグラウンド動作：許可済み", "ko": "백그라운드 동작: 허용됨",
 "ru": "Фоновая активность: разрешена",
}

BG_DENIED = {
 "en": "Background activity: not allowed — tap to request",
 "zh": "后台运行：未允许（点按申请）",
 "de": "Hintergrundaktivität: nicht erlaubt — zum Anfordern tippen",
 "es": "Actividad en segundo plano: no permitida (pulsa para solicitar)",
 "fr": "Activité en arrière-plan : non autorisée (appuyez pour demander)",
 "hi": "बैकग्राउंड गतिविधि: अनुमति नहीं — माँगने के लिए टैप करें",
 "ja": "バックグラウンド動作：未許可（タップして申請）",
 "ko": "백그라운드 동작: 허용 안 됨 — 눌러서 요청",
 "ru": "Фоновая активность: не разрешена — нажмите, чтобы запросить",
}


def esc(s: str) -> str:
    return (s.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace("'", "\\'")
             .replace("\n", "\\n"))


for lang, folder in FOLDERS.items():
    path = f"{BASE}/{folder}/strings.xml"
    content = open(path, encoding="utf-8").read()

    # 1) replace warning text inside the key (idempotent)
    if NEW_WARNING[lang] not in content:
        pattern = re.compile(
            r'(<string name="auto_backup_permission_warning">)(.*?)(</string>)',
            re.DOTALL,
        )
        def repl_warn(m):
            value = m.group(2)
            assert OLD_WARNING[lang] in value, f"{lang}: warning text not found"
            return m.group(1) + value.replace(OLD_WARNING[lang], esc(NEW_WARNING[lang]), 1) + m.group(3)
        assert pattern.search(content), f"{lang}: warning key not found"
        content = pattern.sub(repl_warn, content, count=1)

    # 2) add the three new keys (idempotent)
    if 'auto_backup_grant_button' not in content:
        entries = [
            f'<string name="auto_backup_grant_button">{esc(GRANT_BUTTON[lang])}</string>',
            f'<string name="auto_backup_bg_granted">{esc(BG_GRANTED[lang])}</string>',
            f'<string name="auto_backup_bg_denied">{esc(BG_DENIED[lang])}</string>',
        ]
        block = "\n    " + "\n    ".join(entries)
        idx = content.rfind("</resources>")
        assert idx != -1, f"{lang}: no </resources>"
        content = content[:idx] + block + "\n" + content[idx:]

    open(path, "w", encoding="utf-8").write(content)
    print(f"OK {lang}")

print("DONE")
