#!/usr/bin/env python3
from __future__ import annotations
import argparse, os, subprocess, sys, threading, time
from pathlib import Path

PASS='HMT_SOURCE_PAIRS_VERIFY PASS'
FATAL=(
 'MixinApplyError','InvalidMixinException','InjectionError','Exception in server tick loop',
 'OutOfMemoryError','VK_ERROR_DEVICE_LOST','A fatal error has been detected by the Java Runtime Environment',
 'Failed to start the minecraft server','HMT_SOURCE_PAIRS_VERIFY FAIL','Vulkan backend initialization failed',
)

class H:
    def __init__(self, d:Path, log:Path): self.d=d; self.log=log; self.p=None; self.lines=[]; self.t=None
    def start(self):
        env=os.environ.copy(); opts=env.get('JDK_JAVA_OPTIONS','').strip()
        for o in ('-Dharimt.vulkan.allowCpuDevice=true','-Xms3G','-Xmx3G','-XX:+AlwaysPreTouch'):
            if o not in opts.split(): opts=(opts+' '+o).strip()
        env['JDK_JAVA_OPTIONS']=opts
        self.p=subprocess.Popen(['bash','run.sh','nogui'],cwd=self.d,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,env=env)
        assert self.p.stdin and self.p.stdout
        def rd():
            assert self.p and self.p.stdout
            for line in self.p.stdout:
                print(line,end='',flush=True); self.lines.append(line)
        self.t=threading.Thread(target=rd,daemon=True); self.t.start(); self.wait('Done (',240,0)
    def send(self,c):
        assert self.p and self.p.stdin and self.p.poll() is None
        st=len(self.lines); print('[SOURCE-PAIRS-QA] > '+c,flush=True); self.p.stdin.write(c+'\n'); self.p.stdin.flush(); return st
    def wait(self,text,timeout,start):
        dl=time.monotonic()+timeout; i=start
        while time.monotonic()<dl:
            while i<len(self.lines):
                line=self.lines[i]; i+=1
                for f in FATAL:
                    if f in line: raise RuntimeError(f'observed fatal {f!r} while waiting for {text!r}')
                if text in line: return line
            if self.p and self.p.poll() is not None: raise RuntimeError(f'server exited waiting for {text!r}')
            time.sleep(.05)
        raise TimeoutError(text)
    def stop(self):
        if not self.p: return
        try:
            if self.p.poll() is None:
                self.send('stop'); self.p.wait(timeout=120)
        finally:
            if self.p.poll() is None: self.p.kill(); self.p.wait(timeout=20)
            if self.t: self.t.join(timeout=5)
            self.log.parent.mkdir(parents=True,exist_ok=True); self.log.write_text(''.join(self.lines),encoding='utf-8')

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('server_dir',type=Path); ap.add_argument('evidence_dir',type=Path); a=ap.parse_args()
    d=a.server_dir.resolve(); e=a.evidence_dir.resolve(); e.mkdir(parents=True,exist_ok=True)
    if not (d/'run.sh').is_file(): raise SystemExit(f'missing {d / "run.sh"}')
    h=H(d,e/'source-pairs-exact.log')
    try:
        h.start()
        for c in ('gamerule doMobSpawning false','gamerule maxEntityCramming 0','difficulty normal','forceload add 0 0','time set noon','kill @e[tag=harimt_perf]','kill @e[tag=harimt_local_noise]','fill 0 198 0 15 198 15 minecraft:stone','fill 0 199 0 15 203 15 minecraft:air'): h.send(c)
        time.sleep(2)
        for _ in range(256): h.send('execute in minecraft:overworld run summon minecraft:cow 8.5 199 8.5 {Tags:["harimt_perf"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}')
        for _ in range(512): h.send('execute in minecraft:overworld run summon minecraft:interaction 8.5 199 8.5 {Tags:["harimt_local_noise"],width:0.8f,height:1.8f,response:0b}')
        h.wait('Vulkan push broad-phase sustained: 10 consecutive verified batches completed',120,0)
        time.sleep(5)
        st=h.send('async gpu source-test'); line=h.wait(PASS,90,st)
        if 'population=768' not in line or 'sources=256' not in line: raise RuntimeError('PASS marker did not prove exact fixture sizes: '+line.strip())
        h.send('save-all flush'); time.sleep(2)
        (e/'PASS.txt').write_text(line,encoding='utf-8')
        print('[SOURCE-PAIRS-QA] exact mixed source-pairs verification PASS',flush=True)
    finally: h.stop()
    return 0
if __name__=='__main__': raise SystemExit(main())
