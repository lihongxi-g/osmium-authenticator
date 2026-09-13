# Datenschutzerklärung

*Gültig ab: 13. September 2026 · Osmium v2.4.2*

*Aktuelle Fassung, veröffentlicht auf osmium.im und beim Start der App abgerufen (Über → Datenschutzerklärung).*

Osmium erhebt, verkauft oder nutzt keine personenbezogenen Daten für Werbung oder Analysen. Es gibt kein Kontosystem, keine Registrierung, kein Werbe-SDK und keinen Absturzmelder sowie kein Osmium-Cloud-Konto und keinen Synchronisierungsserver.

Speicherung: Authenticator-Geheimnisse, Kontonamen, Aussteller, Tags und Tag-Farben werden nur auf diesem Gerät gespeichert, verschlüsselt mit AES-256-GCM und einem nicht exportierbaren Android-Keystore-Schlüssel. Exportierte Backups werden mit deinem Passwort verschlüsselt (PBKDF2-HMAC-SHA256 mit 120.000 Iterationen, danach AES-256-GCM). Die WebDAV-Anmeldung und das Passwort für automatische Backups werden lokal gespeichert, verschlüsselt mit dem Keystore-Schlüssel. Wenn du den Klartext-Export des Entwicklermodus aktivierst, ist die exportierte Datei nicht verschlüsselt: Sie enthält alle Geheimnisse in lesbarer Form und ist nur durch den Ort geschützt, an dem du sie aufbewahrst.

Root-Erkennung: Wenn das Gerät gerootet ist, führt Osmium lokale Prüfungen nach bestem Bemühen durch (Root-Manager-Pakete, Mount-Punkte, Kernel- und Speicherspuren, Boot-Status-Eigenschaften), um zu entscheiden, ob die App gehärtet wird. Diese Prüfungen laufen vollständig auf diesem Gerät, senden keine Netzwerkanfragen, und ihre Ergebnisse werden weder gespeichert noch übertragen; wenn Root erstmals erkannt wird, erscheint ein einmaliger Hinweis auf dem Bildschirm.

Import: Das Importieren von Google-Authenticator-QR-Codes und von Exportdateien aus Aegis, 2FAS und Raivo OTP erfolgt vollständig auf diesem Gerät. Die ausgewählte Datei wird zum Erstellen der Importvorschau in den Speicher gelesen; sie wird nicht kopiert, hochgeladen oder irgendwohin gesendet. Nur die Einträge, die du bestätigst, werden im verschlüsselten Tresor gespeichert; alles andere wird verworfen.

Netzwerk: Abgesehen vom Abruf der unten beschriebenen rechtlichen Dokumente verbindet sich die App nur, wenn die betreffende Funktion aktiviert oder gestartet wird. WebDAV verbindet sich mit der von dir konfigurierten Adresse; GitHub-Updateprüfungen fragen öffentliche Versionsinformationen ohne Konto- oder Gerätedaten ab; die LAN-Übertragung verbindet zwei Geräte im selben WLAN für eine temporäre verschlüsselte Sitzung.

Rechtliche Dokumente: Bei jedem Öffnen ruft die App die aktuellen Versionen der Nutzungsbedingungen und der Datenschutzerklärung von osmium.im ab, damit du immer die jeweils geltenden Versionen siehst. Diese Anfragen enthalten keine Konto-, Authenticator- oder Gerätedaten, und dies ist die einzige Netzwerkanfrage, die die App ohne eine Einstellung oder eine Aktion von dir sendet. Schlägt der Abruf fehl, zeigt die App einen Fehlerhinweis mit einem Link zur Website an.

Automatische Backups nutzen die Android-Systemplanung und können durch Energiesparrichtlinien des Herstellers verzögert werden. Sie schreiben verschlüsselte Dateien auf deinen konfigurierten WebDAV-Server oder in den Ordner Download/Osmium (standardmäßig werden die 5 neuesten behalten, bis zu 10 sind konfigurierbar; ältere Dateien werden entfernt). Kamera-Scans, QR-Lesen und Biometrie werden von Android direkt auf dem Gerät verarbeitet. WLAN-Status und Multicast werden nur zur LAN-Geräteerkennung verwendet, solange der Übertragungsbildschirm geöffnet ist. WRITE_EXTERNAL_STORAGE gilt nur für automatische Backups unter Android 8/9. ACCESS_LOCAL_NETWORK ist für künftige Plattformanforderungen deklariert und wird bei targetSdk 34 nicht angefragt.

Wir geben keine Daten an Werbetreibende, Analysedienste oder eine Osmium-Cloud weiter. Ein WebDAV-Server erhält verschlüsselte Dateien, die du hochlädst; der vorgesehene LAN-Empfänger erhält Daten aus einer von dir gestarteten Übertragung; GitHub erhält nur eine öffentliche Versionsabfrage; osmium.im stellt die öffentlichen rechtlichen Dokumente bereit, die die App liest, und es werden keine Nutzerdaten dorthin gesendet. Das Deinstallieren der App oder das Löschen ihrer Daten entfernt keine Kopien, die du woanders gespeichert hast.

---

Kontakt: zhif0776@hotmail.com · https://t.me/osmium2fa
