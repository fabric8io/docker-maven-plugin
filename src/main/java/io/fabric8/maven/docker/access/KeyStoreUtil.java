package io.fabric8.maven.docker.access;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

/**
 * Utility class for building up a keystore which can be used in
 * SSL communication.
 *
 * @author roland
 * @since 20.10.14
 */
public class KeyStoreUtil {

    /**
     * Create a key stored holding certificates and secret keys from the given Docker key cert
     *
     * @param certPath directory holding the keys (key.pem) and certs (ca.pem, cert.pem)
     * @return a keystore where the private key is secured with "docker"
     *
     * @throws IOException is reading of the the PEMs failed
     * @throws GeneralSecurityException when the files in a wrong format
     */
    public static KeyStore createDockerKeyStore(String certPath) throws IOException, GeneralSecurityException {
        PrivateKey privKey = loadPrivateKey(certPath + "/key.pem");
        Certificate[] certs = loadCertificates(certPath + "/cert.pem");

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);

        keyStore.setKeyEntry("docker", privKey, "docker".toCharArray(), certs);
        addCA(keyStore, certPath + "/ca.pem");
        return keyStore;
    }

    public static PrivateKey loadPrivateKey(String keyPath) throws IOException, GeneralSecurityException {
        PEMKeyPair keyPair = loadPEM(keyPath);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyPair.getPrivateKeyInfo().getEncoded());
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static PEMKeyPair loadPEM(String keyPath) throws IOException {
        PEMParser parser = new PEMParser(new BufferedReader(new FileReader(keyPath)));
        return (PEMKeyPair) parser.readObject();
    }

    private static void addCA(KeyStore keyStore, String caPath) throws KeyStoreException, FileNotFoundException, CertificateException {
        for (Certificate cert : loadCertificates(caPath)) {
            X509Certificate crt = (X509Certificate) cert;
            String alias = crt.getSubjectX500Principal().getName();
            keyStore.setCertificateEntry(alias, crt);
        }
    }

    private static Certificate[] loadCertificates(String certPath) throws FileNotFoundException, CertificateException {
        InputStream is = new FileInputStream(certPath);
        Collection<? extends Certificate> certs = CertificateFactory.getInstance("X509").generateCertificates(is);
        return new ArrayList<>(certs).toArray(new Certificate[certs.size()]);
    }
}
