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
    # Startup's existing ADB discovery may open Developer options asynchronously.
    # Return without changing any setting; the updater itself must work without ADB.
    for attempt in range(24):
        ui=tree()
        (output/"initial.xml").write_bytes(ET.tostring(ui))
        (output/"initial.png").write_bytes(adb("exec-out","screencap","-p"))
        if any('.settings' in n.get('package','') for n in ui.iter('node')):
            adb('shell','input','keyevent','4')
            continue
        if any(n.get('text')=='Check for updates' for n in ui.iter('node')):
            break
        tile=next(n for n in ui.iter('node') if n.get('content-desc')==f'Shelby+ Remote {version}' and n.get('clickable')=='true')
        if version == '0.53.3':
            label=next(n for n in ui.iter('node') if n.get('text')=='OTA Test v0.53.3')
            x1,y1,x2,y2=map(int,re.findall(r'\d+',label.get('bounds')))
            assert x2>x1 and y2>y1, 'OTA test label is not visible'
        if tile.get('focused')=='true':
            adb('shell','input','keyevent','23')
            continue
        focused=next((n for n in ui.iter('node') if n.get('focused')=='true'),None)
        def center(node):
            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
            return (x1+x2)/2,(y1+y2)/2
        x,y=center(tile)
        fx,fy=center(focused) if focused is not None else (x+200,y)
        key='20' if y-fy>40 else '19' if fy-y>40 else '22' if x>fx else '21'
        adb('shell','input','keyevent',key)
    else:
        raise AssertionError('Could not reach update menu using D-pad; see UI evidence')
    (output/'menu.xml').write_bytes(ET.tostring(ui))
    (output/'shelby-update-menu.png').write_bytes(adb('exec-out','screencap','-p'))
    check=next(n for n in ui.iter('node') if n.get('text')=='Check for updates')
    parents={child:parent for parent in ui.iter() for child in parent}
    while check.get('focusable')!='true': check=parents[check]
    assert check.get('focused')=='true','Update button must have initial remote focus'
    assert any(n.get('text')==f'Shelby+ Remote {version}' for n in ui.iter('node'))
    (output/'shelby-update-menu.png').write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','input','keyevent','23') # DPAD_CENTER, no touch-only flow
    ui=tree()
    assert any('Updates are not ready for this build.' in n.get('text','') for n in ui.iter('node')),'Unpinned validation APK must not offer production updates'
    (output/'shelby-update-disabled.png').write_bytes(adb('exec-out','screencap','-p'))
    print('Native Android TV update menu/current version, initial D-pad focus and validation-key gate PASS')
    print('Production notes/download/permission/self-install flow still requires a trusted production build/device test.')


if __name__=='__main__': main()
