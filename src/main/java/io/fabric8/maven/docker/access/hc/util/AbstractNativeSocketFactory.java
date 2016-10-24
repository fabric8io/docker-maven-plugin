package io.fabric8.maven.docker.access.hc.util;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

/**
 * @author roland
 * @since 08/08/16
 */
abstract public class AbstractNativeSocketFactory implements ConnectionSocketFactory {

    final private String path;

    public AbstractNativeSocketFactory(String path) {
        this.path = path;
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
        sock.connect(createSocketAddress(path), connectTimeout);
        return sock;
    }

    protected abstract SocketAddress createSocketAddress(String path);
    public abstract Socket createSocket(HttpContext context) throws IOException;
}
