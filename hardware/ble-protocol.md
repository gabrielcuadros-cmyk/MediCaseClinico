# Protocolo BLE del estuche MediCase

Contrato entre el firmware del estuche (ESP32) y la app Android. La app ya implementa
este documento; el firmware debe cumplirlo para que cada función se active.

- **Versión del protocolo:** 1
- **Rol:** el estuche es periférico (servidor GATT); el teléfono es central (cliente).
- **Orden de bytes:** little-endian en todos los campos de más de un byte.
- **Regla general:** el estuche nunca envía un valor que no ha medido. Si un sensor no
  tiene dato, se envía el valor "sin dato" que indica cada característica, o no se
  envía nada. La app muestra `--` o "Sin señal GPS"; nunca debe recibir datos de prueba.

## 1. Hardware del estuche

| Componente | Uso | Dónde aparece en el protocolo |
|---|---|---|
| DS18B20 | Temperatura de la insulina | Health Thermometer (§3) |
| GPS NEO-6M | Posición del estuche | Location and Navigation (§4) |
| Teclado 4×4 | PIN de 4 dígitos | Eventos de acceso (§6.4) |
| Servomotor | Cierre electrónico | Estado del cierre (§6.1) |
| Zumbador | Alarma sonora del Modo Pérdida | Alarma (§6.5) |
| LCD y LEDs | Interfaz local del estuche | Sin característica propia |
| Batería | **No hay sensor** | No se expone (§5) |

## 2. Anuncio (advertising) y conexión

- El anuncio **debe incluir el UUID del servicio Health Thermometer (`0x1809`)**: la app
  filtra el escaneo por ese UUID. Sin él, la app no encuentra el estuche.
- Nombre recomendado: `MediCase`.
- Solo se necesita una conexión simultánea.
- Al desconectarse el teléfono, el estuche vuelve a anunciarse.
- Todo lo que el estuche hace por sí solo (medir, abrir con el teclado, cerrar el
  servomotor) sigue funcionando sin teléfono conectado.

## 3. Temperatura — Health Thermometer (estándar SIG)

| | UUID | Propiedades |
|---|---|---|
| Servicio | `0x1809` | |
| Temperature Measurement | `0x2A1C` | Indicate |

Trama de 5 bytes:

| Byte | Campo | Valor |
|---|---|---|
| 0 | Banderas | `0x00` = grados Celsius, sin marca de tiempo ni tipo |
| 1–3 | Mantisa | entero con signo de 24 bits |
| 4 | Exponente | entero con signo de 8 bits |

`temperatura = mantisa × 10^exponente` (IEEE-11073 FLOAT de 32 bits).

Ejemplos:

| Temperatura | Mantisa | Exponente | Bytes |
|---|---|---|---|
| 4.2 °C | 42 | −1 | `00 2A 00 00 FF` |
| 8.4 °C | 84 | −1 | `00 54 00 00 FF` |
| −1.5 °C | −15 | −1 | `00 F1 FF FF FF` |

Reglas del firmware:

- Enviar una medición cada **2 a 10 segundos**.
- El DS18B20 devuelve `−127 °C` si está desconectado y `85 °C` justo al encenderse.
  **No son temperaturas**: en esos casos enviar NaN (`00 FF FF 7F 00`, mantisa
  `0x7FFFFF`) o no enviar nada. La app descarta NaN y todo valor fuera de −40…125 °C.
- La app guarda una lectura cada 10 s como máximo, o al instante si la temperatura
  cambia de estado (seguro / precaución / crítico).

## 4. Ubicación — Location and Navigation (estándar SIG)

| | UUID | Propiedades |
|---|---|---|
| Servicio | `0x1819` | |
| Location and Speed | `0x2A67` | Notify |

El servicio es **opcional**: sin él la app funciona y muestra "Estuche sin GPS".

Trama con posición (10 bytes):

| Bytes | Campo | Valor |
|---|---|---|
| 0–1 | Banderas (uint16) | `0x0084` = posición presente (bit 2) + estado "Position Ok" (bits 7–8 = 1) |
| 2–5 | Latitud (sint32) | grados × 10⁷ |
| 6–9 | Longitud (sint32) | grados × 10⁷ |

Ejemplo, Plaza de Armas de Arequipa (−16.3989, −71.5369):
`84 00 F8 B9 39 F6 D8 55 5C D5`

Trama **sin señal GPS** (2 bytes): `00 00` (sin posición, estado "No Position").

Reglas del firmware:

- **Sin fix del NEO-6M se envía `00 00`. Nunca coordenadas de prueba, ni fijas, ni la
  última posición con estado "Position Ok".** La app usa las coordenadas para evaluar
  geocercas y alertar: una posición falsa genera alertas falsas o las oculta.
- Se considera fix cuando el NEO-6M entrega una sentencia válida (`$GPRMC` con estado
  `A`, o `$GPGGA` con calidad de fix ≥ 1).
- Los bits 7–8 de las banderas son el estado de la posición: `0` sin posición, `1` Ok,
  `2` estimada, `3` última conocida. La app solo acepta `1` y `2`; `3` lo trata como sin
  señal, porque la última ubicación conocida la guarda la propia app con su hora.
- Si se añaden velocidad (bit 0) o distancia total (bit 1), van **antes** de la
  posición, en ese orden, como manda el estándar. La app las salta.
- Frecuencia de envío: la que fije la característica "Intervalo del GPS" (§6.6);
  por defecto, una trama cada 5 segundos.

## 5. Batería — no implementada

El estuche no tiene sensor de batería. **No se debe exponer el servicio Battery
(`0x180F`) ni enviar un porcentaje fijo.** Sin ese servicio la app muestra `--`.

Si en el futuro se añade un sensor: servicio `0x180F`, característica Battery Level
`0x2A19` (Read y Notify), un byte de 0 a 100. La app ya lo entiende.

## 6. Servicio propio "MediCase Control"

Lo que el estándar SIG no cubre: el cierre, el PIN, los accesos por teclado, la alarma
y la frecuencia del GPS. Es **opcional**: sin él la app muestra esas funciones
desactivadas con "Requiere un estuche compatible".

UUID base: `4D43xxxx-4D45-4449-4341-534500000000`

| Característica | UUID | Propiedades | Tamaño |
|---|---|---|---|
| (Servicio) | `4D430001-4D45-4449-4341-534500000000` | | |
| Estado del cierre | `4D430002-4D45-4449-4341-534500000000` | Read, Notify | 1 byte |
| Seguro (PIN requerido) | `4D430003-4D45-4449-4341-534500000000` | Read, Write (cifrada) | 1 / 5 bytes |
| Cambio de PIN | `4D430004-4D45-4449-4341-534500000000` | Write (cifrada) | 8 bytes |
| Evento de acceso | `4D430005-4D45-4449-4341-534500000000` | Notify | 2 bytes |
| Alarma sonora | `4D430006-4D45-4449-4341-534500000000` | Read, Write (cifrada) | 1 byte |
| Intervalo del GPS | `4D430007-4D45-4449-4341-534500000000` | Read, Write (cifrada) | 1 byte |

Todas las escrituras son **con respuesta** (Write Request, no Write Without Response):
la app necesita saber si el estuche aceptó la orden.

### 6.0 Seguridad de las escrituras

- Toda escritura exige **enlace cifrado**, es decir, que el teléfono esté emparejado
  (bonding) con el estuche. En ESP32: permiso `ESP_GATT_PERM_WRITE_ENCRYPTED`.
- Si el teléfono no está emparejado, el estuche responde `Insufficient Authentication`
  (`0x05`) o `Insufficient Encryption` (`0x0F`). Android abre entonces el diálogo de
  emparejamiento y la app pide al usuario repetir la acción.
- Recomendado: emparejamiento con clave mostrada en la LCD (passkey de 6 dígitos,
  `ESP_GATT_PERM_WRITE_ENC_MITM`), para que solo quien tiene el estuche en la mano
  pueda emparejarse. Como mínimo, "Just Works".
- Las lecturas y las notificaciones no exigen cifrado.
- Cambiar el PIN o el seguro exige además el **PIN actual** dentro de la trama: estar
  emparejado no basta para quitarle la protección al estuche.

Códigos de error de aplicación (respuesta de error ATT a una escritura):

| Código | Significado | Qué muestra la app |
|---|---|---|
| `0x80` | PIN actual incorrecto | "El PIN actual no es correcto." |
| `0x81` | Trama mal formada o valor fuera de rango | "El estuche rechazó el valor." |
| `0x82` | Bloqueado por intentos fallidos (ver §6.4) | "Estuche bloqueado temporalmente." |

Un PIN actual incorrecto recibido por Bluetooth **cuenta como intento fallido**, igual
que uno tecleado: si no, el PIN se podría adivinar por Bluetooth sin límite.

### 6.1 Estado del cierre (`…0002`) — Read, Notify

| Byte 0 | Significado |
|---|---|
| `0x00` | Cerrado (servomotor en posición de cierre) |
| `0x01` | Abierto |

- Notificar **cada cambio**: al abrir con el PIN correcto (`01`) y cuando el servomotor
  se cierra solo a los 4 segundos (`00`).
- Debe reflejar la posición real ordenada al servomotor, no un valor supuesto.
- La app lee el valor al conectar y después se guía por las notificaciones.

### 6.2 Seguro — PIN requerido (`…0003`) — Read, Write

Lectura, 1 byte:

| Byte 0 | Significado |
|---|---|
| `0x00` | Seguro desactivado: el estuche abre sin pedir PIN |
| `0x01` | Seguro activado: hay que teclear el PIN para abrir |

Escritura, 5 bytes:

| Byte | Campo |
|---|---|
| 0 | Nuevo estado: `0x00` desactivar, `0x01` activar |
| 1–4 | PIN actual, 4 dígitos ASCII (`'0'`–`'9'`, `0x30`–`0x39`) |

Ejemplo, desactivar el seguro con el PIN `1234`: `00 31 32 33 34`

- El valor se guarda en memoria no volátil (NVS): sobrevive a un reinicio.
- Con el seguro desactivado, la tecla de abrir acciona el servomotor sin pedir PIN
  (se propone la tecla `#`; es decisión del firmware).

### 6.3 Cambio de PIN (`…0004`) — Write

8 bytes:

| Bytes | Campo |
|---|---|
| 0–3 | PIN actual, 4 dígitos ASCII |
| 4–7 | PIN nuevo, 4 dígitos ASCII |

Ejemplo, cambiar de `1234` a `8052`: `31 32 33 34 38 30 35 32`

- El PIN nuevo se guarda en NVS. **El PIN de fábrica `1234` debe poder cambiarse**:
  hoy está fijo en el código.
- La característica **no se puede leer**: el PIN nunca sale del estuche.
- La app no guarda el PIN en ningún sitio (ni en el teléfono ni en la nube): lo envía
  y lo olvida. Si el usuario lo pierde, hay que restablecer el estuche físicamente.

### 6.4 Evento de acceso (`…0005`) — Notify

Lo que ocurre en el teclado del estuche. 2 bytes:

| Byte | Campo |
|---|---|
| 0 | Tipo de evento |
| 1 | Intentos fallidos consecutivos hasta ahora (0–255) |

| Tipo | Significado | Qué hace la app |
|---|---|---|
| `0x01` | Acceso concedido (PIN correcto, o apertura sin PIN con el seguro desactivado) | Lo recibe; aún no lo muestra |
| `0x02` | Acceso denegado (PIN incorrecto) | Alerta de Seguridad |
| `0x03` | Bloqueo por intentos fallidos | Alerta de Seguridad |

- El contador de fallidos vuelve a 0 con un acceso concedido.
- Se propone bloquear el teclado 30 segundos tras 3 fallos seguidos, y enviar `0x03`.
- **La trama no lleva hora**: el estuche no tiene reloj. La app le pone la hora en que
  la recibe. Por eso los eventos ocurridos sin teléfono conectado no se registran; no
  hay que guardarlos para enviarlos después (llegarían con una hora falsa).

### 6.5 Alarma sonora (`…0006`) — Read, Write

| Byte 0 | Significado |
|---|---|
| `0x00` | Zumbador apagado |
| `0x01` | Zumbador sonando sin parar (Modo Pérdida) |

- La alarma sigue sonando aunque el teléfono se desconecte: sirve para encontrar el
  estuche. Solo se apaga escribiendo `00` o desde el propio estuche con el PIN correcto.
- El zumbador puede seguir usándose para los pitidos del teclado; la alarma tiene
  prioridad.

### 6.6 Intervalo del GPS (`…0007`) — Read, Write

Segundos entre dos tramas de Location and Speed (§4). 1 byte, de `1` a `60`.

| Valor | Uso |
|---|---|
| `5` | Por defecto |
| `1` | Modo Pérdida: máxima frecuencia |

- Un valor fuera de 1–60 se rechaza con el error `0x81`.
- No hace falta guardarlo en NVS: al reiniciar vuelve a 5.

## 7. Comportamiento de la app, para probar el firmware

| Situación | Qué se ve en la app |
|---|---|
| Sin servicio `0x1809` en el anuncio | No encuentra el estuche |
| Sin Location and Navigation | Mapa: "Estuche sin GPS" |
| Trama `00 00` en Location and Speed | Mapa: "Sin señal GPS" y la última ubicación conocida |
| Sin servicio Battery | Batería: `--` |
| Sin servicio MediCase Control | Seguridad: "Requiere un estuche compatible" |
| Escritura sin emparejar | Android pide emparejar; la app pide repetir la acción |
| Evento de acceso `0x02` o `0x03` | Notificación y alerta en la pestaña Alertas → Seguridad |
| Temperatura fuera de 2–8 °C | Notificación y alerta de temperatura |

Para probar sin la app: **nRF Connect** permite ver cada característica, activar las
notificaciones y escribir las tramas de los ejemplos.

## 8. Cambios de versión

| Versión | Cambio |
|---|---|
| 1 | Primera versión: temperatura, ubicación y servicio MediCase Control. |
