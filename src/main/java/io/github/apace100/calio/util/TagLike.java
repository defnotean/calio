package io.github.apace100.calio.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import io.github.apace100.calio.Calio;
import io.github.apace100.calio.data.DataException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;

import java.util.*;

@SuppressWarnings("unused")
public class TagLike<T> {

    private final Registry<T> registry;

    private final List<TagKey<T>> tags = new LinkedList<>();
    private final Set<T> items = new HashSet<>();

    public TagLike(Registry<T> registry) {
        this.registry = registry;
    }

    public void addTag(Identifier id) {
        addTag(TagKey.create(registry.key(), id));
    }

    public void add(Identifier id) {
        registry.getOptional(id).ifPresent(this::add);
    }

    public void addTag(TagKey<T> tagKey) {
        tags.add(tagKey);
    }

    public void add(T t) {
        items.add(t);
    }

    public void addAll(TagLike<T> otherTagLike) {
        this.tags.addAll(otherTagLike.tags);
        this.items.addAll(otherTagLike.items);
    }

    public boolean contains(T t) {

        if (items.contains(t)) {
            return true;
        }

        Holder<T> entry = registry.wrapAsHolder(t);
        return tags
            .stream()
            .anyMatch(entry::is);

    }

    public void clear() {
        this.tags.clear();
        this.items.clear();
    }

    public void write(FriendlyByteBuf buf) {

        buf.writeVarInt(tags.size());
        for (TagKey<T> tagKey : tags) {
            buf.writeIdentifier(tagKey.location());
        }

        List<Identifier> ids = new LinkedList<>();
        for (T t : items) {

            Identifier id = registry.getKey(t);

            if (id != null) {
                ids.add(id);
            }

        }

        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeIdentifier);

    }

    public void read(FriendlyByteBuf buf) {

        this.clear();

        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            tags.add(TagKey.create(registry.key(), buf.readIdentifier()));
        }

        count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            registry.getOptional(buf.readIdentifier()).ifPresent(items::add);
        }

    }

    /**
     * Parses a single entry. Supports:
     * <ul>
     *   <li>A plain string: {@code "minecraft:foo"} or {@code "#minecraft:some_tag"}</li>
     *   <li>An object: {@code {"id": "minecraft:foo", "required": false}} — optional entries that are
     *       missing are silently skipped; required (default) entries throw an error if missing.</li>
     * </ul>
     */
    private static <T> Optional<Either<TagKey<T>, Identifier>> parse(Registry<T> registry, JsonElement jsonElement) {

        Map<TagKey<?>, Collection<Holder<?>>> registryTags = Calio.REGISTRY_TAGS.get();
        ResourceKey<? extends Registry<T>> registryKey = registry.key();

        String entry;
        boolean required = true;

        if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
            entry = jsonPrimitive.getAsString();
        } else if (jsonElement instanceof JsonObject jsonObject) {
            if (!jsonObject.has("id")) {
                throw new JsonSyntaxException("Expected object entry to have an 'id' field.");
            }
            entry = jsonObject.get("id").getAsString();
            required = !jsonObject.has("required") || jsonObject.get("required").getAsBoolean();
        } else {
            throw new JsonSyntaxException("Expected a string or an object with an 'id' field.");
        }

        Identifier entryId;

        if (entry.startsWith("#")) {
            entryId = DynamicIdentifier.of(entry.substring(1));
            TagKey<T> entryTag = TagKey.create(registryKey, entryId);

            if (registryTags != null && !registryTags.containsKey(entryTag)) {
                if (required) {
                    throw new IllegalArgumentException("Tag \"" + entryId + "\" for registry \"" + registryKey.registry() + "\" doesn't exist.");
                }
                return Optional.empty();
            }

            return Optional.of(Either.left(entryTag));

        } else {
            entryId = DynamicIdentifier.of(entry);

            if (!registry.containsKey(entryId)) {
                if (required) {
                    throw new IllegalArgumentException("Type \"" + entryId + "\" is not registered in registry \"" + registryKey.registry() + "\".");
                }
                return Optional.empty();
            }

            return Optional.of(Either.right(entryId));
        }

    }

    public static <T> TagLike<T> fromJson(Registry<T> registry, JsonElement jsonElement) {

        TagLike<T> tagLike = new TagLike<>(registry);
        if (jsonElement instanceof JsonArray jsonArray) {

            for (int i = 0; i < jsonArray.size(); i++) {

                try {
                    parse(registry, jsonArray.get(i)).ifPresent(result ->
                        result.ifLeft(tagLike::addTag).ifRight(tagLike::add)
                    );
                }

                catch (DataException de) {
                    throw de.prepend("[" + i + "]");
                }

                catch (Exception e) {
                    throw new DataException(DataException.Phase.READING, "[" + i + "]", e);
                }

            }

        }

        else if (jsonElement instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isString()) {
            parse(registry, jsonElement).ifPresent(result ->
                result.ifLeft(tagLike::addTag).ifRight(tagLike::add)
            );
        }

        else if (jsonElement instanceof JsonObject) {
            parse(registry, jsonElement).ifPresent(result ->
                result.ifLeft(tagLike::addTag).ifRight(tagLike::add)
            );
        }

        else {
            throw new JsonSyntaxException("Expected a JSON array, a string, or an object.");
        }

        return tagLike;

    }

    public JsonElement toJson() {

        JsonArray jsonArray = new JsonArray();

        for (TagKey<T> tagKey : this.tags) {
            jsonArray.add("#" + tagKey.location().toString());
        }

        for (T t : this.items) {

            Identifier id = this.registry.getKey(t);

            if (id != null) {
                jsonArray.add(id.toString());
            }

        }

        return jsonArray;

    }

    @Deprecated(forRemoval = true)
    public void write(JsonArray array) {

        for (TagKey<T> tagKey : tags) {
            array.add("#" + tagKey.location().toString());
        }

        for (T t : items) {

            Identifier id = registry.getKey(t);

            if (id != null) {
                array.add(id.toString());
            }

        }

    }

}
