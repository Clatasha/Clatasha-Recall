package com.clatasha.recall;

import android.content.Context;
import android.net.Uri;

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
        byte[] secret = null;
        try (RecallDatabase database = new RecallDatabase(context)) {
            List<RecallDatabase.Entry> entries = database.allForExport();
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

            try (OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("Could not open export destination");
                output.write(MAGIC);
                output.write(salt);
                output.write(nonce);
                try (java.io.Writer writer = new java.io.OutputStreamWriter(
                        new javax.crypto.CipherOutputStream(output, cipher), StandardCharsets.UTF_8)) {
                    writer.write("{\"format\":\"Clatasha Recall encrypted export\",\"version\":2,\"entries\":[");
                    boolean first = true;
                    for (RecallDatabase.Entry entry : entries) {
                        JSONObject record = new JSONObject();
                        record.put("app", entry.app);
                        record.put("sender", entry.sender);
                        record.put("body", entry.text);
                        record.put("time", entry.time);
                        record.put("archived", entry.archived);
                        byte[] image = database.image(entry.id);
                        if (image != null) {
                            JSONObject attachment = new JSONObject();
                            attachment.put("mime", "image/jpeg");
                            attachment.put("base64", android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP));
                            record.put("image", attachment);
                        }
                        if (!first) writer.write(",");
                        first = false;
                        writer.write(record.toString());
                    }
                    writer.write("]}");
                }
            }
            return entries.size();
        } finally {
            Arrays.fill(password, '\0');
            if (secret != null) Arrays.fill(secret, (byte) 0);
        }
    }
}
