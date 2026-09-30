# Private exports

In Recall, open **Settings → Export encrypted history**. Choose where to save a `.recall` file, then enter and confirm a password of at least eight characters. Recall encrypts the complete saved and archived history on the phone before writing it. The app has no account or recovery copy of this password.

To read the export on your own computer:

1. Install Python and run `python -m pip install cryptography`.
2. Download [the offline decryptor](../tools/decrypt_export.py).
3. Run `python tools/decrypt_export.py Clatasha-Recall-YYYY-MM-DD.recall my-history.json`.
4. Enter the password when prompted. The JSON output is **unencrypted**; keep or delete it accordingly.

The `.recall` file begins with a version marker, random salt and nonce, followed by AES-256-GCM encrypted JSON. The key is derived from your password with PBKDF2-HMAC-SHA256 (210,000 iterations). No notification content is sent to a server by the export feature.

The Android app currently exports but does not import these files. App lock protects the on-screen history with the phone's device credential; it does not encrypt the app-private SQLite database.

From 0.1.6, exports include captured JPEG previews in each entry's optional `image` object: `mime` and `base64`. The same offline decryptor produces JSON containing these attachments. Decode `base64` to recover the JPEG.

Image capture works only for selected apps that expose an accessible notification picture or the latest message's image attachment. Profile photos and app icons are not saved as message images. Previews are resized to at most 1024 pixels on their longest edge and 256 KB each. The setting affects new captures; existing images remain with their notification until it is deleted or cleaned up. Old notifications cannot be retroactively recovered.
