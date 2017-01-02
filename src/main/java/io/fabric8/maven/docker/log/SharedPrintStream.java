package io.fabric8.maven.docker.log;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

class SharedPrintStream {
    private PrintStream printStream;

    private AtomicInteger numUsers;

    SharedPrintStream(PrintStream ps) {
        this.printStream = ps;
        this.numUsers = new AtomicInteger(1);
    }

    PrintStream getPrintStream() {
        return printStream;
    }

    void allocate() {
        numUsers.incrementAndGet();
    }

    boolean close() {
        int nrUsers = numUsers.decrementAndGet();
        if (nrUsers == 0 && printStream != System.out) {
            printStream.close();;
            return true;
        } else {
            return false;
        }
    }
}
