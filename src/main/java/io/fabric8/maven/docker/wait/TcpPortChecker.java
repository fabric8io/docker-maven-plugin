package io.fabric8.maven.docker.wait;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Check whether a given TCP port is available
 */
public class TcpPortChecker implements WaitChecker {

    private static final int TCP_PING_TIMEOUT = 500;

    private final List<Integer> ports;

    private final List<InetSocketAddress> pending;

    public TcpPortChecker(String host, List<Integer> ports) {
        this.ports = ports;

        this.pending = new ArrayList<>();
        for (int port : ports) {
            this.pending.add(new InetSocketAddress(host, port));
        }

    }

    public List<Integer> getPorts() {
        return ports;
    }

    public List<InetSocketAddress> getPending() {
        return pending;
    }

    @Override
    public boolean check() {
        Iterator<InetSocketAddress> iter = pending.iterator();

        while (iter.hasNext()) {
            InetSocketAddress address = iter.next();

            try {
                Socket s = new Socket();
                s.connect(address, TCP_PING_TIMEOUT);
                s.close();
                iter.remove();
            } catch (IOException e) {
                // Ports isn't opened, yet. So don't remove from queue.
                // Can happen and is part of the flow
            }
        }
        return pending.isEmpty();
    }

    @Override
    public void cleanUp() {
        // No cleanup required
    }
}
