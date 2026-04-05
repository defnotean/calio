package io.github.apace100.calio.util;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides path and namespace aliasing for identifiers.
 * Used by Apoli's type registries to support backwards compatibility with renamed types.
 */
public class IdentifierAlias {

    private final Map<String, String> pathAliases = new HashMap<>();
    private final Map<String, String> namespaceAliases = new HashMap<>();

    public void addPathAlias(String alias, String target) {
        pathAliases.put(alias, target);
    }

    public void addNamespaceAlias(String alias, String target) {
        namespaceAliases.put(alias, target);
    }

    public String resolvePathAlias(String path) {
        return pathAliases.getOrDefault(path, path);
    }

    public String resolveNamespaceAlias(String namespace) {
        return namespaceAliases.getOrDefault(namespace, namespace);
    }

    public Identifier resolve(Identifier id) {
        String namespace = resolveNamespaceAlias(id.getNamespace());
        String path = resolvePathAlias(id.getPath());
        if (namespace.equals(id.getNamespace()) && path.equals(id.getPath())) {
            return id;
        }
        return Identifier.parse(namespace + ":" + path);
    }
}
