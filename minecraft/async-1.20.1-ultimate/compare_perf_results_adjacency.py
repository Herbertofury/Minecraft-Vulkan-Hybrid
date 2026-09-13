#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path

NOISE='dense-256-plus-512-far-markers'
DENSE='dense-256-vulkan-push'
SPREAD='spread-256-single-section'
ORDER=(SPREAD,DENSE,NOISE)
SAMPLES=7
EXPECTED_PERF=256
EXPECTED_NOISE={SPREAD:0,DENSE:0,NOISE:512}
TAGGED=re.compile(r'HMT_PERF_STATS mspt=([0-9]+(?:\.[0-9]+)?) entities=([0-9]+) asyncEntities=([0-9]+) perfTagged=([0-9]+) noiseTagged=([0-9]+)')

def load(p:Path): return json.loads(p.read_text())
def wmap(doc): return {w['name']:w for w in doc['workloads']}
def metric(w,n): return float(w['mspt_summary'][n])
def pct(before,after): return 0.0 if before==0 else (after-before)*100.0/before

def tags(p:Path):
    log=p.parent/'server.log'
    matches=list(TAGGED.finditer(log.read_text(errors='replace')))
    if len(matches)!=len(ORDER)*SAMPLES:
        raise SystemExit(f'expected {len(ORDER)*SAMPLES} tagged samples in {log}, found {len(matches)}')
    out={}
    for i,name in enumerate(ORDER):
        chunk=matches[i*SAMPLES:(i+1)*SAMPLES]
        out[name]=[{'perf':int(m.group(4)),'noise':int(m.group(5))} for m in chunk]
    return out

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('baseline',type=Path); ap.add_argument('candidate',type=Path); ap.add_argument('output',type=Path); a=ap.parse_args()
    b=wmap(load(a.baseline)); c=wmap(load(a.candidate)); bt=tags(a.baseline); ct=tags(a.candidate)
    report={'pass':True,'workloads':{},'gates':[]}
    def gate(name,ok,detail):
        report['gates'].append({'name':name,'pass':bool(ok),'detail':detail})
        if not ok: report['pass']=False
    for name in ORDER:
        bw,cw=b[name],c[name]
        bm,bmean=metric(bw,'median'),metric(bw,'mean')
        cm,cmean=metric(cw,'median'),metric(cw,'mean')
        bd=float(bw.get('gpu',{}).get('last_dispatch_ms',0.0)); cd=float(cw.get('gpu',{}).get('last_dispatch_ms',0.0))
        report['workloads'][name]={'baseline_median_mspt':bm,'candidate_median_mspt':cm,'median_change_percent':pct(bm,cm),'baseline_mean_mspt':bmean,'candidate_mean_mspt':cmean,'mean_change_percent':pct(bmean,cmean),'baseline_p95_mspt':metric(bw,'p95'),'candidate_p95_mspt':metric(cw,'p95'),'baseline_last_gpu_dispatch_ms':bd,'candidate_last_gpu_dispatch_ms':cd}
        expected_noise=EXPECTED_NOISE[name]
        gate(f'{name}: exact tagged population',all(x['perf']==EXPECTED_PERF and x['noise']==expected_noise for x in bt[name]+ct[name]),f'expected perf={EXPECTED_PERF} noise={expected_noise}; baseline={bt[name]}; candidate={ct[name]}')
        gate(f'{name}: tagged population parity',bt[name]==ct[name],f'baseline={bt[name]} candidate={ct[name]}')
        gate(f'{name}: Vulkan dispatch no-regression',cd <= bd*1.08 if bd>0 else cd==0,f'baseline={bd:.3f} ms candidate={cd:.3f} ms change={pct(bd,cd):+.2f}%')

    for stat in ('median','mean','p95'):
        bv,cv=metric(b[SPREAD],stat),metric(c[SPREAD],stat)
        gate(f'spread: {stat} MSPT <=5% regression',cv <= bv*1.05,f'baseline={bv:.3f} candidate={cv:.3f} change={pct(bv,cv):+.2f}%')

    for name in (DENSE,NOISE):
        for stat in ('median','mean','p95'):
            bv,cv=metric(b[name],stat),metric(c[name],stat)
            gate(f'{name}: {stat} MSPT <=3% regression',cv <= bv*1.03,f'baseline={bv:.3f} candidate={cv:.3f} change={pct(bv,cv):+.2f}%')

    base_dense_median=(metric(b[DENSE],'median')+metric(b[NOISE],'median'))/2.0
    cand_dense_median=(metric(c[DENSE],'median')+metric(c[NOISE],'median'))/2.0
    base_dense_mean=(metric(b[DENSE],'mean')+metric(b[NOISE],'mean'))/2.0
    cand_dense_mean=(metric(c[DENSE],'mean')+metric(c[NOISE],'mean'))/2.0
    gate('dense aggregate: >=2% median MSPT gain',cand_dense_median <= base_dense_median*0.98,f'baseline={base_dense_median:.3f} candidate={cand_dense_median:.3f} gain={-pct(base_dense_median,cand_dense_median):.2f}%')
    gate('dense aggregate: >=2% mean MSPT gain',cand_dense_mean <= base_dense_mean*0.98,f'baseline={base_dense_mean:.3f} candidate={cand_dense_mean:.3f} gain={-pct(base_dense_mean,cand_dense_mean):.2f}%')

    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
    for g in report['gates']:
        print(f"[{'PASS' if g['pass'] else 'FAIL'}] {g['name']}: {g['detail']}")
    print(json.dumps(report,indent=2,sort_keys=True))
    return 0 if report['pass'] else 1

if __name__=='__main__': raise SystemExit(main())
