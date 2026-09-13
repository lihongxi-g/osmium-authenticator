#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.4.1 — legal documents move online.

- Remove the in-app terms_body/privacy_body strings (the app now fetches the
  latest versions from osmium.im and shows a failure notice when offline).
- Insert the 5 new legal_* UI strings into all language files.

Sources: /root/osmium-legal/master (en, zh-Hans) + /root/osmium-legal/translated/<code>/ui_strings.json
Idempotent: skips a file if it is already synced.
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
    'legal_loading',
    'legal_fetch_failed',
    'legal_fetch_failed_hint',
    'legal_open_website',
    'legal_retry',
]

EN = {
    'legal_loading': 'Loading…',
    'legal_fetch_failed': 'Request failed',
    'legal_fetch_failed_hint': "Couldn't fetch the latest version. Please view it on the website.",
    'legal_open_website': 'View on the website',
    'legal_retry': 'Retry',
}

ZH_HANS = {
    'legal_loading': '加载中…',
    'legal_fetch_failed': '请求失败',
    'legal_fetch_failed_hint': '未能获取最新版本，请前往官网查看。',
    'legal_open_website': '前往官网查看',
    'legal_retry': '重试',
}


def esc(s: str) -> str:
    return s.replace('&', '&amp;').replace('<', '&lt;').replace("'", "\\'")


def values_for(code: str) -> dict:
    if code == 'en':
        return EN
    if code == 'zh-Hans':
        return ZH_HANS
    with open(f'{W}/translated/{code}/ui_strings.json', encoding='utf-8') as f:
        data = json.load(f)
    missing = [k for k in KEY_ORDER if not str(data.get(k, '')).strip()]
    if missing:
        sys.exit(f'{code}: missing keys in ui_strings.json: {missing}')
    return {k: str(data[k]) for k in KEY_ORDER}


def strip_bodies(content: str) -> str:
    for key in ('terms_body', 'privacy_body'):
        pattern = re.compile(
            r'\n[ \t]*<string name="%s">.*?</string>' % key, re.DOTALL
        )
        if pattern.search(content):
            content = pattern.sub('', content, count=1)
        else:
            print(f'  note: {key} already absent')
    return content


def insert_keys(content: str, vals: dict) -> str:
    if re.search(r'<string name="legal_loading">', content):
        print('  note: legal_* already present')
        return content
    m = re.search(r'[ \t]*<string name="about_privacy_title">[^<]*</string>', content)
    if not m:
        sys.exit('about_privacy_title anchor not found')
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
            content = strip_bodies(content)
            content = insert_keys(content, vals)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            ET.parse(path)  # validate XML
            print(f'OK {folder}')

    # Final verification: parity + absence of the old bodies.
    key_sets = {}
    for code, folders in DIRS.items():
        for folder in folders:
            root = ET.parse(f'{RES}/{folder}/strings.xml').getroot()
            names = {el.get('name') for el in root.iter('string')}
            key_sets[folder] = names
            for gone in ('terms_body', 'privacy_body'):
                assert gone not in names, f'{folder}: {gone} still present'
            for k in KEY_ORDER:
                assert k in names, f'{folder}: {k} missing'
    first = next(iter(key_sets.values()))
    for folder, names in key_sets.items():
        diff = (first ^ names)
        assert not diff, f'{folder}: key set differs: {sorted(diff)[:10]}'
    print(f'ALL OK — {len(key_sets)} files, key parity verified, bodies removed')


if __name__ == '__main__':
    main()
