package org.jolokia.docker.maven.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;
import org.jolokia.docker.maven.util.StartOrderResolver;

/**
 * @author roland
 * @since 02.09.14
 */
public class ImageConfiguration implements StartOrderResolver.Resolvable {

    @Parameter(required = true)
    private String name;

    @Parameter
    private String alias;

    @Parameter
    private RunImageConfiguration run;

    @Parameter
    private BuildImageConfiguration build;

    @Override
    public String getName() {
        return name;
    }

    public RunImageConfiguration getRunConfiguration() {
        return run;
    }

    public BuildImageConfiguration getBuildConfiguration() {
        return build;
    }


    public String getAlias() {
        return alias;
    }

    @Override
    public List<String> getDependencies() {
        RunImageConfiguration runConfig = getRunConfiguration();
        List<String> ret = new ArrayList<>();
        if (runConfig != null) {
            for (List deps : new List[] { runConfig.getVolumesFrom(), runConfig.getLinks() }) {
                if (deps != null) {
                    ret.addAll(deps);
                }
            }
        }
        return ret;
    }
}
