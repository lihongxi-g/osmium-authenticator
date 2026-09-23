# Conditions d’utilisation

*Date d’entrée en vigueur : 23 septembre 2026 · S’applique à Osmium v2.5.3 et versions ultérieures*

*Version actuelle, publiée sur osmium.im et récupérée par l’application à chaque ouverture (À propos → Conditions d’utilisation).*

En installant ou en utilisant Osmium, vous acceptez les conditions suivantes.

Osmium est un logiciel libre et open source publié sous la licence publique générale GNU version 3 ou ultérieure (GPL-3.0-or-later ; voir LICENSE/COPYING).

Le logiciel est fourni « tel quel », sans garantie d’aucune sorte. Dans toute la mesure permise par la loi, le développeur n’est pas responsable des dommages découlant de son utilisation.

## Vos responsabilités :

- Protégez vos secrets, vos PIN et vos mots de passe de sauvegarde. Les clés de chiffrement ne peuvent être récupérées ni par le développeur ni par des tiers ; personne ne peut restaurer un PIN oublié, un mot de passe de sauvegarde perdu ou des données d’application effacées.
- Conservez vos propres sauvegardes. Désinstaller l’application ou effacer ses données supprime définitivement les comptes et paramètres locaux. Les sauvegardes automatiques dépendent de la planification d’Android et du fabricant et peuvent être différées ou bloquées par les politiques d’économie d’énergie.
- N’importez que des fichiers de confiance. Osmium importe depuis les QR codes de transfert de Google Authenticator et depuis les fichiers d’export d’Aegis, de 2FAS et de Raivo OTP. Examinez chaque entrée de l’aperçu avant d’importer, confirmez que le fichier provient bien de l’authentificateur que vous comptez quitter, puis supprimez les fichiers d’export après utilisation — ils contiennent vos secrets en clair.
- Vérifiez les données reçues. Le transfert LAN rapide requiert les deux appareils sur le même réseau Wi-Fi : l’appareil récepteur affiche un code d’association à 12 chiffres que l’appareil expéditeur doit saisir avant tout transfert ; ne communiquez le code qu’à l’appareil depuis lequel vous envoyez et vérifiez les comptes dans l’aperçu d’import.
- Le PIN d’autodestruction est irréversible. Gardez l’horloge de votre appareil exacte et respectez les lois applicables et les règles de réseau pour les serveurs et appareils que vous configurez.
- Appareils rootés. Lorsqu’Osmium détecte que l’appareil est rooté, il active de force ses protections (vérification à l’ouverture, blocage des captures d’écran, codes masqués) et désactive le transfert LAN rapide, la sauvegarde WebDAV, les sauvegardes automatiques, la configuration de l’autodestruction et l’import depuis des tiers. Ces restrictions ne peuvent être levées qu’en mode développeur. La détection de root s’exécute localement sur l’appareil et est effectuée du mieux possible.
- Rapport d’intégrité de l’appareil. Au démarrage, Osmium contrôle également l’intégrité de l’appareil localement : contrôles du système, des montages, du noyau, de l’état de démarrage et de la signature de l’application, recoupements entre ces contrôles, ainsi qu’une attestation de clé matérielle (chaîne de certificats TEE, chaîne de démarrage vérifié et boot hash) vérifiée sur cet appareil avec le vérificateur officiel de Google. Le rapport n’est affiché que sur votre appareil ; lorsque le résultat est suspect ou compromis, l’application vous le rappelle une fois par ouverture. La présence d’une application de gestion de root est considérée comme un indice, et non comme une preuve. Aucun contrôle logiciel n’est parfaitement fiable : considérez le rapport comme une indication sur l’état de votre appareil, et non comme une garantie. Le mode développeur permet de masquer cette fonction ; une fois masquée, l’application n’effectue plus ces contrôles au démarrage et n’affiche plus le rappel.
- Mode développeur. Caché derrière sept tapes sur le nom de l’application dans « À propos » et protégé par une vérification d’identité, il peut lever les restrictions liées au root, masquer des entrées de paramètres, autoriser les codes à 4, 5 ou 7 chiffres, exporter votre coffre en clair et forcer le rechiffrement de la base de données locale. Il propose également un journal de détection détaillé facultatif — les contrôles d’intégrité n’écrivent aucun journal sauf si vous l’activez. Un export en clair contient tous vos secrets sans chiffrement — quiconque obtient le fichier peut lire tous les comptes. Vous utilisez le mode développeur à vos propres risques ; le développeur n’est pas responsable des pertes causées par celui-ci ou par les exports en clair.

Vous pouvez utiliser Osmium pour votre propre authentification à deux facteurs et pour les appareils que vous contrôlez ou que vous êtes autorisé à utiliser.

Osmium n’a aucun système de compte ni aucune synchronisation cloud ; toutes les fonctions d’import s’exécutent entièrement sur cet appareil. L’application peut se connecter au serveur WebDAV que vous configurez, à l’API GitHub Releases pour les vérifications de version (activées par défaut, désactivables dans les paramètres), au site osmium.im pour récupérer les versions les plus récentes des présentes Conditions d’utilisation et de la Politique de confidentialité, à la liste publique des statuts d’attestation de Google lorsque vous actualisez les données de révocation sur l’écran d’intégrité, ou à l’autre appareil pendant un transfert LAN. Le transfert LAN utilise un chiffrement AES-256-GCM dérivé du code d’association et ne passe pas par un serveur cloud Osmium.

Les versions actuelles des présentes Conditions d’utilisation et de la Politique de confidentialité sont publiées sur le site osmium.im. L’application récupère les deux à chaque ouverture afin que vous puissiez lire la version en vigueur ; si la récupération échoue, l’application affiche un message d’échec avec un lien vers le site web. Les versions publiées sur osmium.im sont celles qui s’appliquent.

---

Contact : zhif0776@hotmail.com · https://t.me/osmium2fa
