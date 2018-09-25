/*
 * Projet docker-maven-plugin
 * Copyright S2E 2016
 * ULimitConfig.java, 2016, <Modifier le fichier ecplise.ini pour fixer le nom d'utilisateur>
 */
package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Configuration for ulimit
 *
 * @since 0.15
 * @author Alexis Thaveau
 */
public class UlimitConfig implements Serializable {

    @Parameter
    private String name;

    @Parameter
    private Integer hard;

    @Parameter
    private Integer soft;

    public UlimitConfig(String name, Integer hard, Integer soft) {
        this.name = name;
        this.hard = hard;
        this.soft = soft;
    }

    public String getName() {
		return name;
    }

	public Integer getHard() {
		return hard;
    }

	public Integer getSoft() {
		return soft;
	}

    Pattern ULIMIT_PATTERN = Pattern.compile("^(?<name>[^=]+)=(?<hard>[^:]*):?(?<soft>[^:]*)$");

    public UlimitConfig() {}

    public UlimitConfig(String ulimit) {
        Matcher matcher = ULIMIT_PATTERN.matcher(ulimit);
        if (matcher.matches()) {
            name = matcher.group("name");
            hard = asInteger(matcher.group("hard"));
            soft = asInteger(matcher.group("soft"));
        } else {
            throw new IllegalArgumentException("Invalid ulimit specification " + ulimit);
        }
    }

    private Integer asInteger(String number) {
        if (number == null || number.length() == 0) {
            return null;
        }
        return Integer.parseInt(number);
    }

    public String serialize() {
        if(hard != null && soft != null) {
            return name + "="+hard+":"+soft;
        } else if(hard != null) {
            return name + "="+hard;
        } else if(soft != null) {
            return name + "=:"+soft;
        } else {
            return null;
        }
    }
}
