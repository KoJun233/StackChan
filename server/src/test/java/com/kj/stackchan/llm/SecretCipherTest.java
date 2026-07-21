package com.kj.stackchan.llm;

import com.kj.stackchan.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    @Test
    void encryptsAndDecryptsAnApiKeyWithoutPersistingPlaintext() {
        SecretCipher cipher = new SecretCipher(propertiesWithValidKey());

        SecretCipher.EncryptedSecret encrypted = cipher.encrypt("sk-local-secret");

        assertThat(encrypted.ciphertext()).doesNotContain("sk-local-secret");
        assertThat(encrypted.initializationVector()).isNotBlank();
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-local-secret");
    }

    @Test
    void refusesAnEncryptionKeyThatIsNotExactly32Bytes() {
        AppProperties properties = new AppProperties();
        properties.setSecretsEncryptionKey("dG9vLXNob3J0");

        assertThatThrownBy(() -> new SecretCipher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }

    private AppProperties propertiesWithValidKey() {
        AppProperties properties = new AppProperties();
        properties.setSecretsEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        return properties;
    }
}
