#!/usr/bin/env python3
"""Read-only checks against the running TV app's real HTTP server, not a static fixture.

Run after installing/opening TV Setup on an isolated emulator and forwarding port 8765:
  python3 dev/test/shelby_pwa_server.py [http://127.0.0.1:8765/]
No pairing is granted and no remote commands are sent.
"""
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import struct
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / 'app/shared/web'
BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8765/'
assert urlsplit(BASE).port == 8765, 'Exercise the actual app port'


def fetch(path='', headers=None):
    try:
        response = urlopen(Request(urljoin(BASE, path), headers=headers or {}), timeout=10)
    except HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()


class Metadata(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = {}
        self.meta = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'link':
            self.links[attrs.get('rel')] = attrs
        if tag == 'meta':
            self.meta[attrs.get('name')] = attrs.get('content')


def main():
    for attempt in range(45):
        try:
            status, headers, body = fetch()
            if status == 200:
                break
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2)
    else:
        raise AssertionError('TV app did not start its HTTP server on 8765')
    assert headers['Cache-Control'] == 'no-store'
    html = body.decode()
    assert 'data-core="tv"' in html and '<title>Shelby+ Remote</title>' in html
    metadata = Metadata()
    metadata.feed(html)
    assert metadata.meta['application-name'] == 'Shelby+ Remote'
    assert metadata.meta['apple-mobile-web-app-title'] == 'Shelby+ Remote'
    assert metadata.meta['theme-color'] == '#080808'
    links = metadata.links
    assert links['apple-touch-icon']['sizes'] == '180x180'
    for rel in ('manifest', 'apple-touch-icon', 'icon'):
        assert '?v=' in links[rel]['href'], f'{rel} URL must be versioned'
    status, headers, body = fetch(links['manifest']['href'])
    assert status == 200 and headers['Cache-Control'] == 'no-store'
    assert headers.get_content_type() == 'application/manifest+json'
    manifest = json.loads(body)
    assert manifest['name'] == manifest['short_name'] == 'Shelby+ Remote'
    assert manifest['theme_color'] == manifest['background_color'] == '#080808'
    for icon in manifest['icons']:
        assert '?v=' in icon['src'] and 'shelby' in icon['src']
        status, headers, body = fetch(icon['src'])
        assert status == 200 and headers.get_content_type() == 'image/png'
        size = tuple(map(int, icon['sizes'].split('x')))
        assert struct.unpack('>II', body[16:24]) == size
        expected = (WEB / urlsplit(icon['src']).path).read_bytes()
        assert body == expected, 'Server must return the packaged official artwork'
    status, headers, body = fetch(links['apple-touch-icon']['href'])
    assert status == 200 and headers.get_content_type() == 'image/png'
    assert struct.unpack('>II', body[16:24]) == (180, 180)
    assert body == (WEB / 'icon-shelby180v1.png').read_bytes()
    print('Apple touch icon SHA-256:', hashlib.sha256(body).hexdigest())
    status, headers, body = fetch(links['icon']['href'])
    assert status == 200 and headers.get_content_type() == 'image/x-icon'
    assert body == (WEB / 'icon-shelbyfaviconv1.ico').read_bytes()
    for old, current in [('icon-180.png', 'icon-shelby180v1.png'), ('icon-192.png', 'icon-shelby192v1.png'),
                         ('icon-512.png', 'icon-shelby512v1.png'), ('icon-maskable.png', 'icon-shelbymaskablev1.png')]:
        status, headers, body = fetch(old)
        assert status == 200 and headers['Cache-Control'] == 'no-cache'
        assert body == (WEB / current).read_bytes(), 'Legacy TV icon URL must use new art too'
    status, headers, body = fetch('manifest.json')
    assert status == 200 and json.loads(body)['name'] == 'Shelby+ Remote'
    for asset in ['js/shelby.js', 'css/shelby.css']:
        status, headers, body = fetch(asset)
        assert status == 200 and body == (WEB / asset).read_bytes(), 'Glass UI asset missing from TV-hosted page'
    status, headers, body = fetch('remote.json')
    assert status == 200 and json.loads(body)['rows']['shelby'][0][0]['key'] == 'MENU'
    assert fetch('api/state')[0] == 401, 'API must still require pairing'
    assert fetch('api/state', {'Origin': 'http://other.example'})[0] == 403, 'Cross-origin API denied'
    assert fetch('', {'Host': 'other.example'})[0] == 403, 'DNS rebinding guard intact'
    assert 'serviceWorker.register' not in '\n'.join(p.read_text() for p in (WEB / 'js').glob('*.js'))
    print('TV RemoteService on port 8765: versioned manifest, PWA/Apple/favicon URLs, legacy aliases, cache policy and security checks PASS')
    print('Android TV emulator verification only; real iPhone Add to Home Screen remains untested.')


if __name__ == '__main__':
    main()
