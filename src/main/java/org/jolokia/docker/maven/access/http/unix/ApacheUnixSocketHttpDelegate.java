package org.jolokia.docker.maven.access.http.unix;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jolokia.docker.maven.access.UrlBuilder;
import org.jolokia.docker.maven.access.http.ApacheHttpDelegate;

public class ApacheUnixSocketHttpDelegate extends ApacheHttpDelegate {

    private static final String UNIX_SOKKET_URI_SCHEME = "unix";

    /**
     * @see <a href="http://tools.ietf.org/html/rfc3986#section-2.2">RFC 3986 -
     *      Uniform Resource Identifier (URI): Generic Syntax - 2.2 Reserved
     *      Characters</a>
     */
    private static final String RESERVED_URI_CHARS = ":/?#[]@!$&'()/*+,;=";
    private static final String HEX_CHARS = "0123456789ABCDEF";

    public ApacheUnixSocketHttpDelegate() {
        super(createHttpClient());
    }

    public static boolean isSchemeSupported(String baseUrl) {
        return baseUrl.startsWith(UNIX_SOKKET_URI_SCHEME + "://");
    }

    @Override
    public UrlBuilder createUrlBuilder(String baseUrl, String apiVersion) {
        final String unixSocketPath = extractUnixSocketPath(baseUrl);
        if (unixSocketPath == null) {
            throw new IllegalArgumentException("Unsupported Base URL: " + baseUrl);
        }

        return new UrlBuilder(buildPseudoHostUrl(unixSocketPath), apiVersion);
    }

    private static CloseableHttpClient createHttpClient() {
        final HttpClientBuilder httpBuilder = HttpClients.custom();
        final Registry<ConnectionSocketFactory> registry = buildRegistry();
        final DnsResolver dnsResolver = nullDnsResolver();
        final HttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry, dnsResolver);
        httpBuilder.setConnectionManager(manager);
        return httpBuilder.build();
    }

    private static Registry<ConnectionSocketFactory> buildRegistry() {
        final RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
        registryBuilder.register(UNIX_SOKKET_URI_SCHEME, new UnixConnectionSocketFactory());
        return registryBuilder.build();
    }

    private static DnsResolver nullDnsResolver() {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                return new InetAddress[] {null};
            }
        };
    }

    private static String extractUnixSocketPath(String baseUrl) {
        final URI uri = URI.create(baseUrl);
        final String path = uri.getPath();
        if (!UNIX_SOKKET_URI_SCHEME.equals(uri.getScheme()) || uri.getHost() != null || path == null) {
            return null;
        }

        return path;
    }

    /**
     * Creates a new URL using the {@value #UNIX_SOKKET_URI_SCHEME} scheme and
     * the given path as the host component.
     *
     * @param   unixSocketPath  path to the UNIX Domain Socket file
     *
     * @return  a URL that carries {@code unixSocketPath} in its host component
     */
    private static String buildPseudoHostUrl(String unixSocketPath) {
        return UNIX_SOKKET_URI_SCHEME + "://" + encodeUriPart(unixSocketPath) + ":1";
    }

    private static String encodeUriPart(String part) {
        StringBuilder buf = null;

        final int length = part.length();
        for (int i = 0; i < length; ++i) {
            final char c = part.charAt(i);

            if (RESERVED_URI_CHARS.indexOf(c) >= 0) {
                if (buf == null) {
                    buf = new StringBuilder(length * 4 / 3 + 1);
                    buf.append(part, 0, i);
                }

                buf.append('%');
                buf.append(HEX_CHARS.charAt(c / 16));
                buf.append(HEX_CHARS.charAt(c % 16));
            } else if (buf != null) {
                buf.append(c);
            }
        }

        return buf == null ? part : buf.toString();
    }
}
