package com.unique.unexamine.shared.manage.security;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证认证加密的归属和篡改边界，不依赖数据库或真实环境密钥。 */
class RuntimeSecretCipherTest {
    private final RuntimeSecretCipher cipher = new RuntimeSecretCipher("synthetic-test-master-key-for-runtime-only");

    @Test
    void exactUnicodeAndWhitespaceRoundTripUsesRandomCiphertext() {
        String value = "  中文口令\n空格需保留  ";
        String encrypted = cipher.encrypt(value, "record:154:field:160");
        assertEquals(value, cipher.decrypt(encrypted, "record:154:field:160"));
        assertNotEquals(encrypted, cipher.encrypt(value, "record:154:field:160"));
        assertFalse(encrypted.contains(value));
        assertEquals("", cipher.decrypt(cipher.encrypt("", "empty"), "empty"));
    }

    @Test
    void copiedCiphertextWrongKeyAndPlaintextAreBlocked() {
        String encrypted = cipher.encrypt("synthetic-value", "record:154:field:160");
        assertThrows(DomainException.class, () -> cipher.decrypt(encrypted, "record:151:field:160"));
        assertThrows(DomainException.class, () -> new RuntimeSecretCipher("different-synthetic-master-key-at-least32")
                .decrypt(encrypted, "record:154:field:160"));
        assertThrows(DomainException.class, () -> cipher.decrypt("old-unencrypted-value", "record:154:field:160"));
        assertThrows(DomainException.class, () -> cipher.decrypt("runtime-secret:v1:invalid", "record:154:field:160"));
    }
}
