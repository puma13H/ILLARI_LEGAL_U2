# Recursos de la portada

Imágenes creadas mediante ImageGen integrada de OpenAI para ILLARI LEGAL. La referencia del usuario guió la composición del abogado. El fondo reutiliza la ilustración del Palacio generada previamente en el proyecto. No se descargaron imágenes de terceros.

- abogado-poster.webp: 115048 bytes. Póster estático transparente; no es un rig ni una animación.
- especialidades.webp: 181268 bytes. Atlas de seis escenas (3 columnas × 2 filas), utilizado mediante background-position; no seis descargas distintas.
- palacio.webp: 200356 bytes. Versión optimizada del recurso del login; no se altera el PNG original.

Se usó Pillow únicamente para redimensionado/compresión WebP de los recursos generados, conservando transparencia y sin sustituir el trabajo de generación de imágenes. Los tres WebP suman 496672 bytes. Imágenes diferidas en avatar pequeño; prioridad alta en el abogado principal. No se solicita runtime de animación mientras enabled sea false.

Prompts de creación:

1. Use case stylized-concept. Create a static transparent PNG website illustration closely matching the male lawyer in the supplied reference: friendly handsome young Peruvian lawyer, coiffed dark hair, navy blue suit, white shirt and blue tie, polished premium animated 3D film aesthetic, warm rim light. Waist-up, smiling, right arm raised forward in a welcoming greeting, left hand holding a brown leather book with gold title CÓDIGO CIVIL. Keep the whole head, raised hand and forearm, and torso within frame. No UI, no speech bubble, no other characters, no scenery, no platform or backdrop. Transparent background. This is only a STATIC fallback poster for a future rigged character, not a sprite sheet or animation. Portrait-ish square 1024x1024 composition.

2. Use case ads-marketing. A seamless six-cell website photographic thumbnail ATLAS, 3 equal columns and 2 equal rows, canvas 1536x1024. NO margins NO gutters NO borders NO text. Each cell exactly 512x512, six separate rich photorealistic scenes with warm gold lighting and elegant legal-office materials. Top left: civil law polished wooden gavel resting on brown books, warm marble. Top center: criminal law wooden gavel and silver handcuffs on dark desk. Top right: labor law employment contract sheets and black briefcase on office desk, no legible lettering. Bottom left: family law caring hands sheltering small family-shaped wooden figurines, warm coral lighting. Bottom center: business law blue glass modern towers against sunny blue sky. Bottom right: elegant shelves of navy and brown leather law books with subtle gold spines, no legible lettering. Attractive premium professional Peruvian law firm website visual assets. No UI, labels, icons, watermarks or captions. Each square must be visually independent and fill its cell, arranged precisely as specified.
