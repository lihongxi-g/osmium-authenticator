# Nutzungsbedingungen

*Gültig ab: 22. Oktober 2026 · Gilt für Osmium v2.6.0 und spätere Versionen*

*Aktuelle Fassung, veröffentlicht auf osmium.im. Die App liest sie, wenn du sie unter „Über → Nutzungsbedingungen“ öffnest.*

Durch die Installation oder Nutzung von Osmium stimmst du den folgenden Bedingungen zu.

Osmium ist freie Open-Source-Software, veröffentlicht unter der GNU General Public License Version 3 oder später (GPL-3.0-or-later; siehe LICENSE/COPYING).

Die Software wird „as is“, ohne jegliche Garantie, bereitgestellt. Soweit gesetzlich zulässig, haftet der Entwickler nicht für Schäden, die aus ihrer Nutzung entstehen.

Osmium erzeugt TOTP- und HOTP-Codes mit 4, 5, 6, 7 oder 8 Stellen und unterstützt SHA-1, SHA-256 und SHA-512, Perioden von 30 und 60 Sekunden, Steam-Guard-Codes, Tags und Suche. Alles davon läuft auf deinem Gerät.

## Deine Verantwortlichkeiten:

- Schütze deine Geheimnisse, PINs und Backup-Passwörter. Verschlüsselungsschlüssel können weder vom Entwickler noch von Dritten wiederhergestellt werden; eine vergessene PIN, ein verlorenes Backup-Passwort oder gelöschte App-Daten kann niemand wiederherstellen.
- Lege eigene Backups an. Das Deinstallieren der App oder das Löschen ihrer Daten entfernt lokale Konten und Einstellungen dauerhaft. Automatische Backups hängen von der Zeitplanung von Android und des Herstellers ab und können durch Energiesparrichtlinien verzögert oder blockiert werden.
- Importiere nur Dateien, denen du vertraust. Osmium importiert aus Übertragungs-QR-Codes von Google Authenticator und aus Exportdateien von Aegis, 2FAS und Raivo OTP. Prüfe vor dem Import jeden Eintrag in der Vorschau, vergewissere dich, dass die Datei wirklich von dem Authenticator stammt, den du verlassen willst, und lösche Exportdateien nach der Verwendung — sie enthalten deine Geheimnisse im Klartext. Eine Backup-Datei, deren Einträge Code-Parameter verwenden, die Osmium nicht originalgetreu reproduzieren kann (eine Codelänge oder ein Algorithmus außerhalb des unterstützten Bereichs), wird als Ganzes abgelehnt und nicht teilweise importiert.
- Prüfe empfangene Daten. Die LAN-Übertragung erfordert beide Geräte im selben WLAN: Das empfangende Gerät zeigt einen 12-stelligen Kopplungscode an, und das sendende Gerät muss ihn eingeben, bevor etwas übertragen wird. Danach zeigt jedes Gerät die erkannten Netzwerkrisiken an — auf sich selbst und auf dem anderen Gerät —, und nichts wird gesendet oder akzeptiert, bis beide Seiten bestätigen; wird der Dialog abgebrochen, bricht die Übertragung ab. Teile den Code nur mit dem Gerät, von dem du sendest, und bestätige die Einträge in der Importvorschau. Beide Geräte müssen eine Version ausführen, die dasselbe Übertragungsprotokoll spricht.
- Die Selbstzerstörungs-PIN ist unumkehrbar. Halte die Uhr deines Geräts genau und beachte geltendes Recht sowie die Netzwerkregeln für die Server und Geräte, die du konfigurierst.
- Gerootete Geräte. Wenn Osmium erkennt, dass das Gerät gerootet ist, erzwingt es seine Schutzfunktionen (Prüfung beim Öffnen, Screenshot-Sperre, ausgeblendete Codes) und deaktiviert die LAN-Übertragung, WebDAV-Backup, automatische Backups, Selbstzerstörungs-Konfiguration und den Import von Drittanbietern. Diese Einschränkungen können nur im Entwicklermodus aufgehoben werden. Die Root-Erkennung läuft lokal auf dem Gerät und ist eine Prüfung nach bestem Bemühen.
- Geräteintegritätsbericht. Beim Start prüft Osmium zusätzlich die Geräteintegrität lokal: System-, Mount-, Kernel-, Boot-Status- und App-Signaturprüfungen, Gegenprüfungen zwischen ihnen sowie hardwaregestützte Schlüsselattestierung (TEE-Zertifikatskette, Verified-Boot-Kette und Boot-Hash), die auf diesem Gerät mit Googles offiziellem Verifizierer verifiziert wird. Der Bericht wird nur auf deinem Gerät angezeigt; wenn das Ergebnis verdächtig oder kompromittiert ist, erinnert dich die App bei jedem Öffnen einmal daran. Eine installierte Root-Manager-App gilt als Hinweis, nicht als Beweis. Keine Softwareprüfung ist vollkommen genau, lies den Bericht daher als Orientierung zu deinem Gerät und nicht als Garantie. Im Entwicklermodus lässt sich diese Funktion ausblenden; sobald sie ausgeblendet ist, führt die App diese Prüfungen beim Start nicht mehr aus und zeigt auch keine Erinnerung mehr an.
- Entwicklermodus. Er ist hinter siebenmaligem Tippen auf den App-Namen unter „Über“ verborgen und durch eine Identitätsprüfung geschützt; er kann die Root-Einschränkungen aufheben, Einstellungseinträge ausblenden, deinen Tresor im Klartext exportieren und die erneute Verschlüsselung der lokalen Datenbank erzwingen. Außerdem bietet er ein optionales detailliertes Erkennungsprotokoll — die Integritätsprüfungen schreiben kein Protokoll, solange du es nicht aktivierst. Ein Klartext-Export enthält alle deine Geheimnisse unverschlüsselt — wer die Datei erhält, kann jedes Konto lesen. Du verwendest den Entwicklermodus auf eigenes Risiko; der Entwickler haftet nicht für Verluste, die durch ihn oder durch Klartext-Exporte entstehen.

Du darfst Osmium für deine eigene Zwei-Faktor-Authentifizierung und für Geräte verwenden, die du kontrollierst oder zu deren Nutzung du berechtigt bist.

Osmium hat kein Kontosystem und keine Cloud-Synchronisierung; alle Importfunktionen laufen vollständig auf diesem Gerät. Die App stellt beim Start keine Netzwerkanfrage, es sei denn, du schaltest die optionale Versionsprüfung ein. Ansonsten verbindet sie sich nur, wenn du eine Funktion nutzt, die sie benötigt: den von dir konfigurierten WebDAV-Server, die GitHub-Releases-API für die optionale Versionsprüfung (standardmäßig aus; du kannst sie in den Einstellungen einschalten), die osmium.im-Website, um die aktuellen Fassungen dieser Nutzungsbedingungen und der Datenschutzerklärung zu lesen, wenn du sie unter „Über“ öffnest, Googles öffentliche Statusliste für Attestierungen, wenn du die Sperrdaten auf dem Integritätsbildschirm aktualisierst, oder das andere Gerät während einer LAN-Übertragung. Die LAN-Übertragung verwendet eine vom Kopplungscode abgeleitete AES-256-GCM-Verschlüsselung und läuft nicht über einen Osmium-Cloudserver.

Die aktuellen Fassungen dieser Nutzungsbedingungen und der Datenschutzerklärung werden auf der osmium.im-Website veröffentlicht, und es gelten die dort veröffentlichten Fassungen. Die App liest sie, wenn du das Dokument unter „Über“ öffnest, und behält die gelesene Kopie, damit du sie ohne Verbindung erneut lesen kannst. Schlägt ein Abruf fehl, zeigt die App einen Fehlerhinweis mit einem Link zur Website an.

Backup-Benachrichtigungen und Verifizierung: Automatische Backups können ihr Ergebnis per lokaler Benachrichtigung melden. Diese lässt sich abschalten und kann zudem vom System und von Akku-Richtlinien verzögert oder unterdrückt werden; eine Benachrichtigung ist daher eine Annehmlichkeit und kein Beweis, dass ein Backup gelaufen ist. Die Seite für automatische Backups kann das Backup-Passwort ändern – lassen Sie deshalb „vor dem Öffnen aus einer Benachrichtigung bestätigen“ (standardmäßig an) oder „beim Öffnen bestätigen“ eingeschaltet: Sind beide aus, kann jede Person mit Ihrem entsperrten Gerät über die Benachrichtigung diese Seite erreichen und das Backup-Passwort ändern.

**Sprachfassungen:** Dieses Dokument wird in mehreren Sprachen veröffentlicht. Die englische Fassung ist das Original; die Übersetzungen dienen der Verständlichkeit. Bei Widersprüchen zwischen einer Übersetzung und der englischen Fassung gilt die englische Fassung.

---

Kontakt: zhif0776@hotmail.com · https://t.me/osmium2fa
