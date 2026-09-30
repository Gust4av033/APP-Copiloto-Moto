# Fase 1 — núcleo de comandos

## Primera entrega

- El Intent Router interpreta la frase una sola vez y devuelve la intención junto con sus parámetros. Conserva la distinción entre «detener» (pausar música) y «detener sesión» (cerrar el viaje), y rechaza frases combinadas o volumen fuera de rango.
- El ejecutor multimedia compartido se usa tanto en la prueba visible como en la sesión de viaje. Los ajustes de volumen de la sesión siguen esperando la restauración de la ruta de audio antes de actuar.
- Se aceptan algunas formas naturales adicionales, entre ellas «pon la música en pausa», «pasa de canción», «aumenta el volumen» y «por favor, abre Spotify».
- «Abre [nombre]» busca una aplicación del lanzador por su nombre visible completo. Si no existe o el nombre coincide con varias aplicaciones, no abre ninguna.

## Abrir aplicaciones

Con APP visible, la orden abre la aplicación encontrada. En la sesión de viaje, APP deja la aplicación preparada en una acción **Abrir [nombre]** de la notificación. Android restringe abrir una pantalla desde un servicio en segundo plano; esta primera entrega requiere tocar esa acción. La búsqueda no pide acceso general a todos los paquetes y no usa el nombre pronunciado como identificador de paquete.

Esta acción no es navegación ni controla la aplicación abierta. La integración con Waze corresponde a Fase 2.

## Prueba en el S22+ con la moto detenida

1. Abrir **Pruebas**, pedir una orden corta y decir «por favor, pasa de canción». Verificar el cambio de pista. Repetir «pon la música en pausa» y «reanuda».
2. Decir «volumen al 40 por ciento» y comprobar el nivel de la ruta que muestra APP. Decir «volumen al 80» sin «por ciento»: no debe ajustarlo, porque excede el rango de nivel directo.
3. Con una aplicación instalada, decir «abre [nombre exacto]» en la prueba visible. Confirmar que se abre la aplicación correcta. Probar un nombre inexistente: no debe abrir otra.
4. Repetir en la sesión de viaje con la pantalla apagada. Verificar que aparece **Abrir [nombre]** en la notificación y que solo al tocarla se abre la aplicación. Comprobar que las órdenes de música, volumen, cancelar y detener sesión siguen funcionando.

La compilación y las pruebas locales verifican las reglas y el código Android. El comportamiento del botón, del casco, de la notificación y de las aplicaciones instaladas requiere esta prueba en el teléfono antes de integrar la rama.
