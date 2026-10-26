"""Compile actual palette wrappers; exercise reentrancy, snapshots and real writers."""
from pathlib import Path
import argparse, json, shutil, subprocess, tempfile

I=Path(__file__).resolve().parents[1]
source=I/'overlay/common/src/main/java/com/axalotl/async/common/mixin/world/PalettedContainerMixin.java'
stubs={
 'com/llamalad7/mixinextras/injector/wrapmethod/WrapMethod.java':'package com.llamalad7.mixinextras.injector.wrapmethod; public @interface WrapMethod {String[] method();}',
 'com/llamalad7/mixinextras/injector/wrapoperation/Operation.java':'package com.llamalad7.mixinextras.injector.wrapoperation; public interface Operation<T>{T call(Object... args);}',
 'org/spongepowered/asm/mixin/Mixin.java':'package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value();}',
 'net/minecraft/core/IdMap.java':'package net.minecraft.core; public interface IdMap<T>{}',
 'net/minecraft/network/FriendlyByteBuf.java':'package net.minecraft.network; public class FriendlyByteBuf{}',
 'net/minecraft/world/level/chunk/PalettedContainer.java':'package net.minecraft.world.level.chunk; public class PalettedContainer<T>{public static class Strategy{} public interface CountConsumer<T>{}}',
 'net/minecraft/world/level/chunk/PalettedContainerRO.java':'package net.minecraft.world.level.chunk; public interface PalettedContainerRO<T>{class PackedData<T>{}}',
}

def run(root, text, negative=False):
    tree=root/('negative' if negative else 'production');tree.mkdir()
    for name,body in stubs.items():
        f=tree/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
    (tree/'PalettedContainerMixin.java').write_text(text)
    shutil.copy2(I/'tests/PaletteMonitorRegression.java',tree/'PaletteMonitorRegression.java')
    subprocess.run(['javac','--release','17','-d',str(tree/'classes')]+[str(p) for p in tree.rglob('*.java')],check=True)
    result=subprocess.run(['java','-ea','-cp',str(tree/'classes'),'PaletteMonitorRegression'],capture_output=True,text=True,timeout=30)
    if negative:
        assert result.returncode!=0 and 'snapshot writer exclusion' in result.stderr, result.stdout+result.stderr
    else:
        assert result.returncode==0, result.stdout+result.stderr
        print(result.stdout.strip())

def main():
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
    text=source.read_text()
    with tempfile.TemporaryDirectory(prefix='mvh-palette-monitor-') as tmp:
        root=Path(tmp);run(root,text);run(root,text.replace('private synchronized ','private '),True)
    print('UNPROTECTED_SNAPSHOT_NEGATIVE_CONTROL_REJECTED')
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True)
        a.report.write_text(json.dumps({'passed':True,'scope':'Actual eleven production wrapper methods compiled with type stubs; deterministic snapshot writer exclusion, nested mutation/read, exceptions and concurrent modeled palette consistency; unprotected negative control rejected. Real transformed Minecraft class and game runtime remain separate checks.'},indent=2))
if __name__=='__main__':main()
