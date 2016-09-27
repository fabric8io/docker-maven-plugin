package io.fabric8.maven.docker.access.util;
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

import java.io.File;
import java.io.IOException;

import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Utilities around socket connections
 *
 * @author roland
 * @since 22/09/16
 */
public class LocalSocketUtil {

    /**
     * Check whether we can connect to a local Unix socket
     *
     */
    public static boolean canConnectUnixSocket(File path) {
        try (UnixSocketChannel channel = UnixSocketChannel.open()) {
            return channel.connect(new UnixSocketAddress(path));
        } catch (IOException e) {
            return false;
        }
    }
}
