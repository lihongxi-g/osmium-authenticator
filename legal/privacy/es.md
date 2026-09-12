# Política de privacidad

*Fecha de entrada en vigor: 13 de septiembre de 2026 · Osmium v2.4.1*

*Versión actual, publicada en osmium.im y obtenida por la aplicación cada vez que se abre (Acerca de → Política de privacidad).*

Osmium no recopila, vende ni usa datos personales para publicidad o analítica. No hay sistema de cuentas, ni registro, ni SDK de publicidad, ni informes de fallos, ni cuenta en la nube de Osmium ni servidor de sincronización.

Almacenamiento: los secretos de autenticación, los nombres de cuenta, los emisores, las etiquetas y sus colores se guardan solo en este dispositivo, cifrados con AES-256-GCM mediante una clave no exportable de Android Keystore. Las copias exportadas se cifran con tu contraseña (PBKDF2-HMAC-SHA256 con 120 000 iteraciones y después AES-256-GCM). El inicio de sesión de WebDAV y la contraseña de la copia automática se guardan localmente, cifrados con la clave de Keystore.

Importación: importar desde códigos QR de Google Authenticator y desde archivos de exportación de Aegis, 2FAS y Raivo OTP ocurre íntegramente en este dispositivo. El archivo seleccionado se lee en memoria para construir la vista previa de importación; no se copia, sube ni envía a ningún sitio. Solo las entradas que confirmas se guardan en la bóveda cifrada; todo lo demás se descarta.

Red: aparte de obtener los documentos legales descritos a continuación, la aplicación solo se conecta cuando la función correspondiente está activada o se inicia. WebDAV se conecta a la dirección que configures; las comprobaciones de actualización de GitHub solicitan información pública de versiones sin datos de cuentas ni del dispositivo; la transferencia LAN rápida conecta dos dispositivos en la misma red Wi-Fi para una única sesión cifrada temporal.

Documentos legales: cada vez que la aplicación se abre, obtiene de osmium.im las versiones actuales de los Términos de uso y de la Política de privacidad para que veas siempre las versiones vigentes. Estas solicitudes no contienen datos de cuentas, del autenticador ni del dispositivo, y esta es la única solicitud de red que hace la aplicación sin un ajuste ni una acción por tu parte. Si la obtención falla, la aplicación muestra un aviso de error con un enlace al sitio web.

Las copias automáticas usan la programación del sistema de Android y pueden retrasarse por las políticas de energía del fabricante. Escriben archivos cifrados en tu servidor WebDAV configurado o en la carpeta Download/Osmium (por defecto se conservan las 5 más recientes, hasta 10 configurables; los archivos más antiguos se eliminan). El escaneo con la cámara, la lectura de códigos QR y la biometría las gestiona Android en el propio dispositivo. El estado de Wi-Fi y multicast se usan para el descubrimiento de dispositivos LAN solo mientras la pantalla de transferencia está abierta. WRITE_EXTERNAL_STORAGE solo se aplica a las copias automáticas en Android 8/9. ACCESS_LOCAL_NETWORK se declara para futuros requisitos de la plataforma y no se solicita en targetSdk 34.

No compartimos datos con anunciantes, servicios de analítica ni una nube de Osmium. Un servidor WebDAV recibe archivos cifrados que tú subes; el receptor LAN previsto recibe datos de una transferencia que tú inicias; GitHub recibe solo una consulta pública de versiones; osmium.im proporciona los documentos legales públicos que la aplicación lee, y no se le envía ningún dato del usuario. Desinstalar la aplicación o borrar sus datos no elimina las copias que hayas guardado en otro lugar.

---

Contacto: zhif0776@hotmail.com · https://t.me/osmium2fa
