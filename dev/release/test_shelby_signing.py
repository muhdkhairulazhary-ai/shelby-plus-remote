#!/usr/bin/env python3
"""Signing gates reject absent secrets, unpinned signers, rollback and accidental key rotation."""
import sys
sys.dont_write_bytecode = True
import importlib.util
from pathlib import Path
import tempfile
import os
import base64
import hashlib
import secrets
import subprocess
import contextlib
import io
import unittest

HERE = Path(__file__).resolve().parent

def module(name):
    spec = importlib.util.spec_from_file_location(name, HERE/(name+'.py'))
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value); return value

release = module('shelby_release')
signing = module('shelby_signing')
preflight = module('shelby_preflight')
PIN = 'a'*64
OLD = 'b'*64

def report(pin):
    return f'Verified using v1 scheme (JAR signing): true\nVerified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: {pin}\n'

class SigningPolicyTest(unittest.TestCase):
    def test_real_keystore_is_checked_against_public_pin_without_leaking_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)
            env=dict(os.environ, SHELBY_STORE_PASSWORD=secrets.token_hex(24), SHELBY_KEY_PASSWORD=secrets.token_hex(24), SHELBY_KEY_ALIAS='signing-test')
            source=path/'source.jks'
            subprocess.run(['keytool','-genkeypair','-storetype','JKS','-keystore',str(source),'-alias','signing-test',
                '-storepass:env','SHELBY_STORE_PASSWORD','-keypass:env','SHELBY_KEY_PASSWORD',
                '-keyalg','RSA','-keysize','2048','-validity','1','-dname','CN=Validation only'],env=env,capture_output=True,check=True)
            cert=subprocess.run(['keytool','-exportcert','-keystore',str(source),'-alias','signing-test','-storepass:env','SHELBY_STORE_PASSWORD'],env=env,capture_output=True,check=True).stdout
            env.update(SHELBY_TV_KEYSTORE_BASE64=base64.b64encode(source.read_bytes()).decode(), SHELBY_RELEASE_CERT_SHA256=hashlib.sha256(cert).hexdigest(),RUNNER_TEMP=str(path),GITHUB_ENV=str(path/'env'))
            output=io.StringIO()
            with contextlib.redirect_stdout(output): signing.prepare(env)
            self.assertNotIn(env['SHELBY_STORE_PASSWORD'],output.getvalue())
            self.assertEqual((path/'shelby-release.jks').stat().st_mode & 0o777,0o600)
            env['SHELBY_RELEASE_CERT_SHA256']='f'*64
            with self.assertRaises(ValueError): signing.prepare(env)
            self.assertFalse((path/'shelby-release.jks').exists())

    def test_missing_secret_cannot_fall_back_to_ephemeral_release(self):
        with self.assertRaisesRegex(ValueError, 'not configured'):
            signing.prepare({})

    def test_expected_pin_and_addon_must_match(self):
        for main, addon in [(report(OLD), report(PIN)), (report(PIN), report(OLD))]:
            with self.assertRaises(ValueError): release.identity(main, addon, PIN, report(PIN), True, False)

    def test_permanent_key_rotation_is_never_implicitly_allowed(self):
        with self.assertRaises(ValueError): release.identity(report(PIN), report(PIN), PIN, report(OLD), True, True)
        with self.assertRaises(ValueError): release.identity(report(PIN), report(PIN), PIN, report(OLD), False, False)
        self.assertFalse(release.identity(report(PIN), report(PIN), PIN, report(OLD), False, True))
        self.assertTrue(release.identity(report(PIN), report(PIN), PIN, report(PIN), True, False))

    def test_duplicate_tag_duplicate_version_and_downgrade_are_rejected(self):
        previous = dict(tag_name='shelby-tv-v0.53.1-1',draft=False,prerelease=False)
        self.assertEqual(preflight.validate('0.53.2','0.53.2',[previous],[]),'shelby-tv-v0.53.2-1')
        for version in ['0.53.0','0.53.1']:
            with self.assertRaises(ValueError): preflight.validate(version,version,[previous],[])
        with self.assertRaises(ValueError): preflight.validate('0.53.2','0.53.2',[],[dict(ref='refs/tags/shelby-tv-v0.53.2-1')])
        with self.assertRaises(ValueError): preflight.validate('0.53.2','0.53.2',[],[dict(ref='refs/tags/shelby-tv-v0.53.2-2')])
        with self.assertRaises(ValueError): preflight.validate('0.53.2','0.53.1',[],[])
        with self.assertRaises(ValueError): preflight.version_code('0.053.2')

    def test_missing_signature_scheme_is_rejected_for_both_apks(self):
        for main,addon in [(report(PIN).replace('true','false'),report(PIN)),(report(PIN),report(PIN).replace('true','false'))]:
            with self.assertRaises(ValueError): release.identity(main,addon,PIN,report(PIN),True,False)

    def test_version_code_must_increase_and_package_stays_stable(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory)/'test.apk'; apk.write_bytes(b'verified elsewhere')
            old = "package: name='dev.tvremote.setup' versionCode='105300' versionName='0.53.0'"
            new = "package: name='dev.tvremote.setup' versionCode='105301' versionName='0.53.1'"
            value = release.metadata(apk,new,old,'0.53.1','shelby-tv-v0.53.1-1','owner/repo',PIN)
            self.assertEqual(value['versionCode'],105301)
            self.assertEqual(value['channel'],'production')
            self.assertTrue(value['releaseNotes'])
            self.assertRegex(value['releaseDate'],r'^\d{4}-\d{2}-\d{2}T')
            with self.assertRaises(ValueError): release.metadata(apk,old,old,'0.53.0','shelby-tv-v0.53.0-2','owner/repo',PIN)
            with self.assertRaises(ValueError): release.metadata(apk,new.replace('dev.tvremote.setup','wrong.app'),old,'0.53.1','shelby-tv-v0.53.1-1','owner/repo',PIN)

if __name__ == '__main__': unittest.main()
