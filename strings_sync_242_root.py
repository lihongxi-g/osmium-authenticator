#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.4.2 — root detection strings.

Inserts the 6 root_* UI strings into all language files.
Sources: en/zh-Hans inline + /root/osmium-legal/root-strings/<code>.json
(codes: de, es, fr, hi, ja, ko, ru, zh-Hant).
Idempotent: skips a file if already synced.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET

RES = sys.argv[1] if len(sys.argv) > 1 else '/root/osmium-inspect/app/src/main/res'
W = sys.argv[2] if len(sys.argv) > 2 else '/root/osmium-legal'

DIRS = {
    'en': ['values'],
    'zh-Hans': ['values-zh'],
    'zh-Hant': ['values-zh-rTW', 'values-b+zh+Hant'],
    'de': ['values-de'],
    'es': ['values-es'],
    'fr': ['values-fr'],
    'hi': ['values-hi'],
    'ja': ['values-ja'],
    'ko': ['values-ko'],
    'ru': ['values-ru'],
}

KEY_ORDER = [
    'root_detected_title',
    'root_detected_body',
    'root_detected_ok',
    'root_feature_blocked',
    'root_setting_locked',
    'root_section_locked',
]

EN = {
    'root_detected_title': 'Device rooted',
    'root_detected_body': "This device is rooted, which may reduce security — other apps may be able to read this app's data. To protect your account, security features have been forced on and locked.",
    'root_detected_ok': 'Got it',
    'root_feature_blocked': "The device is rooted, so this feature can't be enabled. This restriction can be lifted in Developer mode.",
    'root_setting_locked': 'This setting is locked by root detection. This restriction can be lifted in Developer mode.',
    'root_section_locked': 'Forced on and locked by root detection',
}

ZH_HANS = {
    'root_detected_title': '检测到设备已 Root',
    'root_detected_body': '本设备已 Root，安全性可能降低：其他应用可能读取本应用的数据。为保护你的账户，安全功能已强制开启并锁定。',
    'root_detected_ok': '我知道了',
    'root_feature_blocked': '检测到设备已 Root，该功能无法开启；可在开发者模式中关闭此限制。',
    'root_setting_locked': '该设置已由 Root 检测锁定；可在开发者模式中关闭此限制。',
    'root_section_locked': '已由 Root 检测强制开启并锁定',
}


def esc(s: str) -> str:
    return s.replace('&', '&amp;').replace('<', '&lt;').replace("'", "\\'")


def values_for(code: str) -> dict:
    if code == 'en':
        return EN
    if code == 'zh-Hans':
        return ZH_HANS
    with open(f'{W}/root-strings/{code}.json', encoding='utf-8') as f:
        data = json.load(f)
    missing = [k for k in KEY_ORDER if not str(data.get(k, '')).strip()]
    if missing:
        sys.exit(f'{code}: missing keys in root-strings json: {missing}')
    return {k: str(data[k]) for k in KEY_ORDER}


def insert_keys(content: str, vals: dict) -> str:
    if re.search(r'<string name="root_detected_title">', content):
        print('  note: root_* already present')
        return content
    m = re.search(r'[ \t]*<string name="verify_pin">[^<]*</string>', content)
    if not m:
        sys.exit('verify_pin anchor not found')
    block = ''.join(
        f'\n    <string name="{k}">{esc(vals[k])}</string>' for k in KEY_ORDER
    )
    return content[:m.end()] + block + content[m.end():]


def main() -> None:
    for code, folders in DIRS.items():
        vals = values_for(code)
        for folder in folders:
            path = f'{RES}/{folder}/strings.xml'
            with open(path, encoding='utf-8') as f:
                content = f.read()
            content = insert_keys(content, vals)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            ET.parse(path)  # validate XML
            print(f'OK {folder}')

    # Final verification: parity across all files.
    key_sets = {}
    for code, folders in DIRS.items():
        for folder in folders:
            root = ET.parse(f'{RES}/{folder}/strings.xml').getroot()
            names = {el.get('name') for el in root.iter('string')}
            key_sets[folder] = names
            for k in KEY_ORDER:
                assert k in names, f'{folder}: {k} missing'
    first = next(iter(key_sets.values()))
    for folder, names in key_sets.items():
        diff = first ^ names
        assert not diff, f'{folder}: key set differs: {sorted(diff)[:10]}'
    print(f'ALL OK — {len(key_sets)} files, key parity verified')


if __name__ == '__main__':
    main()
