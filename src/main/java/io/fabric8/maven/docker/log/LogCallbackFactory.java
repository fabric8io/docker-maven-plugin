package io.fabric8.maven.docker.log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.access.log.LogCallback;

public class LogCallbackFactory {
    
    private static Map<String, PrintStream> printStreamMap = new HashMap<>();

    public static LogCallback createLogCallback(LogOutputSpec spec) throws FileNotFoundException {
        PrintStream ps = createOrReusePrintStream(spec);
        return new DefaultLogCallback(spec, ps);
    }
    
    private static synchronized PrintStream createOrReusePrintStream(LogOutputSpec spec) throws FileNotFoundException {
        String file = spec.getFile();
        if (spec.isLogStdout() || file == null) {
            return System.out;
        }
        PrintStream ps = printStreamMap.get(file);
        if (ps == null) {
            ps = new PrintStream(new FileOutputStream(file), true);
            printStreamMap.put(file, ps);
        }
        return ps;
    }

    public static void closeLogs() {
        for (PrintStream ps : printStreamMap.values()) {
            ps.close();
        }
        printStreamMap.clear();
    }
}
