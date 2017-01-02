package io.fabric8.maven.docker.access.log;/*
 *
 * Copyright 2014 Roland Huss
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

import java.io.FileNotFoundException;
import java.util.concurrent.CancellationException;

import io.fabric8.maven.docker.util.Timestamp;

/**
 * Interface called for each log line received from the docker host when asynchronous
 * log fetching is used.
 *
 * @author roland
 * @since 21/11/14
 */
public interface LogCallback {

    /**
     * Receive a log entry
     * @param type 1 for log on standard output, 2 for standard error
     * @param timestamp timestampp on the server side when this entry happened
     * @param txt log output
     * @throws CancellationException if thrown will stop the logging.
     */
    void log(int type, Timestamp timestamp, String txt) throws DoneException;

    /**
     * Method called in case on an error when reading the logs
     * @param error error description
     */
    void error(String error);

    /**
     * To be called by a client to start the callback and allocate the underlying output stream.
     * In case of a shared stream it might be that the stream is reused
     */
    void open() throws FileNotFoundException;

    /**
     * Invoked by a user when callback is no longer used.
     * Closing the underlying stream may be delayed by the implementation
     * when this stream is shared between multiple clients.
     */
    void close();

    /**
     * Exception indicating that logging is done and should be finished
     */
    class DoneException extends Exception {}
}
