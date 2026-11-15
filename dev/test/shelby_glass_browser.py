#!/usr/bin/env python3
"""Chromium/WebKit UI and action regression checks with a clearly simulated TV.

No real TV is contacted. PNG screenshots and report.json go to --out.
The separate shelby_pwa_server.py checks the actual Android RemoteService on 8765.
"""
import argparse
import asyncio
import json
import mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
from pathlib import Path
from urllib.parse import urlsplit
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / 'app/shared/web'
ALIASES = {'manifest.json': 'manifest-shelby-v1.json', 'icon-180.png': 'icon-shelby180v1.png',
           'icon-192.png': 'icon-shelby192v1.png', 'icon-512.png': 'icon-shelby512v1.png',
           'icon-maskable.png': 'icon-shelbymaskablev1.png'}


def tv_html(core):
    html = (WEB / 'index.html').read_text()
    if core == 'tv':
        html = html.replace('TV Remote', 'Shelby+ Remote').replace('content="#111418"', 'content="#080808"')
        html = html.replace('rel="icon" type="image/png" href="icon-192.png"', 'rel="icon" type="image/x-icon" href="icon-shelbyfaviconv1.ico"')
        for old, new in ALIASES.items():
            html = html.replace(old, new)
    return html.replace('<html ', f'<html data-core="{core}" ')


async def run_engine(pw, engine, args):
    launcher = getattr(pw, engine)
    options = {'executable_path': args.chromium_path, 'args': ['--no-sandbox']} if engine == 'chromium' and args.chromium_path else {}
    browser = await launcher.launch(**options)
    context = await browser.new_context(device_scale_factor=2, reduced_motion='reduce', color_scheme='dark', is_mobile=True, has_touch=True)
    await context.add_init_script("localStorage.setItem('lang','en');localStorage.setItem('theme','dark');localStorage.setItem('homehint','1')")
    host = await context.new_page()
    calls, errors = [], []
    mode = {'scope': 'full', 'paired': True, 'core': 'tv', 'media': None}
    mock = (ROOT / 'docs/demo/mock.js').read_text()
    for old, new in [('com.demo.tube', 'com.google.android.youtube.tv'), ('Tube', 'YouTube'),
                     ('com.demo.stream', 'com.netflix.ninja'), ('Streamly', 'Netflix'),
                     ('com.demo.music', 'com.spotify.tv.android'), ('Music Box', 'Spotify'),
                     ('com.demo.news', 'com.disney.disneyplus'), ('News Daily', 'Disney+')]:
        mock = mock.replace(old, new)

    async def api_reply(method, path, query, body):
        if path == '/api/app' and method == 'POST':
            # The click can finish before this threaded fixture receives/processes the POST.
            # Keep nonzero latency so an immediate Python-list assertion cannot pass by luck.
            await asyncio.sleep(0.25)
            if not mode['paired']:
                return {'status': 401, 'type': 'application/json', 'body': '{"error":"not paired"}'}
        if method == 'POST':
            calls.append({'path': path, 'body': json.loads(body or '{}'), 'authenticated': mode['paired']})
        if path == '/api/state' and not mode['paired']:
            return {'status': 401, 'type': 'application/json', 'body': '{"error":"not paired","name":"PRISM+ Google TV"}'}
        if path == '/api/playing' and mode['media']:
            media = mode['media']
            if method == 'POST':
                action = json.loads(body or '{}')
                if action.get('do') in ('play', 'pause'):
                    media['state'] = 'playing' if action['do'] == 'play' else 'paused'
                if action.get('do') == 'seek':
                    media['pos'] = action['ms']
            return {'status': 200, 'type': 'application/json', 'body': json.dumps(media)}
        result = await host.evaluate('(q)=>demoApi(q)', {'method': method, 'path': path, 'search': '?' + query if query else '', 'body': body})
        if path == '/api/state':
            state = json.loads(result['body'])
            state.update(name='PRISM+ Google TV', app_name='Shelby+ Remote', version='0.53.1', scope=mode['scope'], is_on=True)
            if mode['media']:
                state.update(app=mode['media']['pkg'], app_label=mode['media']['app'])
            result['body'] = json.dumps(state)
        return result

    # A real local HTTP fixture avoids WebKit's synchronous-XHR interception limitation.
    # Only the API is simulated; the normal remote.json loader and script execution run unchanged.
    loop = asyncio.get_running_loop()
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def respond(self, status, content_type, body):
            body = body.encode() if isinstance(body, str) else body
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            self.do_GET()

        def do_GET(self):
            url = urlsplit(self.path)
            path = url.path
            if path.startswith('/api/'):
                body = self.rfile.read(int(self.headers.get('Content-Length', '0'))).decode()
                result = asyncio.run_coroutine_threadsafe(api_reply(self.command, path, url.query, body), loop).result(timeout=25)
                return self.respond(result['status'], result['type'], result['body'])
            if path == '/__mock/host.html':
                return self.respond(200, 'text/html', '<script src="mock.js"></script>')
            if path == '/__mock/mock.js':
                return self.respond(200, 'text/javascript', mock)
            if path == '/':
                return self.respond(200, 'text/html', tv_html(mode['core']))
            asset = path.lstrip('/')
            if mode['core'] == 'tv':
                asset = ALIASES.get(asset, asset)
            file = WEB.parent / asset if asset.startswith('i18n/') else WEB / asset
            if file.resolve().is_relative_to(WEB.parent.resolve()) and file.is_file():
                return self.respond(200, mimetypes.guess_type(str(file))[0] or 'application/octet-stream', file.read_bytes())
            return self.respond(404, 'text/plain', 'missing')

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    ORIGIN = f'http://127.0.0.1:{server.server_address[1]}'
    await host.goto(ORIGIN + '/__mock/host.html')
    await host.wait_for_function('typeof demoApi === "function"')

    async def page_at(width):
        page = await context.new_page()
        await page.set_viewport_size({'width': width, 'height': 844})
        page.on('pageerror', lambda error: errors.append(str(error)))
        await page.goto(ORIGIN + '/')
        if mode['paired']:
            await page.wait_for_function('typeof ADB === "object" && ADB.state === "ready"')
            await page.evaluate('if (window.pickLeave) pickLeave()')
            if mode['scope'] == 'full':
                await page.locator('#quick .tile').first.wait_for()
        await page.wait_for_timeout(600)
        return page

    results = []
    for width in (320, 375, 390, 430, 1100):
        page = await page_at(width)
        assert not errors, errors
        assert await page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'{engine}/{width}: horizontal page overflow'
        pad = await page.locator('.rc .dpad').bounding_box()
        assert pad and abs(pad['width'] - pad['height']) < 1, 'D-pad stays circular'
        assert await page.locator('#name').inner_text() == 'PRISM+ Google TV'
        for selector in ['.shelby-mainkeys [data-k="POWER"]', '#r-home', '#r-back', '#r-shelby-menu', '.rc .dpad .ok', '.pill [data-k="VOL_UP"]', '.pill [data-k="VOL_DOWN"]', '#kbdbtn']:
            box = await page.locator(selector).bounding_box()
            assert box and box['width'] >= 44 and box['height'] >= 44, (engine, width, selector, box)
        assert await page.locator('.shelby-mainkeys button').first.evaluate('(el)=>getComputedStyle(el).transitionDuration') == '0s', 'Reduced motion ignored'
        await page.screenshot(path=args.out / f'shelby-glass-{engine}-{width}.png', full_page=True)
        baseline = await page.evaluate('document.documentElement.scrollHeight')
        for pkg, app in [('com.google.android.youtube.tv', 'YouTube'), ('org.smarttube.stable', 'SmartTube')]:
            mode['media'] = {'state': 'playing', 'title': ('Long video title • 字幕 🎬 ' * 35) + ('UnbrokenVideoTitle' * 70),
                             'sub': 'Channel name with a very long description ' * 20, 'app': app, 'pkg': pkg,
                             'pos': 62000, 'dur': 10800000, 'speed': 1, 'seek': True}
            await page.evaluate('refresh()')
            await page.evaluate('plPoll()')
            await page.wait_for_timeout(700)
            metrics = await page.evaluate('''() => {
              const r=document.querySelector('.rc .dpad').getBoundingClientRect();
              const p=document.querySelector('#playing').getBoundingClientRect();
              const n=document.querySelector('nav.tabs').getBoundingClientRect();
              return {scroll:document.documentElement.scrollWidth, viewport:innerWidth,
                scale:visualViewport?.scale || 1, doc:document.documentElement.scrollHeight,
                padWidth:r.width, padHeight:r.height, center:r.x+r.width/2,
                playerWidth:p.width, playerHeight:p.height, navLeft:n.left, navRight:n.right, navBottom:n.bottom};
            }''')
            assert metrics['scroll'] <= width and metrics['viewport'] == width, (engine, width, app, metrics)
            assert abs(metrics['scale']-1) < .02, (engine, width, 'unexpected zoom', metrics)
            assert abs(metrics['padWidth']-metrics['padHeight']) < 1 and abs(metrics['center']-width/2) < 2, metrics
            assert metrics['playerWidth'] <= width and metrics['playerHeight'] < 160, metrics
            assert metrics['doc'] <= baseline+200, (engine, width, 'media inflated height', metrics)
            assert metrics['navLeft'] >= 0 and metrics['navRight'] <= width and metrics['navBottom'] <= 844, metrics
            if width in (375,390):
                await page.screenshot(path=args.out / f'shelby-media-{engine}-{width}-{app.lower()}.png', full_page=True)
            if width == 390:
                before=len(calls)
                await page.locator('#plplay').click()
                await page.wait_for_timeout(600)
                assert any(c['path']=='/api/playing' and c['body'].get('do')=='pause' for c in calls[before:])
        mode['media'] = None
        await page.evaluate('plPoll()')
        if width == 390:
            if engine == 'chromium':
                media = await context.new_cdp_session(page)
                await media.send('Emulation.setEmulatedMedia', {'features': [{'name': 'prefers-reduced-transparency', 'value': 'reduce'}, {'name': 'prefers-reduced-motion', 'value': 'reduce'}]})
                assert await page.evaluate('getComputedStyle(document.documentElement).getPropertyValue("--glass").trim()') == '#242126', 'Reduced transparency ignored'
                await page.screenshot(path=args.out / 'shelby-glass-reduced-transparency.png', full_page=True)
                await media.send('Emulation.setEmulatedMedia', {'features': [{'name': 'prefers-reduced-motion', 'value': 'reduce'}]})
            for selector, key in [('.rc .dpad .ok', 'OK'), ('#r-home', 'HOME'), ('#r-back', 'BACK'), ('#r-shelby-menu', 'MENU'), ('.pill [data-k="VOL_UP"]', 'VOL_UP'), ('.pill [data-k="VOL_DOWN"]', 'VOL_DOWN'), ('.pill [data-k="CH_UP"]', 'CH_UP')]:
                before = len(calls)
                if key == 'OK':
                    await page.locator(selector).tap()
                else:
                    await page.locator(selector).click()
                await page.wait_for_timeout(150)
                assert any(c['path'] == '/api/key' and c['body'].get('key') == key for c in calls[before:]), key
            before = len(calls)
            await page.locator('.rc .dpad .up').click(delay=550)
            await page.wait_for_timeout(200)
            dirs = [c['body'].get('dir') for c in calls[before:] if c['path'] == '/api/key' and c['body'].get('key') == 'UP']
            assert dirs == ['START_LONG', 'END_LONG'], dirs
            before = len(calls)
            box = await page.locator('.rc .dpad .left').bounding_box()
            await page.mouse.move(box['x'] + box['width']/2, box['y'] + box['height']/2)
            await page.mouse.down()
            await page.mouse.move(1, 1)
            await page.mouse.up()
            await page.wait_for_timeout(150)
            assert not any(c['path'] == '/api/key' and c['body'].get('key') == 'LEFT' for c in calls[before:]), 'Canceled pointer sent a key'
            await page.locator('#kbdbtn').click()
            await page.locator('#text').fill('Shelby glass test')
            await page.wait_for_timeout(500)
            assert any(c['path'] == '/api/type' and 'text' in c['body'] for c in calls), 'Keyboard action disconnected'
            await page.screenshot(path=args.out / f'shelby-glass-{engine}-keyboard.png', full_page=True)
            await page.locator('#kbdbtn').click()
            assert await page.locator('#quick [data-pkg="com.google.android.youtube.tv"]').get_attribute('aria-label') == 'YouTube'
            await page.locator('#quickq').fill('YouTube')
            shortcut = page.locator('#quick .tile:visible')
            assert await shortcut.count() == 1, 'Search did not isolate YouTube'
            assert await shortcut.get_attribute('data-pkg') == 'com.google.android.youtube.tv'
            for activation in ('click', 'tap'):
                before = len(calls)
                # Register before activation; click/tap completion is not HTTP completion.
                async with page.expect_response(lambda r: r.url == ORIGIN + '/api/app' and r.request.method == 'POST', timeout=10000) as pending:
                    await getattr(shortcut, activation)()
                response = await pending.value
                assert response.request.post_data_json == {'link': 'com.google.android.youtube.tv'}, 'Wrong shortcut payload'
                assert response.status == 200 and (await response.json()).get('ok') is True, 'Shortcut request rejected'
                launched = [c for c in calls[before:] if c['path'] == '/api/app']
                assert launched == [{'path': '/api/app', 'body': {'link': 'com.google.android.youtube.tv'}, 'authenticated': True}], 'Shortcut action disconnected'

            await page.locator('#quickq').fill('')
            await page.locator('.shelby-utilities > summary').click()
            await page.locator('#morebtn').click()
            assert await page.locator('#more').is_visible()
            await page.locator('.shelby-utilities > summary').click()
            # Original navigation and all advanced pages remain reachable via the scrolling dock.
            await page.locator('[data-tab="apps"]').click()
            assert await page.locator('[data-panel="apps"]').is_visible()
            for tab in ('files', 'health', 'auto', 'tv', 'remote'):
                await page.locator(f'nav.tabs [data-tab="{tab}"]').click()
                try:
                    await page.locator(f'[data-panel="{tab}"]:visible').first.wait_for(state='visible', timeout=4000)
                except Exception:
                    await page.screenshot(path=args.out / f'failure-{engine}-{tab}.png', full_page=True)
                    print('Tab failure', tab, await page.evaluate('({tab:currentTab, scroll:document.querySelector("nav.tabs").scrollLeft, health:document.querySelector("[data-tab=health]").getBoundingClientRect().toJSON(), selected:[...document.querySelectorAll("nav.tabs [aria-selected=true]")].map(x=>x.dataset.tab), dialogs:[...document.querySelectorAll("dialog[open]")].map(x=>x.textContent.slice(0,150))})'))
                    raise
            await page.locator('#aboutbtn').click()
            assert await page.locator('dialog[open]').count() > 0
            await page.keyboard.press('Escape')
            before = len(calls)
            await page.locator('.shelby-mainkeys [data-k="POWER"]').click()
            assert not any(c['path'] == '/api/key' and c['body'].get('key') == 'POWER' for c in calls[before:]), 'Power bypassed confirmation'
            await page.get_by_role('button', name=await page.evaluate("T('remote.turn_off')"), exact=True).click()
            await page.wait_for_timeout(150)
            assert any(c['path'] == '/api/key' and c['body'].get('key') == 'POWER' for c in calls[before:])
        results.append({'engine': engine, 'width': width, 'overflow': False, 'controls': '44px minimum'})
        await page.close()
    # Pairing and view-only scope still gate the moved keyboard and real controls.
    mode['scope'] = 'remote'
    page = await page_at(390)
    assert await page.locator('#kbdbtn').is_visible(), 'Remote-only guest lost the existing keyboard'
    assert not await page.locator('nav.tabs [data-tab=apps]').is_visible(), 'Remote-only guest exposed owner tabs'
    await page.close()
    mode['scope'] = 'view'
    page = await page_at(390)
    assert not await page.locator('#kbdbtn').is_visible(), 'View-only scope exposed keyboard'
    assert not await page.locator('.shelby-utilities').is_visible(), 'View-only scope exposed moved controls'
    await page.close()
    mode.update(scope='full', paired=False)
    page = await page_at(390)
    await page.locator('#pair').wait_for(state='visible')
    assert not await page.locator('.rc .dpad').is_visible()
    rejected = await page.request.post(ORIGIN + '/api/app', data={'link': 'com.google.android.youtube.tv'})
    assert rejected.status == 401, 'Unpaired fixture accepted an app launch'
    await page.screenshot(path=args.out / f'shelby-glass-{engine}-pairing.png', full_page=True)
    await page.close()
    mode.update(paired=True, core='pc')
    page = await page_at(1100)
    assert await page.locator('.shelby-mainkeys').count() == 0, 'Other core layout changed'
    await page.close()
    assert not errors, errors
    await browser.close()
    server.shutdown()
    server.server_close()
    return results


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', choices=['all', 'chromium', 'webkit'], default='all')
    parser.add_argument('--out', type=Path, default=Path('/tmp/shelby-glass-review'))
    parser.add_argument('--chromium-path')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for source in [WEB / 'index.html', *list((WEB / 'css').glob('*.css')), *list((WEB / 'js').glob('*.js'))]:
        assert '3093A066-A1DA-4B74-91A4-6B080CA5D0FD' not in source.read_text(), 'Reference screenshot loaded as UI'
    results = []
    async with async_playwright() as pw:
        for engine in (['chromium', 'webkit'] if args.engine == 'all' else [args.engine]):
            results += await run_engine(pw, engine, args)
    report = {'fixture': 'Simulated PRISM+ and installed apps; no real TV or iPhone', 'results': results,
              'media': 'YouTube/SmartTube playback with long unbroken and multilingual titles: viewport, D-pad, compact player and safe dock PASS',
              'actions': 'OK/Home/Back/Menu/volume/channel, long press, cancellation, keyboard, app search/launch, tabs/settings and Power confirmation PASS',
              'security_ui': 'Pairing and view-only controls remain gated; PC layout unchanged', 'accessibility': '44px controls, circular D-pad, reduced motion and Chromium reduced-transparency fallback PASS'}
    (args.out / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    asyncio.run(main())
