# Términos de uso

*Fecha de entrada en vigor: 23 de septiembre de 2026 · Se aplica a Osmium v2.5.3 y versiones posteriores*

*Versión actual, publicada en osmium.im y obtenida por la aplicación cada vez que se abre (Acerca de → Términos de uso).*

Al instalar o usar Osmium, aceptas los términos siguientes.

Osmium es software libre y de código abierto publicado bajo la Licencia Pública General de GNU versión 3 o posterior (GPL-3.0-or-later; consulta LICENSE/COPYING).

El software se proporciona «tal cual», sin garantía de ningún tipo. En la máxima medida permitida por la ley, el desarrollador no responde de los daños derivados de su uso.

## Tus responsabilidades:

- Protege tus secretos, PIN y contraseñas de copia. Las claves de cifrado no pueden recuperarlas ni el desarrollador ni terceros; un PIN olvidado, una contraseña de copia perdida o los datos de la aplicación borrados no los puede restaurar nadie.
- Haz tus propias copias de seguridad. Desinstalar la aplicación o borrar sus datos elimina de forma permanente las cuentas y los ajustes locales. Las copias automáticas dependen de la programación de Android y del fabricante, y pueden retrasarse o bloquearse por las políticas de ahorro de energía.
- Importa solo archivos de confianza. Osmium importa desde códigos QR de transferencia de Google Authenticator y desde archivos de exportación de Aegis, 2FAS y Raivo OTP. Revisa cada entrada en la vista previa antes de importar, confirma que el archivo procede realmente del autenticador que piensas dejar y elimina los archivos de exportación después de usarlos — contienen tus secretos en texto plano.
- Revisa los datos recibidos. La transferencia LAN rápida requiere ambos dispositivos en la misma red Wi-Fi: el dispositivo receptor muestra un código de emparejamiento de 12 dígitos que el emisor debe introducir antes de que se transfiera nada; comparte el código solo con el dispositivo desde el que envías y confirma las cuentas en la vista previa de importación.
- El PIN de autodestrucción es irreversible. Mantén precisa la hora del dispositivo y cumple la legislación aplicable y las normas de red de los servidores y dispositivos que configures.
- Dispositivos con root. Cuando Osmium detecta que el dispositivo tiene root, activa forzosamente sus protecciones (verificación al abrir la aplicación, bloqueo de capturas de pantalla, códigos ocultos) y desactiva la transferencia LAN rápida, las copias de seguridad por WebDAV, las copias automáticas, la configuración de autodestrucción y la importación desde terceros. Estas restricciones solo pueden levantarse en el modo desarrollador. La detección de root se ejecuta localmente en el dispositivo y es de mejor esfuerzo.
- Informe de integridad del dispositivo. Al iniciarse, Osmium también comprueba localmente la integridad del dispositivo: comprobaciones del sistema, de los montajes, del kernel, del estado de arranque y de la firma de la aplicación, verificaciones cruzadas entre ellas, y atestación de claves con respaldo de hardware (cadena de certificados de la TEE, cadena de arranque verificado y boot hash) verificada en este dispositivo con el verificador oficial de Google. El informe se muestra solo en tu dispositivo; cuando el resultado es sospechoso o está comprometido, la aplicación te lo recuerda una vez por cada apertura. Que haya instalada una aplicación de gestión de root se considera un indicio, no una prueba. Ninguna comprobación por software es totalmente precisa, así que interpreta el informe como una orientación sobre tu dispositivo y no como una garantía. El modo desarrollador permite ocultar esta función; una vez oculta, la aplicación ya no realiza estas comprobaciones al iniciarse ni muestra el aviso.
- Modo desarrollador. Oculto detrás de siete toques sobre el nombre de la aplicación en Acerca de y protegido por verificación de identidad, puede levantar las restricciones de root, ocultar entradas de ajustes, permitir códigos de 4, 5 y 7 dígitos, exportar tu bóveda en texto plano y forzar el recifrado de la base de datos local. También ofrece un registro detallado de detección opcional: las comprobaciones de integridad no escriben ningún registro a menos que lo actives. Una exportación en texto plano contiene todos tus secretos sin cifrar — cualquiera que obtenga el archivo puede leer todas las cuentas. Usas el modo desarrollador bajo tu propio riesgo; el desarrollador no responde de las pérdidas causadas por su uso o por las exportaciones en texto plano.

Puedes usar Osmium para tu propia autenticación de dos factores y en los dispositivos que controlas o que estás autorizado a usar.

Osmium no tiene sistema de cuentas ni sincronización en la nube; todas las funciones de importación se ejecutan íntegramente en este dispositivo. La aplicación puede conectarse al servidor WebDAV que configures, a la API de GitHub Releases para comprobaciones de versiones (activadas por defecto y desactivables en Ajustes), al sitio web osmium.im para obtener las versiones más recientes de estos Términos de uso y de la Política de privacidad, a la lista pública de estados de atestación de Google cuando actualizas los datos de revocación en la pantalla de integridad, o al otro dispositivo durante una transferencia LAN. La transferencia LAN usa cifrado AES-256-GCM derivado del código de emparejamiento y no pasa por ningún servidor en la nube de Osmium.

Las versiones actuales de estos Términos de uso y de la Política de privacidad se publican en el sitio web osmium.im. La aplicación obtiene ambas cada vez que se abre para que puedas leer la versión vigente; si la obtención falla, la aplicación muestra un aviso de error con un enlace al sitio web. Las versiones publicadas en osmium.im son las que se aplican.

---

Contacto: zhif0776@hotmail.com · https://t.me/osmium2fa
