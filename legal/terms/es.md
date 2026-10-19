# Términos de uso

*Fecha de entrada en vigor: 16 de octubre de 2026 · Se aplica a Osmium v2.5.6 y versiones posteriores*

*Versión actual, publicada en osmium.im. La aplicación la lee cuando abres Acerca de → Términos de uso.*

Al instalar o usar Osmium, aceptas los términos siguientes.

Osmium es software libre y de código abierto publicado bajo la Licencia Pública General de GNU versión 3 o posterior (GPL-3.0-or-later; consulta LICENSE/COPYING).

El software se proporciona «tal cual», sin garantía de ningún tipo. En la máxima medida permitida por la ley, el desarrollador no responde de los daños derivados de su uso.

Osmium genera códigos TOTP y HOTP de 4, 5, 6, 7 u 8 dígitos y admite SHA-1, SHA-256 y SHA-512, periodos de 30 y 60 segundos, códigos de Steam Guard, etiquetas y búsqueda. Todo ello se ejecuta en tu dispositivo.

## Tus responsabilidades:

- Protege tus secretos, PIN y contraseñas de copia. Las claves de cifrado no pueden recuperarlas ni el desarrollador ni terceros; un PIN olvidado, una contraseña de copia perdida o los datos de la aplicación borrados no los puede restaurar nadie.
- Haz tus propias copias de seguridad. Desinstalar la aplicación o borrar sus datos elimina de forma permanente las cuentas y los ajustes locales. Las copias automáticas dependen de la programación de Android y del fabricante, y pueden retrasarse o bloquearse por las políticas de ahorro de energía.
- Importa solo archivos de confianza. Osmium importa desde códigos QR de transferencia de Google Authenticator y desde archivos de exportación de Aegis, 2FAS y Raivo OTP. Revisa cada entrada en la vista previa antes de importar, confirma que el archivo procede realmente del autenticador que piensas dejar y elimina los archivos de exportación después de usarlos — contienen tus secretos en texto plano. Un archivo de copia cuyas entradas usen parámetros de código que Osmium no pueda reproducir fielmente (una longitud de código o un algoritmo fuera del conjunto admitido) se rechaza entero en lugar de importarse en parte.
- Revisa los datos recibidos. La transferencia LAN rápida requiere ambos dispositivos en la misma red Wi-Fi: el dispositivo receptor muestra un código de emparejamiento de 12 dígitos y el emisor debe introducirlo antes de que se transfiera nada. Después, cada dispositivo muestra los riesgos de red que ha detectado —en sí mismo y en el otro dispositivo— y no se envía ni se acepta nada hasta que ambas partes lo confirman; cancelar el diálogo cancela la transferencia. Comparte el código solo con el dispositivo desde el que envías y confirma las entradas en la vista previa de importación. Ambos dispositivos deben ejecutar una versión que hable el mismo protocolo de transferencia.
- El PIN de autodestrucción es irreversible. Mantén precisa la hora del dispositivo y cumple la legislación aplicable y las normas de red de los servidores y dispositivos que configures.
- Dispositivos con root. Cuando Osmium detecta que el dispositivo tiene root, activa forzosamente sus protecciones (verificación al abrir, bloqueo de capturas de pantalla, códigos ocultos) y desactiva la transferencia LAN rápida, la copia por WebDAV, las copias automáticas, la configuración de la autodestrucción y la importación desde terceros. Estas restricciones solo pueden levantarse en el modo desarrollador. La detección de root se ejecuta localmente en el dispositivo y es de mejor esfuerzo.
- Informe de integridad del dispositivo. Al iniciarse, Osmium también comprueba localmente la integridad del dispositivo: comprobaciones del sistema, de los montajes, del kernel, del estado de arranque y de la firma de la aplicación, verificaciones cruzadas entre ellas, y atestación de claves con respaldo de hardware (cadena de certificados de la TEE, cadena de arranque verificado y boot hash) verificada en este dispositivo con el verificador oficial de Google. El informe se muestra solo en tu dispositivo; cuando el resultado es sospechoso o está comprometido, la aplicación te lo recuerda una vez por cada apertura. Que haya instalada una aplicación de gestión de root se considera un indicio, no una prueba. Ninguna comprobación por software es totalmente precisa, así que lee el informe como una orientación sobre tu dispositivo y no como una garantía. El modo desarrollador puede ocultar esta función; una vez oculta, la aplicación ya no realiza estas comprobaciones al iniciarse ni muestra el aviso.
- Modo desarrollador. Oculto tras siete toques sobre el nombre de la aplicación en Acerca de y protegido por verificación de identidad, puede levantar las restricciones de root, ocultar entradas de ajustes, exportar tu bóveda en texto plano y forzar el recifrado de la base de datos local. También ofrece un registro detallado de detección opcional: las comprobaciones de integridad no escriben ningún registro a menos que lo actives. Una exportación en texto plano contiene todos tus secretos sin cifrar — cualquiera que obtenga el archivo puede leer todas las cuentas. Usas el modo desarrollador bajo tu propio riesgo; el desarrollador no responde de las pérdidas causadas por él o por las exportaciones en texto plano.

Puedes usar Osmium para tu propia autenticación de dos factores y en los dispositivos que controlas o que estás autorizado a usar.

Osmium no tiene sistema de cuentas ni sincronización en la nube; todas las funciones de importación se ejecutan íntegramente en este dispositivo. La aplicación no hace ninguna solicitud de red al iniciarse a menos que actives la comprobación de versiones opcional. Por lo demás, solo se conecta cuando usas una función que la necesita: el servidor WebDAV que configures, la API de GitHub Releases para la comprobación de versiones opcional (desactivada por defecto; puedes activarla en Ajustes), el sitio web osmium.im para leer las versiones actuales de estos Términos de uso y de la Política de privacidad cuando las abres en Acerca de, la lista pública de estados de atestación de Google cuando actualizas los datos de revocación en la pantalla de integridad, o el otro dispositivo durante una transferencia LAN. La transferencia LAN usa cifrado AES-256-GCM derivado del código de emparejamiento y no pasa por ningún servidor en la nube de Osmium.

Las versiones actuales de estos Términos de uso y de la Política de privacidad se publican en el sitio web osmium.im, y las versiones publicadas allí son las que se aplican. La aplicación las lee cuando abres el documento desde Acerca de y conserva la copia que leyó para que puedas volver a leerla sin conexión. Si la obtención falla, la aplicación muestra un aviso de error con un enlace al sitio web.

**Idiomas:** este documento se publica en varios idiomas. La versión en inglés es el original; las traducciones se ofrecen por comodidad. Si una traducción entra en conflicto con la versión en inglés, prevalece la versión en inglés.

---

Contacto: zhif0776@hotmail.com · https://t.me/osmium2fa
