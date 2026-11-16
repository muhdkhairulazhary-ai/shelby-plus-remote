#!/usr/bin/env python3
"""Native TV glass dashboard: real state, QR decode, D-pad focus and 720p/1080p/4K evidence.
Only resize the disposable CI emulator. No pairing reset or production update is performed.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/shelby-dashboard.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/shelby-dashboard.xml'))


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def main():
    import zxingcpp
    from PIL import Image
    output = Path(sys.argv[1]); output.mkdir(parents=True, exist_ok=True)
    version = Path('app/tvsetup/VERSION').read_text().strip()
    # The API 30 TV image's config_maxUiWidth caps its UI at 1920. The same native
    # dashboard's genuine 4K canvas is checked separately on a large-screen Android AVD.
    cases = [('4k',3840,2160,480)] if '--only-4k' in sys.argv else [
        ('720p',1280,720,160), ('1080p',1920,1080,240), ('compact-1080p',1920,1080,320)]
    try:
        for label, width, height, density in cases:
            adb('shell', 'wm', 'size', f'{width}x{height}')
            adb('shell', 'wm', 'density', str(density))
            (output/f'{label}-display.txt').write_bytes(adb('shell','wm','size') + adb('shell','wm','density'))
            # Measure a fresh launch at each settled display configuration, not an intermediate wm resize.
            # -S stops only the disposable emulator process; it retains app data, pairing and settings.
            adb('shell', 'am', 'start', '-S', '-W', '-n', 'dev.tvremote.setup/.MainActivity')
            for _ in range(15):
                ui = tree()
                if any('.settings' in n.get('package','') for n in ui.iter('node')):
                    adb('shell','input','keyevent','4'); continue
                if any(n.get('text') == 'Developed by plus+' for n in ui.iter('node')): break
                time.sleep(.3)
            (output/f'{label}.xml').write_bytes(ET.tostring(ui))
            path = output/f'{label}.png'
            path.write_bytes(adb('exec-out','screencap','-p'))
            assert Image.open(path).size == (width,height), (label,'Emulator did not render requested resolution',Image.open(path).size,(width,height))
            nodes = list(ui.iter('node'))
            for text in ['Shelby+ Remote', 'Your TV. Your Control.', 'Developed by plus+', 'Control from a phone', 'About / Licenses', 'Check for updates']:
                node = next((n for n in nodes if n.get('text') == text), None)
                assert node is not None, (label, 'Required control/branding missing', text, [n.get('text') for n in nodes if n.get('text')])
                x1,y1,x2,y2 = bounds(node)
                assert 0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height, (label,text,bounds(node))
            assert any(n.get('content-desc') == f'Shelby+ Remote {version}' for n in nodes)
            assert not any('Hoang Tran' in n.get('text','') for n in nodes), 'Dashboard branding still exposes old developer'
            decoded = zxingcpp.read_barcodes(Image.open(path))
            assert len(decoded) == 1 and decoded[0].valid, (label,'QR is not scannable',decoded)
            data = decoded[0].text
            assert re.fullmatch(r'https?://[^/]+:876[56]',data), (label,'Unexpected remote QR URL',data)
            assert data.split('://',1)[1] in [n.get('text') for n in nodes], 'QR and live server address differ'
            assert 'v'+version in ' '.join(n.get('text','') for n in nodes)
            # D-pad visits every actionable dashboard group, including horizontally hidden TV Apps.
            seen = set()
            for key in [20,21,21,19,22,22,22,20,21,21,21,21,21,22,22,22,22,22,22,20,22,20,20,20,19,19]:
                adb('shell','input','keyevent',str(key))
                view = tree()
                focused = [n for n in view.iter('node') if n.get('focused') == 'true']
                assert focused, (label,'D-pad focus lost')
                n = focused[0]
                # Focusable groups expose their labels through child TextViews. A class name alone
                # collapses every distinct LinearLayout action into the same apparent target.
                seen.add((n.get('class'), n.get('bounds'), n.get('content-desc'),
                          tuple(c.get('text') for c in n.iter('node') if c.get('text'))))
                x1,y1,x2,y2 = bounds(n)
                assert 0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height, (label,'Focus outside viewport',bounds(n))
            assert len(seen) >= 5, (label,'D-pad navigation stuck',seen)
            (output/f'{label}-focus.png').write_bytes(adb('exec-out','screencap','-p'))
            if label == '720p':
                for _ in range(24):
                    view = tree()
                    about = next(n for n in view.iter('node') if n.get('text') == 'About / Licenses')
                    focused = next(n for n in view.iter('node') if n.get('focused') == 'true')
                    if about.get('focused') == 'true':
                        adb('shell','input','keyevent','23'); break
                    x1,y1,x2,y2 = bounds(about); a,b,c,d = bounds(focused)
                    dx = (x1+x2-a-c)/2; dy = (y1+y2-b-d)/2
                    key = 20 if dy > 40 else 19 if dy < -40 else 22 if dx > 0 else 21
                    adb('shell','input','keyevent',str(key))
                else: raise AssertionError('About / Licenses cannot be reached using D-pad')
                legal = tree()
                assert any('Copyright (c) 2026 Hoang Tran' in n.get('text','') and 'MIT License' in n.get('text','') for n in legal.iter('node')), 'Complete upstream attribution is inaccessible'
                assert any(n.get('text') == 'tvremote.pages.dev' for n in legal.iter('node')), 'Upstream functional link missing'
                (output/'licenses.xml').write_bytes(ET.tostring(legal))
                (output/'licenses.png').write_bytes(adb('exec-out','screencap','-p'))
                adb('shell','input','keyevent','4')
            print(f'{label}: visible branding/actions, version, live QR/address, bounded D-pad focus PASS ({len(seen)} targets)')
    finally:
        adb('shell','wm','size','1280x720')
        adb('shell','wm','density','160')
        adb('shell','am','start','-n','dev.tvremote.setup/.MainActivity')


if __name__ == '__main__': main()
