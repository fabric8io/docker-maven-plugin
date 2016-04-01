package io.fabric8.maven.docker.assembly;

/**
 * Fields for  a dockerfile
 * @author Paris Apostolopoulos <javapapo@mac.com>
 * @author Christian Fischer <sw-dev@computerlyrik.de>
 * @since 13.06.05
 */
public enum DockerFileKeyword
{
    MAINTAINER,
    EXPOSE,
    FROM,
    RUN,
    WORKDIR,
    ENTRYPOINT,
    CMD,
    USER,
    ENV,
    ARG,
    LABEL,
    COPY,
    VOLUME;

    /**
     * Append this keyword + optionally some args to a {@link StringBuilder} plus a trailing newline.
     *
     * @param sb stringbuilder to add to
     * @param args args added (space separated)
     */
    public void addTo(StringBuilder sb, String ... args) {
        sb.append(name());
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        sb.append("\n");
    }
}
