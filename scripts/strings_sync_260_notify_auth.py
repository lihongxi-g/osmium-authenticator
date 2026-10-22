#!/usr/bin/env python3
"""Insert the two notification-entry auth strings after auto_backup_notify_desc."""
import io
import re
import sys

T = {
    "values": (
        "Verify before opening",
        "Ask for the fingerprint, the screen lock or the app PIN when this page is opened from a backup notification: it can change the backup password. Not asked twice while \u201cverify on open\u201d is on.",
    ),
    "values-zh": (
        "点通知进入前验证",
        "从备份通知点进来时要求指纹、锁屏密码或应用 PIN——这个页面可以改备份密码。开屏验证开着时不会重复要求。",
    ),
    "values-b+zh+Hant": (
        "點通知進入前驗證",
        "從備份通知點進來時要求指紋、螢幕鎖定密碼或應用程式 PIN——這個頁面可以改備份密碼。開屏驗證開著時不會重複要求。",
    ),
    "values-zh-rTW": (
        "點通知進入前驗證",
        "從備份通知點進來時要求指紋、螢幕鎖定密碼或應用程式 PIN——這個頁面可以改備份密碼。開屏驗證開著時不會重複要求。",
    ),
    "values-ja": (
        "通知から開く前に確認",
        "バックアップ通知からこの画面を開くとき、指紋・画面ロック・アプリPINを要求します。この画面ではバックアップのパスワードを変更できます。「起動時に確認」が有効なときは重ねて要求しません。",
    ),
    "values-ko": (
        "알림에서 열기 전 확인",
        "백업 알림에서 이 화면을 열 때 지문, 화면 잠금 또는 앱 PIN을 요구합니다. 이 화면에서 백업 비밀번호를 바꿀 수 있습니다. '앱 열 때 확인'이 켜져 있으면 다시 묻지 않습니다.",
    ),
    "values-fr": (
        "V\u00e9rifier avant d\u2019ouvrir",
        "Demander l\u2019empreinte, le verrouillage de l\u2019\u00e9cran ou le code PIN de l\u2019app quand cette page est ouverte depuis une notification de sauvegarde : le mot de passe de sauvegarde peut y \u00eatre modifi\u00e9. Pas de seconde demande si \u00ab v\u00e9rifier \u00e0 l\u2019ouverture \u00bb est actif.",
    ),
    "values-de": (
        "Vor dem \u00d6ffnen best\u00e4tigen",
        "Beim \u00d6ffnen dieser Seite aus einer Backup-Benachrichtigung nach Fingerabdruck, Displaysperre oder App-PIN fragen \u2013 hier l\u00e4sst sich das Backup-Passwort \u00e4ndern. Keine zweite Abfrage, wenn \u201eBeim \u00d6ffnen best\u00e4tigen\u201c aktiv ist.",
    ),
    "values-es": (
        "Verificar antes de abrir",
        "Pedir huella, bloqueo de pantalla o PIN de la app al abrir esta p\u00e1gina desde una notificaci\u00f3n de copia de seguridad: aqu\u00ed se puede cambiar la contrase\u00f1a. No se pide otra vez si \u00abverificar al abrir\u00bb est\u00e1 activo.",
    ),
    "values-ru": (
        "\u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u043f\u0435\u0440\u0435\u0434 \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u0435\u043c",
        "\u0417\u0430\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0442\u044c \u043e\u0442\u043f\u0435\u0447\u0430\u0442\u043e\u043a, \u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u043a\u0443 \u044d\u043a\u0440\u0430\u043d\u0430 \u0438\u043b\u0438 PIN \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f \u043f\u0440\u0438 \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u0438 \u044d\u0442\u043e\u0439 \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u044b \u0438\u0437 \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f \u043e \u0440\u0435\u0437\u0435\u0440\u0432\u043d\u043e\u0439 \u043a\u043e\u043f\u0438\u0438: \u0437\u0434\u0435\u0441\u044c \u043c\u043e\u0436\u043d\u043e \u0438\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u043f\u0430\u0440\u043e\u043b\u044c. \u041d\u0435 \u0441\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u043f\u043e\u0432\u0442\u043e\u0440\u043d\u043e, \u0435\u0441\u043b\u0438 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u043f\u0440\u0438 \u0437\u0430\u043f\u0443\u0441\u043a\u0435.",
    ),
    "values-hi": (
        "\u0916\u094b\u0932\u0928\u0947 \u0938\u0947 \u092a\u0939\u0932\u0947 \u0938\u0924\u094d\u092f\u093e\u092a\u0928",
        "\u092c\u0948\u0915\u0905\u092a \u0938\u0942\u091a\u0928\u093e \u0938\u0947 \u092f\u0939 \u092a\u0947\u091c \u0916\u094b\u0932\u0924\u0947 \u0938\u092e\u092f \u092b\u093c\u093f\u0902\u0917\u0930\u092a\u094d\u0930\u093f\u0902\u091f, \u0938\u094d\u0915\u094d\u0930\u0940\u0928 \u0932\u0949\u0915 \u092f\u093e \u0910\u092a PIN \u092e\u093e\u0901\u0917\u0947\u0902 \u2014 \u092f\u0939\u093e\u0901 \u092c\u0948\u0915\u0905\u092a \u092a\u093e\u0938\u0935\u0930\u094d\u0921 \u092c\u0926\u0932\u093e \u091c\u093e \u0938\u0915\u0924\u093e \u0939\u0948\u0964 \"\u0916\u094b\u0932\u0928\u0947 \u092a\u0930 \u0938\u0924\u094d\u092f\u093e\u092a\u093f\u0924 \u0915\u0930\u0947\u0902\" \u091a\u093e\u0932\u0942 \u0939\u094b\u0928\u0947 \u092a\u0930 \u0926\u094b\u092c\u093e\u0930\u093e \u0928\u0939\u0940\u0902 \u092a\u0942\u091b\u093e \u091c\u093e\u090f\u0917\u093e\u0964",
    ),
}

ANCHOR = "auto_backup_notify_desc"


def esc(text: str) -> str:
    # Android needs a literal apostrophe escaped inside <string>.
    return text.replace("'", "\\'")


def main() -> int:
    base = "app/src/main/res"
    for lang, (title, desc) in T.items():
        path = f"{base}/{lang}/strings.xml"
        with io.open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
        if any('name="auto_backup_notify_auth_title"' in ln for ln in lines):
            print(f"{lang}: already present, skipped")
            continue
        out = []
        done = False
        for ln in lines:
            out.append(ln)
            if not done and ANCHOR in ln and ln.strip().startswith("<string"):
                out.append(
                    '    <string name="auto_backup_notify_auth_title">%s</string>\n'
                    % esc(title)
                )
                out.append(
                    '    <string name="auto_backup_notify_auth_desc">%s</string>\n'
                    % esc(desc)
                )
                done = True
        if not done:
            print(f"{lang}: ANCHOR NOT FOUND", file=sys.stderr)
            return 1
        with io.open(path, "w", encoding="utf-8") as fh:
            fh.writelines(out)
        print(f"{lang}: inserted")
    return 0


if __name__ == "__main__":
    sys.exit(main())
