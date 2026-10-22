#!/usr/bin/env python3
"""Gera `InspectionTexts.kt` e as entradas `kmplib_insp_*` dos 4 `strings.xml` do kmplib-ui a partir
de `textos.tsv` (campo, pt-BR, en, es, pt-PT). Idempotente: o bloco do kit nos XML fica entre
marcadores e é reescrito inteiro. Uso: `python3 inspection/tools/gerar_textos.py` na raiz da kmplib."""
import os, re, sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TSV = os.path.join(RAIZ, 'inspection/tools/textos.tsv')
KT = os.path.join(RAIZ, 'inspection/src/commonMain/kotlin/br/com/codecacto/kmplib/inspection/InspectionTexts.kt')
RES = os.path.join(RAIZ, 'ui/src/commonMain/composeResources')
PASTAS = {1: 'values', 2: 'values-en', 3: 'values-es', 4: 'values-pt-rPT'}
INI, FIM = '    <!-- kmplib-inspection: INICIO (gerado por inspection/tools/gerar_textos.py) -->', '    <!-- kmplib-inspection: FIM -->'

def snake(c): return re.sub(r'([A-Z])', lambda m: '_' + m.group(1).lower(), c)
def esc_xml(s): return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace("'", "\\'").replace('"', '\\"')
def esc_kt(s): return s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$')

grupos = []  # (prop, Classe, [linhas])
for l in open(TSV, encoding='utf-8'):
    if not l.strip():
        continue
    if l.startswith('#'):
        prop, classe = l[1:].split()
        grupos.append((prop, classe, []))
        continue
    campos_l = l.rstrip('\n').split('\t')
    assert len(campos_l) == 5, campos_l
    grupos[-1][2].append(campos_l)
linhas = [l for g in grupos for l in g[2]]

for col, pasta in PASTAS.items():
    p = os.path.join(RES, pasta, 'strings.xml')
    s = open(p, encoding='utf-8').read()
    if INI in s:
        s = s[:s.index(INI)] + s[s.index(FIM) + len(FIM) + 1:]
    bloco = INI + '\n' + ''.join(f'    <string name="kmplib_insp_{snake(l[0])}">{esc_xml(l[col])}</string>\n' for l in linhas) + FIM + '\n'
    i = s.rindex('</resources>')
    s = s[:i] + bloco + s[i:]
    open(p, 'w', encoding='utf-8').write(s)

imports = ''.join(f'import br.com.codecacto.kmplib.generated.resources.kmplib_insp_{snake(l[0])}\n' for l in linhas)
classes = ''
lidos = ''
props = ''
for prop, classe, ls in grupos:
    nome = f'Inspection{classe}Texts'
    campos = ''.join(f'    val {l[0]}: String = "{esc_kt(l[1])}",\n' for l in ls)
    classes += f'\n/** Textos do kit — {prop}. */\n@Immutable\ndata class {nome}(\n{campos})\n'
    leitura = ''.join(f'        {l[0]} = kmpStringResource(Res.string.kmplib_insp_{snake(l[0])}),\n' for l in ls)
    lidos += f'\n@Composable\nprivate fun remember{nome}(): {nome} = {nome}(\n{leitura})\n'
    props += f'    val {prop}: {nome} = {nome}(),\n'
construcao = ''.join(f'    {prop} = remember{"Inspection" + classe + "Texts"}(),\n' for prop, classe, _ in grupos)
kt = f'''// GERADO por inspection/tools/gerar_textos.py a partir de inspection/tools/textos.tsv — não editar à mão.
package br.com.codecacto.kmplib.inspection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.generated.resources.Res
{imports}import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Todos os textos do Laudo Kit, por tela. Defaults em pt-BR; [rememberInspectionTexts] traz os 4
 * idiomas da fábrica (pt-BR, en, es, pt-PT) no idioma da tela. Campos com `%1$s`/`%2$s` são MODELOS:
 * formate com `fmt` (ver `InspectionTextFormats.kt`). Para trocar uma frase de domínio ("cômodo" →
 * "ambiente", "laudo" → "documento"): `t.copy(rooms = t.rooms.copy(roomsTitle = …))`.
 */
@Immutable
data class InspectionTexts(
{props})
{classes}
/** Os textos do kit no idioma da tela. */
@Composable
fun rememberInspectionTexts(): InspectionTexts = InspectionTexts(
{construcao})
{lidos}'''
open(KT, 'w', encoding='utf-8').write(kt)
print(len(linhas), 'textos em', len(grupos), 'grupos')
