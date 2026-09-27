#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
import threading
import time
from pathlib import Path


def main() -> int:
    p=argparse.ArgumentParser()
    p.add_argument("server_dir", type=Path)
    p.add_argument("log_file", type=Path)
    p.add_argument("--timeout", type=float, default=240.0)
    args=p.parse_args()

    server=args.server_dir.resolve()
    log=args.log_file.resolve()
    log.parent.mkdir(parents=True, exist_ok=True)

    proc=subprocess.Popen(
        ["./run.sh","nogui"],
        cwd=server,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    assert proc.stdin is not None and proc.stdout is not None
    lines: list[str]=[]

    def reader() -> None:
        assert proc.stdout is not None
        for line in proc.stdout:
            print(line,end="",flush=True)
            lines.append(line)

    t=threading.Thread(target=reader,daemon=True)
    t.start()
    deadline=time.monotonic()+args.timeout
    try:
        while time.monotonic()<deadline:
            joined="".join(lines[-500:])
            if "Done (" in joined:
                proc.stdin.write("save-all flush\n")
                proc.stdin.flush()
                time.sleep(2.0)
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                rc=proc.wait(timeout=120)
                t.join(timeout=5)
                if rc!=0:
                    raise RuntimeError(f"Forge seed server exited with code {rc}")
                if not (server/"world"/"level.dat").is_file():
                    raise RuntimeError("Forge seed server did not produce world/level.dat")
                return 0
            if proc.poll() is not None:
                raise RuntimeError("Forge seed server exited before Done")
            time.sleep(0.25)
        raise TimeoutError("Forge seed server timed out waiting for Done")
    finally:
        if proc.poll() is None:
            try:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                proc.wait(timeout=30)
            except Exception:
                proc.kill()
                proc.wait(timeout=10)
        t.join(timeout=5)
        log.write_text("".join(lines),encoding="utf-8")


if __name__=="__main__":
    sys.exit(main())
