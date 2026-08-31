package com.testryn.integration.jira.oauth;

import com.testryn.common.error.UpstreamServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM authenticated encryption for the persisted OAuth access/refresh
 * tokens (ADR 0018). Ciphertext layout, then Base64: {@code iv(12) || ciphertext ||
 * tag(16)}. A fresh random IV is generated per encryption.
 *
 * <p>The key comes only from {@code TESTRYN_JIRA_OAUTH_ENCRYPTION_KEY} (Base64 of
 * exactly 32 bytes). If it is missing or malformed, every operation fails with a
 * clear, secret-free message rather than the application failing to start -- the
 * API-token auth path never constructs this class.
 *
 * <p>This class never logs, never includes key material or plaintext in an
 * exception message, and holds no plaintext beyond the single call.
 */
public final class SecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    private SecretCipher(SecretKeySpec key) {
        this.key = key;
    }

    /**
     * @throws UpstreamServiceException if {@code base64Key} is null/blank or not
     *         exactly 32 decoded bytes -- deliberately the same exception type the
     *         rest of the OAuth path uses for "not usable", so callers surface one
     *         consistent, non-technical error.
     */
    public static SecretCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new UpstreamServiceException("Jira OAuth encryption key is not configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new UpstreamServiceException("Jira OAuth encryption key is not valid Base64");
        }
        if (keyBytes.length != 32) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new UpstreamServiceException("Jira OAuth encryption key must decode to exactly 32 bytes");
        }
        SecretKeySpec spec = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
        return new SecretCipher(spec);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            // Never surface the underlying JCA exception (could echo internal detail).
            throw new UpstreamServiceException("Failed to encrypt Jira OAuth token");
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new UpstreamServiceException("Failed to decrypt a stored Jira OAuth token");
        }
    }
}
