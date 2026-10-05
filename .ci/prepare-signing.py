"""Restore a signing Secret, or generate a temporary key on a GitHub runner."""
import base64
import os
from pathlib import Path
import subprocess

path = Path(".ci/debug.keystore")
path.parent.mkdir(parents=True, exist_ok=True)
secret = os.environ.get("SIGNING_KEYSTORE_BASE64", "")
if secret:
    try:
        key = base64.b64decode(secret, validate=True)
    except ValueError:
        raise SystemExit("SIGNING_KEYSTORE_BASE64 is not valid Base64") from None
    if not key:
        raise SystemExit("Signing keystore is empty")
    path.write_bytes(key)
    path.chmod(0o600)
    print("Restored signing key from GitHub Actions Secret.")
else:
    subprocess.run([
        "keytool", "-genkeypair", "-noprompt", "-keystore", str(path),
        "-storetype", "JKS", "-storepass", "android", "-keypass", "android",
        "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10000", "-dname", "CN=Calculator Egg Fork Debug,O=Development,C=CN",
    ], check=True)
    print("Generated a temporary fork signing key on GitHub; it cannot update the official APK.")
