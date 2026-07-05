package io.fabric8.maven.docker.access.hc.wslc;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeSocketFactory;
import io.fabric8.maven.docker.util.Logger;
import org.apache.http.protocol.HttpContext;

final class WslcConnectionSocketFactory extends AbstractNativeSocketFactory {

    private final Logger log;

    WslcConnectionSocketFactory(String wslcExecutable, Logger log) {
        super(wslcExecutable);
        this.log = log;
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return new WslcProcessSocket(log);
    }

    @Override
    protected SocketAddress createSocketAddress(String wslcExecutable) {
        // Build the stdio-bridge command: <wslc> system session run docker system dial-stdio
        // `docker system dial-stdio` locates the daemon socket inside the VM itself, so no socket
        // path is needed on the Windows side.
        List<String> command = new ArrayList<>();
        command.add(wslcExecutable);
        command.add("system");
        command.add("session");
        command.add("run");
        command.add("docker");
        command.add("system");
        command.add("dial-stdio");
        return new WslcSocketAddress(command);
    }
}
