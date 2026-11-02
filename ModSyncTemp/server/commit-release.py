"""Production SSH publishing entry. Input paths and hashes are verified before activation."""
import grp
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
from urllib.parse import urlsplit

root = Path('/srv/yumis-mods')
release_id = sys.argv[1]
if not re.fullmatch(r'[0-9]{14}-[a-f0-9]{12}', release_id):
    raise SystemExit('invalid release id')
incoming = root / 'incoming' / release_id
if incoming.is_symlink() or not incoming.is_dir():
    raise SystemExit('invalid incoming directory')
raw = (incoming / 'current.json').read_bytes()
if len(raw) > 2 * 1024 * 1024:
    raise SystemExit('manifest too large')
manifest = json.loads(raw)
if manifest.get('schemaVersion') != 1 or manifest.get('releaseId') != release_id:
    raise SystemExit('invalid manifest')
files = manifest.get('files', [])
if not 1 <= len(files) <= 2048:
    raise SystemExit('invalid file count')
gid = grp.getgrnam('www').gr_gid
seen = set()
expected_objects = set()

def check_file(path, entry):
    if path.is_symlink() or path.stat().st_size != entry['size']:
        raise SystemExit('object size or link mismatch')
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    if h.hexdigest() != entry['sha256']:
        raise SystemExit('object hash mismatch')

for entry in files:
    name = entry['name']
    if not name.lower().endswith('.jar') or '/' in name or '\\' in name or ':' in name or name.casefold() in seen:
        raise SystemExit('invalid filename')
    seen.add(name.casefold())
    for field, count in [('sha256', 64), ('sha1', 40), ('sha512', 128)]:
        if not re.fullmatch('[a-f0-9]{' + str(count) + '}', entry[field]):
            raise SystemExit('invalid hash')
    sources = entry['sources']
    if not sources or len(sources) > 12:
        raise SystemExit('invalid sources')
    kinds = {s['kind'] for s in sources}
    if 'server' in kinds and kinds != {'server'}:
        raise SystemExit('external object must not be mirrored')
    for source in sources:
        uri = urlsplit(source['url'])
        if uri.scheme != 'https' or uri.username or uri.password:
            raise SystemExit('invalid source URL')
        if source['kind'] == 'modrinth' and uri.hostname == 'cdn.modrinth.com':
            continue
        if source['kind'] == 'curseforge' and (uri.hostname == 'forgecdn.net' or uri.hostname.endswith('.forgecdn.net')):
            continue
        if source['kind'] == 'server' and source['url'] == 'https://119.91.113.235/yumis-mods/objects/' + entry['sha256'] + '.jar':
            continue
        raise SystemExit('unapproved source')
    if 'server' not in kinds:
        continue
    object_name = entry['sha256'] + '.jar'
    expected_objects.add(object_name)
    staged = incoming / 'objects' / object_name
    final = root / 'objects' / object_name
    if final.exists():
        check_file(final, entry)
        if staged.exists():
            check_file(staged, entry)
    else:
        check_file(staged, entry)
        os.chown(staged, 0, gid)
        staged.chmod(0o640)
        os.replace(staged, final)

if any(p.name not in expected_objects or p.is_symlink() or not p.is_file() for p in (incoming / 'objects').iterdir()):
    raise SystemExit('unexpected upload payload')
release_path = root / 'releases' / (release_id + '.json')
with release_path.open('xb') as stream:
    stream.write(raw)
    stream.flush()
    os.fsync(stream.fileno())
os.chown(release_path, 0, gid)
release_path.chmod(0o640)
next_path = root / ('current.next-' + release_id)
with next_path.open('xb') as stream:
    stream.write(raw)
    stream.flush()
    os.fsync(stream.fileno())
os.chown(next_path, 0, gid)
next_path.chmod(0o640)
os.replace(next_path, root / 'current.json')
fd = os.open(root, os.O_DIRECTORY)
try:
    os.fsync(fd)
finally:
    os.close(fd)
shutil.rmtree(incoming)
print(json.dumps({'releaseId': release_id, 'files': len(files), 'uploadedObjects': len(expected_objects)}))
