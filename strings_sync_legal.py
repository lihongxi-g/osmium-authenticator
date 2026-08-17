#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sync in-app privacy_body/terms_body with the rewritten docs."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"


def esc(s: str) -> str:
    return (s.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace("'", "\\'")
             .replace("\n", "\\n"))


PRIVACY_EN = """Osmium does not collect any data. There is no account system, no registration, no analytics, no advertising SDK, and no crash reporter.

Storage: your secrets, account names and issuers are stored only on this device, encrypted with AES-256-GCM using a non-exportable key in the Android Keystore. Backup files are encrypted with your password (PBKDF2 + AES-256-GCM). The WebDAV server address and login are stored on your device, with the password encrypted by the Android Keystore.

Network: the INTERNET permission is used for two optional features only. (1) WebDAV backup: the app connects only to the server address you configure — typically a NAS or PC on your local network — and only when you run a backup or restore. Plaintext http:// addresses require an explicit on-screen confirmation. (2) Update checks: when auto-check is enabled, the app asks the GitHub releases API for the latest public version once per day; no account or device data is sent.

Automatic backup: scheduled backups run unattended via the Android system scheduler. The export password you set is stored encrypted with the Android Keystore key and is used only to encrypt backup files. Local backups are written to the public Download/Osmium folder; a configurable number are kept (default 5, max 10) and older ones are pruned. The app keeps no persistent background service and does not drain the battery beyond the short backup job.

Camera: used only to scan QR codes, processed on-device; frames are never stored or transmitted.

Biometrics: handled exclusively by the Android system; the app never sees or stores fingerprint or face data.

Storage permission (Android 8/9 only): WRITE_EXTERNAL_STORAGE is requested only on Android 8 and 9, solely to write automatic backups to the public Download/Osmium folder.

We share nothing, because we hold nothing. Uninstalling the app deletes all locally stored data.

Any future change affecting these guarantees will be stated in the release notes.

Contact: zhif0776@hotmail.com · https://t.me/osmium2fa"""

PRIVACY_ZH = """Osmium 不收集任何数据。没有账号体系、注册、统计分析、广告 SDK 或崩溃上报。

存储：你的密钥、账户名与服务商仅存于本机，使用 Android Keystore 中不可导出的密钥进行 AES-256-GCM 加密。备份文件使用你的密码加密（PBKDF2 + AES-256-GCM）。WebDAV 服务器地址与登录信息存储在本机，密码经 Android Keystore 加密。

网络：INTERNET 权限仅用于两个可选功能。(1) WebDAV 备份：应用只连接你自行配置的服务器地址——通常是局域网内的 NAS 或电脑——且仅在你执行备份或恢复时连接；以 http:// 开头的明文地址需在屏幕上明确确认后才会保存。(2) 更新检查：开启自动检查后，应用每天最多一次向 GitHub Releases API 查询最新公开版本，不发送任何账户或设备数据。

自动备份：定时备份通过 Android 系统调度器无人值守运行。你设置的备份密码用 Android Keystore 密钥加密存储，仅用于加密备份文件。本地备份写入公共的 下载/Osmium 目录，按设置保留最近若干份（默认 5 份，最多 10 份）并清理更旧的备份。应用不驻留任何常驻后台服务，除短暂的备份任务外不会额外耗电。

相机：仅用于扫描二维码，全部在本机处理；画面不会被存储或传输。

生物识别：完全由 Android 系统处理；应用不会看到或存储指纹、人脸数据。

存储权限（仅 Android 8/9）：WRITE_EXTERNAL_STORAGE 仅在 Android 8 和 9 上申请，仅用于将自动备份写入公共的 下载/Osmium 目录。

我们不共享任何数据，因为我们不持有任何数据。卸载应用会删除全部本地数据。

任何影响上述保证的变更都会在发布说明中声明。

联系方式：zhif0776@hotmail.com · https://t.me/osmium2fa"""

TERMS_EN = """By installing or using Osmium, you agree to the following terms.

Osmium is free and open source software released under the MIT License (LICENSE in the repository).

The software is provided \\"as is\\", without warranty of any kind. To the maximum extent permitted by law, the developer is not liable for any damages arising from the use of the software.

Your responsibilities:
• Safeguard your secrets and backup passwords. Encryption keys cannot be recovered by the developer or anyone else — a forgotten PIN or lost backup password cannot be reset. The automatic-backup password is stored on your device (encrypted with the Android Keystore) and is unrecoverable if the app's data is erased.
• Keep backups. Uninstalling the app or clearing its data permanently deletes all accounts. Automatic backups run unattended via the Android system scheduler and depend on the device's power state and background-activity settings (see the README); the app keeps no persistent background service and does not drain the battery beyond the short backup job.
• The self-destruct PIN is irreversible, by design.
• Keep your device clock accurate — time-based codes depend on it.

You may use Osmium for your own two-factor authentication. You may not use it to access systems or data you are not authorized to access.

Osmium runs no account system and connects to no third-party services except the WebDAV server you configure yourself (backups are encrypted with your password before upload) and the GitHub releases API once per day for the optional update check, which sends no account or device data. The Google Authenticator migration feature parses export codes locally on the device.

Contact: zhif0776@hotmail.com · https://t.me/osmium2fa"""

TERMS_ZH = """安装或使用 Osmium 即表示你同意以下条款。

Osmium 是基于 MIT 许可证发布的开源软件（许可证见仓库 LICENSE 文件）。

本软件按\"现状\"提供，不附带任何明示或默示的担保。在法律允许的最大范围内，开发者不对因使用本软件而产生的任何损失承担责任。

你的责任：
• 保管好密钥和备份密码。加密密钥无法被开发者或任何人恢复——遗忘的 PIN 或丢失的备份密码无法重置。自动备份密码存储在本机（经 Android Keystore 加密），应用数据被清除后同样无法恢复。
• 自行备份。卸载应用或清除应用数据将永久删除全部账户。自动备份通过 Android 系统调度器无人值守运行，受设备电源状态与后台运行设置影响（建议做法见 README）；应用不驻留任何常驻后台服务，除短暂的备份任务外不会额外耗电。
• 自毁 PIN 不可逆，属设计行为。
• 保持设备时间准确——时间型验证码依赖设备时钟。

你可将 Osmium 用于自身账户的双因素认证。不得利用本软件访问未经授权的系统或数据。

除你自行配置的 WebDAV 服务器（备份在上传前已用密码加密）与用于可选更新检查的 GitHub Releases API（每天最多一次，不发送任何账户或设备数据）外，Osmium 不运行任何账号体系、不连接任何第三方服务。Google 验证器迁移功能仅在设备本地解析导出码。

联系方式：zhif0776@hotmail.com · https://t.me/osmium2fa"""


def replace_value(path, key, new_value):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    pattern = re.compile(
        r'(<string name="' + re.escape(key) + r'">)(.*?)(</string>)',
        re.DOTALL,
    )
    assert pattern.search(content), f"{path}: key {key} not found"
    content = pattern.sub(
        lambda m: m.group(1) + esc(new_value) + m.group(3), content, count=1
    )
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


# 1) EN + ZH: full rewrite of privacy_body and terms_body
replace_value(f"{BASE}/values/strings.xml", "privacy_body", PRIVACY_EN)
replace_value(f"{BASE}/values/strings.xml", "terms_body", TERMS_EN)
replace_value(f"{BASE}/values-zh/strings.xml", "privacy_body", PRIVACY_ZH)
replace_value(f"{BASE}/values-zh/strings.xml", "terms_body", TERMS_ZH)
print("OK EN/ZH full rewrite")

# 2) other 7 locales: insert the storage-permission bullet after the
# biometrics sentence of privacy_body
STORAGE_BULLET = {
 "de": "Speicher (nur Android 8/9): WRITE_EXTERNAL_STORAGE wird ausschließlich unter Android 8 und 9 angefragt, um automatische Backups in den öffentlichen Ordner Download/Osmium zu schreiben. Ab Android 10 ist keine solche Berechtigung nötig.",
 "es": "Almacenamiento (solo Android 8/9): WRITE_EXTERNAL_STORAGE se solicita únicamente en Android 8 y 9, solo para escribir las copias automáticas en la carpeta pública Download/Osmium. Android 10+ no necesita este permiso.",
 "fr": "Stockage (Android 8/9 uniquement) : WRITE_EXTERNAL_STORAGE n'est demandée que sous Android 8 et 9, uniquement pour écrire les sauvegardes automatiques dans le dossier public Téléchargement/Osmium. Android 10+ n'en a pas besoin.",
 "hi": "स्टोरेज (केवल Android 8/9): WRITE_EXTERNAL_STORAGE केवल Android 8 और 9 पर माँगी जाती है, केवल स्वचालित बैकअप को सार्वजनिक Download/Osmium फ़ोल्डर में लिखने के लिए। Android 10+ को इसकी ज़रूरत नहीं है।",
 "ja": "ストレージ（Android 8/9のみ）：WRITE_EXTERNAL_STORAGEはAndroid 8および9でのみ、自動バックアップを公開フォルダ「ダウンロード/Osmium」に書き込むために要求されます。Android 10以降では不要です。",
 "ko": "저장소(Android 8/9만 해당): WRITE_EXTERNAL_STORAGE는 Android 8 및 9에서만, 자동 백업을 공용 다운로드/Osmium 폴더에 쓰기 위해 요청됩니다. Android 10 이상에서는 필요하지 않습니다.",
 "ru": "Память (только Android 8/9): WRITE_EXTERNAL_STORAGE запрашивается только на Android 8 и 9, исключительно для записи автоматических копий в общую папку Загрузки/Osmium. На Android 10+ это разрешение не требуется.",
}

BIO_SENTENCE = {
 "de": "Biometrie: Ausschließlich vom Android-System verarbeitet; die App sieht oder speichert niemals Fingerabdruck- oder Gesichtsdaten.",
 "es": "Biometría: gestionada exclusivamente por el sistema Android; la app nunca ve ni guarda datos de huella o rostro.",
 "fr": "Biométrie : gérée exclusivement par le système Android ; l\\'app ne voit ni ne stocke jamais de données d\\'empreinte ou de visage.",
 "hi": "बायोमेट्रिक्स: पूरी तरह Android सिस्टम द्वारा संभाला जाता है; ऐप फ़िंगरप्रिंट या चेहरे का डेटा कभी नहीं देखता या रखता।",
 "ja": "生体認証：完全にAndroidシステムが処理します。アプリが指紋や顔のデータを見たり保存したりすることはありません。",
 "ko": "생체 인식: 전적으로 Android 시스템이 처리합니다. 앱은 지문이나 얼굴 데이터를 보거나 저장하지 않습니다.",
 "ru": "Биометрия: обрабатывается исключительно системой Android; приложение никогда не видит и не хранит данные отпечатков или лица.",
}

FOLDERS = {"de": "values-de", "es": "values-es", "fr": "values-fr",
           "hi": "values-hi", "ja": "values-ja", "ko": "values-ko",
           "ru": "values-ru"}

for lang, folder in FOLDERS.items():
    path = f"{BASE}/{folder}/strings.xml"
    with open(path, encoding="utf-8") as f:
        content = f.read()
    pattern = re.compile(
        r'(<string name="privacy_body">)(.*?)(</string>)', re.DOTALL
    )
    bio = BIO_SENTENCE[lang]
    def repl(m, _lang=lang):
        value = m.group(2)
        if "WRITE_EXTERNAL_STORAGE" in value:
            return m.group(0)  # idempotent: bullet already inserted
        assert bio in value, f"{_lang}: bio sentence not found"
        insert = bio + "\\n\\n- " + esc(STORAGE_BULLET[_lang])
        return m.group(1) + value.replace(bio, insert, 1) + m.group(3)
    assert pattern.search(content), f"{lang}: privacy_body not found"
    content = pattern.sub(repl, content, count=1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"OK {lang} storage bullet")

print("ALL DONE")
