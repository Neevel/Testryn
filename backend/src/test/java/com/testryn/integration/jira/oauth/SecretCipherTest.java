package com.testryn.integration.jira.oauth;

import com.testryn.common.error.UpstreamServiceException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]); // 32 zero bytes, test only

    @Test
    void roundTripsAValue() {
        SecretCipher cipher = SecretCipher.fromBase64Key(KEY);
        String secret = "atlassian_refresh_token_value_1234567890";

        String encrypted = cipher.encrypt(secret);

        assertThat(encrypted).isNotEqualTo(secret);
        assertThat(encrypted).doesNotContain(secret);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void usesAFreshIvSoTheSamePlaintextEncryptsDifferentlyEachTime() {
        SecretCipher cipher = SecretCipher.fromBase64Key(KEY);

        String a = cipher.encrypt("same-plaintext");
        String b = cipher.encrypt("same-plaintext");

        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("same-plaintext");
        assertThat(cipher.decrypt(b)).isEqualTo("same-plaintext");
    }

    @Test
    void aTamperedCiphertextIsRejectedByTheGcmTagInsteadOfReturningGarbage() {
        SecretCipher cipher = SecretCipher.fromBase64Key(KEY);
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("value"));
        raw[raw.length - 1] ^= 0x01; // flip a bit in the tag

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(UpstreamServiceException.class);
    }

    @Test
    void aDifferentKeyCannotDecrypt() {
        String encrypted = SecretCipher.fromBase64Key(KEY).encrypt("value");
        byte[] otherKey = new byte[32];
        otherKey[0] = 7;
        SecretCipher other = SecretCipher.fromBase64Key(Base64.getEncoder().encodeToString(otherKey));

        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(UpstreamServiceException.class);
    }

    @Test
    void missingKeyFailsClearlyWithoutLeakingAnything() {
        assertThatThrownBy(() -> SecretCipher.fromBase64Key(null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> SecretCipher.fromBase64Key("   "))
                .isInstanceOf(UpstreamServiceException.class);
    }

    @Test
    void aKeyThatIsNotBase64OrNot32BytesIsRejected() {
        assertThatThrownBy(() -> SecretCipher.fromBase64Key("not valid base64 !!!"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> SecretCipher.fromBase64Key(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("32 bytes");
    }
}
