#!/usr/bin/env python3
"""Android TV emulator: real update menu, D-pad action, disabled unpinned test-build guard and screenshot."""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args):
    return subprocess.check_output(['adb',*args],timeout=25)


def tree():
    adb('shell','uiautomator','dump','/sdcard/shelby-update-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/shelby-update-ui.xml'))


def main():
    output=Path(sys.argv[1]); output.mkdir(parents=True,exist_ok=True)
    version=Path('app/tvsetup/VERSION').read_text().strip()
    ui=tree()
    tile=next(n for n in ui.iter('node') if n.get('content-desc')==f'Shelby+ Remote {version}' and n.get('clickable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',tile.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    ui=tree()
    check=next(n for n in ui.iter('node') if n.get('text')=='Check for updates')
    assert check.get('focusable')=='true' and check.get('focused')=='true','Update button must have initial remote focus'
    assert any(n.get('text')==f'Shelby+ Remote {version}' for n in ui.iter('node'))
    (output/'shelby-update-menu.png').write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','input','keyevent','23') # DPAD_CENTER, no touch-only flow
    ui=tree()
    assert any('Updates are not ready for this build.' in n.get('text','') for n in ui.iter('node')),'Unpinned validation APK must not offer production updates'
    (output/'shelby-update-disabled.png').write_bytes(adb('exec-out','screencap','-p'))
    print('Native Android TV update menu/current version, initial D-pad focus and validation-key gate PASS')
    print('Production notes/download/permission/self-install flow still requires a trusted production build/device test.')


if __name__=='__main__': main()
