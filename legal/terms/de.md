# Nutzungsbedingungen

*Gültig ab: 13. September 2026 · Osmium v2.4.2*

*Aktuelle Fassung, veröffentlicht auf osmium.im und beim Start der App abgerufen (Über → Nutzungsbedingungen).*

Durch die Installation oder Nutzung von Osmium stimmst du den folgenden Bedingungen zu.

Osmium ist freie Open-Source-Software, veröffentlicht unter der GNU General Public License Version 3 oder später (GPL-3.0-or-later; siehe LICENSE/COPYING).

Die Software wird „as is“, ohne jegliche Garantie, bereitgestellt. Soweit gesetzlich zulässig, haftet der Entwickler nicht für Schäden, die aus ihrer Nutzung entstehen.

## Deine Verantwortlichkeiten:

- Schütze deine Geheimnisse, PINs und Backup-Passwörter. Verschlüsselungsschlüssel können weder vom Entwickler noch von Dritten wiederhergestellt werden; eine vergessene PIN, ein verlorenes Backup-Passwort oder gelöschte App-Daten kann niemand wiederherstellen.
- Lege eigene Backups an. Das Deinstallieren der App oder das Löschen ihrer Daten entfernt lokale Konten und Einstellungen dauerhaft. Automatische Backups hängen von der Android- und Hersteller-Planung ab und können durch Energiesparrichtlinien verzögert oder blockiert werden.
- Importiere nur Dateien, denen du vertraust. Osmium importiert Google-Authenticator-Übertragungs-QR-Codes und Exportdateien von Aegis, 2FAS und Raivo OTP. Prüfe jeden Eintrag in der Vorschau, bevor du importierst, vergewissere dich, dass die Datei wirklich von der Authenticator-App stammt, die du verlassen möchtest, und lösche Exportdateien nach der Verwendung — sie enthalten deine Geheimnisse im Klartext.
- Prüfe empfangene Daten. Die LAN-Übertragung erfordert dasselbe WLAN und einen 6-stelligen Kopplungscode; wähle die Konten in der Importvorschau aus und teile den Code nur dem vorgesehenen Empfänger mit.
- Die Selbstzerstörungs-PIN ist unumkehrbar. Halte die Geräteuhr korrekt eingestellt und beachte geltendes Recht sowie die Netzwerkregeln für die Server und Geräte, die du konfigurierst.
- Gerootete Geräte. Wenn Osmium erkennt, dass das Gerät gerootet ist, aktiviert es seine Sicherheitsfunktionen zwangsweise (Prüfung beim Öffnen, Screenshot-Sperre, ausgeblendete Codes) und deaktiviert die LAN-Übertragung, WebDAV-Backup, automatische Backups, Selbstzerstörungs-Konfiguration und den Import von Drittanbieter-Apps. Diese Einschränkungen können nur im Entwicklermodus aufgehoben werden. Die Root-Erkennung läuft lokal auf dem Gerät und ist eine Prüfung nach bestem Bemühen.
- Entwicklermodus. Er ist hinter siebenmaligem Tippen auf den App-Namen unter „Über“ verborgen und durch eine Identitätsprüfung geschützt; er kann die Root-Einschränkungen aufheben, Einstellungseinträge ausblenden, 4/5/7-stellige Codes erlauben, deinen Tresor im Klartext exportieren und die erneute Verschlüsselung der lokalen Datenbank erzwingen. Ein Klartext-Export enthält alle deine Geheimnisse unverschlüsselt — wer die Datei erhält, kann jedes Konto lesen. Du verwendest den Entwicklermodus auf eigenes Risiko; der Entwickler haftet nicht für Verluste, die durch den Entwicklermodus oder durch Klartext-Exporte entstehen.

Du darfst Osmium für deine eigene Zwei-Faktor-Authentifizierung und für Geräte verwenden, die du kontrollierst oder zu deren Nutzung du berechtigt bist.

Osmium hat kein Kontosystem und keine Cloud-Synchronisierung; alle Importfunktionen laufen vollständig auf diesem Gerät. Die App kann sich mit dem von dir konfigurierten WebDAV-Server, mit der GitHub-Releases-API für optionale Versionsprüfungen, mit der osmium.im-Website, um die neuesten Versionen dieser Nutzungsbedingungen und der Datenschutzerklärung abzurufen, oder mit dem anderen Gerät während einer LAN-Übertragung verbinden. Die LAN-Übertragung verwendet eine aus dem Kopplungscode abgeleitete AES-256-GCM-Verschlüsselung und läuft nicht über einen Osmium-Cloudserver.

Die aktuellen Versionen dieser Nutzungsbedingungen und der Datenschutzerklärung werden auf der osmium.im-Website veröffentlicht. Die App ruft beide bei jedem Öffnen ab, damit du die jeweils geltende Version lesen kannst; schlägt ein Abruf fehl, zeigt die App einen Fehlerhinweis mit einem Link zur Website an. Es gelten die auf osmium.im veröffentlichten Versionen.

---

Kontakt: zhif0776@hotmail.com · https://t.me/osmium2fa
