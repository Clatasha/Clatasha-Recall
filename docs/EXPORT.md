# Private exports

In Recall, open **Settings → Export encrypted history**. Choose where to save a `.recall` file, then enter and confirm a password of at least eight characters. Recall encrypts the complete saved and archived history on the phone before writing it. The app has no account or recovery copy of this password.

To read the export on your own computer:

1. Install Python and run `python -m pip install cryptography`.
2. Download [the offline decryptor](../tools/decrypt_export.py).
3. Run `python tools/decrypt_export.py Clatasha-Recall-YYYY-MM-DD.recall my-history.json`.
4. Enter the password when prompted. The JSON output is **unencrypted**; keep or delete it accordingly.

The `.recall` file begins with a version marker, random salt and nonce, followed by AES-256-GCM encrypted JSON. The key is derived from your password with PBKDF2-HMAC-SHA256 (210,000 iterations). No notification content is sent to a server by the export feature.

The Android app currently exports but does not import these files. App lock protects the on-screen history with the phone's device credential; it does not encrypt the app-private SQLite database.
