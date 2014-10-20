package org.jolokia.docker.maven.access;

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


    public static KeyStore createKeyStore(String certPath) throws IOException, GeneralSecurityException {
        PrivateKey privKey = loadPrivateKey(certPath + "/key.pem");
        Certificate[] certs = loadCertificates(certPath + "/cert.pem");

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);

        keyStore.setKeyEntry("docker", privKey, "docker".toCharArray(), certs);
        addCA(keyStore, certPath + "/ca.pem");
        return keyStore;
    }

    private static void addCA(KeyStore keyStore, String caPath) throws KeyStoreException, FileNotFoundException, CertificateException {
        for (Certificate cert : loadCertificates(caPath)) {
            final X509Certificate crt = (X509Certificate) cert;

            final String alias = crt.getSubjectX500Principal().getName();
            keyStore.setCertificateEntry(alias, crt);
        }
    }

    private static Certificate[] loadCertificates(String certPath) throws FileNotFoundException, CertificateException {
        InputStream is = new FileInputStream(certPath);
        Collection<? extends Certificate> certs = CertificateFactory.getInstance("X509").generateCertificates(is);
        return new ArrayList<>(certs).toArray(new Certificate[certs.size()]);
    }

    public static PrivateKey loadPrivateKey(String keyPath) throws IOException, GeneralSecurityException {

        PEMKeyPair keyPair = (PEMKeyPair) loadPEM(keyPath);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyPair.getPrivateKeyInfo().getEncoded());
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }

    private static Object loadPEM(String keyPath) throws IOException {
        PEMParser parser = new PEMParser(new BufferedReader(new FileReader(keyPath)));
        return parser.readObject();
    }
}
