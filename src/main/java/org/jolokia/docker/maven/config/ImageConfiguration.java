package org.jolokia.docker.maven.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;
import org.jolokia.docker.maven.util.EnvUtil;
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

    public String getAlias() {
        return alias;
    }

    public RunImageConfiguration getRunConfiguration() {
        return run;
    }

    public BuildImageConfiguration getBuildConfiguration() {
        return build;
    }

    @Override
    public List<String> getDependencies() {
        RunImageConfiguration runConfig = getRunConfiguration();
        List<String> ret = new ArrayList<>();
        if (runConfig != null) {
            addVolumes(runConfig, ret);
            addLinks(runConfig, ret);
        }
        return ret;
    }

    private void addVolumes(RunImageConfiguration runConfig, List<String> ret) {
        if (runConfig.getVolumesFrom() != null) {
            ret.addAll(runConfig.getVolumesFrom());
        }
    }

    private void addLinks(RunImageConfiguration runConfig, List<String> ret) {
        if (runConfig.getLinks() != null) {
            for (String[] link : EnvUtil.splitLinks(runConfig.getLinks())) {
                ret.add(link[0]);
            }
        }
    }
}
