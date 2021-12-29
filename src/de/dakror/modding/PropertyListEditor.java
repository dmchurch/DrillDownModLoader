package de.dakror.modding;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ModLoader.Enabled
public class PropertyListEditor implements ModLoader.IResourceMod {
    protected Map<String, Map<String, String>> extraProperties = DefaultingHashMap.using(TreeMap::new);

    public void setPropertiesFromClass(String resourceName, Class<?> propsClass) {
        var propsMap = extraProperties.get(resourceName);
        setPropertiesFromClass(propsMap, propsClass, "");
    }

    private void setPropertiesFromClass(Map<String, String> propsMap, Class<?> propsClass, String prefix) {
        for (var field: propsClass.getDeclaredFields()) {
            try {
                propsMap.put(prefix + field.getName(), (String)field.get(null));
            } catch (IllegalAccessException|IllegalArgumentException|NullPointerException e) {}
        }
        for (var innerClass: propsClass.getDeclaredClasses()) {
            setPropertiesFromClass(propsMap, innerClass, prefix + innerClass.getSimpleName() + ".");
        }
    }

    @Override
    public boolean hooksResource(String resourceName) {
        return extraProperties.containsKey(resourceName);
    }

    @Override
    public InputStream redefineResourceStream(String resourceName, InputStream stream, ClassLoader loader) {
        var props = new TreeMap<>(new BufferedReader(new InputStreamReader(stream))
                        .lines()
                        .collect(Collectors.toMap(l -> l.replaceAll(" *=.*", ""), l -> l.replaceAll(".*= *", ""))));
        props.putAll(extraProperties.get(resourceName));
        var result = props.entrySet()
            .stream()
            .map(e -> String.format("%s = %s", e.getKey(), e.getValue()))
            .collect(Collectors.joining("\n", "", "\n"));
        return new ByteArrayInputStream(result.getBytes());
    }
    
}
