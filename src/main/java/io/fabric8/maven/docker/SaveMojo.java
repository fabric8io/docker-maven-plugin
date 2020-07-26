package io.fabric8.maven.docker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
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

    // Used when not automatically determined
	private static final ArchiveCompression STANDARD_ARCHIVE_COMPRESSION = ArchiveCompression.gzip;

	@Component
	private MavenProjectHelper projectHelper;

	@Parameter(property = "docker.save.name")
	private String saveName;

	@Parameter(property = "docker.save.alias")
	private String saveAlias;

	@Parameter
	private String saveFile;

	@Parameter(property = "docker.skip.save", defaultValue = "false")
	private boolean skipSave;

	@Parameter(property = "docker.save.classifier")
	private String saveClassifier;

	@Override
	protected void executeInternal(ServiceHub serviceHub) throws DockerAccessException, MojoExecutionException {

		List<ImageConfiguration> images = getResolvedImages();
		if (skipSaveFor(images)) {
			return;
		}

		ImageConfiguration image = getImageToSave(images);
		String imageName = image.getName();
		String fileName = getFileName(imageName);
		ensureSaveDir(fileName);
		log.info("Saving image %s to %s", imageName, fileName);
		if (!serviceHub.getQueryService().hasImage(imageName)) {
			throw new MojoExecutionException("No image " + imageName + " exists");
		}

		long time = System.currentTimeMillis();
		ArchiveCompression compression = ArchiveCompression.fromFileName(fileName);
		serviceHub.getDockerAccess().saveImage(imageName, fileName, compression);
		log.info("%s: Saved image to %s in %s", imageName, fileName, EnvUtil.formatDurationTill(time));

		String classifier = getClassifier(image);
		if(classifier != null) {
			projectHelper.attachArtifact(project, compression.getFileSuffix(), classifier, new File(fileName));
		}
	}

	private boolean skipSaveFor(List<ImageConfiguration> images) {
		if (skipSave) {
			log.info("docker:save skipped because `skipSave` config is set to true");
			return true;
		}

		if (saveName == null &&
			saveAlias == null &&
			images.stream().allMatch(i -> i.getBuildConfiguration() == null)) {
			log.info("docker:save skipped because no image has a build configuration defined");
			return true;
		}

		return false;
	}

	private String getFileName(String iName) throws MojoExecutionException {
	    String configuredFileName = getConfiguredFileName();
	    if (configuredFileName != null) {
	        return configuredFileName;
        }
		if (saveAlias != null) {
			return completeCalculatedFileName(saveAlias +
                                              "-" + project.getVersion() +
                                              "." + STANDARD_ARCHIVE_COMPRESSION.getFileSuffix());
		}
        ImageName imageName = new ImageName(iName);
        return completeCalculatedFileName(imageName.getSimpleName() +
                                          "-" + imageName.getTag()) +
                                          "." + STANDARD_ARCHIVE_COMPRESSION.getFileSuffix();
    }

    private String getConfiguredFileName() {
        Properties[] propsList = new Properties[] { System.getProperties(), project.getProperties() };
        for (String key : new String[] { "docker.save.file", "docker.file", "file" }) {
            for (Properties props : propsList) {
                if (props.containsKey(key)) {
                    return props.getProperty(key);
                }
            }
        }
        return saveFile;
    }

    private String completeCalculatedFileName(String file) throws MojoExecutionException {
        return project.getBuild().getDirectory() + "/" + file.replace("/","-");
    }

    private void ensureSaveDir(String fileName) throws MojoExecutionException {
        File saveDir = new File(fileName).getAbsoluteFile().getParentFile();
        if (!saveDir.exists()) {
            if (!saveDir.mkdirs()) {
                throw new MojoExecutionException("Can not create directory " + saveDir + " for storing save file");
            }
        }
    }

	private ImageConfiguration getImageToSave(List<ImageConfiguration> images) throws MojoExecutionException {
		// specify image by name or alias
		if (saveName == null && saveAlias == null) {
			List<ImageConfiguration> buildImages = getImagesWithBuildConfig(images);
			if (buildImages.size() == 1) {
				return buildImages.get(0);
			}
			throw new MojoExecutionException("More than one image with build configuration is defined. Please specify the image with 'docker.name' or 'docker.alias'.");
		}
		if (saveName != null && saveAlias != null) {
			throw new MojoExecutionException("Cannot specify both name and alias.");
		}
		for (ImageConfiguration ic : images) {
			if (equalName(ic) || equalAlias(ic)) {
				return ic;
			}
		}
		throw new MojoExecutionException(saveName != null ?
											 "Can not find image with name '" + saveName + "'" :
											 "Can not find image with alias '"+ saveAlias + "'");
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

	private String getClassifier(ImageConfiguration image) {
		if(saveClassifier == null || saveClassifier.length() == 0) {
			return null;
		}

		return saveClassifier.replace("%a", image.getAlias() == null ? "" : image.getAlias());
	}


	private boolean equalAlias(ImageConfiguration ic) {
		return saveAlias != null && saveAlias.equals(ic.getAlias());
	}

	private boolean equalName(ImageConfiguration ic) {
		return saveName != null && saveName.equals(ic.getName());
	}
}
