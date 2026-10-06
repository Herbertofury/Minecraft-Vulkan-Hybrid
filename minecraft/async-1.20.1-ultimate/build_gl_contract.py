#!/usr/bin/env python3
"""Describe audited Vulkan translations, with exact JVM overload descriptors.

An imported @Overwrite alone is not evidence of a working GL operation. The
import contains compatibility placeholders for entire later GL versions. This
contract accepts only the reviewed state/texture/buffer translations below and
also rejects empty bodies. General external shader/compute/legacy draw APIs are
not advertised: Minecraft shaders and geometry use the dedicated Vulkan mixins.
"""
import json, re
from pathlib import Path

# These operations delegate to the renderer's actual state and resource bridges.
# Unsupported targets/versions are intentionally not inferred from GL class names.
REVIEWED = set('''glClear glClearColor glClearDepth glDepthFunc glDepthMask
glColorMask glCullFace glFrontFace glPolygonMode glViewport glScissor
glBlendFunc glBlendFuncSeparate glBlendEquation glBlendEquationSeparate
glActiveTexture glBindTexture glGenTextures glDeleteTextures glIsTexture
glTexParameteri glTexParameterf glPixelStorei glTexImage2D glTexSubImage2D
glGetTexLevelParameteri glGetTexLevelParameteriv
glGenBuffers glDeleteBuffers glBindBuffer glIsBuffer glBufferData glBufferSubData
glMapBuffer glMapBufferRange glUnmapBuffer glFlushMappedBufferRange
glGetBufferParameteri glGetBufferParameteriv glGetBufferPointer glGetBufferPointerv
glGetBufferSubData glCopyBufferSubData'''.split())

PRIMITIVES = dict(void='V', boolean='Z', byte='B', char='C', short='S',
                  int='I', long='J', float='F', double='D')

def balanced(text, start, opening, closing):
    depth, quote, escaped = 0, None, False
    for i in range(start, len(text)):
        c = text[i]
        if quote:
            if escaped: escaped = False
            elif c == '\\': escaped = True
            elif c == quote: quote = None
        elif c in '\"\'': quote = c
        elif c == opening: depth += 1
        elif c == closing:
            depth -= 1
            if depth == 0: return i
    raise ValueError('unbalanced Java method')

def descriptor(typ, imports):
    typ = typ.replace('...', '[]').strip()
    arrays = typ.count('[]'); typ = typ.replace('[]', '')
    if typ in PRIMITIVES: base = PRIMITIVES[typ]
    else:
        full = typ if '.' in typ else imports.get(typ)
        if full is None and typ in {'String', 'CharSequence', 'Object'}: full = 'java.lang.' + typ
        if full is None: raise ValueError('unresolved Java type ' + typ)
        base = 'L' + full.replace('.', '/') + ';'
    return '[' * arrays + base

def methods(path):
    text = path.read_text()
    imports = {s.rsplit('.', 1)[-1]: s for s in re.findall(r'import ([\w.]+);', text)}
    target = re.search(r'@Mixin\(\s*(\w+)\.class\s*\)', text)
    if target is None: return []
    entries = []
    for match in re.finditer(r'@Overwrite(?:\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*public\s+static\s+([\w\[\].]+)\s+(\w+)\s*\(', text):
        start = match.end() - 1; end = balanced(text, start, '(', ')')
        params = re.sub(r'@\w+(?:\([^)]*\))?\s*', '', text[start+1:end])
        args = []
        for param in params.split(','):
            if param.strip(): args.append(descriptor(param.strip().rsplit(' ', 1)[0].replace('final ', ''), imports))
        body_start = text.index('{', end); body_end = balanced(text, body_start, '{', '}')
        body = text[body_start+1:body_end]
        signature = match.group(2) + '(' + ''.join(args) + ')' + descriptor(match.group(1), imports)
        entries.append(dict(owner=target.group(1), name=match.group(2), signature=signature,
                            body=body, source=path.name))
    return entries

def generate(root):
    folder = root/'forge/src/main/java/net/vulkanmod/mixin/compatibility/gl'
    entries = [m for source in sorted(folder.glob('*.java')) for m in methods(source)]
    contracts, rejected, accepted = {}, [], []
    for entry in entries:
        body = re.sub(r'/\*.*?\*/|//[^\n]*', '', entry.pop('body'), flags=re.S).strip()
        supported = entry['name'] in REVIEWED and bool(body)
        if supported:
            # The reviewed entry points must perform work or delegate, not fake a result.
            if re.fullmatch(r'return\s+(?:0[LF]?|true|false|null|""|GL\w+\.GL_TRUE)\s*;', body):
                supported = False
        if supported:
            contracts.setdefault(entry['owner'], set()).add(entry['signature']); accepted.append(entry)
        else:
            entry['reason'] = 'empty placeholder' if not body else 'outside reviewed translation surface'
            rejected.append(entry)
    if not accepted: raise ValueError('no reviewed Vulkan translations found')
    resource = root/'forge/src/main/resources/assets/vulkanmod/compat/harimt_supported_gl_methods.properties'
    resource.parent.mkdir(parents=True, exist_ok=True)
    resource.write_text('# Audited Vulkan state/resource translations; exact JVM method descriptors.\n' +
                        '\n'.join(owner+'='+','.join(sorted(values)) for owner,values in sorted(contracts.items()))+'\n')
    report = dict(schema=1, accepted_overloads=len(accepted), rejected_overloads=len(rejected),
                  accepted=accepted, rejected=rejected,
                  scope='External direct GL calls. Minecraft rendering uses dedicated Vulkan mixins. No empty overwrite implies support.')
    (resource.parent/'harimt_gl_translation_audit.json').write_text(json.dumps(report, indent=2)+'\n')
    return report

if __name__ == '__main__':
    import sys
    report = generate(Path(sys.argv[1]).resolve())
    print('Reviewed GL overloads:', report['accepted_overloads'], 'unadvertised:', report['rejected_overloads'])
