#!/usr/bin/env python3
"""Original complete dependency closure, pinned to the 2.4.10 candidate."""
import os
import runpy
from pathlib import Path
import hari24_modpack_inputs as base
base.CANDIDATE_SHA = '8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f'
if os.environ.get('HARI_C2ME98') == '1':
    runpy.run_path(str(Path(__file__).with_name('hari24_provider_inputs.py')), run_name='__main__')
else:
    base.main()
