package io.fabric8.maven.docker.access.hc.win;

public class NpipeSocketAddress extends java.net.SocketAddress {
    
	private static final long serialVersionUID = 1L;
	
	private String path;

    NpipeSocketAddress() {
    }

    public NpipeSocketAddress(java.io.File path) {
        this.path = path.getPath();
    }
    
    int length() {
        return path.length();
    }

    public String path() {
        return path;
    }

    @Override
    public String toString() {
        return "[ path=" + path + "]";
    }

    @Override
    public boolean equals(Object _other) {
        if (!(_other instanceof NpipeSocketAddress)) return false;

        NpipeSocketAddress other = (NpipeSocketAddress)_other;
        return path.equals(other.path);
    }
}
