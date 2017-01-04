package io.fabric8.maven.docker;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;

@Mojo(name = "save")
public class SaveMojo extends AbstractDockerMojo {
	
	@Component
	private MavenProjectHelper projectHelper;

	@Parameter(property = "docker.name")
	private String name;
	
	@Parameter(property = "docker.alias")
	private String alias;

	@Parameter(property = "docker.file")
	private String file;
	
	@Parameter(property = "docker.attach", defaultValue = "false")
	private boolean attach;
	
	@Parameter(property = "docker.classifier")
	private String classifier;
	
	@Parameter(property = "docker.skip.save", defaultValue = "false")
	private boolean skip;

	@Override
	protected void executeInternal(ServiceHub serviceHub) throws DockerAccessException, MojoExecutionException {
		if (file == null) {
            throw new MojoExecutionException("'file' is required.");
        }
        if (name == null && alias == null) {
            throw new MojoExecutionException("'name' or 'alias' is required.");
        }
		// specify image by name or alias
		if (name != null && alias != null) {
			throw new MojoExecutionException("Cannot specify both name and alias.");
		}
		List<ImageConfiguration> images = getResolvedImages();
		
		ImageConfiguration image = null;
		for (ImageConfiguration ic : images) {
			if (name != null && name.equals(ic.getName())) {
				image = ic;
				break;
			}
			if (alias != null && alias.equals(ic.getAlias())) {
				image = ic;
				break;
			}
		}
		
		if (serviceHub.getQueryService().getImageId(image.getName()) == null) {
			throw new MojoExecutionException("No image found for " + image.getName());
		}
		
		serviceHub.getDockerAccess().saveImage(image.getName(), file, detectCompression(file));
		
		if (attach) {
			File fileObj = new File(file);
			if (fileObj.exists()) {
				String type = FilenameUtils.getExtension(file);
				if (classifier != null) {
					projectHelper.attachArtifact(project, type, classifier, fileObj);
				} else {
					projectHelper.attachArtifact(project, type, fileObj);
				}
			}
		}
		
	}

	private String detectCompression(String filename) throws MojoExecutionException {
		if (filename.endsWith(".gz")) {
			return "gz";
		}

        if (filename.endsWith(".bz") || filename.endsWith(".bzip2")) {
            return "bz";
        }

		if (filename.endsWith(".tar")) {
			return "";
		}

		throw new MojoExecutionException("Unsupported file type in : " + filename);
	}

}
