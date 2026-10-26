#!/usr/bin/env python3
"""Bounded screenshots of the actual packaged-client world-entry flow; not acceptance."""
import argparse,json,os,subprocess,time
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True)
    p.add_argument('--display',default=':91');args,rest=p.parse_known_args()
    evidence=args.evidence.resolve();evidence.mkdir(parents=True,exist_ok=True)
    env=os.environ.copy();env['DISPLAY']=args.display
    command=['python3',str(Path(__file__).with_name('hari24_modpack_client_qa.py')),
             '--evidence',str(evidence/'client'),'--display',args.display]+rest
    snapshots=[];wid=None
    with (evidence/'diagnostic-console.log').open('w') as log:
        proc=subprocess.Popen(command,env=env,stdout=log,stderr=subprocess.STDOUT)
        start=time.monotonic()
        try:
            for second in (30,60,90,120,150,180):
                while proc.poll() is None and time.monotonic()-start<second:time.sleep(1)
                windows=subprocess.run(['xdotool','search','--onlyvisible','--name','Minecraft'],env=env,
                                       text=True,capture_output=True,timeout=10)
                if windows.stdout.strip():
                    wid=windows.stdout.splitlines()[-1];shot=evidence/f'world-entry-{second}s.png'
                    subprocess.run(['import','-display',args.display,'-window',wid,str(shot)],env=env,check=True,timeout=20)
                    snapshots.append(shot.name)
                if proc.poll() is not None:break
        finally:
            if proc.poll() is None:
                if wid:subprocess.run(['xdotool','windowactivate','--sync',wid,'key','--clearmodifiers','alt+F4'],env=env,timeout=20)
                try:proc.wait(timeout=45)
                except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=30)
    receipt={'scope':'Diagnostic only: captured original world-entry UI without bypassing any dialog',
             'snapshots':snapshots,'client_harness_exit':proc.returncode,'native_acceptance':False}
    (evidence/'DIAGNOSTIC-ONLY.json').write_text(json.dumps(receipt,indent=2)+'\n')
    if not snapshots:raise RuntimeError('No native world-entry screenshot captured; inspect original logs')
    print(json.dumps(receipt))

if __name__=='__main__':main()
