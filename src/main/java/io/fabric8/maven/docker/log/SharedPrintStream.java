package io.fabric8.maven.docker.log;

import java.io.PrintStream;

class SharedPrintStream {
    private PrintStream printStream;

    private int numUsers;

    SharedPrintStream(PrintStream ps) {
        this.printStream = ps;
        this.numUsers = 1;
    }

    PrintStream getPrintStream() {
        return printStream;
    }

    public boolean isUsed() {
        return numUsers > 0;
    }

    synchronized void allocate() {
        numUsers++;
    }

    synchronized void free() {
        assert numUsers > 0;
        numUsers--;
    }

    public void close() {
        if (printStream != System.out) {
            printStream.close();;
        }
    }
}
