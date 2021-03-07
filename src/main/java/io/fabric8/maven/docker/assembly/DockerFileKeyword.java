package io.fabric8.maven.docker.assembly;

/**
 * Fields for  a dockerfile
 * @author Paris Apostolopoulos &lt;javapapo@mac.com&gt;
 * @author Christian Fischer &lt;sw-dev@computerlyrik.de&gt;
 * @since 13.06.05
 */
public enum DockerFileKeyword
{
    MAINTAINER,
    ADD,
    EXPOSE,
    FROM,
    SHELL,
    RUN,
    WORKDIR,
    ENTRYPOINT,
    CMD,
    USER,
    ENV,
    ARG,
    LABEL,
    COPY,
    VOLUME,
    HEALTHCHECK,
    NONE;

    /**
     * Append this keyword + optionally some args to a {@link StringBuilder} plus a trailing newline.
     *
     * @param sb stringbuilder to add to
     * @param args args added (space separated)
     */
    public void addTo(StringBuilder sb, String ... args) {
        addTo(sb, true, args);
    }

    /**
     * Append this keyword + optionally some args to a {@link StringBuilder} and a optional trailing newline.
     *
     * @param sb stringbuilder to add to
     * @param newline flag indicating whether a new line should be added
     * @param args args added (space separated)
     */
    public void addTo(StringBuilder sb, boolean newline, String ... args) {
        sb.append(name());
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        if (newline) {
            sb.append("\n");
        }
    }
}
