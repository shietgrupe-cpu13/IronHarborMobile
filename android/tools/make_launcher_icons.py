#!/usr/bin/env python3
"""Write the adaptive-icon resources for the original Iron Harbor mark."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res"

def main() -> None:
    icon = RES / "mipmap-anydpi-v26/ic_launcher_ironharbor.xml"
    icon.parent.mkdir(parents=True, exist_ok=True)
    icon.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_ironharbor_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_ironharbor_foreground" />\n'
        '</adaptive-icon>\n',
        encoding="utf-8",
    )
    (RES / "values/ic_launcher_backgrounds.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources>\n'
        '    <color name="ic_launcher_ironharbor_background">#18392E</color>\n'
        '</resources>\n',
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
