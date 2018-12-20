package io.fabric8.maven.docker;

import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;

/**
 * @author roland
 * @author balazsmaria
 * @since 26/06/15
 */
abstract public class AbstractBuildSupportMojo extends AbstractDockerMojo {
    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html

    @Parameter
    private MavenArchiveConfiguration archive;

    @Component
    private MavenFileFilter mavenFileFilter;

    @Component
    private MavenReaderFilter mavenFilterReader;

    @Parameter
    private Map<String, String> buildArgs;

    @Parameter
    private String pullRegistry;

    @Parameter
    private String sourceDirectory;

    @Parameter
    private String outputDirectory;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<MavenProject> reactorProjects;

    private String getPullRegistry() {
        return getProperty("pull.registry");
    }

    private String getSourceDirectory() {
        return getProperty("source.dir", "src/main/docker");
    }

    private String getOutputDirectory() {
        return getProperty("target.dir", "target/docker");
    }

    protected BuildService.BuildContext getBuildContext() throws MojoExecutionException {
        return new BuildService.BuildContext.Builder()
                .buildArgs(buildArgs)
                .mojoParameters(createMojoParameters())
                .registryConfig(getRegistryConfig(getPullRegistry()))
                .build();
    }

    protected MojoParameters createMojoParameters() {
        return new MojoParameters(session, project, archive, mavenFileFilter, mavenFilterReader,
                                  settings, getSourceDirectory(), getOutputDirectory(), reactorProjects);
    }

    @Override
    public String getPrefix() {
        return "docker.";
    }
}
