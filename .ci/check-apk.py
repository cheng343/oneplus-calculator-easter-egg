"""Check the actual GitHub-built APK rather than only the source metadata."""
from pathlib import Path
import re
import sys
import zipfile

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    entry = archive.read("META-INF/xposed/java_init.list").decode().strip()
    scope = archive.read("META-INF/xposed/scope.list").decode().strip()
    properties = dict(
        line.split("=", 1) for line in
        archive.read("META-INF/xposed/module.prop").decode().splitlines()
        if line and not line.startswith("#")
    )
    assert entry == "io.github.cheng343.calculator.easteregg.ModuleMain", entry
    assert scope == "com.coloros.calculator", scope
    assert properties["minApiVersion"] == "101", properties
    assert properties["targetApiVersion"] == "102", properties
    assert properties["staticScope"] == "true", properties
    assert "assets/xposed_init" not in archive.namelist()
    dex_names = [name for name in archive.namelist()
                 if re.fullmatch(r"classes(?:\d+)?\.dex", name)]
    assert dex_names, "APK has no DEX files"
    dex = b"".join(archive.read(name) for name in dex_names)
    print("Inspecting APK DEX files:", ", ".join(dex_names))
    assert b"Lio/github/cheng343/calculator/easteregg/ModuleMain;" in dex, "Module entry class missing"
    assert b"Lde/robv/android/xposed/" not in dex
    assert b"never_settle_animation.json" in dex
    assert b"Lcom/airbnb/lottie/LottieCompositionFactory;" in dex, "Bundled animation parser missing"
    assert b"Lcom/airbnb/lottie/LottieDrawable;" in dex, "Bundled animation renderer missing"
print("Built APK contains modern API 101-102 module entry, exact target scope, and animation hook.")
