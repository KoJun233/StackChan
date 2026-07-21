package com.kj.stackchan.llm;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

import com.kj.stackchan.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(AppProperties appProperties) {
        try {
            key = Base64.getDecoder().decode(appProperties.getSecretsEncryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("companion.secrets-encryption-key must be Base64 encoded", exception);
        }
        if (key.length != AES_256_KEY_LENGTH_BYTES) {
            throw new IllegalStateException("companion.secrets-encryption-key must decode to exactly 32 bytes");
        }
    }

    public EncryptedSecret encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt LLM API key", exception);
        }
    }

    public String decrypt(EncryptedSecret encryptedSecret) {
        try {
            byte[] iv = Base64.getDecoder().decode(encryptedSecret.initializationVector());
            byte[] ciphertext = Base64.getDecoder().decode(encryptedSecret.ciphertext());
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt LLM API key", exception);
        }
    }

    public record EncryptedSecret(String ciphertext, String initializationVector) {
    }
}
