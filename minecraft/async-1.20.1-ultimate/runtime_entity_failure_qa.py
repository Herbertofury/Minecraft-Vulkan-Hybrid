#!/usr/bin/env python3
"""Fault a real asynchronous entity tick, require fatal reporting, then reopen the saved world."""
import argparse,os,signal,subprocess,threading,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('server',type=Path);p.add_argument('evidence',type=Path);args=p.parse_args()
server=args.server.resolve();evidence=args.evidence.resolve();evidence.mkdir(parents=True,exist_ok=True)
marker='HARI_QA_INTENTIONAL_ASYNC_ENTITY_TICK_FAULT'
for fault in (True,False):
    lines=[];env=os.environ.copy()
    options=env.get('JDK_JAVA_OPTIONS','')+' -Dharimt.vulkan.allowCpuDevice=true'
    if fault:options+=' -Dharimt.qa.entityFault=true'
    env['JDK_JAVA_OPTIONS']=options
    proc=subprocess.Popen(['bash','run.sh','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,env=env,start_new_session=True)
    def read():
        for line in proc.stdout:lines.append(line)
    thread=threading.Thread(target=read,daemon=True);thread.start()
    try:
        deadline=time.monotonic()+180
        if fault:
            while proc.poll() is None and time.monotonic()<deadline:time.sleep(.2)
            if proc.poll() is None:raise TimeoutError('async entity tick failure did not stop the server')
            thread.join(5);joined=''.join(lines)
            for required in (marker,'Error during async tick','Encountered an unexpected exception','Stopping server','Saving worlds'):
                if required not in joined:raise RuntimeError('Missing real asynchronous failure marker: '+required)
            reports=[path for path in (server/'crash-reports').glob('*.txt') if marker in path.read_text()]
            if not reports:raise RuntimeError('Async entity tick failure produced no matching Minecraft crash report')
            if not any('Description: Exception ticking world' in path.read_text() for path in reports):raise RuntimeError('The intentional entity failure did not reach Minecraft server tick-loop reporting')
            for path in reports:(evidence/path.name).write_bytes(path.read_bytes())
        else:
            while 'Done (' not in ''.join(lines) and time.monotonic()<deadline:
                if proc.poll() is not None:raise RuntimeError('world failed to reopen after async entity crash')
                time.sleep(.2)
            if 'Done (' not in ''.join(lines):raise TimeoutError('world reopen did not finish')
            def query_until(command, required):
                next_query=0
                while required not in ''.join(lines) and time.monotonic()<deadline:
                    if proc.poll() is not None:raise RuntimeError('reopened server exited before '+required+'; inspect post-entity-fault-reopen.log')
                    if time.monotonic()>=next_query:
                        proc.stdin.write(command+'\n');proc.stdin.flush();next_query=time.monotonic()+2
                    time.sleep(.2)
                if required not in ''.join(lines):raise RuntimeError('saved-world recovery did not satisfy '+required)
            query_until('execute if entity @e[tag=harimt_qa] run say HARI_QA_POST_FAULT_ENTITIES_PRESENT','HARI_QA_POST_FAULT_ENTITIES_PRESENT')
            query_until('execute store result score entities harimtCount if entity @e[tag=harimt_qa]\nexecute if score entities harimtCount matches 256 run say HARI_QA_POST_FAULT_COUNT_256','HARI_QA_POST_FAULT_COUNT_256')
            proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=90);thread.join(5)
            for fatal in ('Encountered an unexpected exception','Exception ticking world','Exception in server tick loop','MixinApplyError','NoSuchMethodError','ServerHangWatchdog','Off-thread world random access','Async entity load','Async entity unload',marker):
                if fatal in ''.join(lines):raise RuntimeError('unexpected reopened-world failure: '+fatal)
        print('PASS: '+('real async tick failure surfaced and server saved' if fault else 'saved world reopened with entities retained'))
    finally:
        if proc.poll() is None:
            os.killpg(proc.pid,signal.SIGTERM)
            try:proc.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=10)
        thread.join(5)
        (evidence/('async-entity-fault.log' if fault else 'post-entity-fault-reopen.log')).write_text(''.join(lines))
