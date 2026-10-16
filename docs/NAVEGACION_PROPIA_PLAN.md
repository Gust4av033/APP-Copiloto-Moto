# Camino hacia navegación propia de APP

Estado: propuesta de arquitectura registrada el 15 de octubre de 2026 para entregas
posteriores. No activa un motor de rutas, una cuenta de Google Cloud ni un
backend en Fase 2. El trabajo continúa en `feature/ui-storm-refined`; `main`
permanece estable hasta la validación física del PR #22.

## Decisión de producto

APP debe poder planificar y seguir su propia ruta. Waze y Maps seguirán como
navegadores opcionales y fuentes externas claramente identificadas. La Capa 1
existente aporta GPS propio con permiso y calidad comprobada; la Capa 3 solo
calcula hoy distancia en línea recta a un destino con coordenadas. La Capa 2
prevista requiere un grafo vial y un motor de rutas para producir distancia
por carretera, ETA y maniobras propios. Ninguna notificación de Maps o Waze
se convierte por sí sola en una ruta de APP.

La captura de campo de Maps muestra 7 min y 2,4 km observados, mientras APP
indica que no se le envió destino en ese viaje. Esos dos números no contienen
coordenadas del destino ni geometría de ruta. Restar tiempo o kilómetros entre
avisos sería una proyección frágil, no una ruta propia más exacta. Se conserva
el origen, la antigüedad y el alcance de cada observación.

## Contrato que debe sostener la futura implementación

| Pieza | Entrada | Salida y regla |
| --- | --- | --- |
| Planificador | GPS preciso autorizado, destino con coordenadas, perfil de moto y datos viales con versión | Geometría, distancia vial, duración inicial, maniobras y procedencia `APP_RUTA_PROPIA`; si falta una entrada, informa que no hay plan |
| Seguidor | Plan vigente y muestras GPS recientes con precisión | Posición ajustada a la ruta, avance, distancia restante y desvío; una posición dudosa no autoriza un giro |
| Replanificador | Desvío confirmado o destino cambiado | Nuevo plan con identificador y hora propios; invalida maniobras y ETA del plan anterior |
| Estimador | Plan vigente, avance y velocidades locales verificadas | ETA propia aproximada con confianza y caducidad; no se presenta como ETA de Maps/Waze |
| Presentación | Plan y observaciones externas independientes | «Ruta de APP», «Dato observado de Maps/Waze» y «Último dato» siempre distinguibles en UI y voz |

El contrato conserva `source`, captura monotónica, confianza, TTL e
`isSimulated` de `TripContext`. Un cambio de sesión, permiso, destino, versión
de mapa o plan invalida sus derivados. La solicitud de abrir otro navegador
no confirma que éste haya iniciado una ruta. Elegir o editar el destino exige
la moto detenida; un recálculo automático durante el viaje no debe requerir
interacción ni interrumpir el audio, y solo anuncia maniobras verificadas.

## Ruta técnica sin dependencia de pago por viaje

1. Comparar en el S22+ un prototipo **sin activarlo en producción** con un
   extracto vial pequeño de San Salvador: memoria, almacenamiento, tiempo de
   cálculo, batería, cobertura, maniobras en español y actualización de mapas.
2. Evaluar [Valhalla](https://github.com/valhalla/valhalla) como motor libre
   para datos de [OpenStreetMap](https://www.openstreetmap.org/copyright).
   Decidir con mediciones si el grafo se procesa en el teléfono o en un
   servidor propio; servidor, datos y transferencia pueden tener costos aun
   cuando el software y los datos sean abiertos. No depender del servidor
   público de demostración como infraestructura de APP.
3. Incorporar planificación, seguimiento sobre la geometría y recálculo
   antes de anunciar giros o ETA propios. Medir desvíos GPS, túneles, ausencia
   de red, cambios de destino, rutas incorrectas, batería y audio del casco.
4. Después, evaluar calibración personal local con historial propio. MOD-009
   sigue **reservada**: Fase 9 corresponde al historial y Fase 13 al tráfico
   colaborativo. Una ETA propia no debe depender del tráfico comunitario para
   funcionar con un solo conductor.

Los datos OSM requieren atribución y cumplimiento de ODbL. Sus servidores
públicos de mapas y geocodificación no son una promesa de servicio gratuito
para terceros. El diseño debe prever datos propios actualizables y el texto
de atribución antes de publicar una ruta calculada con OSM.

## Navigation Connect como adaptador opcional

Según la [tarifa oficial consultada el 15 de octubre de 2026](https://developers.google.com/maps/documentation/navigation/connect/usage-and-billing),
la API está en Preview y su uso de API cuesta $0 durante esa etapa, pero
**requiere facturación habilitada y OAuth**. Después de GA se publican topes
mensuales gratuitos de 5 000 viajes Pro y 1 000 Enterprise fuera de India;
superarlos se factura. Pedir ruta restante, tráfico, desvíos o actualizaciones
de cinco segundos activa Enterprise. Pub/Sub y el backend tienen costos
separados. Los precios y la disponibilidad deben verificarse de nuevo antes
de activar cualquier integración.

La [configuración oficial](https://developers.google.com/maps/documentation/navigation/connect/setup-overview-requirements)
requiere proyecto Cloud, verificación de la app, cuenta de servicio, OAuth,
backend y consentimiento del conductor en Maps/Waze. El token de viaje lo
crea el backend; no se guarda una cuenta de servicio en el APK. APP necesitaría
un opt-in independiente para enviar datos del viaje, revocación y una política
de costos antes de usarlo. Si el conductor no consiente o la API deja de ser
gratuita para el uso previsto, APP conserva su ruta propia o los datos locales
que realmente tenga, sin afirmar datos externos inexistentes.

Navigation Connect no es la fuente de entrenamiento del motor propio. Las
[condiciones de Google Maps Platform](https://cloud.google.com/maps-platform/terms)
restringen crear contenido derivado y almacenar contenido de Maps; cualquier
uso de esos datos para calibración o entrenamiento requiere revisión de las
condiciones aplicables antes de diseñarlo. El motor propio se basará en datos
viales permitidos y GPS/historial de APP con permiso del conductor.

## Puertas de aceptación

- La Fase 2 actual debe terminar su prueba física sin presentar cálculos
  propios como ruta vial.
- Una prueba de motor propio necesita destino inequívoco, posición precisa,
  mapa con cobertura y maniobras verificadas frente a recorridos reales.
- En falta de mapa, GPS o confianza, APP debe retirar ETA/giro propios y
  comunicar el motivo sin fingir una ruta activa.
- Inicio, Viaje, voz, burbuja y notificación deben mostrar la misma fuente y
  vigencia. Elegir o cambiar manualmente el destino exige la moto detenida;
  el recálculo automático no debe pedir interacción durante la marcha.
- No se habilitan servicios de pago, facturación ni tráfico colaborativo como
  efecto secundario de esta base documental.
