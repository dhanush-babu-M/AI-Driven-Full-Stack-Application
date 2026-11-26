package com.pathpilot.security;

import com.pathpilot.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts optional third-party access tokens (such as a GitHub PAT) using AES-256-GCM
 * before persisting in connected_accounts. Plaintext tokens are never sent back to browsers.
 */
@Service
public class TokenEncryptionService {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.trim().getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt token.", ex);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        if (!encryptedValue.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported encrypted token format.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedValue.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt token.", ex);
        }
    }

    private SecretKey deriveKey() throws Exception {
        String configured = appProperties.github() != null ? appProperties.github().tokenEncryptionKey() : null;
        if (configured == null || configured.isBlank()) {
            configured = appProperties.jwt() != null ? appProperties.jwt().secret() : null;
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Token encryption key is not configured.");
        }
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha256.digest(configured.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
