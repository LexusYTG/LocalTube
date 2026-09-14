# LocalTube

**Reproductor de video local para Android — sin publicidad, sin internet, sin rastreo.**

---

## ¿Qué es LocalTube?

LocalTube es una aplicación Android de código abierto (GPL v3) diseñada para **organizar y reproducir tu propia colección de videos almacenados localmente** en el dispositivo o en almacenamiento externo (tarjeta SD, USB OTG). Funciona de manera completamente autónoma: no requiere conexión a Internet, no tiene cuenta de usuario, no envía ningún dato a ningún servidor.

La interfaz está inspirada en plataformas de streaming modernas, pero toda la lógica opera sobre archivos que ya están en tu dispositivo.

---

## ¿Qué hace?

### Organización del contenido
- Escanea una **carpeta raíz** que tú eliges y construye un índice local de todos tus videos.
- Organiza el contenido automáticamente en **canales** (subcarpetas), **series** (temporadas/episodios), **películas** y **shorts** (videos cortos).
- Genera miniaturas a partir de los propios archivos de video.
- Admite reindexado incremental: solo procesa lo que cambió desde el último escaneo.

### Reproducción
- Reproductor de video integrado con controles completos: play/pausa, retroceso/avance de 10 segundos, barra de progreso, volumen.
- **Velocidad de reproducción ajustable** (múltiples velocidades).
- **Picture-in-Picture (PiP)** en dispositivos con Android 8 o superior.
- Reproducción en segundo plano mediante servicio de notificación.
- Gestos táctiles sobre el video para buscar posición.
- Reanudación automática desde el punto donde se dejó cada video.

### Navegación
- Pantalla de inicio con feed mixto de contenido recomendado (videos, series, películas, shorts).
- Motor de recomendaciones basado en historial de reproducción local.
- Sección dedicada a **Shorts** (videos cortos en formato vertical).
- Sección dedicada a **Películas**.
- Sección dedicada a **Series** con agrupación por temporada y episodio.
- Vista de **Canal** con listado de todo el contenido de una carpeta/creador.
- **Historial** de reproducción.
- **Búsqueda** por título sobre el índice local.

### Configuración
- Selector de carpeta raíz desde el explorador de archivos del sistema.
- Selección de idioma de la interfaz.
- Opción para limpiar y reconstruir el índice completo.

---

## ¿Qué NO hace?

| Función | Estado |
|---|---|
| Acceso a Internet | ✗ No tiene |
| Streaming de video en línea | ✗ No soportado |
| Descarga de videos | ✗ No incluida |
| Sincronización con servicios en la nube | ✗ No incluida |
| Reproducción de DRM / contenido protegido por Anthropic | ✗ No compatible |
| Inicio de sesión o cuenta de usuario | ✗ No existe |
| Publicidad | ✗ No contiene |
| Rastreo, telemetría o analytics | ✗ No existe |
| Subtítulos externos (.srt, .vtt, etc.) | ✗ No implementado en esta versión |
| Soporte para formatos de contenedor no nativos de Android | Depende del dispositivo |

---

## ¿Cómo se usa?

### Primera vez

1. **Instalar la app** en un dispositivo Android (se requieren permisos de almacenamiento).
2. Abrir la app y, cuando lo solicite, **conceder el permiso de acceso al almacenamiento** (en Android 11+ se solicita acceso completo a archivos).
3. Ir a **Ajustes** (icono de engranaje) → **Carpeta raíz** → pulsar **Elegir carpeta** y seleccionar la carpeta donde están almacenados tus videos.
4. La app ejecutará un **escaneo automático** de esa carpeta y construirá el índice local.
5. Cuando el escaneo termine, el contenido aparecerá en la pantalla de inicio.

### Estructura de carpetas esperada

```
/Tu carpeta raíz/
└── Catalogo/
    ├── NombreDeCanal1/
    │   ├── video1.mp4
    │   └── video2.mp4
    ├── NombreDeCanal2/
    │   ├── Serie A/
    │   │   ├── S01E01.mp4
    │   │   └── S01E02.mp4
    │   └── pelicula.mp4
    └── ...
```

Los canales corresponden a **subcarpetas directas** dentro de `Catalogo/`. Las series son subcarpetas dentro de un canal. Los videos sueltos se tratan como videos normales o películas según su configuración.

### Uso diario

- Navega entre las pestañas **Inicio**, **Shorts**, **Series** y **Películas** desde la barra inferior.
- Pulsa cualquier video para reproducirlo.
- Usa el **mini reproductor** para seguir navegando mientras un video sigue en segundo plano.
- Activa **PiP** con el botón correspondiente en el reproductor para reducir la ventana del video mientras usas otras apps.
- Consulta el **Historial** para retomar videos que hayas visto parcialmente.
- Usa la **Búsqueda** (lupa) para encontrar un video por nombre.

---

## Contexto de uso contemplado

LocalTube está pensado para usuarios que:

- Disponen de una **colección personal de videos** (grabaciones propias, material educativo sin restricciones, producciones independientes, etc.) almacenada en el dispositivo o en almacenamiento externo.
- Quieren una interfaz **organizada y cómoda** para consumir ese contenido en Android, similar a la de una plataforma de streaming, pero completamente offline.
- Valoran la **privacidad total**: ningún dato de reproducción sale del dispositivo.
- Usan el dispositivo en **entornos sin conectividad** (zonas rurales, viajes, redes restringidas, uso offline deliberado).
- Desean una alternativa libre de publicidad y sin dependencias de servicios externos.

No está orientado al consumo de contenido en streaming, ni como sustituto de plataformas de video en línea.

---

## Aviso legal sobre contenido

> **LocalTube no incluye, distribuye, descarga ni facilita acceso a ningún contenido de video.**
>
> La aplicación es exclusivamente un **reproductor e indexador de archivos locales**. Toda la responsabilidad sobre los archivos reproducidos recae en el usuario. El desarrollador no proporciona fuentes de contenido, no tiene afiliación con ninguna plataforma de distribución de video, y no es responsable del uso que se haga de la aplicación con material protegido por derechos de autor.
>
> **Queda estrictamente prohibido el uso de LocalTube para reproducir o distribuir contenido con derechos de autor sin la autorización expresa del titular de esos derechos.**

---

## Licencia

LocalTube se distribuye bajo los términos de la **GNU General Public License v3.0**.
Copyright © 2026 LexusYTG — consulta el archivo `LICENSE` para más información.
