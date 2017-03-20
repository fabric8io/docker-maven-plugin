package io.fabric8.maven.docker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.util.ImageName;
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

	private final static ArchiveCompression STANDARD_ARCHIVE_COMPRESSION = ArchiveCompression.gzip;

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
		if (skip) {
			return;
		}

		String imageName = getImageName();
		String fileName = getFileName(imageName);
		log.info("Saving image %s to %s", imageName, fileName);
		if (!serviceHub.getQueryService().hasImage(imageName)) {
			throw new MojoExecutionException("No image " + imageName + " exists");
		}

		serviceHub.getDockerAccess().saveImage(imageName, fileName, ArchiveCompression.fromFileName(fileName));

		if (attach) {
			attachSaveArchive();
		}

	}

	private String getFileName(String iName) throws MojoExecutionException {
		if (file != null) {
			return file;
		}
		if (alias != null) {
			return alias + "." + STANDARD_ARCHIVE_COMPRESSION.getFileSuffix();
		}
		ImageName imageName = new ImageName(iName);
		return imageName.getSimpleName() + ("latest".equals(imageName.getTag()) ? "" : "-" + imageName.getTag())
			   + "." + STANDARD_ARCHIVE_COMPRESSION.getFileSuffix();
	}

	private String getImageName() throws MojoExecutionException {
		List<ImageConfiguration> images = getResolvedImages();
		// specify image by name or alias
		if (name == null && alias == null) {
			List<ImageConfiguration> buildImages = getImagesWithBuildConfig(images);
			if (buildImages.size() == 1) {
				return buildImages.get(0).getName();
			}
			throw new MojoExecutionException("If more than one image with build configuration is defined, " +
											 "then 'name' or 'alias' is required.");
		}
		if (name != null && alias != null) {
			throw new MojoExecutionException("Cannot specify both name and alias.");
		}
		for (ImageConfiguration ic : images) {
			if (equalName(ic) || equalAlias(ic)) {
				return ic.getName();
			}
		}
		throw new MojoExecutionException("Cannot find image with name or alias " + (name != null ? name : alias));
	}

	private List<ImageConfiguration> getImagesWithBuildConfig(List<ImageConfiguration> images) {
		List<ImageConfiguration> ret = new ArrayList<>();
		for (ImageConfiguration image : images) {
			if (image.getBuildConfiguration() != null) {
				ret.add(image);
			}
		}
		return ret;
	}

	private void attachSaveArchive() {
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

	private boolean equalAlias(ImageConfiguration ic) {
		return alias != null && alias.equals(ic.getAlias());
	}

	private boolean equalName(ImageConfiguration ic) {
		return name != null && name.equals(ic.getName());
	}
}
