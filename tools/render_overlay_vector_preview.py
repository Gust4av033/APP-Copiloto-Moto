"""Render the production VectorDrawable resource as SVG; this is not an Android screenshot.
Run from repository root with CairoSVG installed. Paths, alpha and gradients come from XML.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
import cairosvg

ANDROID = '{http://schemas.android.com/apk/res/android}'
AAPT = '{http://schemas.android.com/aapt}'
SVG = 'http://www.w3.org/2000/svg'
ET.register_namespace('', SVG)
root = ET.Element('svg', {'xmlns': SVG, 'width': '720', 'height': '330', 'viewBox': '0 0 720 330'})
defs = ET.SubElement(root, 'defs')
serial = 0

def color(value):
    if value in ('@android:color/transparent', '#00000000'): return '#000000', '0'
    if len(value) == 9 and value.startswith('#'):
        return '#' + value[3:], str(int(value[1:3], 16) / 255)
    return value, '1'

def convert(parent, resource, tint=None):
    global serial
    for node in resource:
        if node.tag == 'group':
            attrs = {}
            tx = node.get(ANDROID+'translateX', '0'); ty = node.get(ANDROID+'translateY', '0')
            sx = node.get(ANDROID+'scaleX', '1'); sy = node.get(ANDROID+'scaleY', '1')
            attrs['transform'] = f'translate({tx} {ty}) scale({sx} {sy})'
            convert(ET.SubElement(parent, 'g', attrs), node, tint)
        elif node.tag == 'path':
            attrs = {'d': node.get(ANDROID+'pathData'), 'fill': 'none'}
            for a, b in [('strokeWidth','stroke-width'), ('strokeLineCap','stroke-linecap'), ('strokeLineJoin','stroke-linejoin'), ('fillType','fill-rule')]:
                if node.get(ANDROID+a): attrs[b] = node.get(ANDROID+a).replace('evenOdd', 'evenodd')
            for a, b in [('fillColor', 'fill'), ('strokeColor', 'stroke')]:
                value = node.get(ANDROID+a)
                if value:
                    rgb, opacity = color(value)
                    attrs[b] = tint if tint and opacity != '0' else rgb
                    attrs[b+'-opacity'] = opacity
            for attr in node.findall(AAPT+'attr'):
                target = attr.get('name').split(':')[-1]
                target = {'fillColor':'fill', 'strokeColor':'stroke'}[target]
                grad = attr.find('gradient'); serial += 1; gid = 'gradient'+str(serial)
                if grad.get(ANDROID+'type') == 'radial':
                    g = ET.SubElement(defs,'radialGradient', {'id':gid,'gradientUnits':'userSpaceOnUse',
                        'cx':grad.get(ANDROID+'centerX'),'cy':grad.get(ANDROID+'centerY'),'r':grad.get(ANDROID+'gradientRadius')})
                else:
                    g = ET.SubElement(defs,'linearGradient', {'id':gid,'gradientUnits':'userSpaceOnUse',
                        'x1':grad.get(ANDROID+'startX'),'y1':grad.get(ANDROID+'startY'),
                        'x2':grad.get(ANDROID+'endX'),'y2':grad.get(ANDROID+'endY')})
                for stop in grad.findall('item'):
                    rgb, opacity = color(stop.get(ANDROID+'color'))
                    ET.SubElement(g,'stop', {'offset':stop.get(ANDROID+'offset'),'stop-color':rgb,'stop-opacity':opacity})
                attrs[target] = f'url(#{gid})'; attrs.pop(target+'-opacity', None)
            ET.SubElement(parent,'path', attrs)

def text(x,y,value,size=15,fill='#E9E8F7'):
    e=ET.SubElement(root,'text',{'x':str(x),'y':str(y),'font-family':'sans-serif','font-size':str(size),'fill':fill});e.text=value

ET.SubElement(root,'rect',{'width':'360','height':'330','fill':'#0B1020'})
ET.SubElement(root,'rect',{'x':'360','width':'360','height':'330','fill':'#E9EEFA'})
text(28,36,'BURBUJA DE AGUA',18);text(388,36,'FONDO CLARO',18,'#243050')
resource_dir=Path('app/src/main/res/drawable')
for x, label, icon, dot in [(68,'Espera manual','ic_button','#D3BFFF'),(232,'Escuchando','ic_mic','#79DDFF'),
                            (428,'Espera manual','ic_button','#D3BFFF'),(592,'Escuchando','ic_mic','#79DDFF')]:
    container=ET.SubElement(root,'g',{'transform':f'translate({x-8},72) scale(1.85)'})
    convert(container,ET.parse(resource_dir/'overlay_water_orb.xml').getroot())
    glyph=ET.SubElement(container,'g',{'transform':'translate(23.625 20.25) scale(1.03125)'})
    convert(glyph,ET.parse(resource_dir/(icon+'.xml')).getroot(),'#F4EEFF')
    ET.SubElement(container,'circle',{'cx':'36','cy':'54.5625','r':'3.9375','fill':dot,'stroke':'#152448','stroke-width':'1'})
    text(x-12,235,label,14,'#BDC5DA' if x<360 else '#485A7A')
text(28,288,'Recurso vectorial de APP · morado / celeste / azul',13)
text(28,312,'Previsualización del material. No es una captura de Android.',12)
# El pie permanece sobre fondo oscuro en todo el ancho.
for node in list(root)[-2:]:
    root.remove(node)
ET.SubElement(root,'rect',{'x':'0','y':'266','width':'720','height':'64','fill':'#10182A'})
text(28,288,'Recurso vectorial de APP · morado / celeste / azul',13)
text(28,312,'Previsualización del material. No es una captura de Android.',12)
output=Path('docs/images');output.mkdir(parents=True,exist_ok=True)
svg=ET.tostring(root,encoding='unicode')
(output/'burbuja_agua_vector.svg').write_text(svg)
cairosvg.svg2png(bytestring=svg.encode(),write_to=str(output/'burbuja_agua_vector.png'),output_width=1440,output_height=660)
print(output/'burbuja_agua_vector.png')
