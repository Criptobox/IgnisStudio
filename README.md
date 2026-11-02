<p align="center"><img src="docs/logo.png" width="140" alt="Ignis Studio"></p>

# Ignis Studio

Agente de diseño con modelos locales — app Android construida a partir del diseño **Ignis Studio**.

La app usa exactamente el HTML, CSS y JS del diseño original (`app/src/main/assets/`), mostrado a pantalla completa dentro de un WebView nativo, una pantalla a la vez.

## Pantallas
1. Pantalla de inicio · 2. Chat / Agente · 3. Modelos GGUF · 4. Proyecto Plantillas · 5. Resultado / Vista previa · 6. Skill Store · 7. MCP / Herramientas · 8. Biblioteca de Diseños · 9. Ajustes / Sistema · 10. Generar APK

## Funciones
- **Chat con modelos reales**: locales (llama.cpp, sin internet) o en la nube con tu clave de API. Respuesta en directo, *Detener*, historial (reloj) y nueva conversación.
- **Caja del chat**: 🖼 imagen de referencia (Ignis extrae su paleta de colores), 🎤 dictado por voz, ↗ compartir el diseño.
- **Pedidos que editan**: «cámbialo a verde», «añade precios»… envían el diseño actual al modelo para modificarlo.
- **Resultado**: vista aislada (sin scripts), código completo, *Guardar HTML* (Descargas, Drive…), *Compartir* y pantalla completa. Cada diseño queda en **Proyectos recientes** (Inicio).
- **Plantillas**: cada una es un pedido real al agente.
- **Crear app**: añade un icono en la pantalla de inicio que abre el diseño a pantalla completa.
- **Skills**: UI Components, Tailwind Style, React Ready, Android Builder, Design System, Modo oscuro, Accesibilidad y Animaciones; cada skill instalada añade instrucciones al agente.
- **Herramientas (MCP)**: revisión automática del HTML (Playwright), Figma como referencia, guardar en Drive, compartir a Mega e inspiración en Dribbble (Browser).
- **Ajustes**: Apariencia (paleta de los diseños, vibración), Almacenamiento (espacio, borrar proyectos), Rendimiento (contexto, longitud, creatividad, hilos), Avatar, Cerebro y Actualizaciones.
- Barra inferior con gestos, botón atrás de Android y «toca un logo para ponerle el oficial».

## Modelos en la nube
En **Modelos ▸ Nube** pega tu clave de API y elige el modelo de tu cuenta:

| Servicio | Dónde se consigue la clave |
|---|---|
| Claude (Anthropic) | console.anthropic.com ▸ API Keys · por defecto Claude Opus 5.5, con respaldo automático si un modelo rechaza la petición |
| ChatGPT (OpenAI) | platform.openai.com ▸ API keys |
| Gemini (Google) | aistudio.google.com ▸ Get API key |
| DeepSeek · Mistral · Grok · Perplexity | sus consolas de desarrollador |

Las claves se guardan cifradas con el almacén seguro de Android. Los pedidos y el contexto del proyecto se envían a ese servicio, que cobra según su tarifa. Copilot no tiene una API pública para apps.

## Ignis, el avatar del agente
Personaje llama que cambia según lo que hace el agente:

<p align="center"><img src="docs/ignis-estados.png" width="560" alt="Estados de Ignis"></p>

| Estado | Cuándo aparece |
|---|---|
| Listo | En espera |
| Pensando | Al enviar un pedido en el Chat |
| Diseñando | Generando, descargando un modelo o compilando la APK |
| ¡Listo! | Al terminar cualquiera de esas tareas |
| Algo falló | Al intentar un modelo no compatible |
| Descansando | Tras 1 minuto sin actividad; se despierta al tocar la pantalla |

Se ve en Inicio, en el Chat y en **Ajustes ▸ Avatar del agente**, donde se pueden previsualizar los estados.

## Biblioteca: guardados, referencia y páginas ancladas
- **Guardados**: toca el corazón en la Galería, en Plantillas, en Resultado o en el Chat.
- **Referencia**: toca un diseño ▸ *Usar como referencia*. Ignis lo usa como base en todos tus pedidos. Aparece en el Chat sobre la caja de texto y se puede cambiar o quitar desde ahí.
- **Anclados**: páginas de inspiración (Dribbble, Behance, Mobbin, Awwwards, Land-book, Pinterest) y las que añadas. Se abren dentro de la app; con **♥ Guardar** capturas lo que ves y pasa a Guardados, y con **Anclar** fijas la página.
- Al quitar una captura de Guardados también se borra del teléfono.
- **Todo queda en el cerebro** (`cerebro.json`): guardados, referencia, páginas ancladas y memoria. Al cambiar de modelo no se pierde nada, y `igContext()` arma el contexto que recibe el modelo en cada pedido.

## Actualizaciones dentro de la app
Cada cambio en `main` compila una versión nueva (`1.0.N`, donde N es el número de compilación) y la publica en **Releases**.
En la app, **Ajustes ▸ Actualizaciones** la detecta (también al abrir, como mucho cada 6 h), la descarga con barra de progreso y la instala con el aviso de Android encima de la app. No hay que salir de la pantalla.

Requisitos:
1. **Repositorio público**, para que la app pueda leer las versiones sin iniciar sesión.
2. **Firma fija**: añade en *Settings ▸ Secrets and variables ▸ Actions* los secretos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. Sin ellos, cada versión se firma distinto y Android no deja instalar una encima de otra.
3. La primera vez, Android pide permitir que Ignis Studio instale aplicaciones. Se hace desde el mismo panel y solo una vez.

Seguridad: cada versión se publica con su huella SHA-256 (`IgnisStudio-1.0.N.apk.sha256`) y la app no instala una descarga que no coincida.

## Navegación
- Barra inferior flotante con burbuja violeta que se desliza a la pestaña activa.
- **Vibración** al tocar (nativa en Android, respeta los ajustes del teléfono).
- **Desliza a los lados** para cambiar de pestaña.
- La barra **se oculta al bajar** y vuelve al subir.
- **Aviso naranja** en Chat cuando Ignis termina mientras estás en otra pantalla.
- **Mantén pulsado Crear** para un menú rápido: Diseño Web, App Android, UI/UX o plantillas.
- **Pantalla de bienvenida** con la llama del logo (SplashScreen de Android 12+, compatible desde Android 8).

## Modelos locales (llama.cpp)
La APK lleva [llama.cpp](https://github.com/ggml-org/llama.cpp) (versión fija `b12000`) compilado para ARM de 64 bits. Todo se ejecuta en el teléfono, sin internet ni cuentas.

- **Descargar**: en *Modelos*, toca ↓ en un modelo del catálogo (GGUF Q4_K_M de Hugging Face). La descarga se puede pausar (✕) y continúa donde quedó; la pantalla se mantiene encendida mientras tanto.
- **Importar .gguf**: usa cualquier modelo GGUF que ya tengas en el teléfono.
- **Usar**: toca ▶. El modelo se carga en memoria al escribir en el Chat (o al tocar ▶ otra vez) y la respuesta aparece en directo; *Detener* la corta.
- **Borrar**: toca ⋮ dos veces.
- **Resultado**: cuando Ignis entrega un diseño, el HTML se ve en *Resultado ▸ Vista* (sin scripts, aislado), *Código* lo muestra entero, *Copiar código* lo copia y *Pantalla completa* lo abre a todo el ancho.

Catálogo: Qwen3 1.7B / 4B / 8B, Qwen2.5 Coder 3B, Gemma 3 1B / 4B y Llama 3.2 3B. Con 6–8 GB de RAM, los de 1–4B son los recomendados.
El motor elige al arrancar la versión más rápida para el procesador del teléfono (ARMv8.0, 8.2 con dotprod, 8.6 con i8mm…).

## Compatibilidad de modelos
La app lee el teléfono real (RAM total y libre, espacio libre, chip, núcleos y arquitectura) y marca cada modelo:

| Etiqueta | Regla |
|---|---|
| Compatible | peso del modelo + 1 GB ≤ 60 % de la RAM |
| Justo · contexto reducido | ≤ 70 % de la RAM |
| No compatible | más que eso, o procesador sin 64 bits (no se deja descargar) |

La pestaña **Recom.** solo muestra los modelos compatibles con holgura. En la vista previa web se puede simular la RAM (4–16 GB).

## Cerebro del agente
Memoria del proyecto que no depende del modelo: guarda tus pedidos, los cambios de modelo y el historial del chat. Al cambiar de modelo, el agente sigue donde lo dejaste.
- En Android se guarda en `cerebro.json` dentro de la app (y en el almacenamiento del WebView).
- Se ve y se borra en **Ajustes ▸ Cerebro del agente**; en el Chat aparece el contador de recuerdos.

## Compilar la APK

### Con GitHub Actions (sin instalar nada)
1. Sube esta carpeta a un repositorio público en GitHub (rama `main`) y añade los secretos de firma.
2. Cada push ejecuta **Compilar y publicar APK** (también con *Run workflow*).
3. La APK queda en **Releases** como `IgnisStudio-1.0.N.apk`. Instálala una vez; las siguientes llegan desde la propia app.

### En tu PC
Abre la carpeta en Android Studio (Hedgehog o superior) y pulsa *Build ▸ Build APK(s)*, o desde la terminal con JDK 17 (el Gradle Wrapper descarga Gradle 8.9 solo). Hace falta el NDK y CMake del SDK de Android y conexión a internet la primera vez (descarga llama.cpp):

```bash
./gradlew assembleDebug
```

La versión de publicación (`assembleRelease`) pasa por R8, que quita el código y los recursos sin usar.

## Estructura
```
app/src/main/assets/index.html      # Página principal (solo enlaza los archivos de abajo)
app/src/main/assets/css/styles.css  # Estilos del diseño + modo app (una pantalla a la vez)
app/src/main/assets/js/app.js       # Pantallas, chat, cerebro, biblioteca y actualizaciones
app/src/main/assets/js/app-mode.js  # Modo app: navegación, gestos y bienvenida
app/src/main/assets/js/logos.js     # Logos oficiales por defecto (imágenes incrustadas)
app/src/main/assets/img/splash.svg  # Llama de la bienvenida en el navegador
app/src/main/assets/js/llm.js       # Modelos locales y chat en directo (usa window.IgnisLLM)
app/src/main/cpp/                   # llama.cpp (se descarga al compilar) + puente JNI ignis_llm.cpp
app/src/main/java/com/ignis/studio/LlmBridge.kt      # Descarga, importa, carga y genera con los modelos
app/src/main/java/com/ignis/studio/LlamaNative.kt    # Enlace con la biblioteca nativa
app/src/main/java/com/ignis/studio/CloudBridge.kt    # Modelos en la nube y claves cifradas
app/src/main/java/com/ignis/studio/ClaudeStream.java # Claude con el SDK oficial de Anthropic
app/src/main/java/com/ignis/studio/DesignActivity.kt # Diseños convertidos en app
app/src/main/assets/js/extras.js    # Proyectos, historial, voz, imagen, compartir, plantillas
app/src/main/assets/js/ajustes.js   # Skills y Ajustes
app/src/main/assets/js/cloud.js     # Pestaña Nube
app/src/main/assets/js/tools.js     # Crear app y herramientas MCP
app/src/main/java/com/ignis/studio/MainActivity.kt   # WebView, selector de imágenes, botón atrás
app/src/main/java/com/ignis/studio/DeviceBridge.kt   # Lee RAM, espacio, chip y arquitectura
app/src/main/java/com/ignis/studio/BrainBridge.kt    # Guarda el cerebro en cerebro.json y limpia capturas huérfanas
app/src/main/java/com/ignis/studio/HapticBridge.kt   # Vibración al tocar
app/src/main/java/com/ignis/studio/UpdateBridge.kt   # Busca, descarga e instala actualizaciones
app/src/main/java/com/ignis/studio/InspoActivity.kt  # Visor de páginas ancladas con captura
app/src/main/java/com/ignis/studio/WebBridge.kt      # Abre páginas en el visor
app/src/main/res/                   # Tema oscuro, icono con el orbe Eclipse
app/proguard-rules.pro              # Reglas de R8 (conserva los puentes con el HTML)
gradlew, gradle/wrapper/            # Gradle Wrapper (Gradle 8.9)
.github/workflows/build-apk.yml     # Compila y publica cada versión en Releases
```
