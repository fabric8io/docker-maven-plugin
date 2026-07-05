package io.fabric8.maven.docker.access.hc.wslc;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeClientBuilder;
import io.fabric8.maven.docker.util.Logger;
import org.apache.http.conn.socket.ConnectionSocketFactory;

/**
 * HttpClient builder for the WSL Containers (wslc) transport. Each connection is a child process
 * ({@code wslc system session run docker system dial-stdio}) whose stdio is bridged to the Docker
 * daemon socket inside the wslc virtual machine.
 * <p>
 * The {@code path} carried through the {@code wslc://} URL is interpreted as the wslc executable to
 * invoke (default {@code wslc.exe}); this lets a build point at a non-default wslc location.
 */
public class WslcClientBuilder extends AbstractNativeClientBuilder {

    public static final String DEFAULT_WSLC_EXECUTABLE = "wslc.exe";

    // URL used on auto-detection. It carries no executable in the path (that would break URI parsing
    // for paths with spaces/backslashes); the executable is resolved from WSLC_EXECUTABLE instead.
    // Needs a (dummy) authority because "wslc://" alone is not a valid URI.
    public static final String AUTO_DETECT_URL = "wslc://localhost";

    public WslcClientBuilder(String wslcExecutable, int maxConnections, Logger log) {
        super(resolveExecutable(wslcExecutable), maxConnections, log);
    }

    static String resolveExecutable(String wslcExecutable) {
        if (wslcExecutable == null || wslcExecutable.trim().isEmpty() || "/".equals(wslcExecutable)) {
            // No executable in the URL: honour WSLC_EXECUTABLE (read from the environment here rather
            // than via the URL, so paths with spaces or backslashes are not forced through URI parsing).
            String fromEnv = System.getenv("WSLC_EXECUTABLE");
            return fromEnv != null && !fromEnv.trim().isEmpty() ? fromEnv : DEFAULT_WSLC_EXECUTABLE;
        }
        // A leading slash comes from a URL like wslc:///wslc.exe -- strip it for the executable name.
        return wslcExecutable.startsWith("/") ? wslcExecutable.substring(1) : wslcExecutable;
    }

    @Override
    protected ConnectionSocketFactory getConnectionSocketFactory() {
        return new WslcConnectionSocketFactory(path, log);
    }

    @Override
    protected String getProtocol() {
        return "wslc";
    }
}
