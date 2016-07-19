/*
 * Projet docker-maven-plugin
 * Copyright S2E 2016
 * ULimitConfig.java, 2016, <Modifier le fichier ecplise.ini pour fixer le nom d'utilisateur>
 */
package io.fabric8.maven.docker.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.maven.docker.config.VolumeConfiguration.Builder;

/**
 * @since 0.15
 * @author Alexis Thaveau
 */
public class ULimitConfig {

    private String name;
    private int hard;
    private int soft;

    /**
     * Accesseur de name
     * 
     * @return the name
     */
    public String getName() {
	return name;
    }

    /**
     * Muttateur de name
     * 
     * @param pName
     *            le name a affecter
     */
    public void setName(String pName) {
	name = pName;
    }

    /**
     * Accesseur de hard
     * 
     * @return the hard
     */
    public int getHard() {
	return hard;
    }

    /**
     * Muttateur de hard
     * 
     * @param pHard
     *            le hard a affecter
     */
    public void setHard(int pHard) {
	hard = pHard;
    }

    /**
     * Accesseur de soft
     * 
     * @return the soft
     */
    public int getSoft() {
	return soft;
    }

    /**
     * Muttateur de soft
     * 
     * @param pSoft
     *            le soft a affecter
     */
    public void setSoft(int pSoft) {
	soft = pSoft;
    }

    public static class Builder {

	private List<ULimitConfig> config = new ArrayList<>();

	public Builder() {
	    this.config = new ArrayList<>();
	}

	public List<ULimitConfig> build() {
	    return config;
	}

	public Builder add(List<String> ulimits) {
	    if (ulimits != null) {

		for (String ulimit : ulimits) {
		    String[] ulimitParsed = ulimit.split("=");
		    String type = ulimitParsed[0];
		    String values = ulimitParsed[1];
		    String[] valuesParsed = values.split(":");
		    String hard = valuesParsed[0];
		    String soft = null;
		    if (valuesParsed.length > 1) {
			soft = valuesParsed[1];
		    }
		    config.add(ulimit(type, hard, soft));
		}
	    }
	    return this;
	}

	private ULimitConfig ulimit(String name, String hard, String soft) {
	    ULimitConfig uLimitConfig = new ULimitConfig();
	    uLimitConfig.setName(name);
	    if (!StringUtils.isEmpty(hard)) {
		uLimitConfig.setHard(Integer.parseInt(hard));
	    }
	    if (!StringUtils.isEmpty(soft)) {
		uLimitConfig.setSoft(Integer.parseInt(soft));
	    }
	    return uLimitConfig;
	}
    }

}
