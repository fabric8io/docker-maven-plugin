package io.fabric8.maven.docker.access.ecr;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import io.fabric8.maven.docker.access.AuthConfig;

/**
 * AwsSigner4 implementation that signs requests with the AWS4 signing protocol. Refer to the AWS docs for mor details.
 *
 * @author chas
 * @since 2016-12-9
 */
class AwsSigner4 {

    // a-f must be lower case
    final private static char[] HEXITS = "0123456789abcdef".toCharArray();

    private final String service;
    private final String region;

    /**
     * A signer for a particular region and service.
     *
     * @param region The aws region.
     * @param service The aws service.
     */
    AwsSigner4(String region, String service) {
        this.region = region;
        this.service = service;
    }

    /**
     * Sign a request.  Add the headers that authenticate the request.
     *
     * @param request The request to sign.
     * @param credentials The credentials to use when signing.
     * @param signingTime The invocation time to use;
     */
    void sign(HttpRequest request, AuthConfig credentials, Date signingTime) {
        AwsSigner4Request sr = new AwsSigner4Request(region, service, request, signingTime);
        if(!request.containsHeader("X-Amz-Date")) {
            request.addHeader("X-Amz-Date", sr.getSigningDateTime());
        }
        request.addHeader("Authorization", task4(sr, credentials));
    }

    /**
     * Task 1.
     * <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html">Create a Canonical Request</a>
     */
    String task1(AwsSigner4Request sr) {
        StringBuilder sb = new StringBuilder(sr.getMethod()).append('\n')
                .append(sr.getUri().getRawPath()).append('\n')
                .append(getCanonicalQuery(sr.getUri())).append('\n')
                .append(sr.getCanonicalHeaders()).append('\n')
                .append(sr.getSignedHeaders()).append('\n');

        hexEncode(sb, sha256(sr.getBytes()));
        return sb.toString();
    }

    /**
     * Task 2.
     * <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html">Create a String to Sign for Signature Version 4</a>
     */
    String task2(AwsSigner4Request sr) {
        StringBuilder sb = new StringBuilder("AWS4-HMAC-SHA256\n")
                .append(sr.getSigningDateTime()).append('\n')
                .append(sr.getScope()).append('\n');
        hexEncode(sb, sha256(task1(sr)));
        return sb.toString();
    }

    /**
     * Task 3.
     * <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html">Calculate the Signature for AWS Signature Version 4</a>
     */
    final byte[] task3(AwsSigner4Request sr, AuthConfig credentials) {
        return hmacSha256(getSigningKey(sr, credentials), task2(sr));
    }

    private static byte[] getSigningKey(AwsSigner4Request sr, AuthConfig credentials) {
        byte[] kSecret = ("AWS4" + credentials.getPassword()).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, sr.getSigningDate());
        byte[] kRegion = hmacSha256(kDate, sr.getRegion());
        byte[] kService = hmacSha256(kRegion, sr.getService());
        return hmacSha256(kService, "aws4_request");
    }

    /**
     * Task 4.
     * <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4-add-signature-to-request.html">Add the Signing Information to the Request</a>
     */
    String task4(AwsSigner4Request sr, AuthConfig credentials) {
        StringBuilder sb = new StringBuilder("AWS4-HMAC-SHA256 Credential=")
                .append(credentials.getUsername() ).append( '/' ).append( sr.getScope() )
                .append(", SignedHeaders=").append(sr.getSignedHeaders())
                .append(", Signature=" );
        hexEncode(sb, task3(sr, credentials));
        return sb.toString();
    }

    private String getCanonicalQuery(URI uri) {
        String query = uri.getQuery();
        if(query == null || query.isEmpty()) {
            return "";
        }
        List<NameValuePair> params = URLEncodedUtils.parse(query, StandardCharsets.UTF_8);
        Collections.sort(params, new Comparator<NameValuePair>() {
            @Override
            public int compare(NameValuePair l, NameValuePair r) {
                return l.getName().compareToIgnoreCase(r.getName());
            }
        });
        return URLEncodedUtils.format(params, StandardCharsets.UTF_8);
    }

    static void hexEncode(StringBuilder dst, byte[] src) {
        for (byte aSrc : src) {
            int v = aSrc & 0xFF;
            dst.append(HEXITS[v >>> 4]);
            dst.append(HEXITS[v & 0x0F]);
        }
    }

    private static byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    private static byte[] sha256(String string) {
        return sha256(string.getBytes(StandardCharsets.UTF_8));
    }

     private static byte[] sha256(byte[] bytes) {
         try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            return md.digest();
         }
         catch (NoSuchAlgorithmException e) {
             throw new UnsupportedOperationException(e.getMessage(), e);
         }
    }
}
