#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
SPREAD='spread-256-single-section'; DENSE='dense-256-vulkan-push'; FAR='dense-256-plus-512-far-markers'; LOCAL='dense-256-plus-512-local-nonsources'; AFFINITY='affinity-1024-64-chunks'
ORDER=(SPREAD,DENSE,FAR,LOCAL,AFFINITY); SAMPLES=7
EXPECTED={SPREAD:(256,0,0),DENSE:(256,0,0),FAR:(256,512,0),LOCAL:(256,0,512),AFFINITY:(1024,0,0)}
TAGGED=re.compile(r'HMT_PERF_STATS mspt=([0-9]+(?:\.[0-9]+)?) entities=([0-9]+) asyncEntities=([0-9]+) perfTagged=([0-9]+) noiseTagged=([0-9]+) localNoiseTagged=([0-9]+)')
def load(p): return json.loads(p.read_text())
def wmap(d): return {w['name']:w for w in d['workloads']}
def metric(w,n): return float(w['mspt_summary'][n])
def pct(b,a): return 0.0 if b==0 else (a-b)*100.0/b
def tags(p):
    m=list(TAGGED.finditer((p.parent/'server.log').read_text(errors='replace'))); need=len(ORDER)*SAMPLES
    if len(m)!=need: raise SystemExit(f'expected {need} tagged samples in {p.parent / "server.log"}, found {len(m)}')
    return {name:[(int(x.group(4)),int(x.group(5)),int(x.group(6))) for x in m[i*SAMPLES:(i+1)*SAMPLES]] for i,name in enumerate(ORDER)}
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('baseline',type=Path); ap.add_argument('candidate',type=Path); ap.add_argument('output',type=Path); a=ap.parse_args()
    b=wmap(load(a.baseline)); c=wmap(load(a.candidate)); bt=tags(a.baseline); ct=tags(a.candidate)
    missing=[n for n in ORDER if n not in b or n not in c]
    if missing: raise SystemExit(f'missing workloads: {missing}')
    r={'pass':True,'workloads':{},'gates':[]}
    def gate(n,ok,d):
        r['gates'].append({'name':n,'pass':bool(ok),'detail':d})
        if not ok: r['pass']=False
    for n in ORDER:
        bw,cw=b[n],c[n]; bm,bmean,bp95=metric(bw,'median'),metric(bw,'mean'),metric(bw,'p95'); cm,cmean,cp95=metric(cw,'median'),metric(cw,'mean'),metric(cw,'p95'); bd=float(bw.get('gpu',{}).get('last_dispatch_ms',0.0)); cd=float(cw.get('gpu',{}).get('last_dispatch_ms',0.0))
        r['workloads'][n]={'baseline_median_mspt':bm,'candidate_median_mspt':cm,'median_change_percent':pct(bm,cm),'baseline_mean_mspt':bmean,'candidate_mean_mspt':cmean,'mean_change_percent':pct(bmean,cmean),'baseline_p95_mspt':bp95,'candidate_p95_mspt':cp95,'p95_change_percent':pct(bp95,cp95),'baseline_last_gpu_dispatch_ms':bd,'candidate_last_gpu_dispatch_ms':cd,'gpu_change_percent':pct(bd,cd)}
        exp=EXPECTED[n]; gate(f'{n}: exact tagged population',all(v==exp for v in bt[n]+ct[n]),f'expected={exp} baseline={bt[n]} candidate={ct[n]}'); gate(f'{n}: tagged population parity',bt[n]==ct[n],f'baseline={bt[n]} candidate={ct[n]}')
        gate(f'{n}: median MSPT <=3% regression',cm<=bm*1.03,f'baseline={bm:.3f} candidate={cm:.3f} change={pct(bm,cm):+.2f}%'); gate(f'{n}: mean MSPT <=3% regression',cmean<=bmean*1.03,f'baseline={bmean:.3f} candidate={cmean:.3f} change={pct(bmean,cmean):+.2f}%'); gate(f'{n}: p95 MSPT <=5% regression',cp95<=bp95*1.05,f'baseline={bp95:.3f} candidate={cp95:.3f} change={pct(bp95,cp95):+.2f}%')
        if n!=AFFINITY: gate(f'{n}: Vulkan dispatch <=8% regression',cd<=bd*1.08 if bd>0 else cd==0,f'baseline={bd:.3f} candidate={cd:.3f} change={pct(bd,cd):+.2f}%')
    lb,lc=b[LOCAL],c[LOCAL]; gate('local mixed: >=5% median MSPT gain',metric(lc,'median')<=metric(lb,'median')*.95,f"baseline={metric(lb,'median'):.3f} candidate={metric(lc,'median'):.3f} gain={-pct(metric(lb,'median'),metric(lc,'median')):.2f}%"); gate('local mixed: >=5% mean MSPT gain',metric(lc,'mean')<=metric(lb,'mean')*.95,f"baseline={metric(lb,'mean'):.3f} candidate={metric(lc,'mean'):.3f} gain={-pct(metric(lb,'mean'),metric(lc,'mean')):.2f}%")
    lbd=float(lb.get('gpu',{}).get('last_dispatch_ms',0.0)); lcd=float(lc.get('gpu',{}).get('last_dispatch_ms',0.0)); gate('local mixed: >=15% Vulkan dispatch gain',lbd>0 and lcd<=lbd*.85,f'baseline={lbd:.3f} candidate={lcd:.3f} gain={-pct(lbd,lcd):.2f}%')
    base_med=sum(metric(b[n],'median') for n in ORDER)/len(ORDER); cand_med=sum(metric(c[n],'median') for n in ORDER)/len(ORDER); base_mean=sum(metric(b[n],'mean') for n in ORDER)/len(ORDER); cand_mean=sum(metric(c[n],'mean') for n in ORDER)/len(ORDER)
    gate('all-workload aggregate: >=3% median MSPT gain',cand_med<=base_med*.97,f'baseline={base_med:.3f} candidate={cand_med:.3f} gain={-pct(base_med,cand_med):.2f}%'); gate('all-workload aggregate: >=3% mean MSPT gain',cand_mean<=base_mean*.97,f'baseline={base_mean:.3f} candidate={cand_mean:.3f} gain={-pct(base_mean,cand_mean):.2f}%')
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(r,indent=2,sort_keys=True)+'\n')
    for g in r['gates']: print(f"[{'PASS' if g['pass'] else 'FAIL'}] {g['name']}: {g['detail']}")
    return 0 if r['pass'] else 1
if __name__=='__main__': raise SystemExit(main())
