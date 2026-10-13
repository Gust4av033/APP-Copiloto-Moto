"""Design mockup of the compact native panel; this is not an Android screenshot.

Run from the repository root with CairoSVG installed. Icon paths and water material
come from production XML; native measurement, fonts and touch require a device.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
import cairosvg
import render_overlay_vector_preview as vectors

root = ET.Element('svg', {'xmlns': vectors.SVG, 'width': '980', 'height': '420', 'viewBox': '0 0 980 420'})
defs = ET.SubElement(root, 'defs')
vectors.defs = defs
vectors.serial = 0

def rect(parent, x, y, width, height, fill, radius=0, **attrs):
    return ET.SubElement(parent, 'rect', dict(x=str(x), y=str(y), width=str(width), height=str(height), fill=fill, rx=str(radius), **attrs))

def text(parent, x, y, label, size, fill, bold=False):
    node = ET.SubElement(parent, 'text', {'x': str(x), 'y': str(y), 'font-family': 'sans-serif', 'font-size': str(size),
        'fill': fill, 'font-weight': 'bold' if bold else 'normal'})
    node.text = label

def gradient(name, colors, vertical=False):
    g = ET.SubElement(defs, 'linearGradient', {'id': name, 'x1': '0', 'y1': '0', 'x2': '0' if vertical else '1', 'y2': '1'})
    for i, value in enumerate(colors):
        rgb, opacity = vectors.color(value)
        ET.SubElement(g, 'stop', {'offset': str(i / (len(colors) - 1)), 'stop-color': rgb, 'stop-opacity': opacity})

def icon(parent, name, x, y, size, tint):
    g = ET.SubElement(parent, 'g', {'transform': f'translate({x} {y}) scale({size / 24})'})
    vectors.convert(g, ET.parse(Path('app/src/main/res/drawable') / (name + '.xml')).getroot(), tint)

strings = {s.get('name'): ''.join(s.itertext()) for s in ET.parse('app/src/main/res/values/ui_strings.xml').getroot()}
gradient('primary', ['#FF6546A2', '#FF28538C'])
gradient('sheen', ['#26FFFFFF', '#00FFFFFF', '#0B79DDFF'], True)
gradient('reflection', ['#44FFFFFF', '#08FFFFFF', '#1A79DDFF'], True)
rect(root, 0, 0, 490, 420, '#0B1020')
rect(root, 490, 0, 490, 420, '#E9EEFA')
text(root, 34, 36, 'PANEL COMPACTO · RC1', 19, '#E9E8F7', True)
text(root, 524, 36, 'TEMA CLARO', 19, '#243050', True)
for night, x in [(True, 74), (False, 564)]:
    suffix = 'night' if night else 'day'
    fg = '#F4EEFF' if night else '#243050'
    muted = '#CDD6EB' if night else '#465671'
    gradient(suffix, ['#EA2F2857', '#E61A2745', '#EC171D39'] if night else ['#EFF7F3FF', '#EBEDF7FF', '#F0E3EBFB'])
    gradient('secondary-' + suffix, ['#B832405F', '#D126304D'] if night else ['#E6FFFFFF', '#E6E5ECFA'])
    panel = ET.SubElement(root, 'g', {'transform': f'translate({x} 76) scale(1.4)'})
    for inset in range(8, 0, -1):
        rect(panel, -inset, 3 - inset, 244 + inset * 2, 182 + inset * 2, '#000000', 24 + inset, opacity='0.012')
    rect(panel, 0, 0, 244, 182, 'url(#' + suffix + ')', 24, stroke='#9CDCF5' if night else '#FFFFFF', **{'stroke-opacity': '0.64' if night else '0.81'})
    rect(panel, 2, 2, 240, 178, 'url(#reflection)', 23)
    rect(panel, 2, 2, 240, 178, 'none', 22, stroke='#44B7EA' if night else '#89A4D8', **{'stroke-opacity': '0.13' if night else '0.21'})
    water = ET.SubElement(panel, 'g', {'transform': 'translate(12 14) scale(0.5)'})
    vectors.convert(water, ET.parse('app/src/main/res/drawable/overlay_water_orb.xml').getroot())
    icon(panel, 'ic_button', 23, 25, 14, '#F4EEFF')
    text(panel, 54, 27, strings['overlay_panel_mode'].replace('%1$d', '2'), 14, fg, True)
    text(panel, 54, 44, strings['overlay_wait_button_compact'], 12.5, muted)
    icon(panel, 'ic_overlay_close', 198, 22, 20, fg)
    rect(panel, 12, 64, 220, 52, 'url(#primary)', 16, stroke='#9DCFFF', **{'stroke-opacity': '0.67'})
    rect(panel, 12, 64, 220, 52, 'url(#sheen)', 16)
    icon(panel, 'ic_mic', 24, 79, 22, '#F4EEFF')
    text(panel, 54, 95, strings['overlay_command'], 14, '#F4EEFF', True)
    for sx, glyph, label in [(12, 'ic_home', 'overlay_open_app'), (126, 'ic_overlay_hide', 'overlay_hide_short')]:
        rect(panel, sx, 124, 106, 48, 'url(#secondary-' + suffix + ')', 16, stroke='#88ABD5', **{'stroke-opacity': '0.27'})
        rect(panel, sx, 124, 106, 48, 'url(#sheen)', 16)
        icon(panel, glyph, sx + 9, 139, 18, fg)
        text(panel, sx + 33, 152, strings[label], 12, fg, True)
    text(root, x, 356, '244 dp · altura según contenido', 15, muted)
rect(root, 0, 380, 980, 40, '#10182A')
text(root, 24, 405, 'Maqueta de diseño. No es una captura de Android; tamaño nativo pendiente de comprobar.', 13, '#E9E8F7')
out = Path('docs/images')
out.mkdir(exist_ok=True, parents=True)
svg = ET.tostring(root, encoding='unicode')
(out / 'panel_compacto_02.svg').write_text(svg)
cairosvg.svg2png(bytestring=svg.encode(), write_to=str(out / 'panel_compacto_02.png'))
print(out / 'panel_compacto_02.png')
