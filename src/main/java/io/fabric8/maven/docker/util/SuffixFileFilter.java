package io.fabric8.maven.docker.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author roland
 * @since 19.10.14
 */
public class SuffixFileFilter implements FilenameFilter {

    final public static FilenameFilter PEM_FILTER = new SuffixFileFilter(".pem");

    private String suffix;

    public SuffixFileFilter(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public boolean accept(File dir, String name) {
        return name.endsWith(suffix);
    }
}
