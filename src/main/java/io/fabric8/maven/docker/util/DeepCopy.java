package io.fabric8.maven.docker.util;
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

import java.io.*;

public class DeepCopy {

    /**
     * Returns a copy of the object, or null if the object cannot
     * be serialized.
     */
    public static <T> T copy(T orig) {
        if (orig == null) {
            return null;
        }
        try {
            // Write the object out to a byte array
            ByteArrayOutputStream fbos = new ByteArrayOutputStream();

            try (ObjectOutputStream out = new ObjectOutputStream(fbos)) {
                out.writeObject(orig);
                out.flush();
            }

            // Retrieve an input stream from the byte array and read
            // a copy of the object back in.
            try (ByteArrayInputStream fbis = new ByteArrayInputStream(fbos.toByteArray());
                 ObjectInputStream in = new ObjectInputStream(fbis))  {
                return (T) in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Cannot copy " + orig, e);
        }
    };
}
