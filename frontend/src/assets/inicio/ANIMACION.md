# Abogado visual por capas — sin voz

Rig implementado en src/app/shared/legal-avatar/legal-avatar.component.html y CSS. Renderer nativo SVG, sin librerías ni runtimes descargados.

- Fuente visual: abogado-poster.webp existente. Se reutiliza mediante máscaras/clip paths para separar torso, cabeza, antebrazo y mano. El recurso bitmap completo nunca se traslada ni rota como una única capa animada.
- Cabeza: pivote independiente en el cuello; oscilación suave.
- Ojos: dos capas de párpados, parpadeo periódico.
- Sonrisa: capa local de la boca; variación sutil de la expresión, sin sincronización labial ni audio.
- Saludo: antebrazo y mano con pivotes independientes durante 3000 ms, luego estado reposo.
- Señalamiento: pose de mano con índice extendido, en abogado-senalar.webp; máscara de mano y giro de muñeca hacia el botón situado a la izquierda. La cara y el cuerpo conservan el recurso original.
- Hover/foco en Solicitar asesoría activa senalar. Mouseleave/blur vuelve a reposo.
- Movimiento reducido: sin saludo, señalamiento ni animaciones cíclicas. Reacciona también a cambios de preferencia.
- Rendimiento: pausa animaciones cuando el documento está oculto o el avatar queda fuera del viewport, donde IntersectionObserver esté disponible. Limpia temporizadores, listeners y observadores al destruirse.

No existe ningún elemento audio/video ni llamada a síntesis de voz en la implementación.

abogado-senalar.webp fue creado con ImageGen integrada de OpenAI a partir del personaje existente. Se utiliza solo su brazo/mano mediante máscaras. Se conserva el PNG original generado. El WebP local optimizado pesa 105004 bytes.

Prompt utilizado:
Edit target: the attached transparent 3D lawyer image. Change ONLY the lawyer’s extended greeting hand (the hand on the LEFT side of the canvas) into a clear index-finger pointing gesture toward the LEFT edge of the image, indicating a website button to his left. Extend the index finger horizontally left; naturally curl other fingers and thumb. Preserve exact face identity, haircut, facial expression, suit, body proportions, book labeled CÓDIGO CIVIL, book-holding hand, colors, lighting, viewpoint, framing and positions. Keep pointing wrist and forearm at exactly the same position as the original greeting wrist/forearm; do not move the arm or shoulder. Keep 1000x1000 square composition and transparent background. No text changes, no new objects, no audio, no UI. This edited pose will be clipped at the wrist and used as an independently articulated animation layer. Match original silhouette and alignment as closely as possible.
