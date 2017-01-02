package io.fabric8.maven.docker.log;

import java.io.PrintStream;

class SharedPrintStream {
    private PrintStream printStream;

    private int numUsers;
    
    public SharedPrintStream(PrintStream ps) {
        this.printStream = ps;
        this.numUsers = 1;
    }

    public PrintStream getPrintStream() {
        return printStream;
    }

    public void setPrintStream(PrintStream printStream) {
        this.printStream = printStream;
    }

    public boolean isUsed() {
        return numUsers > 0;
    }

    public void allocate() {
        numUsers++;
    }
    
    public void free() {
        assert numUsers > 0;
        numUsers--;
    }
}
