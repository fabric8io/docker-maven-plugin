package io.fabric8.maven.docker.access.hc.wslc;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Address of a WSL Containers (wslc) Docker endpoint. There is no real socket path on the
 * Windows host: the Docker daemon lives inside the wslc virtual machine and is reached by
 * spawning a bridge command whose stdin/stdout stream raw bytes to {@code /var/run/docker.sock}
 * inside the VM. This address therefore carries the command line to spawn.
 */
class WslcSocketAddress extends SocketAddress {

    private static final long serialVersionUID = 1L;

    private final List<String> command;

    WslcSocketAddress(List<String> command) {
        this.command = command;
    }

    List<String> command() {
        return command;
    }

    @Override
    public String toString() {
        return "WslcSocketAddress{command=" + command + "}";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WslcSocketAddress && command.equals(((WslcSocketAddress) other).command);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(command.toArray());
    }
}
