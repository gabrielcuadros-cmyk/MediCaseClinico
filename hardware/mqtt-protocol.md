# Protocolo MQTT del estuche MediCase

Contrato entre el firmware del estuche (ESP32 con Wi-Fi) y la app Android cuando los
datos viajan por un broker MQTT. Cubre lo mismo que `ble-protocol.md`: temperatura,
batería, ubicación, cierre, accesos por teclado y órdenes al estuche.

- **Versión del protocolo:** 1
- **Transporte:** MQTT 3.1.1 sobre TCP, con TLS opcional.
- **Contenido:** JSON en UTF-8. Números con punto decimal. Los campos que no se
  conocen se ignoran, para que versiones futuras puedan añadir campos.
- **Regla general (la misma que por Bluetooth):** el estuche nunca publica un valor que
  no ha medido. Sin dato, no se publica nada o se publica el valor "sin dato" de cada
  topic. La app muestra `--` o "Sin señal GPS"; nunca debe recibir datos de prueba.

## 1. Bluetooth o MQTT

- Si la app se compila con un broker configurado, usa **solo MQTT**. Si no, usa
  Bluetooth (`ble-protocol.md`), que queda como respaldo.
- El firmware puede mantener los dos a la vez: el teclado, el servomotor, la LCD y la
  alarma funcionan igual con o sin red.

## 2. Broker y topics

La dirección, el puerto, el usuario y la contraseña del broker **no se escriben en este
documento ni en ningún archivo versionado**. En la app van en `local.properties`
(claves `MQTT_*`, ver `local.properties.example`); en el firmware, en un archivo de
configuración que tampoco se versiona.

Todos los topics cuelgan de una raíz común:

| Configuración | Raíz | Ejemplo de topic |
|---|---|---|
| Solo prefijo (broker de prueba) | `<prefijo>` | `Ana/prueba/temperatura` |
| Prefijo + id del estuche (broker propio) | `<prefijo>/<id>` | `Ana/prueba/estuche-01/temperatura` |

En este documento `<raíz>` es esa raíz común. Ni el prefijo ni el id pueden contener
`+`, `#` ni niveles vacíos.

| Topic | Publica | QoS | Retained | Sección |
|---|---|---|---|---|
| `<raíz>/estado` | Estuche (y su LWT) | 1 | sí | §4 |
| `<raíz>/temperatura` | Estuche | 0 | no | §5 |
| `<raíz>/bateria` | Estuche | 1 | sí | §6 |
| `<raíz>/ubicacion` | Estuche | 0 | no | §7 |
| `<raíz>/cierre` | Estuche | 1 | sí | §8 |
| `<raíz>/acceso` | Estuche | 1 | no | §9 |
| `<raíz>/orden/pin` | App | 1 | **nunca** | §10.2 |
| `<raíz>/orden/seguro` | App | 1 | **nunca** | §10.3 |
| `<raíz>/orden/alarma` | App | 1 | **nunca** | §10.4 |
| `<raíz>/orden/gps` | App | 1 | **nunca** | §10.5 |
| `<raíz>/orden/respuesta` | Estuche | 1 | no | §10.1 |

Por qué así:

- **Temperatura y ubicación sin retained:** llegan cada pocos segundos, y un valor
  guardado en el broker podría tener horas. La app lo tomaría como actual: una
  temperatura vieja oculta una alerta y una posición vieja dispara geocercas falsas.
  Por eso tampoco necesitan QoS 1: perder una lectura no importa, llega otra enseguida.
- **Estado, batería y cierre con retained:** cambian poco, y la app necesita conocerlos
  nada más conectarse. La app solo les hace caso mientras `estado` dice `online` (§4).
- **Órdenes nunca retained:** una orden guardada se ejecutaría otra vez en cada
  reconexión del estuche, y un PIN quedaría almacenado en el broker.

Suscripciones:

- **Estuche:** los cuatro topics de órdenes, uno por uno (`orden/pin`, `orden/seguro`,
  `orden/alarma`, `orden/gps`), con QoS 1. No usar `orden/#`: incluiría su propio
  `orden/respuesta`.
- **App:** `estado`, `temperatura`, `bateria`, `ubicacion`, `cierre`, `acceso` y
  `orden/respuesta`.

## 3. Conexión

| | Estuche | App |
|---|---|---|
| Client ID | `medicase-esp32-<MAC>` (12 dígitos hex de la MAC Wi-Fi, en minúsculas) | `medicase-app-<UUID>`, generado una vez por instalación |
| Clean session | sí | sí |
| Keep alive | 30 s | 30 s |
| Reconexión | automática, con espera creciente (1 s → 60 s) | automática, con espera creciente |

- **Los dos Client ID deben ser distintos y únicos.** El broker desconecta a un cliente
  cuando entra otro con el mismo ID: si el estuche y la app compartieran uno, se
  echarían mutuamente sin parar. Por eso ninguno de los dos se escribe a mano.
- El estuche, al conectar: registra su LWT (§4), publica `estado` = online, se suscribe
  a las órdenes y publica `cierre` y, si tiene sensor, `bateria`.

## 4. Estado — `<raíz>/estado`

QoS 1, retained. Es el equivalente a "estuche conectado" del Bluetooth.

```json
{"online": true}
```

- **LWT (Last Will):** al conectar, el estuche registra como testamento
  `{"online": false}` en este topic, QoS 1, retained. Si se queda sin batería o pierde
  el Wi-Fi, el broker lo publica solo, ~45 s después (1,5 × keep alive).
- Antes de un apagado ordenado, el estuche publica `{"online": false}` él mismo.
- La app:
  - `online: true` → "Conectado". Desde aquí acepta los demás topics.
  - `online: false`, o sin conexión con el broker → "Desconectado". Descarta lo que
    llegue de `bateria` y `cierre` (puede ser viejo) y muestra `--`.

## 5. Temperatura — `<raíz>/temperatura`

QoS 0, sin retained.

```json
{"celsius": 4.2}
```

- Una medición cada **2 a 10 segundos**, como por Bluetooth.
- El DS18B20 devuelve `−127 °C` si está desconectado y `85 °C` al encenderse.
  **No son temperaturas: en esos casos no se publica nada.**
- La app descarta valores fuera de −40…125 °C y les pone la hora de recepción (el
  estuche no tiene reloj).

## 6. Batería — `<raíz>/bateria`

QoS 1, retained. **El estuche actual no tiene sensor de batería: no publica este
topic** y la app muestra `--`. Si en el futuro se añade un sensor:

```json
{"porcentaje": 78}
```

Entero de 0 a 100. Nunca un porcentaje fijo.

## 7. Ubicación — `<raíz>/ubicacion`

QoS 0, sin retained. Frecuencia: la del intervalo del GPS (§10.5), 5 s por defecto.

Con fix del NEO-6M:

```json
{"fix": true, "lat": -16.3989, "lng": -71.5369}
```

Sin fix:

```json
{"fix": false}
```

- Grados decimales (WGS84), al menos 6 decimales.
- **Sin fix se publica `{"fix": false}`. Nunca coordenadas de prueba, ni fijas, ni la
  última posición con `fix: true`.** La app usa las coordenadas para evaluar geocercas:
  una posición falsa genera alertas falsas o las oculta.
- Hay fix cuando el NEO-6M entrega `$GPRMC` con estado `A`, o `$GPGGA` con calidad ≥ 1.
- La última ubicación conocida la guarda la propia app con su hora: el estuche no tiene
  que reenviarla.

## 8. Cierre y seguro — `<raíz>/cierre`

QoS 1, retained.

```json
{"cierre": "cerrado", "seguro": true}
```

| Campo | Valores |
|---|---|
| `cierre` | `"cerrado"` (servomotor en posición de cierre) o `"abierto"` |
| `seguro` | `true`: hay que teclear el PIN para abrir; `false`: abre sin PIN |

- Publicar **en cada cambio**: al abrir (`abierto`), cuando el servomotor se cierra solo
  a los 4 s (`cerrado`) y cuando cambia el seguro.
- Debe reflejar la posición real ordenada al servomotor, no un valor supuesto.

## 9. Acceso — `<raíz>/acceso`

QoS 1, sin retained: es un evento, no un estado.

```json
{"tipo": "denegado", "intentosFallidos": 2}
```

| `tipo` | Significado | Qué hace la app |
|---|---|---|
| `"concedido"` | PIN correcto, o apertura sin PIN con el seguro desactivado | Lo recibe; aún no lo muestra |
| `"denegado"` | PIN incorrecto | Alerta de Seguridad |
| `"bloqueo"` | Teclado bloqueado por intentos fallidos | Alerta de Seguridad |

- `intentosFallidos`: intentos fallidos seguidos hasta ahora (0–255); vuelve a 0 con un
  acceso concedido.
- **Sin hora:** la app pone la de recepción. Los eventos ocurridos sin conexión al
  broker no se guardan para después (llegarían con una hora falsa).

## 10. Órdenes de la app al estuche

La app publica en `orden/<nombre>` con QoS 1 y **sin retained**. Cada orden lleva un
`id` único (UUID) y el estuche responde siempre en `orden/respuesta` con ese `id`.

### 10.1 Respuesta — `<raíz>/orden/respuesta`

QoS 1, sin retained.

```json
{"id": "0b6f…", "ok": true}
```

```json
{"id": "0b6f…", "ok": false, "error": "pin_incorrecto"}
```

| `error` | Significado | Qué muestra la app |
|---|---|---|
| `"pin_incorrecto"` | El PIN actual no es el del estuche | "El PIN actual no es correcto." |
| `"bloqueado"` | Bloqueado por intentos fallidos | "Estuche bloqueado temporalmente." |
| `"valor_invalido"` | JSON mal formado o valor fuera de rango | "El estuche rechazó el valor." |
| `"requiere_tls"` | Orden de PIN o seguro recibida por una conexión sin TLS | "Requiere conexión segura" |

- Si en **10 segundos** no llega respuesta, la app da la orden por fallida.
- QoS 1 puede entregar una orden dos veces: el estuche recuerda el último `id` de cada
  topic y, si se repite, responde lo mismo sin ejecutarla otra vez.
- Un PIN actual incorrecto recibido por MQTT **cuenta como intento fallido**, igual que
  uno tecleado.

### 10.2 Cambio de PIN — `<raíz>/orden/pin`

```json
{"id": "0b6f…", "pinActual": "1234", "pinNuevo": "8052"}
```

PINs de 4 dígitos en texto. El PIN nuevo se guarda en NVS. **Solo con TLS** (§11).

### 10.3 Seguro — `<raíz>/orden/seguro`

```json
{"id": "0b6f…", "activar": false, "pinActual": "1234"}
```

`activar: true` exige el PIN para abrir; `false` lo quita. Se guarda en NVS y el estuche
publica `cierre` con el nuevo `seguro`. **Solo con TLS** (§11).

### 10.4 Alarma sonora — `<raíz>/orden/alarma`

```json
{"id": "0b6f…", "encender": true}
```

La alarma sigue sonando aunque el estuche pierda la conexión: sirve para encontrarlo.
Solo se apaga con `encender: false` o desde el estuche con el PIN correcto.

### 10.5 Intervalo del GPS — `<raíz>/orden/gps`

```json
{"id": "0b6f…", "segundos": 1}
```

Segundos entre dos publicaciones de `ubicacion`, de 1 a 60 (5 por defecto, 1 en Modo
Pérdida). Fuera de rango: `valor_invalido`. No se guarda en NVS.

## 11. Seguridad

- **Sin TLS todo viaja en texto plano**, y un broker sin usuario ni contraseña lo puede
  leer cualquiera que conozca el prefijo: temperatura, ubicación y accesos incluidos.
  El broker de prueba solo sirve para desarrollo: no usar datos de un paciente real.
- Por eso, **sin TLS la app no envía `orden/pin` ni `orden/seguro`** y muestra
  "Requiere conexión segura": el PIN nunca viaja en claro. El estuche aplica la misma
  regla por su lado: si está conectado sin TLS, responde a esas órdenes con
  `requiere_tls` sin ejecutarlas.
- Sin autenticación, cualquiera podría publicar `orden/alarma` u `orden/gps`. Es una
  limitación conocida del broker de prueba; en un broker propio, con usuario y
  contraseña y una ACL que deje publicar órdenes solo a la app, se resuelve.
- La app no guarda el PIN en ningún sitio: lo publica y lo olvida.
- Librería recomendada para el ESP32: **espMqttClient** (Arduino) o **esp-mqtt**
  (ESP-IDF), que publican con QoS 1 y admiten TLS y LWT. *PubSubClient* solo publica
  con QoS 0: `acceso` y `orden/respuesta` podrían perderse.

## 12. Comportamiento de la app, para probar el firmware

| Situación | Qué se ve en la app |
|---|---|
| Broker inaccesible o `estado` sin publicar | "Desconectado" |
| `estado` = `{"online": false}` (o LWT) | "Desconectado"; batería y cierre en `--` |
| `ubicacion` = `{"fix": false}` | Mapa: "Sin señal GPS" y la última ubicación conocida |
| Nunca se publica `bateria` | Batería: `--` |
| Nunca se publica `cierre` | Cierre: `--` |
| `acceso` con `denegado` o `bloqueo` | Notificación y alerta en Alertas → Seguridad |
| Temperatura fuera de 2–8 °C | Notificación y alerta de temperatura |
| Orden sin respuesta en 10 s | La acción falla con un mensaje de error |

Para probar sin el estuche: **MQTT Explorer** (o `mosquitto_pub`/`mosquitto_sub`)
permite suscribirse a `<raíz>/#` y publicar los JSON de los ejemplos. Para simular el
estado, publicar `{"online": true}` en `<raíz>/estado` con retained.

## 13. Cambios de versión

| Versión | Cambio |
|---|---|
| 1 | Primera versión: estado con LWT, telemetría, cierre, accesos y órdenes con respuesta. |
