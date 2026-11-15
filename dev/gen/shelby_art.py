#!/usr/bin/env python3
"""Package the exact uploaded Shelby+ image and proportional TV derivatives.

python3 dev/gen/shelby_art.py [--check]
Needs Pillow. Only Shelby+ TV and TV PWA resources are written; the root upload stays intact.
"""
import argparse
from pathlib import Path
import shutil
from PIL import Image, ImageChops, ImageOps

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / '0EC7D146-3DDF-4716-8FAC-91805B16C55B.png'
RES = ROOT / 'app/tvsetup/src/main/res'
ICON = RES / 'drawable-nodpi/shelby_icon.png'
BANNER = RES / 'drawable-xhdpi/shelby_banner.png'
SPLASH = RES / 'drawable/shelby_splash_logo.png'
WEB = ROOT / 'app/shared/web'
FAVICON = WEB / 'icon-shelbyfaviconv1.ico'


def contained(source, size):
    image = Image.new('RGB', size, 'black')
    logo = ImageOps.contain(source, size, Image.Resampling.LANCZOS)
    image.paste(logo, ((size[0] - logo.width) // 2, (size[1] - logo.height) // 2))
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    check = parser.parse_args().check
    source = Image.open(SOURCE).convert('RGB')
    assert source.width == source.height, 'Launcher source must remain square'
    images = {BANNER: contained(source, (320, 180)), SPLASH: contained(source, (160, 160))}
    for size in (180, 192, 512):
        images[WEB / f'icon-shelby{size}v1.png'] = contained(source, (size, size))
    # Keep the whole square artwork inside a maskable icon's central safe circle.
    maskable = Image.new('RGB', (512, 512), 'black')
    maskable.paste(contained(source, (280, 280)), (116, 116))
    images[WEB / 'icon-shelbymaskablev1.png'] = maskable
    if check:
        assert ICON.read_bytes() == SOURCE.read_bytes(), 'Launcher icon must be the exact uploaded image'
        for path, expected in images.items():
            actual = Image.open(path).convert('RGB')
            assert actual.size == expected.size, f'Wrong dimensions: {path}'
            assert ImageChops.difference(actual, expected).getbbox() is None, f'Distorted or stale branding: {path}'
        assert Image.open(FAVICON).ico.sizes() == {(16, 16), (32, 32), (48, 48)}, 'Favicon sizes'
        print('Exact launcher source, TV artwork and 180/192/512 PWA artwork: OK')
    else:
        for path in [ICON, *images]:
            path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(SOURCE, ICON)
        for path, image in images.items():
            image.save(path, optimize=True)
        source.save(FAVICON, format='ICO', sizes=[(16, 16), (32, 32), (48, 48)])
        print('Shelby+ TV and PWA artwork written')


if __name__ == '__main__':
    main()
