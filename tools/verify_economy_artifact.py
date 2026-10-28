"""Audit packaged classes, resources and editable Aseprite art for the economy revision."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from hashlib import sha256
import json, struct, subprocess, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
jar = root / 'temp/build/libs/YumisDungeonAndFight-0.1.0.jar'
report_dir = root / 'dist/verification/economy'
report_dir.mkdir(parents=True, exist_ok=True)
with ZipFile(jar) as archive:
    assert archive.testzip() is None
    classes = list((root / 'temp/build/classes/java/main').rglob('*.class'))
    for path in classes:
        assert path.read_bytes() == archive.read(path.relative_to(root / 'temp/build/classes/java/main').as_posix()), path
    resources = list((root / 'temp/build/resources/main').rglob('*'))
    resources = [p for p in resources if p.is_file()]
    for path in resources:
        assert path.read_bytes() == archive.read(path.relative_to(root / 'temp/build/resources/main').as_posix()), path
    json_count = 0
    for name in archive.namelist():
        if name.endswith('.json'):
            json.loads(archive.read(name)); json_count += 1
    prefix = 'assets/yumis_dungeon_and_fight/'
    png = archive.read(prefix + 'textures/item/armor/mage_blood_legs.png')
    assert png[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', png[16:24]) == (32, 32)
    assert png[25] == 6, 'PNG must retain RGBA transparency'
    model = json.loads(archive.read(prefix + 'items/legs_armor.json'))
    assert any(c['when'] == 'mage_blood_legs' for c in model['model']['cases'])

ase = root / 'art/new/equipment/mage_blood_legs.aseprite'
data = ase.read_bytes()
assert struct.unpack_from('<IHHHH', data)[:2] == (len(data), 0xA5E0)
assert struct.unpack_from('<HH', data, 8) == (32, 32)
assert subprocess.run(['git', 'check-ignore', '--quiet', 'doc/本机环境.md'], cwd=root).returncode == 0
assert not subprocess.check_output(['git', 'ls-files', 'doc/本机环境.md'], cwd=root).strip()

tests = [ET.parse(p).getroot() for p in (root / 'temp/build/test-results/test').glob('TEST-*.xml')]
count = sum(int(t.attrib['tests']) for t in tests)
assert count >= 105
assert sum(int(t.attrib['failures']) + int(t.attrib['errors']) for t in tests) == 0

with ZipFile(root / 'dist/mage-blood-source-art.zip', 'w', ZIP_DEFLATED) as archive:
    for path in [ase, root / 'src/main/resources/assets/yumis_dungeon_and_fight/textures/item/armor/mage_blood_legs.png', root / 'tools/mage_blood_art.lua']:
        archive.write(path, path.name)
report = {'jar_sha256': sha256(jar.read_bytes()).hexdigest(), 'matching_classes': len(classes), 'matching_resources': len(resources), 'valid_json': json_count, 'junit_tests': count, 'junit_failures': 0, 'icon_dimensions': [32, 32], 'aseprite_source_bytes': len(data), 'local_environment_doc_ignored': True}
(report_dir / 'artifact-audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report))
