package org.jolokia.docker.maven.util;

import java.util.*;

import org.apache.maven.plugin.MojoFailureException;

/**
 * @author roland
 * @since 16.10.14
 */
public class StartOrderResolver {

    public static final int MAX_RESOLVE_RETRIES = 10;

    // Check images for volume / link dependencies and return it in the right order.
    // Only return images which should be run
    // Images references via volumes but with no run configuration are started once to create
    // an appropriate container which can be linked into the image
    public static List<Resolvable> resolve(List<Resolvable> images) throws MojoFailureException {
        List<Resolvable> ret = new ArrayList<>();
        List<Resolvable> secondPass = new ArrayList<>();
        Set<String> processedImages = new HashSet<>();

        // First pass: Pick all data images and all without dependencies
        for (Resolvable config : images) {
            List<String> volumesOrLinks = extractDependentImagesFor(config);
            if (volumesOrLinks == null) {
                // A data image only or no dependency. Add it to the list of data image which can be always
                // created first.
                updateProcessedImages(processedImages, config);
                ret.add(config);
            } else {
                secondPass.add(config);
            }
        }

        // Next passes: Those with dependencies are checked whether they already have been visited.
        List<Resolvable> remaining = secondPass;
        int retries = MAX_RESOLVE_RETRIES;
        String error = null;
        try {
            do {
                remaining = resolveImageDependencies(ret,remaining,processedImages);
            } while (remaining.size() > 0  && retries-- > 0);
        } catch (ResolveSteadyStateException e) {
            error = "Cannot resolve image dependencies for start order\n" + remainingImagesDescription(remaining);
        }
        if (retries == 0 && remaining.size() > 0) {
            error = "Cannot resolve image dependencies after " + MAX_RESOLVE_RETRIES + " passes\n"
                    + remainingImagesDescription(remaining);
        }
        if (error != null) {
            throw new MojoFailureException(error);
        }
        return ret;
    }

    private static void updateProcessedImages(Set<String> processedImageNames, Resolvable config) {
        processedImageNames.add(config.getName());
        if (config.getAlias() != null) {
            processedImageNames.add(config.getAlias());
        }
    }

    private static String remainingImagesDescription(List<Resolvable> configs) {
        StringBuffer ret = new StringBuffer();
        ret.append("Unresolved images:\n");
        for (Resolvable config : configs) {
            ret.append("     " + config.getName() + "\n");
        }
        return ret.toString();
    }

    private static List<Resolvable> resolveImageDependencies(
            List<Resolvable> ret,
            List<Resolvable> secondPass,
            Set<String> processedImages) throws ResolveSteadyStateException {
        boolean changed = false;
        List<Resolvable> nextPass = new ArrayList<>();
        for (Resolvable config : secondPass) {
            List<String> dependentImagesFrom = extractDependentImagesFor(config);
            if (containsAll(processedImages, dependentImagesFrom)) {
                updateProcessedImages(processedImages,config);
                ret.add(config);
                changed = true;
            } else {
                // Still unresolved dependencies
                nextPass.add(config);
            }
        }
        if (!changed) {
            throw new ResolveSteadyStateException();
        }
        return nextPass;
    }

    private static List<String> extractDependentImagesFor(Resolvable config) {
        LinkedHashSet<String> ret = new LinkedHashSet<>();
        for (String id : config.getDependencies()) {
            ret.add(id);
        }
        return ret.isEmpty() ? null : new ArrayList<>(ret);
    }

    private static boolean containsAll(Set<String> images, List<String> toCheck) {
        for (String c : toCheck) {
            if (!images.contains(c)) {
                return false;
            }
        }
        return true;
    }

    // Exception indicating a steady state while resolving start order of images
    private static class ResolveSteadyStateException extends Throwable { }

    public interface Resolvable {
        String getName();
        String getAlias();
        List<String> getDependencies();
    }
}
