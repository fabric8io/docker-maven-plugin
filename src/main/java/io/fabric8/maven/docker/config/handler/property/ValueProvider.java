package io.fabric8.maven.docker.config.handler.property;

import java.util.*;

import io.fabric8.maven.docker.util.EnvUtil;

import static io.fabric8.maven.docker.util.EnvUtil.*;

/**
 * Helper to extract values from a set of Properties, potentially mixing it up with XML-based configuration based on the
 * {@link PropertyMode} setting.
 *
 * Obtaining a value is done via data-type specific methods (such as {@link #getString}). The ConfigKey parameter
 * tells us which property to look for, and how to handle combination of multiple values.
 *
 * For {@link PropertyMode#Only} we only look at the properties, ignoring any config value.
 * For {@link PropertyMode#Skip} we only look at the config, ignoring any properties value.
 * For {@link PropertyMode#Override} we use the property value if it is non-null, else the config value.
 * For {@link PropertyMode#Fallback} we use the config value if it is non-null, else the property value.
 *
 * For Override and Fallback mode, merging may take place as dictated by the {@link ValueCombinePolicy}
 * defined in the {@link ConfigKey}, or as overriden by the property &lt;prefix.someproperty&gt<b>._combine</b>
 * ({@link EnvUtil#PROPERTY_COMBINE_POLICY_SUFFIX}).
 *
 * If {@link ValueCombinePolicy#Replace} is used, only the prioritized value (first non-null) is used.
 * If {@link ValueCombinePolicy#Merge} is used, the merge method depends on the data type.
 * For simple types (string, int, long, boolean) this is not supported and will throw exception.
 * For Lists, the non-null values will be appended to each other (with values from first source added first)
 * For Maps, all maps are merged into one map, with data from the first map taking precedence. *
 *
 * @author Johan Ström
 */
public class ValueProvider {
    private String prefix;
    private Properties properties;
    private PropertyMode propertyMode;

    private StringListValueExtractor stringListValueExtractor;
    private IntListValueExtractor intListValueExtractor;
    private MapValueExtractor mapValueExtractor;
    private StringValueExtractor stringValueExtractor;
    private IntValueExtractor intValueExtractor;
    private LongValueExtractor longValueExtractor;
    private BooleanValueExtractor booleanValueExtractor;
    private DoubleValueExtractor doubleValueExtractor;
    
    /**
     * Initiates ValueProvider which is to work with data from the given properties.
     *
     * The PropertyMode controls which source(s) to consult, and in which order.
     *
     * @param prefix Only look at properties with this prefix.
     * @param properties
     * @param propertyMode Which source to prioritize
     */
    public ValueProvider(String prefix, Properties properties, PropertyMode propertyMode) {
        this.prefix = prefix;
        this.properties = properties;
        this.propertyMode = propertyMode;

        stringListValueExtractor = new StringListValueExtractor();
        intListValueExtractor = new IntListValueExtractor();
        mapValueExtractor = new MapValueExtractor();
        stringValueExtractor = new StringValueExtractor();
        intValueExtractor = new IntValueExtractor();
        longValueExtractor = new LongValueExtractor();
        booleanValueExtractor = new BooleanValueExtractor();
        doubleValueExtractor = new DoubleValueExtractor();
    }

    public String getString(ConfigKey key, String fromConfig) {
        return stringValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public Integer getInteger(ConfigKey key, Integer fromConfig) {
        return intValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public int getInt(ConfigKey key, Integer fromConfig) {
        Integer integer = getInteger(key, fromConfig);
        if(integer == null) {
            return 0;
        }
        return integer;
    }

    public Long getLong(ConfigKey key, Long fromConfig) {
        return longValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public Boolean getBoolean(ConfigKey key, Boolean fromConfig) {
        return booleanValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public List<String> getList(ConfigKey key, List<String> fromConfig) {
        return stringListValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public List<Integer> getIntList(ConfigKey key, List<Integer> fromConfig) {
        return intListValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public Map<String, String> getMap(ConfigKey key, Map<String, String> fromConfig) {
        return mapValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public Double getDouble(ConfigKey key, Double fromConfig){
        return doubleValueExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    public <T> T getObject(ConfigKey key, T fromConfig, final com.google.common.base.Function<String, T> converter) {
        ValueExtractor<T> arbitraryExtractor = new ValueExtractor<T>() {
            @Override
            protected T withPrefix(String prefix, ConfigKey key, Properties properties) {
                return converter.apply(properties.getProperty(key.asPropertyKey(prefix)));
            }
        };

        return arbitraryExtractor.getFromPreferredSource(prefix, key, fromConfig);
    }

    /**
     * Helper base class for picking values out of the the Properties class and/or config value.
     *
     * If there is only one source defined, we only use that. If multiple source are defined, the first one get's priority.
     * If more than one value is specified, a merge policy as specified for the ConfigKey
     */
    private abstract class ValueExtractor<T> {
        T getFromPreferredSource(String prefix, ConfigKey key, T fromConfig) {
            if(propertyMode == PropertyMode.Skip) {
                return fromConfig;
            }

            List<T> values = new ArrayList<>();

            // Find all non-null values, put into "values" with order based on the given propertyMode
            T fromProperty = withPrefix(prefix, key, properties);

            // Short-circuit
            if(fromProperty == null && fromConfig == null) {
                return null;
            }

            switch (propertyMode) {
                case Only:
                    return fromProperty;
                case Override:
                    if(fromProperty != null) {
                        values.add(fromProperty);
                    }
                    if(fromConfig != null) {
                        values.add(fromConfig);
                    }
                    break;
                case Fallback:
                    if(fromConfig != null) {
                        values.add(fromConfig);
                    }
                    if(fromProperty != null) {
                        values.add(fromProperty);
                    }
                    break;
                default:
                    throw new AssertionError("Invalid PropertyMode");
            }

            if(values.size() == 1) {
                return values.get(0);
            }

            // values now has non-null values from both sources, in preference order.
            // Let's merge according to the combine policy
            ValueCombinePolicy combinePolicy = key.getValueCombinePolicy();
            String overrideCombinePolicy = properties.getProperty(key.asPropertyKey(prefix) + "." + EnvUtil.PROPERTY_COMBINE_POLICY_SUFFIX);
            if(overrideCombinePolicy != null) {
                combinePolicy = ValueCombinePolicy.fromString(overrideCombinePolicy);
            }

            switch(combinePolicy) {
                case Replace:
                    return values.get(0);
                case Merge:
                    return merge(key, values);
            }
            return null;
        }

        /**
         * Data type-specific extractor to read value from properties.
         *
         * @param prefix
         * @param key
         * @param properties
         * @return
         */
        protected abstract T withPrefix(String prefix, ConfigKey key, Properties properties);

        protected T merge(ConfigKey key, List<T> values) {
            throw new IllegalArgumentException("Combine policy Merge is not available for "+key.asPropertyKey(prefix));
        }
    }




    private class StringValueExtractor extends ValueExtractor<String> {
        @Override
        protected String withPrefix(String prefix, ConfigKey key, Properties properties) {
            return properties.getProperty(key.asPropertyKey(prefix));
        }
    }

    private class IntValueExtractor extends ValueExtractor<Integer> {
        @Override
        protected Integer withPrefix(String prefix, ConfigKey key, Properties properties) {
            String prop = properties.getProperty(key.asPropertyKey(prefix));
            return prop == null ? null : Integer.valueOf(prop);
        }
    }


    private class LongValueExtractor extends ValueExtractor<Long> {
        @Override
        protected Long withPrefix(String prefix, ConfigKey key, Properties properties) {
            String prop = properties.getProperty(key.asPropertyKey(prefix));
            return prop == null ? null : Long.valueOf(prop);
        }
    }

    private class BooleanValueExtractor extends ValueExtractor<Boolean> {
        @Override
        protected Boolean withPrefix(String prefix, ConfigKey key, Properties properties) {
            String prop = properties.getProperty(key.asPropertyKey(prefix));
            return prop == null ? null : Boolean.valueOf(prop);
        }
    }

    private class DoubleValueExtractor extends ValueExtractor<Double> {
        @Override
        protected Double withPrefix(String prefix, ConfigKey key, Properties properties) {
            String prop = properties.getProperty(key.asPropertyKey(prefix));
            return prop == null ? null : Double.valueOf(prop);
        }
    }

    private abstract class ListValueExtractor<T> extends ValueExtractor<List<T>> {
        @Override
        protected List<T> withPrefix(String prefix, ConfigKey key, Properties properties) {
            List<String> strings = extractFromPropertiesAsList(key.asPropertyKey(prefix), properties);
            if(strings == null) {
                return null;
            }
            return process(strings);
        }

        protected abstract List<T> process(List<String> strings);

        @Override
        protected List<T> merge(ConfigKey key, List<List<T>> values) {
            List<T> merged = new ArrayList<>();
            for (List<T> value : values) {
                merged.addAll(value);
            }
            return merged;
        }
    }

    private class StringListValueExtractor extends ListValueExtractor<String> {
        @Override
        protected List<String> process(List<String> strings) {
            return strings;
        }
    }

    private class IntListValueExtractor extends ListValueExtractor<Integer> {
        @Override
        protected List<Integer> process(List<String> strings) {
            List<Integer> ints = new ArrayList<>();
            for (String s : strings) {
                ints.add(s != null ? Integer.parseInt(s) : 0);
            }
            return ints;
        }
    }



    private class MapValueExtractor extends ValueExtractor<Map<String, String>> {
        @Override
        protected Map<String, String> withPrefix(String prefix, ConfigKey key, Properties properties) {
            return extractFromPropertiesAsMap(key.asPropertyKey(prefix), properties);
        }

        @Override
        protected Map<String, String> merge(ConfigKey key, List<Map<String, String>> values) {
            Map<String, String> merged = null;

            // Iterate in reverse, the first entry in values has highest priority
            for(int i = values.size() - 1; i >= 0; i--) {
                Map<String, String> value = values.get(i);
                if(merged == null) {
                    merged = value;
                } else {
                    merged.putAll(value);
                }
            }
            return merged;
        }
    }
}
