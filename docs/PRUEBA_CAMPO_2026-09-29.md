# Prueba de campo — S22+ y Y12 — 2026-09-29

Datos transcritos de las capturas enviadas por el usuario. Las pantallas corresponden a varios momentos y pruebas; no se asume que todos los contadores pertenezcan a un único intento ni que una solicitud enviada haya cambiado efectivamente la canción.

## Audio del casco

| Momento | Modo | Ruta comunicación | Salida música prevista | Música | Comunicación |
| --- | --- | --- | --- | --- | --- |
| Antes de iniciar | normal | otra ruta | Bluetooth A2DP | 12/15 | 15/15 |
| Esperando Alexa | normal | Bluetooth SCO | Bluetooth SCO | 12/15 | 15/15 |
| Después, captura inmediata | normal | Bluetooth SCO | Bluetooth SCO | 15/15 | 15/15 |
| Lectura actual posterior | normal | otra ruta | Bluetooth A2DP | 11/15 | 15/15 |

La ruta de música informada cambia de A2DP a SCO mientras APP espera Alexa, aunque el nivel de comunicación ya marca 15/15. La captura automática «Después» todavía mostró SCO y una actualización posterior mostró A2DP: la restauración de ruta es asíncrona. Esta evidencia explica la degradación de calidad por cambio de perfil, pero no mide por sí sola el volumen acústico del Y12. No se ajustarán niveles automáticamente sin otra comparación de sonido y ruta.

## Reconocimiento y micrófono

- Una captura de resultados muestra 24 activaciones por voz, 23 transcripciones, 20 solicitudes enviadas y 1 fallo registrado. Son contadores de etapas diferentes, no una tasa de aciertos medida ni confirmación de ejecución en la app musical.
- En esa captura, la última orden tardó 1442 ms desde el inicio de la orden hasta STT listo y 5305 ms hasta el resultado. Otras capturas muestran resultados de 5281 ms y 4746 ms; incluyen esperas y habla, no solo procesamiento.
- La medición de 5 segundos registró entrada real Bluetooth SCO, 80 000 muestras en 5058 ms, nivel medio −54 dBFS y pico −36 dBFS. Esta lectura sola no establece una tasa de activación con ruido de moto.
- Una prueba de orden corta transcribió «cancelar cancelar» y lo rechazó por coincidencia exacta. Se añade ese caso como cancelación sin ejecutar acciones.
- Las capturas de la prueba de activación con APP visible muestran 4 detecciones en un momento y una sesión detenida por «detener sesión» en otro. No se deduce de ellas cuántos intentos fueron pronunciados.
- El resultado mostrado para Y12 es 0 órdenes; las capturas no validan aún la pulsación larga durante la sesión. Los toques cortos de pista son controles propios del casco y se verifican por el cambio real de canción.

## Corrección y próxima prueba

APP actualiza la lectura «Después» durante los segundos posteriores al cierre para esperar la restauración de A2DP. Se reconoce «cancelar cancelar» como cancelación segura. En el siguiente ensayo estacionario comparar sonido A2DP/SCO con la misma canción, verificar si la lectura final queda en A2DP y probar una activación y orden con ruido de motor. Durante el viaje registrar verbalmente o de memoria los fallos y revisar la pantalla únicamente al detenerse.

## Observaciones posteriores del usuario

- Con el volumen de música en 15/15 el casco sonó un poco más fuerte, pero la diferencia fue poco notable. El cambio de A2DP a SCO sigue siendo el problema de calidad y nivel más evidente.
- En la salida donde se vieron 24 activaciones, 20 solicitudes y 23 transcripciones, el usuario estima que de unas 25 veces que dijo «Alexa», aproximadamente 22–23 activaron y entendieron bien la instrucción. Es una estimación de campo, no una tasa medida por los contadores ni confirmación del resultado en la otra app.
- Fallo prioritario: al detener la sesión, por voz o desde la app, el reproductor figuraba en reproducción pero dejó de oírse hasta iniciar otra sesión.

## Corrección para repetir la prueba

El cierre conserva el servicio hasta liberar el detector, limpiar la selección de comunicación y observar el retorno de la salida multimedia. Si APP había pausado una canción para escuchar o para cambiar de ruta, la reanuda al final. La orden explícita «pausa» permanece pausada. La espera está acotada a ocho segundos y muestra diagnóstico si no se confirma la restauración; requiere completar la validación real con S22+ y Y12.

Con la moto detenida: reproducir una canción, iniciar viaje, detener desde la app y comprobar sonido sin reiniciar; repetir diciendo «detener sesión». Revisar «Audio del casco»: salida prevista A2DP y modo normal. También probar una pausa por voz antes de detener para verificar que la app no reproduzca contra la intención del usuario. No ajustar 15/15 automáticamente.

## Validación posterior con Y12 Pro (reporte del usuario)

- Al decir «detener sesión», la canción vuelve a escucharse sin tener que iniciar otro viaje. Queda validada en esta prueba la recuperación del sonido tras el cierre por voz.
- Mantener presionado el botón del Y12 Pro activa la alternativa de asistente. El reporte confirma la activación con pulsación larga, pero no cuantifica órdenes ejecutadas por esa vía ni sustituye el uso de toques cortos para cambiar pista.
- Pendiente: repetir el cierre desde la app o la notificación, comprobar la ruta final A2DP, probar que «pausa» siga pausada al cerrar y comparar el volumen/calidad durante la espera de Alexa. No se modifica volumen automáticamente.
