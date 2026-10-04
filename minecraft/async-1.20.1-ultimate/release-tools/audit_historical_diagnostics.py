import gzip,hashlib,json,re
from collections import Counter
from pathlib import Path
root=Path('hari-performance-release/evidence/diagnostics-repaired')
patterns={
 'private_key':re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'),
 'github_token':re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})\b'),
 'google_access_token':re.compile(r'\bya29\.[A-Za-z0-9_-]{20,}\b'),
 'bearer_token':re.compile(r'(?i)\bBearer\s+[A-Za-z0-9_.~-]{20,}'),
 'aws_key':re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
 'signed_url':re.compile(r'https?://[^\s\"<>]+[?&](?:X-Amz-Signature|sig|token)=[^\s\"<>]+',re.I),
 'email':re.compile(r'\b[A-Za-z0-9.!#$%&\x27*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+\b'),
}
modules=re.compile(r'(?:MC-BOOTSTRAP/|TRANSFORMER/)?(?:io\.netty\.[A-Za-z.]+|authlib|minecraft|fmlloader|cpw\.mods\.(?:modlauncher|bootstraplauncher)|org\.lwjgl|lwjgl\.vulkan|harimt|forge|java\.(?:base|management)|com\.sun\.jna|com\.electronwill\.nightconfig\.core|c2meforge)@\d+\.\d+(?:\.[A-Za-z0-9-]+)*')
files=sorted(p for p in root.rglob('*') if p.is_file());text_files=images=compressed=0;findings=[];false=Counter()
for path in files:
 data=path.read_bytes()
 if path.suffix.lower() in {'.png','.jpg','.jpeg','.gif'}:images+=1;continue
 if path.suffix=='.gz':data=gzip.decompress(data);compressed+=1
 if b'\x00' in data[:4096]:continue
 s=data.decode('utf-8','replace');text_files+=1
 for category,pattern in patterns.items():
  for m in pattern.finditer(s):
   value=m.group()
   if category=='email' and modules.fullmatch(value):false['Java module version in stack trace']+=1;continue
   findings.append({'category':category,'file':path.relative_to(root).as_posix(),'value_sha256':hashlib.sha256(value.encode()).hexdigest()})
 false['setup-java GITHUB_TOKEN variable reference']+=len(re.findall(r'\bGITHUB_TOKEN\b',s))
report={'schema':1,'product_commit':'6594c6cc652db554213c52ae02f7acd8f439644c','diagnostic_files':len(files),'scanned_text_files':text_files,'compressed_logs_scanned':compressed,'images':images,'unclassified_findings':findings,'false_positive_classifications':dict(false),'scope':'Text pattern scan, compressed log scan and provenance review. Screenshots originate from generated offline CI fixtures; no independent image secret scanner claim.','source':'29 immutable GitHub Actions artifact sets plus original complete job logs; no user worlds or launcher accounts','destination':'Existing Hari project Drive folder 1vq6hWD_QqVdxMlgYrupWbhVdlNDa8K-B; historical upload still requires specific approval after automatic rejection'}
Path('performance-evidence/DIAGNOSTIC-DISCLOSURE-AUDIT.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='unclassified_findings'}|{'unclassified_findings_count':len(findings)},indent=2))
