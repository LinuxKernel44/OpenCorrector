package com.opencorrector.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Verifies a downloaded model file against its known-good SHA-256 (see LlamaModel). */
public final class ChecksumVerifier {

    private ChecksumVerifier() {
    }

    public static boolean verify(File file, String expectedSha256) {
        try {
            String actual = sha256(file);
            return actual.equalsIgnoreCase(expectedSha256);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    public static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1 << 16];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
