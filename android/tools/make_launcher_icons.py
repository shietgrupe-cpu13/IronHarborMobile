#!/usr/bin/env python3
"""Generate Iron Harbor's original scalable adaptive launcher icon resources."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res"

FOREGROUND = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#102C28" android:strokeColor="#C9A96A" android:strokeWidth="2.6"
        android:strokeLineJoin="round" android:pathData="M54,13 L82,23 L81,51 C81,70 69,83 54,93 C39,83 27,70 27,51 L26,23 Z" />
    <path android:fillColor="#00000000" android:strokeColor="#547267" android:strokeWidth="1.4"
        android:pathData="M54,20 L74,27 L73,51 C73,65 65,76 54,84 C43,76 35,65 35,51 L34,27 Z" />
    <path android:fillColor="#BD9660" android:pathData="M39,42 L69,42 L64,36 L44,36 Z" />
    <path android:fillColor="#D9E0D5" android:pathData="M44,43 L64,43 L67,68 L41,68 Z" />
    <path android:fillColor="#24453D" android:pathData="M50,52 L58,52 L58,68 L50,68 Z" />
    <path android:fillColor="#C9A96A" android:pathData="M48,30 L60,30 L63,36 L45,36 Z" />
    <path android:fillColor="#A6F26B" android:pathData="M51,24 A3,3 0,1 0 57,24 A3,3 0,1 0 51,24 M53,28 L55,28 L55,31 L53,31 Z" />
    <path android:fillColor="#00000000" android:strokeColor="#A6F26B" android:strokeWidth="2.2"
        android:strokeLineCap="round" android:pathData="M37,75 Q45,69 53,75 Q61,81 69,75 M40,81 Q47,76 54,81 Q61,86 68,81" />
    <path android:fillColor="#C9A96A" android:pathData="M36,70 L72,70 L69,73 L39,73 Z" />
</vector>
'''

MONOCHROME = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M54,15 L78,24 L77,50 C77,66 67,78 54,87 C41,78 31,66 31,50 L30,24 Z" />
    <path android:fillColor="#FFFFFFFF" android:pathData="M39,44 L69,44 L64,38 L44,38 Z M44,45 L64,45 L66,66 L42,66 Z M50,52 L58,52 L58,66 L50,66 Z M48,32 L60,32 L63,38 L45,38 Z M51,27 A3,3 0,1 0 57,27 A3,3 0,1 0 51,27" />
    <path android:fillColor="#FFFFFFFF" android:pathData="M38,73 Q46,67 54,73 Q62,79 70,73 L70,76 Q62,82 54,76 Q46,70 38,76 Z M41,81 Q48,76 55,81 Q62,86 69,81 L69,84 Q62,89 55,84 Q48,79 41,84 Z" />
</vector>
'''


def main() -> None:
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    (RES / "values").mkdir(parents=True, exist_ok=True)
    (RES / "drawable/ic_launcher_ironharbor_foreground.xml").write_text(FOREGROUND, encoding="utf-8")
    (RES / "drawable/ironharbor_icon_monochrome.xml").write_text(MONOCHROME, encoding="utf-8")
    (RES / "mipmap-anydpi-v26/ic_launcher_ironharbor.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_ironharbor_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_ironharbor_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ironharbor_icon_monochrome" />\n'
        '</adaptive-icon>\n', encoding="utf-8")
    (RES / "values/ic_launcher_backgrounds.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources>\n'
        '    <color name="ic_launcher_ironharbor_background">#0B1E22</color>\n'
        '</resources>\n', encoding="utf-8")


if __name__ == "__main__":
    main()
