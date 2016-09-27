package io.fabric8.maven.docker.access.hc.win;

import java.io.File;

class NpipeSocketAddress extends java.net.SocketAddress {

	private static final long serialVersionUID = 1L;

	private String path;

    NpipeSocketAddress(File path) {
        this.path = path.getPath();
    }

    public String path() {
        return path;
    }

    @Override
    public String toString() {
        return "NpipeSocketAddress{path=" + path + "}";
    }

    @Override
    public boolean equals(Object _other) {
        return _other instanceof NpipeSocketAddress && path.equals(((NpipeSocketAddress) _other).path);
    }
}
