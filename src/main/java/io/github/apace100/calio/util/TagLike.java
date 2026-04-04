package io.github.apace100.calio.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

//  TODO: Implement support for optional entries
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
        add(registry.get(id));
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

            T t = registry.get(buf.readIdentifier());

            if (t != null) {
                items.add(t);
            }

        }

    }

    private static <T> Either<TagKey<T>, Identifier> parse(Registry<T> registry, JsonElement jsonElement) {

        if (!(jsonElement instanceof JsonPrimitive jsonPrimitive) || !jsonPrimitive.isString()) {
            throw new JsonSyntaxException("Expected a string.");
        }

        Map<TagKey<?>, Collection<Holder<?>>> registryTags = Calio.REGISTRY_TAGS.get();
        ResourceKey<? extends Registry<T>> registryKey = registry.key();

        String entry = jsonElement.getAsString();
        Identifier entryId;

        if (entry.startsWith("#")) {

            entryId = DynamicIdentifier.of(entry.substring(1));
            TagKey<T> entryTag = TagKey.create(registryKey, entryId);

            if (registryTags != null && !registryTags.containsKey(entryTag)) {
                throw new IllegalArgumentException("Tag \"" + entryId + "\" for registry \"" + registryKey.location() + "\" doesn't exist.");
            }

            return Either.left(entryTag);

        }

        else {

            entryId = DynamicIdentifier.of(entry);
            if (!registry.containsKey(entryId)) {
                throw new IllegalArgumentException("Type \"" + entryId + "\" is not registered in registry \"" + registryKey.location() + "\".");
            }

            return Either.right(entryId);

        }

    }

    public static <T> TagLike<T> fromJson(Registry<T> registry, JsonElement jsonElement) {

        TagLike<T> tagLike = new TagLike<>(registry);
        if (jsonElement instanceof JsonArray jsonArray) {

            for (int i = 0; i < jsonArray.size(); i++) {

                try {
                    parse(registry, jsonArray.get(i))
                        .ifLeft(tagLike::addTag)
                        .ifRight(tagLike::add);
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
            parse(registry, jsonElement)
                .ifLeft(tagLike::addTag)
                .ifRight(tagLike::add);
        }

        else {
            throw new JsonSyntaxException("Expected a JSON array or a string.");
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

        for(T t : items) {

            Identifier id = registry.getKey(t);

            if (id != null) {
                array.add(id.toString());
            }

        }

    }

}
