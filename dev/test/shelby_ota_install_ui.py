#!/usr/bin/env python3
"""Isolated emulator only: prove standard Android confirmation, then confirm the disposable-key test update."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args): return subprocess.check_output(['adb',*args],timeout=25)
def tree():
    adb('shell','uiautomator','dump','/sdcard/ota-install-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/ota-install-ui.xml'))
def code():
    return int(re.search(r'versionCode=(\d+)',adb('shell','dumpsys','package','dev.tvremote.setup').decode())[1])
def center(node):
    a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds')))
    return (a+c)/2,(b+d)/2


def main():
    output=Path(sys.argv[1]);output.mkdir(parents=True,exist_ok=True)
    major,minor,patch=map(int,Path("app/tvsetup/VERSION").read_text().strip().split("."))
    candidate=100000+major*10000+minor*100+patch
    assert candidate>105304
    assert code()==105304,'Candidate installed without user confirmation'
    for _ in range(24):
        ui=tree()
        (output/'confirmation.xml').write_bytes(ET.tostring(ui))
        (output/'confirmation.png').write_bytes(adb('exec-out','screencap','-p'))
        buttons=[n for n in ui.iter('node') if ('packageinstaller' in n.get('package','') or 'permissioncontroller' in n.get('package','')) and n.get('text','').lower() in ('update','install') and n.get('enabled')=='true']
        if not buttons:
            # Existing startup discovery can open developer options; return without changing a setting.
            if any('.settings' in n.get('package','') for n in ui.iter('node')):
                adb('shell','input','keyevent','4');continue
            raise AssertionError('No standard Android install/update confirmation found')
        button=buttons[0];parents={c:p for p in ui.iter() for c in p}
        while button.get('focusable')!='true':button=parents[button]
        if button.get('focused')=='true':
            assert code()==105304,'Update happened before confirmation'
            adb('shell','input','keyevent','23') # Explicit emulated user consent.
            break
        focused=next((n for n in ui.iter('node') if n.get('focused')=='true'),None)
        x,y=center(button);fx,fy=center(focused) if focused is not None else (x-100,y)
        key='20' if y-fy>40 else '19' if fy-y>40 else '22' if x>fx else '21'
        adb('shell','input','keyevent',key)
    else:raise AssertionError('Could not focus Android confirmation with D-pad')
    until=time.monotonic()+45
    while time.monotonic()<until:
        if code()==candidate:break
        time.sleep(.5)
    else:raise AssertionError('User-confirmed package update did not complete')
    (output/'installed.png').write_bytes(adb('exec-out','screencap','-p'))
    print(f'Standard Android PackageInstaller confirmation + D-pad consent + in-place105304->{candidate} PASS (isolated test-key emulator only)')


if __name__=='__main__':main()
