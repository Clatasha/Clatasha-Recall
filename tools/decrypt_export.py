#!/usr/bin/env python3
"""Decrypt a Clatasha Recall export to a JSON file on your own computer.

Requires: python -m pip install cryptography
Usage: python tools/decrypt_export.py backup.recall backup.json
The resulting JSON is plaintext. Keep it somewhere private.
"""
import getpass
import json
import sys
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

MAGIC = b"CLRECALL1"


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: decrypt_export.py <backup.recall> <output.json>")
    source, destination = map(Path, sys.argv[1:])
    data = source.read_bytes()
    if len(data) < len(MAGIC) + 16 + 12 + 16 or not data.startswith(MAGIC):
        raise SystemExit("Unsupported or incomplete Clatasha Recall export")
    salt = data[len(MAGIC):len(MAGIC) + 16]
    nonce = data[len(MAGIC) + 16:len(MAGIC) + 28]
    encrypted = data[len(MAGIC) + 28:]
    password = getpass.getpass("Export password: ").encode("utf-8")
    key = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32,
                    salt=salt, iterations=210000).derive(password)
    try:
        plaintext = AESGCM(key).decrypt(nonce, encrypted, MAGIC)
    except Exception as exc:
        raise SystemExit("Decryption failed. Check the password and file.") from exc
    parsed = json.loads(plaintext.decode("utf-8"))
    if parsed.get("version") != 1:
        raise SystemExit("Unsupported export version")
    with destination.open("x", encoding="utf-8") as output:
        json.dump(parsed, output, ensure_ascii=False, indent=2)
    print(f"Saved {len(parsed.get('entries', []))} entries to {destination}")


if __name__ == "__main__":
    main()
