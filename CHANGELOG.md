# Cambios

## 3.6
- **App completa en inglés y español**: también la notificación, los widgets, el mosaico, los
  mensajes del fondo, la calibración y las explicaciones del equipo y del ajuste automático.
- **Copia de seguridad**: guarda y restaura en un .zip tus perfiles, preajustes, skins importados,
  foto del fondo y opciones.
- **Accesibilidad**: TalkBack lee cada banda («Banda de 125 Hz, +3 dB») y la ajusta de a 0,5 dB, lee
  las curvas y las vistas previas, y la comparación A/B se alterna con un toque doble.

## 3.5
- **Menos batería en el fondo animado**: 30 o 60 cuadros por segundo de verdad también en pantallas
  de 90 y 120 Hz, y el fondo deja de redibujarse solo cuando no suena nada (vuelve con la música, al
  tocar el skin o si cambia algo que se ve; un título largo se sigue desplazando).
- **Guía de inicio**: la primera vez, los permisos en orden con para qué sirve cada uno y una marca
  cuando ya está; se vuelve a abrir desde Créditos.

## 3.4.2
- El espectro detrás de la curva del ecualizador del skin pasa a ser una opción, apagada por
  defecto: como en Winamp, la curva va sola.

## 3.4.1
- **Limitador más firme**: actúa desde −2 dBFS, 30:1 y en medio milisegundo; antes (−1 dBFS, 10:1,
  1 ms) dejaba pasar picos que se recortaban.
- **Protección del altavoz del teléfono** (activa por defecto): no sube los graves bajo 125 Hz que el
  altavoz no puede dar, limita el volumen extra a +2 dB y usa protección «Máxima».

## 3.4
- **Barra (shade)**: la ventana plegada de Winamp como modo del fondo y como widget de una fila, con
  el mini visor (5 filas, como el de Winamp), el reloj con la letra del skin, los mini botones y la
  barra de posición.
- Los skins importados en la 3.0–3.3 no piden reimportarse: usan la barra plegada del skin base
  hasta rearmarse solos desde su .wsz.
- Arreglos de la revisión: el doble toque rápido ya cuenta, la carátula se actualiza aunque llegue
  después del título, y menos trabajo por cuadro en el fondo y en los widgets.
- **El ecualizador del skin se mueve con la música**: el espectro de lo que suena aparece detrás de
  la curva, con los colores del analizador del skin.
- **Carátula**: la portada de lo que suena (si el reproductor la entrega) en la app y en la
  notificación, y opción de usarla desenfocada como fondo de pantalla.

## 3.3.1
- **Toques en el fondo**: doble toque (nuevo, por defecto), un toque o apagados. Android le pasa al
  fondo también los toques de los íconos y del menú de apps sin decir cuáles son; con doble toque,
  los botones del skin solo responden si se tocan dos veces seguidas en el mismo lugar.

## 3.3
- **Curvas medidas de AutoEQ** para 48 audífonos Bluetooth populares (Sony, AirPods Pro 2/3/Max, Galaxy Buds,
  Bose, JBL, Sennheiser, Beats, Pixel Buds, Nothing, Soundcore, Jabra, Audio-Technica…). Se
  reconocen por el nombre Bluetooth y las usan el ajuste automático y «Crear perfil fijo».
- **PRESETS del skin funciona**: en Winamp completo pasa al preajuste siguiente.
- **Importar preajustes de Winamp** (.eqf y winamp.q1) como preajustes propios.
- **13 preajustes nuevos**: Electrónica, Hip-hop, Reggaetón, Metal, Jazz, Acústica, R&B, Latina,
  Cine, Noche, Menos graves, Agudos extra y Parlante pequeño.
- **Widget del reproductor** con la ventana del skin, lo que suena y sus botones.
- **Skin al azar**: cada día o cada vez que se enciende la pantalla.
- **Sonido envolvente** (Virtualizer de Android) en los teléfonos que lo permiten en todo el audio.
- **Aviso de versión nueva** sin usar Internet: botón para buscarla, avisos con Obtainium y un
  recordatorio cuando la versión instalada tiene más de 45 días.

## 3.2.1
- Los nombres de los equipos del perfil (altavoz del teléfono, audífonos, parlante, auto, TV…) se
  muestran en inglés o español según el idioma del teléfono; los Bluetooth conservan su nombre.

## 3.2
- Asistente **Varios parlantes**: revisa tus parlantes vinculados y tu teléfono y te lleva a lo que
  funciona: el modo fiesta de la marca si tienes dos de la misma (Sony Party Connect, JBL PartyBoost,
  UE PartyUp, Soundcore PartyCast, Marshall Stack Mode…), Compartir audio (Auracast) si el teléfono
  transmite LE Audio, Audio dual en Samsung o un cable AUX. Android no deja que una app una
  parlantes de distintas marcas por sí sola.

## 3.1
- El menú de la app sigue el idioma del teléfono: inglés por defecto y español si el teléfono está
  en español (en Android 13+ también se elige en Ajustes › Idioma de las apps). Los skins no cambian;
  la notificación, el widget, el fondo y las explicaciones técnicas siguen en español por ahora.

## 3.0.1
- La lista de reproducción del skin completo usa la letra del propio skin (TEXT.BMP), teñida con
  los colores de su lista; también en skins con letra con sombra o degradado.
- Fondo multitáctil: cada dedo cuenta por separado y ya no se pierden toques seguidos.
- Con el cajón de apps abierto los toques ya no llegan al reproductor (por el zoom del fondo en
  Android 11+ o, si el launcher no lo usa, tras deslizar hacia arriba hasta 5 s sin tocar). Si el
  launcher avisa de los toques en zonas libres, solo se usan esos.

## 3.0
- **Winamp completo** en el fondo: el skin hacia abajo con el reproductor, el ecualizador (con tus
  bandas reales: se tocan para cambiarlas, ON lo enciende y AUTO activa el ajuste automático) y la
  lista de reproducción con la cola del reproductor (Spotify y otros); tocar una canción salta a ella.
- **Ajuste automático sin micrófono**: la curva sigue al equipo conectado, a lo que suena (música,
  música con graves, acústica, voz o video, por el género o la app) y al volumen (más graves y
  agudos a poco volumen; protege parlantes chicos y oídos a mucho volumen).
- Reconoce audífonos y parlantes Bluetooth LE Audio y la TV por HDMI.
- Créditos y botón de Ko-fi dentro de la app.
- El botón de aleatorio (SHUFFLE) se ve encendido en todos los skins.

## 2.9.4
- El reproductor de la barra sale de «Silenciadas» (canal de prioridad normal, sin sonido) y su vista
  compacta es más limpia: título, artista y tiempo con los botones del skin.

## 2.9.3
- Los botones del fondo de pantalla responden a toques directos (launchers que no envían toques al
  fondo, como el de vivo).
- Se pide el permiso de notificaciones para el reproductor de la barra.

## 2.9.2
- «Solo el espectro» vuelve al marco simple, ahora con lo que suena.

## 2.9.1
- Reconexión del servicio de «lo que suena».

## 2.9
- Reproductor funcional: botones, barra de posición y volumen del skin.
- Reproductor en la barra de notificaciones con la ventana del skin.

## 2.8
- Lo que suena en vivo en la ventana del skin; botones del skin y mejor «Solo el espectro».

## 2.7
- Solo skins clásicos; el fondo puede mostrar el reproductor completo o solo el espectro.

## 2.6
- Ventana real de los skins de Winamp, número de barras y textos completos.

## 2.5.1
- Subir los graves ya no resta volumen; el espectro ya no falla al iniciar.

## 2.4
- El fondo animado ya no baja la resolución de la foto.
