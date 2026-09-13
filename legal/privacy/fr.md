# Politique de confidentialité

*Date d’entrée en vigueur : 13 septembre 2026 · Osmium v2.4.2*

*Version actuelle, publiée sur osmium.im et récupérée par l’application à chaque ouverture (À propos → Politique de confidentialité).*

Osmium ne collecte, ne vend ni n’utilise de données personnelles à des fins publicitaires ou d’analyse. Il n’existe ni système de compte, ni inscription, ni SDK publicitaire, ni rapporteur de plantages, ni compte cloud Osmium, ni serveur de synchronisation.

Stockage : les secrets d’authentification, les noms de compte, les émetteurs, les tags et les couleurs de tags sont stockés uniquement sur cet appareil, chiffrés avec AES-256-GCM au moyen d’une clé Android Keystore non exportable. Les sauvegardes exportées sont chiffrées avec votre mot de passe (PBKDF2-HMAC-SHA256 avec 120 000 itérations, puis AES-256-GCM). L’identifiant WebDAV et le mot de passe de sauvegarde automatique sont stockés localement, chiffrés avec la clé Keystore. Si vous activez l’export en clair du mode développeur, le fichier exporté n’est pas chiffré : il contient tous les secrets sous forme lisible et n’est protégé que par l’endroit où vous le conservez.

Détection de root : lorsque l’appareil est rooté, Osmium effectue des vérifications locales du mieux possible (paquets des gestionnaires de root, montages, traces du noyau et de la mémoire, propriétés d’état de démarrage) afin de décider s’il convient de renforcer la protection de l’application. Ces vérifications s’exécutent entièrement sur cet appareil, n’effectuent aucune requête réseau et leurs résultats ne sont jamais stockés ni transmis ; un message s’affiche une seule fois à l’écran lors de la première détection de root.

Import : l’import depuis les QR codes de Google Authenticator et depuis les fichiers d’export d’Aegis, de 2FAS et de Raivo OTP se fait entièrement sur cet appareil. Le fichier sélectionné est lu en mémoire pour construire l’aperçu d’import ; il n’est ni copié, ni envoyé, ni transmis où que ce soit. Seules les entrées que vous confirmez sont enregistrées dans le coffre chiffré ; tout le reste est écarté.

Réseau : à l’exception de la récupération des documents juridiques décrite ci-dessous, l’application ne se connecte que lorsque la fonction concernée est activée ou lancée. WebDAV se connecte à l’adresse que vous configurez ; les vérifications de mise à jour GitHub demandent des informations publiques de version, sans données de compte ni d’appareil ; le transfert LAN rapide connecte deux appareils sur le même Wi-Fi pour une session chiffrée temporaire.

Documents juridiques : à chaque ouverture, l’application récupère depuis osmium.im les versions actuelles des Conditions d’utilisation et de la Politique de confidentialité afin que vous voyiez toujours les versions en vigueur. Ces requêtes ne comportent aucune donnée de compte, d’authentificateur ou d’appareil, et c’est la seule requête réseau que l’application effectue sans réglage ni action de votre part. Si la récupération échoue, l’application affiche un message d’échec avec un lien vers le site web.

Les sauvegardes automatiques utilisent la planification système d’Android et peuvent être différées par les politiques d’économie d’énergie du fabricant. Elles écrivent des fichiers chiffrés sur votre serveur WebDAV configuré ou dans le dossier Téléchargement/Osmium (les 5 plus récentes conservées par défaut, jusqu’à 10 configurables ; les fichiers plus anciens sont purgés). La numérisation par caméra, la lecture des QR codes et la biométrie sont traitées sur l’appareil par Android. L’état Wi-Fi et le multicast ne sont utilisés que pour la découverte de pairs LAN pendant que l’écran de transfert est ouvert. WRITE_EXTERNAL_STORAGE ne s’applique qu’aux sauvegardes automatiques sur Android 8/9. ACCESS_LOCAL_NETWORK est déclarée pour les futures exigences de plateforme et n’est pas demandée sur targetSdk 34.

Nous ne partageons aucune donnée avec des annonceurs, des services d’analyse ou un cloud Osmium. Un serveur WebDAV reçoit les fichiers chiffrés que vous envoyez ; le destinataire LAN prévu reçoit les données d’un transfert que vous lancez ; GitHub ne reçoit qu’une requête publique de version ; osmium.im fournit les documents juridiques publics que l’application lit, et aucune donnée utilisateur ne lui est envoyée. Désinstaller l’application ou effacer ses données ne supprime pas les copies que vous avez enregistrées ailleurs.

---

Contact : zhif0776@hotmail.com · https://t.me/osmium2fa
