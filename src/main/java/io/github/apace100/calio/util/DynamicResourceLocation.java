package io.github.apace100.calio.util;

import com.google.gson.JsonElement;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ResourceLocationException;

public class DynamicResourceLocation {

    public static final String DEFAULT_NAMESPACE = ResourceLocation.DEFAULT_NAMESPACE;

    protected DynamicResourceLocation() {
    }

    public static ResourceLocation of(JsonElement jsonElement) {
        return of(jsonElement.getAsString());
    }

    public static ResourceLocation of(String idString) {
        return of(idString, DEFAULT_NAMESPACE);
    }

    public static ResourceLocation of(String idString, String defaultNamespace) {

        String[] namespaceAndPath = splitWithNamespace(idString, defaultNamespace);
        if (namespaceAndPath[0].contains("*")) {
            String currentNamespace = SerializableData.CURRENT_NAMESPACE.get();
            if (currentNamespace != null) {
                namespaceAndPath[0] = namespaceAndPath[0].replace("*", currentNamespace);
            } else {
                throw new ResourceLocationException("ResourceLocations may only contain '*' in its namespace in data loaders that support it.");
            }
        }

        if (namespaceAndPath[1].contains("*")) {
            String currentPath = SerializableData.CURRENT_PATH.get();
            if (currentPath != null) {
                namespaceAndPath[1] = namespaceAndPath[1].replace("*", currentPath);
            } else {
                throw new ResourceLocationException("ResourceLocations may only contain '*' in its path in data loaders that support it.");
            }
        }

        return ResourceLocation.fromNamespaceAndPath(namespaceAndPath[0], namespaceAndPath[1]);

    }

    public static String[] splitWithNamespace(String idString, String defaultNamespace) {

        String[] namespaceAndPath = idString.split(":");
        if (namespaceAndPath.length > 2) {
            throw new ResourceLocationException("ResourceLocation \"" + idString + "\" must only have one \":\" separating its namespace and path.");
        }

        if (namespaceAndPath.length == 1) {
            return new String[]{defaultNamespace, namespaceAndPath[0]};
        }

        return namespaceAndPath;

    }

}
