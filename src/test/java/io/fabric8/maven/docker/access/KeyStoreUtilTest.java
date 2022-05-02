package io.fabric8.maven.docker.access;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;

/**
 * @author Stas Sukhanov
 * @since 08.03.2017
 */
class KeyStoreUtilTest {

    @Test
    void createKeyStore() throws Exception {
        KeyStore keyStore = KeyStoreUtil.createDockerKeyStore(getFile("certpath"));
        KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry("docker",
                new KeyStore.PasswordProtection("docker".toCharArray()));
        Assertions.assertNotNull(pkEntry);
        Assertions.assertNotNull(pkEntry.getCertificate());
        Assertions.assertNotNull(keyStore.getCertificate("cn=ca-test,o=internet widgits pty ltd,st=some-state,c=cr"));
        Assertions.assertNotNull(keyStore.getCertificate("cn=ca-test-2,o=internet widgits pty ltd,st=some-state,c=cr"));
    }

    @ParameterizedTest
    // ecdsa.pem has been created via `openssl ecparam -name secp521r1 -genkey -param_enc explicit -out ecdsa.pem`
    @ValueSource(strings = {"keys/pkcs1.pem","keys/pkcs8.pem","keys/ecdsa.pem"})
    void loadKey(String keyFile) throws Exception {
        PrivateKey privateKey = KeyStoreUtil.loadPrivateKey(getFile(keyFile));
        Assertions.assertNotNull(privateKey);
    }

    @Test
    void loadInvalidPrivateKey() {
        GeneralSecurityException gse= Assertions.assertThrows(GeneralSecurityException.class, () -> KeyStoreUtil.loadPrivateKey(getFile("keys/invalid.pem")));
        Assertions.assertTrue( gse.getMessage().startsWith("Cannot generate private key from file: "));
    }

    private String getFile(String path) {
        return KeyStoreUtilTest.class.getResource(path).getFile();
    }
}
