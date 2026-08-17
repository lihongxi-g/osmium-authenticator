#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Osmium v2.3.2 string sync: 40 new keys x 9 locales + doc-string fixes."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"
LOCALES = ["values", "values-zh", "values-de", "values-es", "values-fr",
           "values-hi", "values-ja", "values-ko", "values-ru"]


def esc(s: str) -> str:
    """Android XML string escaping: & < ' and literal \\n sequences."""
    return (s.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace("'", "\\'")
             .replace("\n", "\\n"))


# ---------------------------------------------------------------- new keys
# key -> {locale: text}
NEW = {
"auto_backup_title": {
 "en": "Auto backup", "zh": "自动备份",
 "de": "Automatisches Backup", "es": "Copia de seguridad automática",
 "fr": "Sauvegarde automatique", "hi": "स्वचालित बैकअप",
 "ja": "自動バックアップ", "ko": "자동 백업", "ru": "Автобэкап"},
"auto_backup_desc": {
 "en": "Back up on a schedule to WebDAV or local storage",
 "zh": "按计划自动备份到 WebDAV 服务器或手机本地存储",
 "de": "Automatische Sicherung auf WebDAV oder lokalen Speicher",
 "es": "Copia programada a WebDAV o almacenamiento local",
 "fr": "Sauvegarde planifiée vers WebDAV ou le stockage local",
 "hi": "शेड्यूल अनुसार WebDAV या स्थानीय स्टोरेज पर बैकअप",
 "ja": "スケジュールに従ってWebDAVまたはローカルに自動バックアップ",
 "ko": "일정에 따라 WebDAV 또는 로컬 저장소에 백업",
 "ru": "Резервное копирование по расписанию на WebDAV или локально"},
"auto_backup_target_label": {
 "en": "Backup location", "zh": "备份位置",
 "de": "Backup-Ziel", "es": "Ubicación de la copia",
 "fr": "Emplacement de la sauvegarde", "hi": "बैकअप स्थान",
 "ja": "バックアップ先", "ko": "백업 위치",
 "ru": "Место хранения копии"},
"auto_backup_target_webdav": {
 "en": "WebDAV server", "zh": "WebDAV 服务器",
 "de": "WebDAV-Server", "es": "Servidor WebDAV",
 "fr": "Serveur WebDAV", "hi": "WebDAV सर्वर",
 "ja": "WebDAVサーバー", "ko": "WebDAV 서버",
 "ru": "Сервер WebDAV"},
"auto_backup_target_local": {
 "en": "Phone storage", "zh": "手机本地存储",
 "de": "Telefonspeicher", "es": "Almacenamiento del teléfono",
 "fr": "Stockage du téléphone", "hi": "फ़ोन का स्थानीय स्टोरेज",
 "ja": "端末のローカルストレージ", "ko": "휴대전화 로컬 저장소",
 "ru": "Локальное хранилище телефона"},
"auto_backup_interval_label": {
 "en": "Frequency", "zh": "备份频率",
 "de": "Häufigkeit", "es": "Frecuencia",
 "fr": "Fréquence", "hi": "आवृत्ति",
 "ja": "バックアップ頻度", "ko": "백업 주기",
 "ru": "Периодичность"},
"auto_backup_interval_day": {
 "en": "Every day", "zh": "每天",
 "de": "Jeden Tag", "es": "Cada día",
 "fr": "Chaque jour", "hi": "हर दिन",
 "ja": "毎日", "ko": "매일", "ru": "Каждый день"},
"auto_backup_interval_every_days": {
 "en": "Every %1$d days", "zh": "每 %1$d 天",
 "de": "Alle %1$d Tage", "es": "Cada %1$d días",
 "fr": "Tous les %1$d jours", "hi": "हर %1$d दिन में",
 "ja": "%1$d日ごと", "ko": "%1$d일마다", "ru": "Каждые %1$d дн."},
"auto_backup_time_label": {
 "en": "Backup time", "zh": "备份时间",
 "de": "Backup-Zeit", "es": "Hora de la copia",
 "fr": "Heure de la sauvegarde", "hi": "बैकअप समय",
 "ja": "バックアップ時刻", "ko": "백업 시간",
 "ru": "Время копирования"},
"auto_backup_password_label": {
 "en": "Backup password", "zh": "备份密码",
 "de": "Backup-Passwort", "es": "Contraseña de copia",
 "fr": "Mot de passe de sauvegarde", "hi": "बैकअप पासवर्ड",
 "ja": "バックアップ用パスワード", "ko": "백업 비밀번호",
 "ru": "Пароль копии"},
"auto_backup_password_set": {
 "en": "Set (tap to change)", "zh": "已设置（点按修改）",
 "de": "Gesetzt (zum Ändern tippen)",
 "es": "Configurada (pulsa para cambiar)",
 "fr": "Défini (appuyez pour changer)",
 "hi": "सेट है (बदलने के लिए टैप करें)",
 "ja": "設定済み（タップで変更）", "ko": "설정됨(눌러서 변경)",
 "ru": "Задан (нажмите, чтобы изменить)"},
"auto_backup_password_missing": {
 "en": "Not set", "zh": "未设置",
 "de": "Nicht gesetzt", "es": "No configurada",
 "fr": "Non défini", "hi": "सेट नहीं है",
 "ja": "未設定", "ko": "설정 안 됨", "ru": "Не задан"},
"auto_backup_password_desc": {
 "en": "Unattended backups need a stored password. It is encrypted with the Android Keystore key and never leaves the device.",
 "zh": "自动备份需无人值守加密，此密码用 Android Keystore 密钥加密后仅存于本机。",
 "de": "Automatische Backups brauchen ein gespeichertes Passwort. Es wird mit dem Android-Keystore-Schlüssel verschlüsselt und verlässt das Gerät nie.",
 "es": "Las copias automáticas necesitan una contraseña guardada. Se cifra con la clave de Android Keystore y nunca sale del dispositivo.",
 "fr": "Les sauvegardes automatiques nécessitent un mot de passe enregistré. Il est chiffré avec la clé Android Keystore et ne quitte jamais l'appareil.",
 "hi": "स्वचालित बैकअप के लिए सहेजा गया पासवर्ड ज़रूरी है। यह Android Keystore कुंजी से एन्क्रिप्ट होता है और डिवाइस से बाहर नहीं जाता।",
 "ja": "自動バックアップには保存されたパスワードが必要です。Android Keystoreキーで暗号化され、端末の外に出ることはありません。",
 "ko": "자동 백업에는 저장된 비밀번호가 필요합니다. Android Keystore 키로 암호화되며 기기를 벗어나지 않습니다.",
 "ru": "Для автоматических копий нужен сохранённый пароль. Он шифруется ключом Android Keystore и никогда не покидает устройство."},
"auto_backup_next_run": {
 "en": "Next backup: %1$s", "zh": "下次备份：%1$s",
 "de": "Nächstes Backup: %1$s", "es": "Próxima copia: %1$s",
 "fr": "Prochaine sauvegarde : %1$s", "hi": "अगला बैकअप: %1$s",
 "ja": "次回バックアップ：%1$s", "ko": "다음 백업: %1$s",
 "ru": "Следующая копия: %1$s"},
"auto_backup_last_success": {
 "en": "Last backup: %1$s (success)", "zh": "上次备份：%1$s（成功）",
 "de": "Letztes Backup: %1$s (erfolgreich)",
 "es": "Última copia: %1$s (correcta)",
 "fr": "Dernière sauvegarde : %1$s (réussie)",
 "hi": "पिछला बैकअप: %1$s (सफल)",
 "ja": "前回バックアップ：%1$s（成功）", "ko": "지난 백업: %1$s (성공)",
 "ru": "Последняя копия: %1$s (успешно)"},
"auto_backup_last_failed": {
 "en": "Last backup: %1$s (failed: %2$s)", "zh": "上次备份：%1$s（失败：%2$s）",
 "de": "Letztes Backup: %1$s (fehlgeschlagen: %2$s)",
 "es": "Última copia: %1$s (falló: %2$s)",
 "fr": "Dernière sauvegarde : %1$s (échec : %2$s)",
 "hi": "पिछला बैकअप: %1$s (विफल: %2$s)",
 "ja": "前回バックアップ：%1$s（失敗：%2$s）",
 "ko": "지난 백업: %1$s (실패: %2$s)",
 "ru": "Последняя копия: %1$s (ошибка: %2$s)"},
"auto_backup_never_run": {
 "en": "No backups yet", "zh": "尚未执行过备份",
 "de": "Noch kein Backup", "es": "Aún no hay copias",
 "fr": "Aucune sauvegarde pour l'instant", "hi": "अभी तक कोई बैकअप नहीं",
 "ja": "まだバックアップはありません", "ko": "아직 백업 없음",
 "ru": "Копий ещё не было"},
"auto_backup_tz_label": {
 "en": "(GMT+8)", "zh": "（GMT+8）", "de": "(GMT+8)",
 "es": "(GMT+8)", "fr": "(GMT+8)", "hi": "(GMT+8)",
 "ja": "(GMT+8)", "ko": "(GMT+8)", "ru": "(GMT+8)"},
"auto_backup_run_now": {
 "en": "Back up now", "zh": "立即备份",
 "de": "Jetzt sichern", "es": "Copiar ahora",
 "fr": "Sauvegarder maintenant", "hi": "अभी बैकअप करें",
 "ja": "今すぐバックアップ", "ko": "지금 백업",
 "ru": "Скопировать сейчас"},
"auto_backup_started": {
 "en": "Backup started", "zh": "备份已开始",
 "de": "Backup gestartet", "es": "Copia iniciada",
 "fr": "Sauvegarde lancée", "hi": "बैकअप शुरू हुआ",
 "ja": "バックアップを開始しました", "ko": "백업 시작됨",
 "ru": "Копирование начато"},
"auto_backup_local_dir_desc": {
 "en": "Files are saved to: Download/Osmium", "zh": "备份文件保存在：下载/Osmium 目录",
 "de": "Ablageort: Download/Osmium",
 "es": "Archivos guardados en: Download/Osmium",
 "fr": "Fichiers enregistrés dans : Téléchargement/Osmium",
 "hi": "फ़ाइलें यहाँ सहेजी जाती हैं: Download/Osmium",
 "ja": "保存先：ダウンロード/Osmium", "ko": "저장 위치: 다운로드/Osmium",
 "ru": "Файлы сохраняются в: Загрузки/Osmium"},
"auto_backup_local_permission_desc": {
 "en": "This Android version needs storage permission to write here — tap to grant",
 "zh": "此 Android 版本需存储权限才能写入此目录，点按授权",
 "de": "Diese Android-Version braucht eine Speicherberechtigung — zum Erteilen tippen",
 "es": "Esta versión de Android necesita permiso de almacenamiento — pulsa para conceder",
 "fr": "Cette version d'Android nécessite une autorisation de stockage — appuyez pour l'accorder",
 "hi": "इस Android संस्करण को स्टोरेज अनुमति चाहिए — देने के लिए टैप करें",
 "ja": "このAndroidバージョンでは保存先への書き込みに権限が必要です——タップして許可",
 "ko": "이 Android 버전은 저장 권한이 필요합니다 — 눌러서 허용",
 "ru": "Этой версии Android нужно разрешение на память — нажмите, чтобы выдать"},
"auto_backup_webdav_missing": {
 "en": "No WebDAV server configured — set one up first",
 "zh": "尚未配置 WebDAV 服务器，请先完成配置",
 "de": "Kein WebDAV-Server konfiguriert — bitte zuerst einrichten",
 "es": "No hay servidor WebDAV configurado — configúralo primero",
 "fr": "Aucun serveur WebDAV configuré — configurez-en un d'abord",
 "hi": "कोई WebDAV सर्वर सेट नहीं है — पहले इसे सेट करें",
 "ja": "WebDAVサーバーが未設定です。先に設定してください",
 "ko": "WebDAV 서버가 설정되지 않았습니다. 먼저 설정하세요",
 "ru": "Сервер WebDAV не настроен — сначала настройте его"},
"auto_backup_need_password": {
 "en": "Set a backup password first", "zh": "请先设置备份密码",
 "de": "Zuerst ein Backup-Passwort setzen",
 "es": "Primero configura una contraseña",
 "fr": "Définissez d'abord un mot de passe",
 "hi": "पहले बैकअप पासवर्ड सेट करें",
 "ja": "先にバックアップ用パスワードを設定してください",
 "ko": "먼저 백업 비밀번호를 설정하세요",
 "ru": "Сначала задайте пароль копии"},
"auto_backup_password_saved": {
 "en": "Backup password saved", "zh": "备份密码已保存",
 "de": "Backup-Passwort gespeichert", "es": "Contraseña guardada",
 "fr": "Mot de passe enregistré", "hi": "बैकअप पासवर्ड सहेजा गया",
 "ja": "パスワードを保存しました", "ko": "백업 비밀번호 저장됨",
 "ru": "Пароль сохранён"},
"auto_backup_time_picker_title": {
 "en": "Choose a time", "zh": "选择备份时间",
 "de": "Zeit wählen", "es": "Elige una hora",
 "fr": "Choisir une heure", "hi": "समय चुनें",
 "ja": "時刻を選択", "ko": "시간 선택", "ru": "Выберите время"},
"auto_backup_interval_picker_title": {
 "en": "Choose a frequency", "zh": "选择备份频率",
 "de": "Häufigkeit wählen", "es": "Elige una frecuencia",
 "fr": "Choisir une fréquence", "hi": "आवृत्ति चुनें",
 "ja": "頻度を選択", "ko": "주기 선택", "ru": "Выберите периодичность"},
"update_check_label": {
 "en": "Auto-check for updates", "zh": "自动检查更新",
 "de": "Automatische Update-Prüfung", "es": "Comprobar actualizaciones",
 "fr": "Vérification automatique des mises à jour", "hi": "अपडेट स्वतः जाँचें",
 "ja": "更新を自動確認", "ko": "업데이트 자동 확인",
 "ru": "Автопроверка обновлений"},
"update_check_desc": {
 "en": "Silently checks GitHub for new versions when the app opens (once per day)",
 "zh": "每次打开应用时静默检查 GitHub 上的新版本（每天最多一次）",
 "de": "Prüft beim Öffnen still auf GitHub nach neuen Versionen (einmal pro Tag)",
 "es": "Comprueba GitHub en silencio al abrir la app (una vez al día)",
 "fr": "Vérifie silencieusement GitHub à l'ouverture (une fois par jour)",
 "hi": "ऐप खुलने पर GitHub पर नया संस्करण चुपचाप जाँचता है (दिन में एक बार)",
 "ja": "アプリ起動時にGitHubの新バージョンを静かに確認（1日1回）",
 "ko": "앱을 열 때 GitHub의 새 버전을 조용히 확인(하루 1회)",
 "ru": "Тихо проверяет новые версии на GitHub при открытии (раз в день)"},
"update_available_title": {
 "en": "New version available", "zh": "发现新版本",
 "de": "Neue Version verfügbar", "es": "Nueva versión disponible",
 "fr": "Nouvelle version disponible", "hi": "नया संस्करण उपलब्ध है",
 "ja": "新しいバージョンがあります", "ko": "새 버전 사용 가능",
 "ru": "Доступна новая версия"},
"update_available_body": {
 "en": "Version %1$s is available (installed: %2$s). Open GitHub to see it?",
 "zh": "新版本 %1$s 已发布（当前版本 %2$s）。前往 GitHub 查看？",
 "de": "Version %1$s ist verfügbar (installiert: %2$s). Auf GitHub ansehen?",
 "es": "La versión %1$s está disponible (instalada: %2$s). ¿Abrir GitHub?",
 "fr": "La version %1$s est disponible (installée : %2$s). Ouvrir GitHub ?",
 "hi": "संस्करण %1$s उपलब्ध है (स्थापित: %2$s)। GitHub खोलें?",
 "ja": "バージョン%1$sが公開されています（現在：%2$s）。GitHubで確認しますか？",
 "ko": "버전 %1$s이(가) 출시되었습니다(현재: %2$s). GitHub에서 확인할까요?",
 "ru": "Доступна версия %1$s (установлена: %2$s). Открыть GitHub?"},
"update_go_github": {
 "en": "Open GitHub", "zh": "前往 GitHub",
 "de": "GitHub öffnen", "es": "Abrir GitHub",
 "fr": "Ouvrir GitHub", "hi": "GitHub खोलें",
 "ja": "GitHubを開く", "ko": "GitHub 열기", "ru": "Открыть GitHub"},
"update_later": {
 "en": "Later", "zh": "稍后",
 "de": "Später", "es": "Más tarde",
 "fr": "Plus tard", "hi": "बाद में",
 "ja": "後で", "ko": "나중에", "ru": "Позже"},
"webdav_http_warning_title": {
 "en": "Plaintext connection warning", "zh": "明文连接警告",
 "de": "Warnung: unverschlüsselte Verbindung",
 "es": "Aviso de conexión sin cifrar",
 "fr": "Avertissement de connexion en clair",
 "hi": "प्लेनटेक्स्ट कनेक्शन चेतावनी",
 "ja": "平文接続の警告", "ko": "평문 연결 경고",
 "ru": "Предупреждение о незашифрованном соединении"},
"webdav_http_warning_body": {
 "en": "This http:// address transmits data in plaintext — anyone on the network can read it. Use it only on a trusted local network. Save anyway?",
 "zh": "该地址使用 http:// 明文传输，网络上的其他人可能截获内容。仅在可信局域网内使用。确认保存？",
 "de": "Diese http://-Adresse überträgt Daten unverschlüsselt — jeder im Netzwerk kann sie lesen. Nur in einem vertrauenswürdigen lokalen Netzwerk verwenden. Trotzdem speichern?",
 "es": "Esta dirección http:// transmite datos sin cifrar: cualquiera en la red puede leerlos. Úsala solo en una red local de confianza. ¿Guardar de todos modos?",
 "fr": "Cette adresse http:// transmet les données en clair — n'importe qui sur le réseau peut les lire. Utilisez-la uniquement sur un réseau local de confiance. Enregistrer quand même ?",
 "hi": "यह http:// पता डेटा को प्लेनटेक्स्ट में भेजता है — नेटवर्क पर कोई भी इसे पढ़ सकता है। केवल विश्वसनीय लोकल नेटवर्क पर उपयोग करें। फिर भी सहेजें?",
 "ja": "このhttp://アドレスは平文でデータを送信するため、ネットワーク上の他者に読み取られる恐れがあります。信頼できるローカルネットワークでのみ使用してください。それでも保存しますか？",
 "ko": "이 http:// 주소는 데이터를 평문으로 전송하므로 네트워크의 다른 사용자가 읽을 수 있습니다. 신뢰하는 로컬 네트워크에서만 사용하세요. 그래도 저장할까요?",
 "ru": "Адрес http:// передаёт данные открытым текстом — их может прочитать кто угодно в сети. Используйте только в доверенной локальной сети. Всё равно сохранить?"},
"webdav_http_confirm": {
 "en": "Save anyway", "zh": "仍然保存",
 "de": "Trotzdem speichern", "es": "Guardar de todos modos",
 "fr": "Enregistrer quand même", "hi": "फिर भी सहेजें",
 "ja": "それでも保存", "ko": "그래도 저장",
 "ru": "Всё равно сохранить"},
"manual_auto_backup_title": {
 "en": "Automatic backup", "zh": "自动备份",
 "de": "Automatisches Backup", "es": "Copia automática",
 "fr": "Sauvegarde automatique", "hi": "स्वचालित बैकअप",
 "ja": "自動バックアップ", "ko": "자동 백업", "ru": "Автобэкап"},
"manual_auto_backup_body": {
 "en": "Settings → Data → Auto backup runs an unattended backup on a schedule: pick a target (WebDAV server or phone storage), an interval in days, and a time of day. The next run time is shown with a GMT+8 label.\\n\\nBackups use the same password-encrypted export format as manual backups. The export password is stored encrypted with the Android Keystore key and is used only to encrypt backup files. Local backups are written to the public Download/Osmium folder; the 5 newest auto-backups are kept and older ones are pruned.\\n\\nThe last run result is shown on the auto-backup screen. A backup that fails (e.g. no server reachable) is recorded there and retried once; the next scheduled run tries again.",
 "zh": "设置 → 数据 → 自动备份会按计划无人值守地备份：选择目标（WebDAV 服务器或手机本地存储）、间隔天数与每日时间。下次执行时间以 GMT+8 标注显示。\\n\\n备份文件与手动备份使用同一密码加密导出格式。备份密码用 Android Keystore 密钥加密存储，仅用于加密备份文件。本地备份写入公共的 下载/Osmium 目录；自动保留最新 5 份并清理更旧的备份。\\n\\n自动备份界面会显示上次执行结果。失败（如服务器不可达）会记录并重试一次，下一个计划时间点会再次尝试。",
 "de": "Einstellungen → Daten → Automatisches Backup führt unbeaufsichtigt Backups nach Plan aus: Ziel (WebDAV-Server oder Telefonspeicher), Intervall in Tagen und Uhrzeit wählen. Die nächste Ausführung wird mit GMT+8 angezeigt.\\n\\nDie Backups nutzen dasselbe passwortverschlüsselte Exportformat wie manuelle Backups. Das Export-Passwort wird mit dem Android-Keystore-Schlüssel verschlüsselt gespeichert und dient nur zum Verschlüsseln der Backup-Dateien. Lokale Backups landen im öffentlichen Ordner Download/Osmium; die 5 neuesten bleiben erhalten, ältere werden gelöscht.\\n\\nDas Ergebnis des letzten Laufs wird auf dem Auto-Backup-Bildschirm angezeigt. Ein fehlgeschlagenes Backup (z. B. Server nicht erreichbar) wird dort vermerkt und einmal erneut versucht; der nächste geplante Lauf versucht es wieder.",
 "es": "Ajustes → Datos → Copia automática ejecuta copias sin intervención según un plan: elige destino (servidor WebDAV o almacenamiento del teléfono), intervalo en días y hora. La próxima ejecución se muestra con etiqueta GMT+8.\\n\\nLas copias usan el mismo formato de exportación cifrado con contraseña que las manuales. La contraseña se guarda cifrada con la clave de Android Keystore y solo sirve para cifrar los archivos. Las copias locales van a la carpeta pública Download/Osmium; se conservan las 5 más recientes y se borran las antiguas.\\n\\nEl resultado del último intento se muestra en la pantalla de copia automática. Un fallo (p. ej. servidor inaccesible) se registra y se reintenta una vez; el siguiente intento programado volverá a probar.",
 "fr": "Réglages → Données → Sauvegarde automatique exécute des sauvegardes sans intervention selon un planning : choisissez la cible (serveur WebDAV ou stockage du téléphone), un intervalle en jours et une heure. La prochaine exécution est affichée avec le libellé GMT+8.\\n\\nLes sauvegardes utilisent le même format d'export chiffré par mot de passe que les sauvegardes manuelles. Le mot de passe est stocké chiffré avec la clé Android Keystore et ne sert qu'à chiffrer les fichiers. Les sauvegardes locales vont dans le dossier public Téléchargement/Osmium ; les 5 plus récentes sont conservées et les anciennes supprimées.\\n\\nLe résultat du dernier essai est affiché sur l'écran de sauvegarde automatique. Un échec (serveur injoignable, etc.) y est noté puis réessayé une fois ; le prochain lancement planifié réessaiera.",
 "hi": "सेटिंग्स → डेटा → स्वचालित बैकअप शेड्यूल पर बिना देखरेख बैकअप चलाता है: लक्ष्य (WebDAV सर्वर या फ़ोन स्टोरेज), दिनों में अंतराल और समय चुनें। अगले बैकअप का समय GMT+8 लेबल के साथ दिखता है।\\n\\nबैकअप मैन्युअल बैकअप जैसे ही पासवर्ड-एन्क्रिप्टेड एक्सपोर्ट फ़ॉर्मेट का उपयोग करते हैं। पासवर्ड Android Keystore कुंजी से एन्क्रिप्ट होकर सहेजा जाता है और केवल बैकअप फ़ाइलें एन्क्रिप्ट करने के लिए उपयोग होता है। लोकल बैकअप सार्वजनिक Download/Osmium फ़ोल्डर में लिखे जाते हैं; सबसे नई 5 फ़ाइलें रखी जाती हैं और पुरानी हटा दी जाती हैं।\\n\\nपिछले बैकअप का परिणाम स्वचालित बैकअप स्क्रीन पर दिखता है। विफलता (जैसे सर्वर उपलब्ध न होना) वहाँ दर्ज होती है और एक बार दोबारा कोशिश की जाती है; अगला शेड्यूल रन फिर प्रयास करेगा।",
 "ja": "設定 → データ → 自動バックアップはスケジュールに従って無人でバックアップします。保存先（WebDAVサーバーまたは端末ローカル）、間隔（日数）、時刻を選べます。次回の実行時刻はGMT+8表記で表示されます。\\n\\nバックアップは手動バックアップと同じパスワード暗号化エクスポート形式です。パスワードはAndroid Keystoreキーで暗号化して保存され、バックアップファイルの暗号化にのみ使われます。ローカルバックアップは公開フォルダ「ダウンロード/Osmium」に書き込まれ、最新5件を残して古いものは削除されます。\\n\\n前回の実行結果は自動バックアップ画面に表示されます。失敗（サーバーに接続できない等）は記録され1回再試行され、次の予定時刻に再び試みます。",
 "ko": "설정 → 데이터 → 자동 백업이 일정에 따라 무인 백업을 실행합니다: 대상(WebDAV 서버 또는 휴대전화 저장소), 일 단위 간격, 시간을 선택하세요. 다음 실행 시간은 GMT+8 라벨로 표시됩니다.\\n\\n백업은 수동 백업과 동일한 비밀번호 암호화 내보내기 형식을 사용합니다. 비밀번호는 Android Keystore 키로 암호화되어 저장되며 백업 파일 암호화에만 사용됩니다. 로컬 백업은 공용 다운로드/Osmium 폴더에 저장되고 최신 5개만 남기고 오래된 파일은 삭제됩니다.\\n\\n마지막 실행 결과는 자동 백업 화면에 표시됩니다. 실패(예: 서버에 연결할 수 없음)는 기록되고 한 번 재시도되며, 다음 예약 실행에서 다시 시도합니다.",
 "ru": "Настройки → Данные → Автобэкап выполняет резервное копирование без участия пользователя по расписанию: выберите цель (сервер WebDAV или локальное хранилище), интервал в днях и время. Время следующего запуска отображается с меткой GMT+8.\\n\\nКопии используют тот же шифрованный паролем формат экспорта, что и ручные копии. Пароль хранится зашифрованным ключом Android Keystore и используется только для шифрования файлов копий. Локальные копии записываются в общую папку Загрузки/Osmium; хранятся 5 последних, более старые удаляются.\\n\\nРезультат последнего запуска виден на экране автобэкапа. Неудача (например, сервер недоступен) записывается и повторяется один раз; следующий запуск по расписанию попробует снова."},
"manual_updates_title": {
 "en": "Update checks", "zh": "更新检查",
 "de": "Update-Prüfung", "es": "Comprobación de actualizaciones",
 "fr": "Vérification des mises à jour", "hi": "अपडेट जाँच",
 "ja": "更新確認", "ko": "업데이트 확인", "ru": "Проверка обновлений"},
"manual_updates_body": {
 "en": "When auto-check is enabled (Settings → About → Auto-check for updates), Osmium silently asks the GitHub releases API for the newest version once per day when the app opens. If a newer version exists, a dialog offers to open the GitHub releases page — it never downloads or installs anything by itself.\\n\\nNo account data or device information is sent; the request only asks for the latest public release. The check stays silent on network failures.",
 "zh": "开启自动检查更新后（设置 → 关于 → 自动检查更新），Osmium 会在每次打开应用时静默向 GitHub Releases API 查询最新版本（每天最多一次）。发现新版本时弹窗询问是否前往 GitHub Releases 页面——应用本身不会下载或安装任何内容。\\n\\n查询不会发送任何账户数据或设备信息，仅请求最新的公开版本信息。网络失败时保持静默。",
 "de": "Ist die automatische Prüfung aktiviert (Einstellungen → Über → Automatische Update-Prüfung), fragt Osmium beim Öffnen einmal pro Tag still die GitHub-Releases-API nach der neuesten Version. Gibt es eine neuere Version, bietet ein Dialog an, die GitHub-Releases-Seite zu öffnen — heruntergeladen oder installiert wird nie etwas automatisch.\\n\\nEs werden keine Kontodaten oder Geräteinformationen gesendet; abgefragt wird nur die neueste öffentliche Version. Bei Netzwerkfehlern bleibt die Prüfung still.",
 "es": "Si la comprobación automática está activada (Ajustes → Acerca de → Comprobar actualizaciones), Osmium consulta en silencio la API de versiones de GitHub una vez al día al abrir la app. Si hay una versión nueva, un diálogo ofrece abrir la página de versiones de GitHub — nunca descarga ni instala nada por su cuenta.\\n\\nNo se envían datos de cuentas ni del dispositivo; solo se pide la última versión pública. Si falla la red, la comprobación permanece en silencio.",
 "fr": "Si la vérification automatique est activée (Réglages → À propos → Vérification automatique des mises à jour), Osmium interroge silencieusement l'API des versions GitHub une fois par jour à l'ouverture de l'app. Si une version plus récente existe, une boîte de dialogue propose d'ouvrir la page des versions GitHub — l'app ne télécharge ni n'installe jamais rien elle-même.\\n\\nAucune donnée de compte ni information d'appareil n'est envoyée ; seule la dernière version publique est demandée. En cas de panne réseau, la vérification reste silencieuse.",
 "hi": "जब स्वतः जाँच चालू हो (सेटिंग्स → ऐप के बारे में → अपडेट स्वतः जाँचें), Osmium ऐप खुलने पर दिन में एक बार चुपचाप GitHub Releases API से नवीनतम संस्करण पूछता है। नया संस्करण मिलने पर एक संवाद GitHub Releases पृष्ठ खोलने का विकल्प देता है — ऐप कभी स्वयं कुछ डाउनलोड या इंस्टॉल नहीं करता।\\n\\nकोई खाता डेटा या डिवाइस जानकारी नहीं भेजी जाती; केवल नवीनतम सार्वजनिक संस्करण पूछा जाता है। नेटवर्क विफल होने पर जाँच चुप रहती है।",
 "ja": "自動確認が有効な場合（設定 → このアプリについて → 更新を自動確認）、Osmiumはアプリ起動時に1日1回、静かにGitHub Releases APIへ最新バージョンを問い合わせます。新しいバージョンがあると、GitHub Releasesページを開くかどうかを尋ねるダイアログを表示します——アプリが自動でダウンロードやインストールをすることはありません。\\n\\nアカウントデータや端末情報は送信されず、最新の公開バージョン情報のみを取得します。ネットワークエラー時は何も表示しません。",
 "ko": "자동 확인이 켜져 있으면(설정 → 정보 → 업데이트 자동 확인) Osmium은 앱을 열 때 하루 한 번 조용히 GitHub Releases API에 최신 버전을 묻습니다. 새 버전이 있으면 GitHub Releases 페이지를 열지 묻는 대화상자가 표시되며, 앱이 스스로 다운로드하거나 설치하지는 않습니다.\\n\\n계정 데이터나 기기 정보는 전송되지 않으며 최신 공개 버전 정보만 요청합니다. 네트워크 오류 시 조용히 넘어갑니다.",
 "ru": "Если включена автопроверка (Настройки → О приложении → Автопроверка обновлений), Osmium при открытии один раз в день молча запрашивает у GitHub Releases API последнюю версию. Если есть более новая версия, появляется диалог с предложением открыть страницу релизов GitHub — приложение само ничего не скачивает и не устанавливает.\\n\\nНикакие данные аккаунтов или устройства не отправляются; запрашивается только последний публичный релиз. При сбоях сети проверка проходит молча."},
}

LOCALE_CODE = {
 "values": "en", "values-zh": "zh", "values-de": "de", "values-es": "es",
 "values-fr": "fr", "values-hi": "hi", "values-ja": "ja", "values-ko": "ko",
 "values-ru": "ru",
}

# ------------------------------------------------------- doc-string rewrites
# 1) manual_notes_body: hardware phrase -> non-exportable Keystore wording
NOTES_HARDWARE = {
 "en": ("encrypted with a hardware-backed key",
        "encrypted with a non-exportable Android Keystore key"),
 "zh": ("使用硬件级密钥加密", "使用不可导出的 Android Keystore 密钥加密"),
 "de": ("mit einem hardwaregestützten Schlüssel verschlüsselt",
        "mit einem nicht exportierbaren Android-Keystore-Schlüssel verschlüsselt"),
 "es": ("con una clave respaldada por hardware",
        "con una clave no exportable de Android Keystore"),
 "fr": ("avec une clé adossée au matériel",
        "avec une clé non exportable d'Android Keystore"),
 "hi": ("हार्डवेयर-समर्थित कुंजी से एन्क्रिप्टेड",
        "गैर-निर्यात योग्य Android Keystore कुंजी से एन्क्रिप्टेड"),
 "ja": ("ハードウェア保護の鍵で暗号化され",
        "エクスポート不可のAndroid Keystore鍵で暗号化され"),
 "ko": ("하드웨어 기반 키로 암호화되며",
        "내보낼 수 없는 Android Keystore 키로 암호화되며"),
 "ru": ("зашифрованы аппаратным ключом",
        "зашифрованы неэкспортируемым ключом Android Keystore"),
}

# 2) manual_security_body: replace LAST bullet
SECURITY_LAST = {
 "en": "• Osmium never connects to anything except the WebDAV backup server you configure yourself and the GitHub releases API once per day for update checks (when auto-check is enabled) — no accounts, no telemetry, no cloud.",
 "zh": "• Osmium 只连接你自行配置的 WebDAV 备份服务器，以及用于更新检查的 GitHub Releases API（启用自动检查时每天最多一次）——无账号、无遥测、无云。",
 "de": "• Osmium verbindet sich ausschließlich mit dem von dir konfigurierten WebDAV-Backup-Server und — einmal pro Tag zur Update-Prüfung, sofern aktiviert — mit der GitHub-Releases-API. Keine Konten, keine Telemetrie, keine Cloud.",
 "es": "• Osmium solo se conecta con el servidor WebDAV de copias que configures y, una vez al día, con la API de versiones de GitHub para buscar actualizaciones (si está activada). Sin cuentas, sin telemetría, sin nube.",
 "fr": "• Osmium ne se connecte à rien d'autre que le serveur WebDAV que vous configurez et l'API des versions GitHub une fois par jour pour la vérification des mises à jour (si activée) — pas de compte, pas de télémétrie, pas de cloud.",
 "hi": "• Osmium केवल आपके द्वारा सेट किए गए WebDAV बैकअप सर्वर और अपडेट जाँच के लिए GitHub Releases API (स्वतः जाँच चालू होने पर, दिन में एक बार) से जुड़ता है — कोई खाता नहीं, कोई टेलीमेट्री नहीं, कोई क्लाउड नहीं।",
 "ja": "• Osmiumが接続するのは、あなた自身が設定したWebDAVバックアップサーバーと、更新確認のためのGitHub Releases API（自動確認が有効な場合、1日1回）のみです。アカウントなし、テレメトリなし、クラウドなし。",
 "ko": "• Osmium은 사용자가 직접 설정한 WebDAV 백업 서버와, 업데이트 확인을 위한 GitHub Releases API(자동 확인이 켜져 있을 때 하루 1회)에만 연결합니다. 계정 없음, 원격 측정 없음, 클라우드 없음.",
 "ru": "• Osmium подключается только к настроенному вами серверу WebDAV и — раз в день для проверки обновлений, если включено — к API релизов GitHub. Никаких аккаунтов, телеметрии и облака.",
}

# 3) terms_body: replace LAST paragraph
TERMS_LAST = {
 "en": "Osmium connects only to the WebDAV backup server you configure yourself (typically on your local network) and, once per day, to the GitHub releases API for update checks when auto-check is enabled. It never connects to anything else.",
 "zh": "Osmium 仅连接你自行配置的 WebDAV 备份服务器（通常在局域网内），并在自动检查更新开启时每天最多连接一次 GitHub Releases API 检查更新。除此之外不会连接任何其他地址。",
 "de": "Osmium verbindet sich nur mit dem von dir selbst konfigurierten WebDAV-Backup-Server (normalerweise im lokalen Netzwerk) und — sofern die automatische Update-Prüfung aktiviert ist — einmal pro Tag mit der GitHub-Releases-API. Es verbindet sich mit nichts anderem.",
 "es": "Osmium solo se conecta al servidor WebDAV de copias que configures (normalmente en tu red local) y, una vez al día, a la API de versiones de GitHub para buscar actualizaciones si la comprobación automática está activada. Nunca se conecta a nada más.",
 "fr": "Osmium ne se connecte qu'au serveur WebDAV que vous configurez vous-même (généralement sur votre réseau local) et, une fois par jour, à l'API des versions GitHub pour la vérification des mises à jour si elle est activée. Il ne se connecte jamais à autre chose.",
 "hi": "Osmium केवल आपके द्वारा सेट किए गए WebDAV बैकअप सर्वर (आमतौर पर आपके लोकल नेटवर्क पर) और, स्वतः जाँच चालू होने पर, दिन में एक बार GitHub Releases API से जुड़ता है। यह किसी और चीज़ से कभी नहीं जुड़ता।",
 "ja": "Osmiumが接続するのは、あなた自身が設定したWebDAVバックアップサーバー（通常はローカルネットワーク上）と、自動更新確認が有効な場合に1日1回接続するGitHub Releases APIのみです。それ以外には一切接続しません。",
 "ko": "Osmium은 사용자가 직접 설정한 WebDAV 백업 서버(일반적으로 로컬 네트워크)와, 자동 업데이트 확인이 켜져 있을 때 하루 1회 GitHub Releases API에만 연결합니다. 그 외에는 어떤 곳에도 연결하지 않습니다.",
 "ru": "Osmium подключается только к настроенному вами серверу WebDAV (обычно в локальной сети) и — если включена автопроверка обновлений — один раз в день к API релизов GitHub. Ни к чему другому он не подключается.",
}

# 4) privacy_body: FULL rewrite per locale
PRIVACY_FULL = {
 "en": """Osmium does not collect any data. There is no account system, no registration, no analytics, no advertising SDK, and no crash reporter.

Storage: your secrets, account names and issuers are stored only on this device, encrypted with AES-256-GCM using a non-exportable key in the Android Keystore. Backup files are encrypted with your password (PBKDF2 + AES-256-GCM).

Network: the INTERNET permission is used for two features only. (1) WebDAV backup: the app connects only to the server address you configure — typically a NAS or PC on your local network — and only when you run a backup or restore. (2) Update checks: when auto-check is enabled, the app asks the GitHub releases API for the latest public version once per day. Backups are password-encrypted before they leave the device.

Automatic backup: scheduled backups run unattended. The export password you set is stored encrypted with the Android Keystore key and is used only to encrypt backup files. Local backups are written to the public Download/Osmium folder; the 5 newest are kept and older ones are pruned.

Camera: used only to scan QR codes, processed on-device; frames are never stored or transmitted.

Biometrics: handled exclusively by the Android system; the app never sees or stores fingerprint or face data.

We share nothing, because we hold nothing. Uninstalling the app deletes all locally stored data.

Any future change affecting these guarantees will be stated in the release notes.

Contact: zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "zh": """Osmium 不收集任何数据。没有账号体系、注册、统计分析、广告 SDK 或崩溃上报。

存储：你的密钥、账户名与服务商仅存于本机，使用 Android Keystore 中不可导出的密钥进行 AES-256-GCM 加密。备份文件使用你的密码加密（PBKDF2 + AES-256-GCM）。

网络：INTERNET 权限仅用于两个功能。(1) WebDAV 备份：应用只连接你自行配置的服务器地址——通常是局域网内的 NAS 或电脑——且仅在你执行备份或恢复时连接。(2) 更新检查：开启自动检查后，应用每天最多一次向 GitHub Releases API 查询最新公开版本。备份文件在离开设备前已用密码加密。

自动备份：定时备份无人值守运行。你设置的备份密码用 Android Keystore 密钥加密存储，仅用于加密备份文件。本地备份写入公共的 下载/Osmium 目录，保留最新 5 份并清理更旧的备份。

相机：仅用于扫描二维码，全部在本机处理；画面不会被存储或传输。

生物识别：完全由 Android 系统处理；应用不会看到或存储指纹、人脸数据。

我们不共享任何数据，因为我们不持有任何数据。卸载应用会删除全部本地数据。

任何影响上述保证的变更都会在发布说明中声明。

联系方式：zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "de": """Osmium sammelt keine Daten. Es gibt kein Kontosystem, keine Registrierung, keine Analyse, kein Werbe-SDK und keinen Absturzbericht.

Speicherung: Deine Geheimnisse, Kontonamen und Anbieter werden nur auf diesem Gerät gespeichert, mit AES-256-GCM und einem nicht exportierbaren Schlüssel im Android Keystore verschlüsselt. Backup-Dateien sind mit deinem Passwort verschlüsselt (PBKDF2 + AES-256-GCM).

Netzwerk: Die INTERNET-Berechtigung dient nur zwei Funktionen. (1) WebDAV-Backup: Die App verbindet sich nur mit der von dir konfigurierten Serveradresse — normalerweise ein NAS oder PC im lokalen Netzwerk — und nur bei Backup oder Wiederherstellung. (2) Update-Prüfung: Bei aktivierter Auto-Prüfung fragt die App einmal pro Tag die GitHub-Releases-API nach der neuesten öffentlichen Version. Backups werden vor dem Verlassen des Geräts passwortverschlüsselt.

Automatisches Backup: Geplante Backups laufen unbeaufsichtigt. Das von dir gesetzte Export-Passwort wird mit dem Android-Keystore-Schlüssel verschlüsselt gespeichert und dient nur zum Verschlüsseln der Backup-Dateien. Lokale Backups landen im öffentlichen Ordner Download/Osmium; die 5 neuesten bleiben, ältere werden gelöscht.

Kamera: Nur zum Scannen von QR-Codes, Verarbeitung auf dem Gerät; Bilder werden nie gespeichert oder übertragen.

Biometrie: Ausschließlich vom Android-System verarbeitet; die App sieht oder speichert niemals Fingerabdruck- oder Gesichtsdaten.

Wir teilen nichts, weil wir nichts besitzen. Das Deinstallieren der App löscht alle lokal gespeicherten Daten.

Jede zukünftige Änderung dieser Garantien wird in den Release-Notes genannt.

Kontakt: zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "es": """Osmium no recoge ningún dato. No hay sistema de cuentas, registro, analíticas, SDK de publicidad ni informes de fallos.

Almacenamiento: tus secretos, nombres de cuenta y emisores se guardan solo en este dispositivo, cifrados con AES-256-GCM mediante una clave no exportable del Android Keystore. Los archivos de copia se cifran con tu contraseña (PBKDF2 + AES-256-GCM).

Red: el permiso INTERNET se usa solo para dos funciones. (1) Copia WebDAV: la app solo se conecta a la dirección del servidor que configures — normalmente un NAS o PC en tu red local — y solo al copiar o restaurar. (2) Comprobación de actualizaciones: si está activada, la app pregunta a la API de versiones de GitHub por la última versión pública una vez al día. Las copias se cifran con contraseña antes de salir del dispositivo.

Copia automática: las copias programadas se ejecutan sin intervención. La contraseña de exportación se guarda cifrada con la clave de Android Keystore y solo sirve para cifrar los archivos de copia. Las copias locales van a la carpeta pública Download/Osmium; se conservan las 5 más recientes y se borran las antiguas.

Cámara: solo para escanear códigos QR, procesados en el dispositivo; las imágenes nunca se guardan ni transmiten.

Biometría: gestionada exclusivamente por el sistema Android; la app nunca ve ni guarda datos de huella o rostro.

No compartimos nada, porque no tenemos nada. Desinstalar la app elimina todos los datos locales.

Cualquier cambio futuro que afecte a estas garantías se indicará en las notas de la versión.

Contacto: zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "fr": """Osmium ne collecte aucune donnée. Pas de système de compte, pas d'inscription, pas d'analyse, pas de SDK publicitaire, pas de rapport de plantage.

Stockage : vos secrets, noms de compte et émetteurs sont stockés uniquement sur cet appareil, chiffrés en AES-256-GCM avec une clé non exportable du Android Keystore. Les fichiers de sauvegarde sont chiffrés avec votre mot de passe (PBKDF2 + AES-256-GCM).

Réseau : l'autorisation INTERNET ne sert qu'à deux fonctions. (1) Sauvegarde WebDAV : l'app ne se connecte qu'à l'adresse de serveur que vous configurez — généralement un NAS ou un PC sur votre réseau local — et uniquement lors d'une sauvegarde ou d'une restauration. (2) Vérification des mises à jour : si activée, l'app demande à l'API des versions GitHub la dernière version publique une fois par jour. Les sauvegardes sont chiffrées par mot de passe avant de quitter l'appareil.

Sauvegarde automatique : les sauvegardes planifiées s'exécutent sans intervention. Le mot de passe d'export que vous définissez est stocké chiffré avec la clé Android Keystore et ne sert qu'à chiffrer les fichiers de sauvegarde. Les sauvegardes locales sont écrites dans le dossier public Téléchargement/Osmium ; les 5 plus récentes sont conservées et les anciennes supprimées.

Caméra : uniquement pour scanner des QR codes, traités sur l'appareil ; les images ne sont jamais stockées ni transmises.

Biométrie : gérée exclusivement par le système Android ; l'app ne voit ni ne stocke jamais de données d'empreinte ou de visage.

Nous ne partageons rien, car nous ne détenons rien. Désinstaller l'app supprime toutes les données stockées localement.

Tout changement futur affectant ces garanties sera indiqué dans les notes de version.

Contact : zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "hi": """Osmium कोई डेटा एकत्र नहीं करता। कोई खाता प्रणाली नहीं, कोई पंजीकरण नहीं, कोई एनालिटिक्स नहीं, कोई विज्ञापन SDK नहीं, कोई क्रैश रिपोर्ट नहीं।

स्टोरेज: आपके सीक्रेट, खाता नाम और जारीकर्ता केवल इसी डिवाइस पर संग्रहीत होते हैं, Android Keystore की गैर-निर्यात योग्य कुंजी से AES-256-GCM एन्क्रिप्शन के साथ। बैकअप फ़ाइलें आपके पासवर्ड से एन्क्रिप्ट होती हैं (PBKDF2 + AES-256-GCM)।

नेटवर्क: INTERNET अनुमति केवल दो सुविधाओं के लिए उपयोग होती है। (1) WebDAV बैकअप: ऐप केवल आपके द्वारा सेट किए गए सर्वर पते से जुड़ता है — आमतौर पर आपके लोकल नेटवर्क पर NAS या PC — और केवल बैकअप या रिस्टोर के समय। (2) अपडेट जाँच: स्वतः जाँच चालू होने पर ऐप दिन में एक बार GitHub Releases API से नवीनतम सार्वजनिक संस्करण पूछता है। बैकअप डिवाइस छोड़ने से पहले पासवर्ड से एन्क्रिप्ट होते हैं।

स्वचालित बैकअप: शेड्यूल किए गए बैकअप बिना देखरेख चलते हैं। आपका सेट किया एक्सपोर्ट पासवर्ड Android Keystore कुंजी से एन्क्रिप्ट होकर सहेजा जाता है और केवल बैकअप फ़ाइलें एन्क्रिप्ट करने के लिए उपयोग होता है। लोकल बैकअप सार्वजनिक Download/Osmium फ़ोल्डर में लिखे जाते हैं; सबसे नई 5 फ़ाइलें रखी जाती हैं और पुरानी हटाई जाती हैं।

कैमरा: केवल QR कोड स्कैन करने के लिए, प्रोसेसिंग डिवाइस पर ही; फ़्रेम कभी सहेजे या भेजे नहीं जाते।

बायोमेट्रिक्स: पूरी तरह Android सिस्टम द्वारा संभाला जाता है; ऐप फ़िंगरप्रिंट या चेहरे का डेटा कभी नहीं देखता या रखता।

हम कुछ साझा नहीं करते, क्योंकि हमारे पास कुछ नहीं है। ऐप अनइंस्टॉल करने से सभी लोकल डेटा हट जाते हैं।

इन गारंटियों को प्रभावित करने वाला कोई भी भविष्य का बदलाव रिलीज़ नोट्स में बताया जाएगा।

संपर्क: zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "ja": """Osmiumはデータを一切収集しません。アカウントシステム、登録、解析、広告SDK、クラッシュレポートはありません。

保存：秘密鍵、アカウント名、発行者はこの端末にのみ保存され、Android Keystoreのエクスポート不可キーによるAES-256-GCMで暗号化されます。バックアップファイルはあなたのパスワードで暗号化されます（PBKDF2 + AES-256-GCM）。

ネットワーク：INTERNET権限は2つの機能のみに使われます。(1) WebDAVバックアップ：アプリはあなたが設定したサーバーアドレス——通常はローカルネットワーク上のNASやPC——にのみ、バックアップまたは復元の実行時のみ接続します。(2) 更新確認：自動確認が有効な場合、アプリは1日1回、GitHub Releases APIに最新の公開バージョンを問い合わせます。バックアップは端末を離れる前にパスワードで暗号化されます。

自動バックアップ：スケジュールされたバックアップは無人で実行されます。設定したエクスポート用パスワードはAndroid Keystoreキーで暗号化して保存され、バックアップファイルの暗号化にのみ使用されます。ローカルバックアップは公開フォルダ「ダウンロード/Osmium」に書き込まれ、最新5件を残して古いものは削除されます。

カメラ：QRコードのスキャンにのみ使用され、端末上で処理されます。映像が保存・送信されることはありません。

生体認証：完全にAndroidシステムが処理します。アプリが指紋や顔のデータを見たり保存したりすることはありません。

私たちは何も保持していないため、何も共有しません。アプリをアンインストールすると、ローカルに保存されたデータはすべて削除されます。

これらの保証に影響する将来の変更は、リリースノートに明記されます。

連絡先：zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "ko": """Osmium은 어떤 데이터도 수집하지 않습니다. 계정 시스템, 가입, 분석, 광고 SDK, 크래시 리포트가 없습니다.

저장: 비밀 키, 계정 이름, 발급자는 이 기기에만 저장되며 Android Keystore의 내보낼 수 없는 키로 AES-256-GCM 암호화됩니다. 백업 파일은 사용자의 비밀번호로 암호화됩니다(PBKDF2 + AES-256-GCM).

네트워크: INTERNET 권한은 두 기능에만 사용됩니다. (1) WebDAV 백업: 앱은 사용자가 설정한 서버 주소 — 일반적으로 로컬 네트워크의 NAS 또는 PC — 에만, 백업 또는 복원을 실행할 때만 연결합니다. (2) 업데이트 확인: 자동 확인이 켜져 있으면 앱은 하루 1회 GitHub Releases API에 최신 공개 버전을 묻습니다. 백업은 기기를 떠나기 전에 비밀번호로 암호화됩니다.

자동 백업: 예약된 백업은 무인으로 실행됩니다. 설정한 내보내기 비밀번호는 Android Keystore 키로 암호화되어 저장되며 백업 파일 암호화에만 사용됩니다. 로컬 백업은 공용 다운로드/Osmium 폴더에 저장되고 최신 5개만 남기고 오래된 파일은 삭제됩니다.

카메라: QR 코드 스캔에만 사용되며 기기에서 처리됩니다. 화면이 저장되거나 전송되지 않습니다.

생체 인식: 전적으로 Android 시스템이 처리합니다. 앱은 지문이나 얼굴 데이터를 보거나 저장하지 않습니다.

우리는 아무것도 보유하지 않으므로 아무것도 공유하지 않습니다. 앱을 삭제하면 로컬에 저장된 모든 데이터가 삭제됩니다.

이 보장에 영향을 주는 향후 변경은 릴리스 노트에 명시됩니다.

문의: zhif0776@hotmail.com · https://t.me/osmium2fa""",
 "ru": """Osmium не собирает никаких данных. Нет системы аккаунтов, регистрации, аналитики, рекламных SDK и отчётов о сбоях.

Хранение: ваши секреты, имена аккаунтов и издатели хранятся только на этом устройстве, зашифрованы AES-256-GCM с помощью неэкспортируемого ключа в Android Keystore. Файлы копий шифруются вашим паролем (PBKDF2 + AES-256-GCM).

Сеть: разрешение INTERNET используется только для двух функций. (1) Резервное копирование WebDAV: приложение подключается только к настроенному вами адресу сервера — обычно NAS или ПК в локальной сети — и только при выполнении копирования или восстановления. (2) Проверка обновлений: если включена автопроверка, приложение раз в день запрашивает у API релизов GitHub последнюю публичную версию. Копии шифруются паролем до выхода с устройства.

Автобэкап: резервные копии по расписанию выполняются без участия пользователя. Заданный вами пароль экспорта хранится зашифрованным ключом Android Keystore и используется только для шифрования файлов копий. Локальные копии записываются в общую папку Загрузки/Osmium; хранятся 5 последних, более старые удаляются.

Камера: используется только для сканирования QR-кодов, обработка на устройстве; кадры никогда не сохраняются и не передаются.

Биометрия: обрабатывается исключительно системой Android; приложение никогда не видит и не хранит данные отпечатков или лица.

Мы ничего не передаём, потому что ничего не храним. Удаление приложения стирает все локальные данные.

Любое будущее изменение этих гарантий будет указано в примечаниях к выпуску.

Контакты: zhif0776@hotmail.com · https://t.me/osmium2fa""",
}

# ---------------------------------------------------------------- helpers

def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def write(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def replace_value(content, key, new_value):
    """Replace the full value of <string name=key>...</string>."""
    pattern = re.compile(
        r'(<string name="' + re.escape(key) + r'">)(.*?)(</string>)',
        re.DOTALL,
    )
    if not pattern.search(content):
        raise RuntimeError(f"key not found: {key}")
    return pattern.sub(
        lambda m: m.group(1) + esc(new_value) + m.group(3),
        content, count=1,
    )

def replace_last_segment(content, key, new_last):
    """Replace the final \\n-separated segment of a string value."""
    pattern = re.compile(
        r'(<string name="' + re.escape(key) + r'">)(.*?)(</string>)',
        re.DOTALL,
    )
    def repl(m):
        value = m.group(2)
        if "\\n" in value:
            head, sep, _ = value.rpartition("\\n")
            return m.group(1) + head + sep + esc(new_last) + m.group(3)
        return m.group(1) + esc(new_last) + m.group(3)
    if not pattern.search(content):
        raise RuntimeError(f"key not found: {key}")
    return pattern.sub(repl, content, count=1)

def add_new_strings(content, entries):
    """Insert new <string> entries before </resources>."""
    block = "\n    " + "\n    ".join(entries)
    idx = content.rfind("</resources>")
    assert idx != -1, "no </resources>"
    return content[:idx] + block + "\n" + content[idx:]

# ------------------------------------------------------------------- main

for folder in LOCALES:
    path = f"{BASE}/{folder}/strings.xml"
    content = read(path)
    lang = LOCALE_CODE[folder]

    # 1) notes body hardware phrase
    old_phrase, new_phrase = NOTES_HARDWARE[lang]
    assert old_phrase in content, f"{folder}: notes phrase not found"
    content = content.replace(old_phrase, esc(new_phrase), 1)

    # 2) security body last bullet
    content = replace_last_segment(content, "manual_security_body", SECURITY_LAST[lang])

    # 3) terms body last paragraph
    content = replace_last_segment(content, "terms_body", TERMS_LAST[lang])

    # 4) privacy body full rewrite
    content = replace_value(content, "privacy_body", PRIVACY_FULL[lang])

    # 5) new keys
    entries = [
        f'<string name="{key}">{esc(NEW[key][lang])}</string>'
        for key in NEW
    ]
    content = add_new_strings(content, entries)

    write(path, content)
    print(f"OK {folder} ({lang})")

print("ALL DONE")
