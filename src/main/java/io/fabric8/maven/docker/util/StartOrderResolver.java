package io.fabric8.maven.docker.util;

import java.util.*;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.QueryService;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author roland
 * @since 16.10.14
 */
public class StartOrderResolver {

    public static final int MAX_RESOLVE_RETRIES = 10;

    private final QueryService queryService;
    
    private final List<Resolvable> secondPass;
    private final Set<String> processedImages;
    
    public static List<Resolvable> resolve(QueryService queryService, List<Resolvable> convertToResolvables) {
        return new StartOrderResolver(queryService).resolve(convertToResolvables);
    }

    private StartOrderResolver(QueryService queryService) {
        this.queryService = queryService;
     
        this.secondPass = new ArrayList<>();
        this.processedImages = new HashSet<>();
    }

    
    // Check images for volume / link dependencies and return it in the right order.
    // Only return images which should be run
    // Images references via volumes but with no run configuration are started once to create
    // an appropriate container which can be linked into the image
    private List<Resolvable> resolve(List<Resolvable> images) {
        List<Resolvable> resolved = new ArrayList<>();
        // First pass: Pick all data images and all without dependencies
        for (Resolvable config : images) {
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

    private List<Resolvable> resolveRemaining(List<Resolvable> ret) {
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

    private void updateProcessedImages(Resolvable config) {
        processedImages.add(config.getName());
        if (config.getAlias() != null) {
            processedImages.add(config.getAlias());
        }
    }

    private String remainingImagesDescription() {
        StringBuffer ret = new StringBuffer();
        ret.append("Unresolved images:\n");
        for (Resolvable config : secondPass) {
            ret.append("* ")
               .append(config.getAlias())
               .append(" depends on ")
               .append(StringUtils.join(config.getDependencies().toArray(), ","))
               .append("\n");
        }
        return ret.toString();
    }

    private void resolveImageDependencies(List<Resolvable> resolved) throws DockerAccessException, ResolveSteadyStateException {
        boolean changed = false;        
        Iterator<Resolvable> iterator = secondPass.iterator();

        while (iterator.hasNext()) {
            Resolvable config = iterator.next();
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

    private boolean hasRequiredDependencies(Resolvable config) throws DockerAccessException {
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
    
    private List<String> extractDependentImagesFor(Resolvable config) {
        LinkedHashSet<String> ret = new LinkedHashSet<>();
        for (String id : config.getDependencies()) {
            ret.add(id);
        }
        return ret.isEmpty() ? null : new ArrayList<>(ret);
    }

    // Exception indicating a steady state while resolving start order of images
    private static class ResolveSteadyStateException extends Throwable { }

    public interface Resolvable {
        String getName();
        String getAlias();
        List<String> getDependencies();
    }


}
