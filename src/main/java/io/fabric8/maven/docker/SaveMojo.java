package io.fabric8.maven.docker;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;

@Mojo(name = "save")
public class SaveMojo extends AbstractDockerMojo {

    // Used when not automatically determined
	private static final ArchiveCompression STANDARD_ARCHIVE_COMPRESSION = ArchiveCompression.gzip;

	@Component
	MavenProjectHelper projectHelper;

	@Parameter(property = "docker.save.name")
	String saveName;

	@Parameter(property = "docker.save.names")
	List<String> saveNames;

	@Parameter(property = "docker.save.alias")
	String saveAlias;

	@Parameter(property = "docker.save.aliases")
	List<String> saveAliases;

	@Parameter
	String saveFile;

	@Parameter(property = "docker.skip.save", defaultValue = "false")
	boolean skipSave;

	@Parameter(property = "docker.save.classifier")
	String saveClassifier;

	@Override
	protected void executeInternal(ServiceHub serviceHub) throws DockerAccessException, MojoExecutionException {

		List<ImageConfiguration> images = getResolvedImages();
		if (skipSaveFor(images)) {
			return;
		}

		List<ImageConfiguration> imagesToSave = getImagesToSave(images);

		List<String> imageNames = imagesToSave.stream().map(ic -> ic.getName()).collect(Collectors.toList());
		String fileName = getFileName(imageNames);
		ensureSaveDir(fileName);
		for (String imageName : imageNames) {
			log.info("Saving image %s to %s", imageName, fileName);
			if (!serviceHub.getQueryService().hasImage(imageName)) {
				throw new MojoExecutionException("No image " + imageName + " exists");
			}
		}

		long time = System.currentTimeMillis();
		ArchiveCompression compression = ArchiveCompression.fromFileName(fileName);
		if(imageNames.size() == 1) {
			String imageName = imageNames.get(0);
			serviceHub.getDockerAccess().saveImage(imageName, fileName, compression);
			log.info("%s: Saved image to %s in %s", imageName, fileName, EnvUtil.formatDurationTill(time));
		} else {
			serviceHub.getDockerAccess().saveImages(imageNames, fileName, compression);
			log.info("%s: Saved image to %s in %s", imageNames, fileName, EnvUtil.formatDurationTill(time));
		}

		String classifier = getClassifier(imagesToSave.get(0));
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

	private String getFileName(List<String> iNames) throws MojoExecutionException {
	    String configuredFileName = getConfiguredFileName();
	    if (configuredFileName != null) {
            if (new File(configuredFileName).isAbsolute()) {
                return configuredFileName;
            }
            return new File(project.getBasedir(), configuredFileName).getAbsolutePath();
        }
		if (saveAlias != null) {
			return completeCalculatedFileName(saveAlias +
                                              "-" + project.getVersion() +
                                              "." + STANDARD_ARCHIVE_COMPRESSION.getFileSuffix());
		}
		if (iNames.size() == 1){
			ImageName imageName = new ImageName(iNames.get(0));
			return completeCalculatedFileName(imageName.getSimpleName() +
											  "-" + imageName.getTag()) +
											  "." + STANDARD_ARCHIVE_COMPRESSION.getFileSuffix();
		}
		throw new MojoExecutionException("More than one image to save. Please configure a fileName.");
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

    private String completeCalculatedFileName(String file) {
        return new File(project.getBuild().getDirectory(), file.replace("/","-")).getAbsolutePath();
    }

    private void ensureSaveDir(String fileName) throws MojoExecutionException {
        File saveDir = new File(fileName).getAbsoluteFile().getParentFile();
        if (!saveDir.exists()) {
            if (!saveDir.mkdirs()) {
                throw new MojoExecutionException("Can not create directory " + saveDir + " for storing save file");
            }
        }
    }

	private List<ImageConfiguration> getImagesToSave(List<ImageConfiguration> images) throws MojoExecutionException {
		// specify images by name or alias
		if (saveName == null && saveAlias == null && (saveNames == null || (saveNames != null && saveNames.isEmpty())) && (saveAliases == null || (saveAliases != null && saveAliases.isEmpty()))) {
			List<ImageConfiguration> buildImages = getImagesWithBuildConfig(images);
			if (buildImages.size() == 1) {
				return Arrays.asList(buildImages.get(0));
			}
			throw new MojoExecutionException("More than one image with build configuration is defined. Please specify the image with 'docker.save.name', 'docker.save.alias', 'docker.save.names' or 'docker.save.aliases'.");
		}

		checkValidImageInputsOrThrow();
		List<String> saveNamesPendingToSave = getSaveNamesPendingToSave();
		List<String> saveAliasesPendingToSave = getSaveAliasesPendingToSave();
		List<ImageConfiguration> imagesToSave = new ArrayList<>();
		for (ImageConfiguration ic : images) {
			if (equalName(ic) || containsName(ic)) {
				imagesToSave.add(ic);
				saveNamesPendingToSave.remove(ic.getName());
			} else if (equalAlias(ic) || containsAlias(ic)) {
				imagesToSave.add(ic);
				saveAliasesPendingToSave.remove(ic.getAlias());
			}
		}
		if (!saveNamesPendingToSave.isEmpty()) {
			throw new MojoExecutionException(saveNamesPendingToSave.size() > 1
					? "Can not find images with name: " + saveNamesPendingToSave
					: "Can not find image with name: " + saveNamesPendingToSave.get(0));
		}
		if (!saveAliasesPendingToSave.isEmpty()) {
			throw new MojoExecutionException(saveAliasesPendingToSave.size() > 1
					? "Can not find images with alias: " + saveAliasesPendingToSave
					: "Can not find image with alias: " + saveAliasesPendingToSave.get(0));
		}

		return imagesToSave;
	}

	private void checkValidImageInputsOrThrow() throws MojoExecutionException {
		if (saveName != null && saveAlias != null) {
			throw new MojoExecutionException("Cannot specify both name and alias.");
		}
		if (saveName != null && saveNames != null && !saveNames.isEmpty()) {
			throw new MojoExecutionException("Cannot specify both name and name list.");
		}
		if (saveName != null && saveAliases != null && !saveAliases.isEmpty()) {
			throw new MojoExecutionException("Cannot specify both name and alias list.");
		}
		if (saveAlias != null && saveNames != null && !saveNames.isEmpty()) {
			throw new MojoExecutionException("Cannot specify both alias and name list.");
		}
		if (saveAlias != null && saveAliases != null && !saveAliases.isEmpty()) {
			throw new MojoExecutionException("Cannot specify both alias and alias list.");
		}
		if (saveNames != null && !saveNames.isEmpty() && saveAliases != null && !saveAliases.isEmpty()) {
			throw new MojoExecutionException("Cannot specify both name list and alias list.");
		}
	}

	private List<String> getSaveNamesPendingToSave() {
		List<String> exit = new ArrayList<>();
		if (saveName != null) {
			exit.add(saveName);
		}
		if (saveNames != null && !saveNames.isEmpty()) {
			exit.addAll(saveNames);
		}
		return exit;
	}

	private List<String> getSaveAliasesPendingToSave() {
		List<String> exit = new ArrayList<>();
		if (saveAlias != null) {
			exit.add(saveAlias);
		}
		if (saveAliases != null && !saveAliases.isEmpty()) {
			return exit;
		}
		return exit;
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

	private boolean containsAlias(ImageConfiguration ic) {
		return saveAliases != null && !saveAliases.isEmpty() && saveAliases.contains(ic.getAlias());
	}

	private boolean containsName(ImageConfiguration ic) {
		return saveNames != null && !saveNames.isEmpty() && saveNames.contains(ic.getName());
	}
}
