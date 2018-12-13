package io.fabric8.maven.docker.util;

import java.util.*;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.run.NetworkConfiguration;
import io.fabric8.maven.docker.config.run.RunConfiguration;
import io.fabric8.maven.docker.config.run.RunVolumeConfiguration;
import io.fabric8.maven.docker.service.QueryService;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author roland
 * @since 16.10.14
 */
public class StartOrderResolver {

    public static final int MAX_RESOLVE_RETRIES = 10;

    private final QueryService queryService;

    private final List<ImageConfiguration> secondPass;
    private final Set<String> processedImages;


    public StartOrderResolver(QueryService queryService) {
        this.queryService = queryService;

        this.secondPass = new ArrayList<>();
        this.processedImages = new HashSet<>();
    }


    // Check images for volume / link dependencies and return it in the right order.
    // Only return images which should be run
    // Images references via volumes but with no run configuration are started once to create
    // an appropriate container which can be linked into the image
    public List<ImageConfiguration> resolve(List<ImageConfiguration> images) {
        List<ImageConfiguration> resolved = new ArrayList<>();
        // First pass: Pick all data images and all without dependencies
        for (ImageConfiguration config : images) {
            List<String> volumesOrLinks = extractDependentImagesFor(config);
            if (volumesOrLinks == null) {
                // A data image only or no dependency. Add it to the list of data image which can be always
                // created first.
                updateProcessedImages(config);
                resolved.add(config);
            } else {
                secondPass.add(config);
            }
        }

        // Next passes: Those with dependencies are checked whether they already have been visited.
        return secondPass.size() > 0 ? resolveRemaining(resolved) : resolved;
    }

    public static List<String> getDependencies(ImageConfiguration image) {
        RunConfiguration runConfig = image.getRunConfiguration();
        List<String> ret = new ArrayList<>();
        if (runConfig != null) {
            addVolumes(runConfig, ret);
            addLinks(runConfig, ret);
            addContainerNetwork(runConfig, ret);
            addDependsOn(runConfig, ret);
        }
        return ret;
    }

    // ======================================================================================================

    private List<ImageConfiguration> resolveRemaining(List<ImageConfiguration> ret) {
        int retries = MAX_RESOLVE_RETRIES;
        String error = null;
        try {
            do {
                resolveImageDependencies(ret);
            } while (secondPass.size() > 0  && retries-- > 0);
        } catch (DockerAccessException | ResolveSteadyStateException e) {
            error = "Cannot resolve image dependencies for start order\n" + remainingImagesDescription();
        }
        if (retries == 0 && secondPass.size() > 0) {
            error = "Cannot resolve image dependencies after " + MAX_RESOLVE_RETRIES + " passes\n"
                    + remainingImagesDescription();
        }
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return ret;
    }

    private void updateProcessedImages(ImageConfiguration config) {
        processedImages.add(config.getName());
        if (config.getAlias() != null) {
            processedImages.add(config.getAlias());
        }
    }

    private String remainingImagesDescription() {
        StringBuffer ret = new StringBuffer();
        ret.append("Unresolved images:\n");
        for (ImageConfiguration config : secondPass) {
            ret.append("* ")
               .append(config.getAlias())
               .append(" depends on ")
               .append(StringUtils.join(getDependencies(config).toArray(), ","))
               .append("\n");
        }
        return ret.toString();
    }

    private void resolveImageDependencies(List<ImageConfiguration> resolved) throws DockerAccessException, ResolveSteadyStateException {
        boolean changed = false;
        Iterator<ImageConfiguration> iterator = secondPass.iterator();

        while (iterator.hasNext()) {
            ImageConfiguration config = iterator.next();
            if (hasRequiredDependencies(config)) {
                updateProcessedImages(config);
                resolved.add(config);
                changed = true;
                iterator.remove();
            }
        }

        if (!changed) {
            throw new ResolveSteadyStateException();
        }
    }

    private boolean hasRequiredDependencies(ImageConfiguration config) throws DockerAccessException {
        List<String> dependencies = extractDependentImagesFor(config);
        if (dependencies == null) {
            return false;
        }

        for (String dependency : dependencies) {
            // make sure the container exists, it's state will be verified elsewhere
            if (processedImages.contains(dependency) ||
                // Check also whether a *container* with this name exists in which case it is interpreted
                // as an external dependency which is already running
                queryService.hasContainer(dependency)) {
                continue;
            }
            return false;
        }

        return true;
    }

    private List<String> extractDependentImagesFor(ImageConfiguration config) {
        LinkedHashSet<String> ret = new LinkedHashSet<>(getDependencies(config));
        return ret.isEmpty() ? null : new ArrayList<>(ret);
    }

    // Exception indicating a steady state while resolving start order of images
    private static class ResolveSteadyStateException extends Throwable { }

    private static void addVolumes(RunConfiguration runConfig, List<String> ret) {
        RunVolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            List<String> volumeImages = volConfig.getFrom();
            if (volumeImages != null) {
                ret.addAll(volumeImages);
            }
        }
    }

    private static void addLinks(RunConfiguration runConfig, List<String> ret) {
        // Custom networks can have circular links, no need to be considered for the starting order.
        if (!runConfig.getNetworkingConfig().isCustomNetwork() && runConfig.getLinks() != null) {
            runConfig.getLinks()
                     .stream()
                     .map(s -> !s.contains(":") ? s : s.substring(0, s.lastIndexOf(":")))
                     .forEach(ret::add);
        }
    }

    private static void addContainerNetwork(RunConfiguration runConfig, List<String> ret) {
        NetworkConfiguration config = runConfig.getNetworkingConfig();
        String alias = config.getContainerAlias();
        if (alias != null) {
            ret.add(alias);
        }
    }

    private static void addDependsOn(RunConfiguration runConfig, List<String> ret) {
        // Only used in custom networks.
        if (runConfig.getNetworkingConfig().isCustomNetwork()) {
            ret.addAll(runConfig.getDependsOn());
        }
    }

}
