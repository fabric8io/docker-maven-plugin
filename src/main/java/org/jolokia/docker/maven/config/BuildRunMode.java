package org.jolokia.docker.maven.config;

public enum BuildRunMode {

    /**
     * build and run the container
     */
    both(true, true),

    /**
     * build the container only
     */
    build(true, false),

    /**
     * run the container only
     */
    run(false, true),

    /**
     * ignore the container entirely
     */
    skip(false, false);
    
    private final boolean isBuild;
    private final boolean isRun;

    BuildRunMode(boolean build, boolean run) {
        this.isBuild = build;
        this.isRun = run;
    }

    public boolean isBuild() {
        return isBuild;
    }

    public boolean isRun() {
        return isRun;
    }
}
