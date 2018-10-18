package io.fabric8.maven.docker;

import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.build.maven.MavenArchiveService;
import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import io.fabric8.maven.docker.build.maven.MavenRegistryContext;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;
import org.apache.maven.archiver.MavenArchiveConfiguration;
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
    protected MavenArchiveConfiguration archive;

    @Component
    protected MavenFileFilter mavenFileFilter;

    @Component
    protected MavenReaderFilter mavenReaderFilter;

    @Parameter
    protected Map<String, String> buildArgs;

    @Parameter(property = "docker.pull.registry")
    protected String pullRegistry;

    @Parameter(property = "docker.source.dir", defaultValue="src/main/docker")
    protected String sourceDirectory;

    @Parameter(property = "docker.target.dir", defaultValue="target/docker")
    protected String outputDirectory;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    protected List<MavenProject> reactorProjects;


    protected MavenBuildContext getBuildContext(MavenArchiveService archiveService) {

        MavenRegistryContext registryContext = new MavenRegistryContext.Builder()
            .authConfigFactory(authConfigFactory)
            .defaultImagePullPolicy(imagePullPolicy != null ? ImagePullPolicy.fromString(imagePullPolicy) : null)
            .pullRegistry(pullRegistry)
            .build();

        return new MavenBuildContext.Builder()
            .project(project)
            .sourceDirectory(sourceDirectory)
            .outputDirectory(outputDirectory)
            .session(session)
            .settings(settings)
            .mavenFileFilter(mavenFileFilter)
            .mavenReaderFilter(mavenReaderFilter)
            .reactorProjects(reactorProjects)
            .archiveConfiguration(archive)
            .archiveService(archiveService)
            .registryContext(registryContext)
            .build();
    }
}
