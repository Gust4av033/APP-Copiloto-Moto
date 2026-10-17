# Bases de clima y puntos de interés (Fase 7)

Fase 2 despacha búsquedas de lugares al navegador elegido. Registra categoría, proveedor y hora como **solicitud**, sin atribuirse resultados ni una ruta. En Waze usa `waze://?q=...`; en Maps usa `geo:0,0?q=...` con el paquete de Maps. La búsqueda no lleva `navigate=yes`; el motociclista elige el resultado cuando está detenido.

Las categorías iniciales son gasolinera, taller de moto, parada de comida, farmacia, cajero y personalizada. La búsqueda libre de Waze queda a criterio de Waze cerca del usuario. No se ha verificado una opción documentada de enlace profundo que la acote a coordenadas; no se añade un parámetro supuesto.

`WeatherProvider` recibe posición y hora. `PlacesProvider` recibe `PoiQuery`: categoría, posición o geometría de ruta y radio. Devuelven `ContextValue` con procedencia, vigencia y la posibilidad de no tener dato. Estos contratos no consultan red en Fase 2. Los proveedores reales y sus resultados pertenecen a Fase 7, Nivel 2 (Internet opcional). Usarán posición GPS y destino propios de APP; no se extraen clima ni resultados de pantallas de Waze o Maps.

Verificación física pendiente: abrir las búsquedas en Waze y Maps en un S22+ y confirmar que ofrecen resultados sin iniciar navegación automática. La documentación de enlaces de Waze no fue accesible desde este entorno; revisar `https://developers.google.com/waze/deeplinks` antes de prometer búsquedas relativas a una posición.
