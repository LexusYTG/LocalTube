LocalTube

Reproductor de video local para Android — sin publicidad, sin internet, sin rastreo.

---

¿Qué es?

Aplicación Android de código abierto (GPL v3) para organizar y reproducir tu propia colección de videos almacenados en el dispositivo o en almacenamiento externo (SD, USB OTG). Funciona totalmente offline: sin cuenta, sin telemetría, sin servidores.

La interfaz está inspirada en plataformas de streaming modernas, pero opera solo sobre archivos locales.

---

Funciones

Organización

· Escanea una carpeta raíz elegida por ti y construye un índice local.
· Clasifica automáticamente en canales, series, películas y shorts.
· Genera miniaturas desde los propios videos.
· Reindexado incremental: solo procesa lo que cambió.

Reproducción

· Reproductor integrado: play/pausa, ±10 s, barra de progreso, volumen.
· Velocidad de reproducción ajustable.
· Picture-in-Picture (Android 8+).
· Reproducción en segundo plano.
· Gestos táctiles para buscar posición.
· Reanudación automática desde el último punto.

Navegación

· Inicio con feed mixto y recomendaciones basadas en tu historial local.
· Pestañas dedicadas a Shorts, Series (agrupadas por temporada/episodio) y Películas.
· Vista de Canal con todo el contenido de una carpeta.
· Historial y búsqueda por título.

Configuración

· Selector de carpeta raíz.
· Idioma de la interfaz.
· Limpiar y reconstruir el índice.

---

Limitaciones

· Sin subtítulos externos (.srt, .vtt).
· Sin DRM.
· El soporte de contenedores no nativos depende del dispositivo.

---

Uso

Primera vez

1. Instalar y conceder permiso de almacenamiento.
2. Ajustes → Carpeta raíz → Elegir carpeta.
3. La app escanea y construye el índice automáticamente.

Estructura esperada

```
/Tu carpeta raíz/
└── Catalogo/
    ├── Canal1/
    │   ├── video1.mp4
    │   └── video2.mp4
    └── Canal2/
        ├── Serie A/
        │   ├── S01E01.mp4
        │   └── S01E02.mp4
        └── pelicula.mp4
```

Canales = subcarpetas dentro de Catalogo/. Series = subcarpetas dentro de un canal.

Uso diario

· Navega con la barra inferior (Inicio, Shorts, Series, Películas).
· Pulsa un video para reproducir; el mini reproductor mantiene la reproducción en segundo plano.
· Activa PiP desde el reproductor para seguir viendo mientras usas otras apps.
· Usa Historial o Búsqueda (lupa) para retomar o encontrar videos.

---

Licencia

GNU General Public License v3.0.

Copyright © 2026 LexusYTG — leonpackpro@gmail.com

---
