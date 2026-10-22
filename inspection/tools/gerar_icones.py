#!/usr/bin/env python3
"""Gera `InspectionIconPaths.kt` e `InspectionIcon.kt` a partir dos SVG OFICIAIS do Material Symbols
Outlined (peso 400, grade 0, 24 px; FILL 0 e FILL 1), baixados de github.com/google/material-design-icons
(`symbols/web/<nome>/materialsymbolsoutlined/<nome>[_fill1]_24px.svg`, Apache 2.0).

Uso (na raiz da kmplib):  python3 inspection/tools/gerar_icones.py <pasta-temporaria>
Para acrescentar um ícone: ponha o nome em NOMES e rode de novo."""
import os, re, sys, urllib.request

NOMES = """add arrow_back arrow_forward broken_image cameraswitch cancel check check_circle chevron_left
chevron_right cleaning_services close cloud_done content_copy delete do_not_disturb_on drag_indicator draw edit
edit_note error expand_more fact_check flash_auto flash_off flash_on gpp_bad help hide_image hourglass_bottom
layers library_add link link_off mic more_vert new_releases opacity photo_camera photo_library play_circle
refresh schedule search_off share sms stop task_alt timer undo update upload verified videocam warning
wifi_off""".split()
URL = 'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{n}/materialsymbolsoutlined/{n}{v}_24px.svg'
RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DESTINO = os.path.join(RAIZ, 'inspection/src/commonMain/kotlin/br/com/codecacto/kmplib/inspection/icons')

def caminho(nome, variante, pasta):
    arq = os.path.join(pasta, f'{nome}{variante}.svg')
    if not os.path.exists(arq):
        urllib.request.urlretrieve(URL.format(n=nome, v=variante), arq)
    ds = re.findall(r' d="([^"]+)"', open(arq).read())
    assert len(ds) == 1, arq
    return ds[0]

def camel(n): return ''.join(p.capitalize() for p in n.split('_'))

def main(pasta):
    os.makedirs(pasta, exist_ok=True)
    nomes = sorted(NOMES)
    linhas = []
    for n in nomes:
        linhas.append(f'    const val {n.upper()}_OUTLINED: String = "{caminho(n, "", pasta)}"\n')
        linhas.append(f'    const val {n.upper()}_FILLED: String = "{caminho(n, "_fill1", pasta)}"\n')
    open(os.path.join(DESTINO, 'InspectionIconPaths.kt'), 'w').write(
        '// GERADO por inspection/tools/gerar_icones.py a partir dos SVG oficiais do Material Symbols Outlined\n'
        '// (peso 400, grade 0, 24 px; FILL 0 e FILL 1) — github.com/google/material-design-icons, Apache 2.0.\n'
        '// Não editar à mão: acrescente o nome em NOMES no script e rode de novo.\n'
        'package br.com.codecacto.kmplib.inspection.icons\n\n'
        '/** Os caminhos SVG (viewBox `0 -960 960 960`) de cada símbolo: contorno e cheio. */\n'
        'internal object InspectionIconPaths {\n' + ''.join(linhas) + '}\n')
    enum = ''.join(f'    {camel(n)}("{n}", InspectionIconPaths.{n.upper()}_OUTLINED, InspectionIconPaths.{n.upper()}_FILLED),\n' for n in nomes)
    open(os.path.join(DESTINO, 'InspectionIcon.kt'), 'w').write(
        '// GERADO por inspection/tools/gerar_icones.py — não editar à mão.\n'
        'package br.com.codecacto.kmplib.inspection.icons\n\n'
        '/**\n * O subconjunto do **Material Symbols Outlined** que o Laudo Kit usa (§1.2 da especificação), com as\n'
        ' * duas variações de preenchimento. O nome do símbolo ([symbol]) é o mesmo da fonte, para casar com a\n'
        ' * weblib (`InspectionIcon name="check_circle"`). Embutido como vetor: o kit não depende de fonte\n'
        ' * remota nem de `material-icons-extended` (que é o Material Icons antigo, de traço diferente).\n */\n'
        'enum class InspectionIcon(val symbol: String, internal val outlined: String, internal val filled: String) {\n'
        + enum + '    ;\n}\n')
    print(len(nomes), 'ícones')

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '/tmp/material-symbols')
