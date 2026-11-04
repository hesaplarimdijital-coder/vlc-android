#!/usr/bin/env python3
"""apkcheck.py <apk>: package, versions, debuggable, signing scheme + cert SHA-256, baseline profile, ABIs, size."""
import sys, os, hashlib, zipfile
from loguru import logger
logger.remove()
from androguard.core.apk import APK
p = sys.argv[1]
a = APK(p)
print('package      ', a.get_package())
print('versionName  ', a.get_androidversion_name())
print('versionCode  ', a.get_androidversion_code())
print('minSdk/target', a.get_min_sdk_version(), a.get_target_sdk_version())
dbg = a.get_attribute_value('application', 'debuggable')
print('debuggable   ', dbg)
print('signed v1/v2/v3', a.is_signed_v1(), a.is_signed_v2(), a.is_signed_v3())
for c in (a.get_certificates_v3() or a.get_certificates_v2() or a.get_certificates_v1()):
    print('cert sha256  ', hashlib.sha256(c.dump()).hexdigest())
z = zipfile.ZipFile(p)
names = z.namelist()
print('baseline.prof', [n for n in names if n.startswith('assets/dexopt/')])
print('ABIs         ', sorted({n.split('/')[1] for n in names if n.startswith('lib/')}))
print('libs         ', len([n for n in names if n.startswith('lib/')]), 'e.g. nagmedar:', [os.path.basename(n) for n in names if 'nagmedar' in n][:6])
print('size         ', os.path.getsize(p))
with open(p, 'rb') as f: print('sha256       ', hashlib.sha256(f.read()).hexdigest())
