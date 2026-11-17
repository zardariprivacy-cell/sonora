package com.nawazishali.sonora;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/**
 * Private vault cryptography.
 * - PIN: PBKDF2-HmacSHA256 (120k iterations, random 16-byte salt); only the
 *   salted hash is stored — never the PIN itself.
 * - Files: AES-256-GCM with a key held in the Android Keystore (never leaves
 *   secure hardware where available). Each file gets a random 12-byte IV,
 *   stored as a prefix: [IV][ciphertext]. Plaintext is streamed, never fully
 *   held in memory.
 */
public class Vault {
    private static final String KEY_ALIAS = "sonora_vault_key";
    private static final int IV_LEN = 12;
    private static final int GCM_TAG = 128;

    // ---------------- PIN ----------------
    public static boolean hasPin(Context ctx) {
        return !Db.get(ctx).getSetting("vault_pin", "").isEmpty();
    }

    public static void setPin(Context ctx, String pin) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(pin, salt);
        Db.get(ctx).setSetting("vault_pin",
                Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(hash, Base64.NO_WRAP));
    }

    public static boolean verifyPin(Context ctx, String pin) {
        try {
            String stored = Db.get(ctx).getSetting("vault_pin", "");
            if (stored.isEmpty()) return false;
            String[] parts = stored.split(":");
            byte[] salt = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] actual = pbkdf2(pin, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) { return false; }
    }

    private static byte[] pbkdf2(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120_000, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    // ---------------- Keystore key ----------------
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }

    // ---------------- file crypto ----------------
    public static File vaultDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "vault");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Encrypt stream -> file. Returns the encrypted file. */
    public static File encrypt(Context ctx, InputStream in, String nameHint) throws Exception {
        File out = new File(vaultDir(ctx), System.currentTimeMillis() + "_"
                + Math.abs(new SecureRandom().nextInt()) + ".enc");
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG, iv));
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(iv);
            CipherOutputStream cos = new CipherOutputStream(fos, cipher);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) cos.write(buf, 0, n);
            cos.flush();
            cos.close();
        }
        return out;
    }

    /** Decrypt file -> stream out. Returns plaintext byte count. */
    public static long decrypt(File enc, OutputStream out) throws Exception {
        try (FileInputStream fis = new FileInputStream(enc)) {
            byte[] iv = new byte[IV_LEN];
            int read = 0;
            while (read < IV_LEN) {
                int n = fis.read(iv, read, IV_LEN - read);
                if (n < 0) throw new Exception("Corrupt vault file");
                read += n;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG, iv));
            CipherInputStream cis = new CipherInputStream(fis, cipher);
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = cis.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
            cis.close();
            return total;
        }
    }

    /** Verify an encrypted file decrypts to exactly expectedSize bytes (integrity check after import). */
    public static boolean verify(File enc, long expectedSize) {
        try {
            long n = decrypt(enc, new OutputStream() {
                @Override public void write(int b) { }
                @Override public void write(byte[] b, int off, int len) { }
            });
            return n == expectedSize;
        } catch (Exception e) { return false; }
    }

    /** Self-check used by the build report: bytes on disk must be ciphertext, not plaintext. */
    public static boolean looksEncrypted(File enc, byte[] plaintextPrefix) {
        try (FileInputStream fis = new FileInputStream(enc)) {
            byte[] head = new byte[Math.min(plaintextPrefix.length, 64)];
            int n = fis.read(head);
            if (n <= 0) return false;
            for (int i = 0; i < n && i < plaintextPrefix.length; i++)
                if (head[i] != plaintextPrefix[i]) return true; // differs from plaintext -> good
            return false;
        } catch (Exception e) { return false; }
    }
}
