package com.fintrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService service;

    // 32 zero-bytes, base64-encoded (44 chars ending in =)
    private static final String VALID_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @BeforeEach
    void setUp() {
        service = new EncryptionService();
        ReflectionTestUtils.setField(service, "base64Key", VALID_KEY);
        service.init();
    }

    @Test
    void encryptThenDecrypt_shouldReturnOriginal() {
        String plaintext = "access-sandbox-abc123xyz";
        String encrypted = service.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encryptThenDecrypt_shouldPreserveUtf8Text() {
        String plaintext = "access-token-\u20ac-\u2603";
        String encrypted = service.encrypt(plaintext);

        assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_calledTwice_shouldProduceDifferentCiphertext() {
        String pt = "same-plaintext";
        assertThat(service.encrypt(pt)).isNotEqualTo(service.encrypt(pt));
    }

    @Test
    void init_withBlankKey_shouldThrowIllegalState() {
        EncryptionService svc = new EncryptionService();
        ReflectionTestUtils.setField(svc, "base64Key", "");
        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void init_withWrongLengthKey_shouldThrowIllegalState() {
        EncryptionService svc = new EncryptionService();
        // "tooshort" base64-encoded — decodes to 8 bytes, not 32
        ReflectionTestUtils.setField(svc, "base64Key", "dG9vc2hvcnQ=");
        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void init_withInvalidBase64Key_shouldThrowIllegalState() {
        EncryptionService svc = new EncryptionService();
        ReflectionTestUtils.setField(svc, "base64Key", "not base64");

        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_withShortPayload_shouldThrowRuntimeException() {
        assertThatThrownBy(() -> service.decrypt("c2hvcnQ="))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }
}
