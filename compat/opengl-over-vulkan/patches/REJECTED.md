# Rejected WGL GLthread experiment

`wgl-glthread.patch` is preserved as rejected research, not an enabled build recipe. It attempted opt-in Windows WGL command marshalling and drained before context handoffs, sharing and framebuffer locks. The hardware probe failed before a renderer/readback result. Do not enable or ship this patch. The exact verified upstream DLLs were restored from checksummed recoverable copies; no game uses the rejected DLLs and no system driver/library changed. Further work needs native failure analysis and context/flush/ownership tests before benchmarking.
