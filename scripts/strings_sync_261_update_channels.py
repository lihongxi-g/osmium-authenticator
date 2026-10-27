#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.6.1: update checks become a two-switch page.

Replaces the single "auto-check for updates" switch strings
(``update_check_label`` / ``update_check_desc``) with the strings for
Settings → update checks: a page title, its settings-row description, and one
title/description pair per channel (stable, preview) in all 11 locales.

Idempotent: a file that already carries ``update_settings_title`` is skipped.
Run from the repo root: ``python3 scripts/strings_sync_261_update_channels.py``
"""
import io
import sys

# folder -> (settings_title, settings_desc, stable_title, stable_desc,
#            preview_title, preview_desc)
T = {
    "values": (
        "Update checks",
        "Automatically look for new versions: stable releases, preview builds, or both",
        "Check for stable updates",
        "Released versions. Silent: one GitHub request when the app opens",
        "Check for preview builds",
        "Beta and test builds that are not released yet \u2014 they may be unstable. Off by default",
    ),
    "values-zh": (
        "\u68c0\u67e5\u66f4\u65b0",
        "\u81ea\u52a8\u68c0\u67e5\u65b0\u7248\u672c\uff1a\u6b63\u5f0f\u7248\u3001\u9884\u89c8\u7248\uff0c\u53ef\u5206\u522b\u5f00\u542f",
        "\u68c0\u67e5\u6b63\u5f0f\u7248\u66f4\u65b0",
        "\u5df2\u6b63\u5f0f\u53d1\u5e03\u7684\u7248\u672c\uff1b\u6253\u5f00\u5e94\u7528\u65f6\u9759\u9ed8\u8bf7\u6c42\u4e00\u6b21 GitHub",
        "\u68c0\u67e5\u9884\u89c8\u7248\u66f4\u65b0",
        "\u5c1a\u672a\u6b63\u5f0f\u53d1\u5e03\u7684\u6d4b\u8bd5\u7248\uff0c\u53ef\u80fd\u4e0d\u7a33\u5b9a\uff1b\u9ed8\u8ba4\u5173\u95ed",
    ),
    "values-b+zh+Hant": (
        "\u6aa2\u67e5\u66f4\u65b0",
        "\u81ea\u52d5\u6aa2\u67e5\u65b0\u7248\u672c\uff1a\u6b63\u5f0f\u7248\u3001\u9810\u89bd\u7248\uff0c\u53ef\u5206\u5225\u958b\u555f",
        "\u6aa2\u67e5\u6b63\u5f0f\u7248\u66f4\u65b0",
        "\u5df2\u6b63\u5f0f\u767c\u4f48\u7684\u7248\u672c\uff1b\u6253\u958b\u61c9\u7528\u6642\u975c\u9ed8\u8acb\u6c42\u4e00\u6b21 GitHub",
        "\u6aa2\u67e5\u9810\u89bd\u7248\u66f4\u65b0",
        "\u5c1a\u672a\u6b63\u5f0f\u767c\u4f48\u7684\u6e2c\u8a66\u7248\uff0c\u53ef\u80fd\u4e0d\u7a69\u5b9a\uff1b\u9810\u8a2d\u95dc\u9589",
    ),
    "values-zh-rTW": (
        "\u6aa2\u67e5\u66f4\u65b0",
        "\u81ea\u52d5\u6aa2\u67e5\u65b0\u7248\u672c\uff1a\u6b63\u5f0f\u7248\u3001\u9810\u89bd\u7248\uff0c\u53ef\u5206\u5225\u958b\u555f",
        "\u6aa2\u67e5\u6b63\u5f0f\u7248\u66f4\u65b0",
        "\u5df2\u6b63\u5f0f\u767c\u4f48\u7684\u7248\u672c\uff1b\u6253\u958b\u61c9\u7528\u6642\u975c\u9ed8\u8acb\u6c42\u4e00\u6b21 GitHub",
        "\u6aa2\u67e5\u9810\u89bd\u7248\u66f4\u65b0",
        "\u5c1a\u672a\u6b63\u5f0f\u767c\u4f48\u7684\u6e2c\u8a66\u7248\uff0c\u53ef\u80fd\u4e0d\u7a69\u5b9a\uff1b\u9810\u8a2d\u95dc\u9589",
    ),
    "values-ja": (
        "\u30a2\u30c3\u30d7\u30c7\u30fc\u30c8\u306e\u78ba\u8a8d",
        "\u65b0\u3057\u3044\u30d0\u30fc\u30b8\u30e7\u30f3\u3092\u81ea\u52d5\u3067\u78ba\u8a8d\u3057\u307e\u3059\uff08\u6b63\u5f0f\u7248\u30fb\u30d7\u30ec\u30d3\u30e5\u30fc\u7248\u3092\u500b\u5225\u306b\u9078\u3079\u307e\u3059\uff09",
        "\u6b63\u5f0f\u7248\u306e\u66f4\u65b0\u3092\u78ba\u8a8d",
        "\u516c\u958b\u6e08\u307f\u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u3002\u30a2\u30d7\u30ea\u8d77\u52d5\u6642\u306bGitHub\u30781\u56de\u3060\u3051\u9759\u304b\u306b\u554f\u3044\u5408\u308f\u305b\u307e\u3059",
        "\u30d7\u30ec\u30d3\u30e5\u30fc\u7248\u306e\u66f4\u65b0\u3092\u78ba\u8a8d",
        "\u307e\u3060\u516c\u958b\u3055\u308c\u3066\u3044\u306a\u3044\u30c6\u30b9\u30c8\u7248\u3067\u3059\u3002\u4e0d\u5b89\u5b9a\u306a\u5834\u5408\u304c\u3042\u308a\u307e\u3059\uff08\u521d\u671f\u8a2d\u5b9a\u306f\u30aa\u30d5\uff09",
    ),
    "values-ko": (
        "\uc5c5\ub370\uc774\ud2b8 \ud655\uc778",
        "\uc0c8 \ubc84\uc804\uc744 \uc790\ub3d9\uc73c\ub85c \ud655\uc778\ud569\ub2c8\ub2e4(\uc815\uc2dd \ubc84\uc804\uacfc \ud504\ub9ac\ubdf0 \ubc84\uc804\uc744 \ub530\ub85c \ucf24 \uc218 \uc788\uc2b5\ub2c8\ub2e4)",
        "\uc815\uc2dd \ubc84\uc804 \uc5c5\ub370\uc774\ud2b8 \ud655\uc778",
        "\uc774\ubbf8 \ucd9c\uc2dc\ub41c \ubc84\uc804\uc785\ub2c8\ub2e4. \uc571\uc744 \uc5f4 \ub54c GitHub\uc5d0 \ud55c \ubc88\ub9cc \uc870\uc6a9\ud788 \uc694\ucd08\ud569\ub2c8\ub2e4",
        "\ud504\ub9ac\ubdf0 \ubc84\uc804 \uc5c5\ub370\uc774\ud2b8 \ud655\uc778",
        "\uc544\uc9c1 \ucd9c\uc2dc\ub418\uc9c0 \uc54a\uc740 \ud14c\uc2a4\ud2b8 \ube4c\ub4dc\uc774\uba70 \ubd88\uc548\uc815\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4(\uae30\ubcf8\uac12 \uae7c\uc9d0)",
    ),
    "values-fr": (
        "V\u00e9rification des mises \u00e0 jour",
        "Recherche automatique des nouvelles versions\u00a0: stables, d\u2019aper\u00e7u, ou les deux",
        "V\u00e9rifier les versions stables",
        "Versions d\u00e9j\u00e0 publi\u00e9es. Silencieux\u00a0: une requ\u00eate GitHub \u00e0 l\u2019ouverture de l\u2019app",
        "V\u00e9rifier les versions d\u2019aper\u00e7u",
        "Compilations de test pas encore publi\u00e9es\u00a0: elles peuvent \u00eatre instables. D\u00e9sactiv\u00e9 par d\u00e9faut",
    ),
    "values-de": (
        "Update-Pr\u00fcfung",
        "Automatisch nach neuen Versionen suchen: stabil, Vorschau oder beides",
        "Auf stabile Updates pr\u00fcfen",
        "Ver\u00f6ffentlichte Versionen. Still: eine GitHub-Anfrage beim \u00d6ffnen der App",
        "Auf Vorschauversionen pr\u00fcfen",
        "Noch nicht ver\u00f6ffentlichte Testversionen \u2013 sie k\u00f6nnen instabil sein. Standardm\u00e4\u00dfig aus",
    ),
    "values-es": (
        "B\u00fasqueda de actualizaciones",
        "Busca nuevas versiones autom\u00e1ticamente: estables, preliminares o ambas",
        "Buscar actualizaciones estables",
        "Versiones ya publicadas. Silencioso: una consulta a GitHub al abrir la app",
        "Buscar versiones preliminares",
        "Versiones de prueba a\u00fan no publicadas; pueden ser inestables. Desactivado por defecto",
    ),
    "values-ru": (
        "\u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0439",
        "\u0410\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438 \u0438\u0441\u043a\u0430\u0442\u044c \u043d\u043e\u0432\u044b\u0435 \u0432\u0435\u0440\u0441\u0438\u0438: \u0441\u0442\u0430\u0431\u0438\u043b\u044c\u043d\u044b\u0435, \u043f\u0440\u0435\u0434\u0432\u0430\u0440\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u0438\u043b\u0438 \u043e\u0431\u0435",
        "\u041f\u0440\u043e\u0432\u0435\u0440\u044f\u0442\u044c \u0441\u0442\u0430\u0431\u0438\u043b\u044c\u043d\u044b\u0435 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f",
        "\u041e\u043f\u0443\u0431\u043b\u0438\u043a\u043e\u0432\u0430\u043d\u043d\u044b\u0435 \u0432\u0435\u0440\u0441\u0438\u0438. \u0422\u0438\u0445\u043e: \u043e\u0434\u0438\u043d \u0437\u0430\u043f\u0440\u043e\u0441 \u043a GitHub \u043f\u0440\u0438 \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u0438 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f",
        "\u041f\u0440\u043e\u0432\u0435\u0440\u044f\u0442\u044c \u043f\u0440\u0435\u0434\u0432\u0430\u0440\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u0432\u0435\u0440\u0441\u0438\u0438",
        "\u0415\u0449\u0451 \u043d\u0435 \u0432\u044b\u043f\u0443\u0449\u0435\u043d\u043d\u044b\u0435 \u0442\u0435\u0441\u0442\u043e\u0432\u044b\u0435 \u0441\u0431\u043e\u0440\u043a\u0438 \u2014 \u043c\u043e\u0433\u0443\u0442 \u0440\u0430\u0431\u043e\u0442\u0430\u0442\u044c \u043d\u0435\u0441\u0442\u0430\u0431\u0438\u043b\u044c\u043d\u043e. \u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e",
    ),
    "values-hi": (
        "\u0905\u092a\u0921\u0947\u091f \u091c\u093e\u0901\u091a",
        "\u0928\u090f \u0938\u0902\u0938\u094d\u0915\u0930\u0923 \u0905\u092a\u0928\u0947 \u0906\u092a \u0916\u094b\u091c\u0947\u0902: \u0938\u094d\u0925\u093f\u0930, \u092a\u094d\u0930\u0940\u0935\u094d\u092f\u0942 \u092f\u093e \u0926\u094b\u0928\u094b\u0902",
        "\u0938\u094d\u0925\u093f\u0930 \u0905\u092a\u0921\u0947\u091f \u091c\u093e\u0901\u091a\u0947\u0902",
        "\u091c\u093e\u0930\u0940 \u0915\u093f\u090f \u0917\u090f \u0938\u0902\u0938\u094d\u0915\u0930\u0923\u0964 \u091a\u0941\u092a\u091a\u093e\u092a: \u0910\u092a \u0916\u0941\u0932\u0928\u0947 \u092a\u0930 GitHub \u092a\u0930 \u090f\u0915 \u0905\u0928\u0941\u0930\u094b\u0927",
        "\u092a\u094d\u0930\u0940\u0935\u094d\u092f\u0942 \u092c\u093f\u0932\u094d\u0921 \u091c\u093e\u0901\u091a\u0947\u0902",
        "\u0905\u092d\u0940 \u091c\u093e\u0930\u0940 \u0928\u0939\u0940\u0902 \u0939\u0941\u090f \u092a\u0930\u0940\u0915\u094d\u0937\u0923 \u092c\u093f\u0932\u094d\u0921 \u2014 \u092f\u0947 \u0905\u0938\u094d\u0925\u093f\u0930 \u0939\u094b \u0938\u0915\u0924\u0947 \u0939\u0948\u0902\u0964 \u0921\u093f\u092b\u093c\u0949\u0932\u094d\u091f \u0930\u0942\u092a \u0938\u0947 \u092c\u0902\u0926",
    ),
}

BASE = "app/src/main/res"
OLD_KEYS = ("update_check_label", "update_check_desc")
NEW_KEYS = (
    "update_settings_title",
    "update_settings_desc",
    "update_stable_title",
    "update_stable_desc",
    "update_preview_title",
    "update_preview_desc",
)


def esc(text: str) -> str:
    # Android needs a literal apostrophe escaped inside <string>.
    return text.replace("'", "\\'")


def main() -> int:
    for folder, values in T.items():
        if len(values) != len(NEW_KEYS):
            print(f"{folder}: expected {len(NEW_KEYS)} strings, got {len(values)}", file=sys.stderr)
            return 1
        path = f"{BASE}/{folder}/strings.xml"
        with io.open(path, encoding="utf-8") as fh:
            lines = fh.readlines()

        if any(f'name="{NEW_KEYS[0]}"' in line for line in lines):
            print(f"{folder}: already present, skipped")
            continue

        label_at = [i for i, line in enumerate(lines) if f'name="{OLD_KEYS[0]}"' in line]
        desc_at = [i for i, line in enumerate(lines) if f'name="{OLD_KEYS[1]}"' in line]
        if len(label_at) != 1 or len(desc_at) != 1 or desc_at[0] != label_at[0] + 1:
            print(
                f"{folder}: expected the two old strings on adjacent lines "
                f"(label at {label_at}, desc at {desc_at})",
                file=sys.stderr,
            )
            return 1

        start = label_at[0]
        new_lines = [
            f'    <string name="{key}">{esc(value)}</string>\n'
            for key, value in zip(NEW_KEYS, values)
        ]
        out = lines[:start] + new_lines + lines[start + 2:]

        with io.open(path, "w", encoding="utf-8") as fh:
            fh.writelines(out)
        print(f"{folder}: replaced 2 old strings with {len(new_lines)} new ones")

    return 0


if __name__ == "__main__":
    sys.exit(main())
