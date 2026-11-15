#!/usr/bin/env python3
"""Public release/version checks, before any protected key is opened."""
import json
import os
from pathlib import Path
import re
import subprocess


def version_code(version):
    if not re.fullmatch(r'(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)', version):
        raise ValueError('Use major.minor.patch, each component 0..99 without leading zeros')
    major, minor, patch = map(int, version.split('.'))
    return 100000 + major*10000 + minor*100 + patch


def validate(version, committed, releases, refs):
    code = version_code(version)
    if version != committed.strip():
        raise ValueError('Version input must match reviewed app/tvsetup/VERSION')
    tag = f'shelby-tv-v{version}-1'
    if any(r['ref'] == 'refs/tags/'+tag for r in refs) or any(r['tag_name'] == tag for r in releases):
        raise ValueError('Release tag already exists; it must never be overwritten')
    for release in releases:
        match = re.fullmatch(r'shelby-tv-v(.+)-[0-9]+', release['tag_name'])
        if not match:
            continue
        previous = version_code(match[1])
        if previous == code:
            raise ValueError('Duplicate release version')
        if not release['draft'] and not release['prerelease'] and previous >= code:
            raise ValueError('Version code must exceed every published stable release')
    return tag


def api(path):
    result = subprocess.run(['gh','api',path], capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


def main():
    repo = os.environ['GH_REPO']
    version = os.environ['RELEASE_VERSION']
    version_code(version)  # Validate before using input in any API path.
    pages = subprocess.run(['gh','api','--paginate','--slurp',f'repos/{repo}/releases?per_page=100'],capture_output=True,text=True,check=True)
    releases = [r for page in json.loads(pages.stdout) for r in page]
    refs = api(f'repos/{repo}/git/matching-refs/tags/shelby-tv-v{version}-')
    tag = validate(version,Path('app/tvsetup/VERSION').read_text(),releases,refs)
    # Existing production metadata is required to keep its pinned identity; old temporary releases require explicit migration.
    stable = [r for r in releases if not r['draft'] and not r['prerelease'] and r['tag_name'].startswith('shelby-tv-v')]
    if not stable:
        raise ValueError('Previous Shelby release evidence is required')
    previous = max(stable,key=lambda r: version_code(re.fullmatch(r'shelby-tv-v(.+)-[0-9]+',r['tag_name'])[1]))
    Path(os.environ['RUNNER_TEMP'],'previous-release.json').write_text(json.dumps(previous))
    with Path(os.environ['GITHUB_ENV']).open('a') as output:
        output.write(f'RELEASE_TAG={tag}\nPREVIOUS_TAG={previous["tag_name"]}\n')
    print('Unused release tag and increasing Android versionCode verified:', tag, version_code(version))


if __name__ == '__main__':
    main()
