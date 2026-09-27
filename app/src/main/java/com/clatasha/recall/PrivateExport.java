package com.clatasha.recall;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class PrivateExport {
    private static final byte[] MAGIC = "CLRECALL1".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 210000;

    static int write(Context context, Uri destination, char[] password) throws Exception {
        byte[] plain = null;
        byte[] secret = null;
        try {
            List<RecallDatabase.Entry> entries = new RecallDatabase(context).allForExport();
            JSONArray records = new JSONArray();
            for (RecallDatabase.Entry entry : entries) {
                JSONObject record = new JSONObject();
                record.put("app", entry.app);
                record.put("sender", entry.sender);
                record.put("body", entry.text);
                record.put("time", entry.time);
                record.put("archived", entry.archived);
                records.put(record);
            }
            JSONObject document = new JSONObject();
            document.put("format", "Clatasha Recall encrypted export");
            document.put("version", 1);
            document.put("entries", records);
            plain = document.toString().getBytes(StandardCharsets.UTF_8);

            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            byte[] nonce = new byte[12];
            random.nextBytes(salt);
            random.nextBytes(nonce);
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
            try {
                secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC);
            byte[] encrypted = cipher.doFinal(plain);
            try (OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("Could not open export destination");
                output.write(MAGIC);
                output.write(salt);
                output.write(nonce);
                output.write(encrypted);
            }
            return entries.size();
        } finally {
            Arrays.fill(password, '\0');
            if (plain != null) Arrays.fill(plain, (byte) 0);
            if (secret != null) Arrays.fill(secret, (byte) 0);
        }
    }
}
