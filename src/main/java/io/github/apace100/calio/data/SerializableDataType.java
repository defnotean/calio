package io.github.apace100.calio.data;

import com.google.common.collect.BiMap;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.apace100.calio.Calio;
import io.github.apace100.calio.ClassUtil;
import io.github.apace100.calio.FilterableWeightedList;

import io.github.apace100.calio.util.ArgumentWrapper;
import io.github.apace100.calio.util.DynamicIdentifier;
import io.github.apace100.calio.util.TagLike;
import com.mojang.serialization.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.random.WeightedList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SerializableDataType<T> {

    private final Class<T> dataClass;
    private final BiConsumer<FriendlyByteBuf, T> send;
    private final Function<FriendlyByteBuf, T> receive;
    private final Function<JsonElement, T> read;
    private final Function<T, JsonElement> write;

    @Deprecated
    public SerializableDataType(Class<T> dataClass,
                                BiConsumer<FriendlyByteBuf, T> send,
                                Function<FriendlyByteBuf, T> receive,
                                Function<JsonElement, T> read) {
        this(dataClass, send, receive, read, (obj) -> {
            Calio.LOGGER.warn("Could not write serializable data type of class {} as it does not have a write function set.", dataClass.getName());
            return new JsonObject();
        });
    }

    public SerializableDataType(Class<T> dataClass,
                                BiConsumer<FriendlyByteBuf, T> send,
                                Function<FriendlyByteBuf, T> receive,
                                Function<JsonElement, T> read,
                                Function<T, JsonElement> write) {
        this.dataClass = dataClass;
        this.send = send;
        this.receive = receive;
        this.read = read;
        this.write = write;
    }

    public void send(FriendlyByteBuf buffer, Object value) {
        send.accept(buffer, cast(value));
    }

    public T receive(FriendlyByteBuf buffer) {
        return receive.apply(buffer);
    }

    public T read(JsonElement jsonElement) {
        return read.apply(jsonElement);
    }

    public JsonElement writeUnsafely(Object value) throws Exception {
        try {
            return write.apply(cast(value));
        } catch (ClassCastException e) {
            throw new Exception(e);
        }
    }

    public JsonElement write(T value) {
        return write.apply(value);
    }

    public T cast(Object data) {
        return dataClass.cast(data);
    }

    // --- Extended API methods needed by Apoli ---

    public Codec<T> codec() {
        SerializableDataType<T> self = this;
        return new Codec<>() {
            @Override
            public <I> DataResult<I> encode(T input, DynamicOps<I> ops, I prefix) {
                JsonElement json = self.write.apply(input);
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
            }

            @Override
            public <I> DataResult<com.mojang.datafixers.util.Pair<T, I>> decode(DynamicOps<I> ops, I input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    T result = self.read.apply(json);
                    return DataResult.success(com.mojang.datafixers.util.Pair.of(result, input));
                } catch (Exception e) {
                    return DataResult.error(e::getMessage);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    public StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> packetCodec() {
        return new StreamCodec<>() {
            @Override
            public T decode(net.minecraft.network.RegistryFriendlyByteBuf buf) {
                return receive.apply(buf);
            }

            @Override
            public void encode(net.minecraft.network.RegistryFriendlyByteBuf buf, T value) {
                send.accept(buf, value);
            }
        };
    }

    public <I> DataResult<I> write(DynamicOps<I> ops, T value) {
        JsonElement json = write.apply(value);
        return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
    }

    public <U> SerializableDataType<U> xmap(Function<T, U> to, Function<U, T> from) {
        return new SerializableDataType<>(ClassUtil.castClass(Object.class),
            (buf, u) -> send.accept(buf, from.apply(u)),
            buf -> to.apply(receive.apply(buf)),
            json -> to.apply(read.apply(json)),
            u -> write.apply(from.apply(u)));
    }

    public <U> SerializableDataType<U> comapFlatMap(Function<T, DataResult<U>> to, Function<U, T> from) {
        return new SerializableDataType<>(ClassUtil.castClass(Object.class),
            (buf, u) -> send.accept(buf, from.apply(u)),
            buf -> to.apply(receive.apply(buf)).getOrThrow(),
            json -> to.apply(read.apply(json)).getOrThrow(),
            u -> write.apply(from.apply(u)));
    }

    public SerializableDataType<java.util.Optional<T>> optional() {
        SerializableDataType<T> self = this;
        return new SerializableDataType<>(ClassUtil.castClass(java.util.Optional.class),
            (buf, opt) -> {
                buf.writeBoolean(opt.isPresent());
                opt.ifPresent(v -> self.send.accept(buf, v));
            },
            buf -> buf.readBoolean() ? java.util.Optional.of(self.receive.apply(buf)) : java.util.Optional.empty(),
            json -> {
                try {
                    return java.util.Optional.of(self.read.apply(json));
                } catch (Exception e) {
                    return java.util.Optional.empty();
                }
            },
            opt -> opt.map(self.write).orElse(com.google.gson.JsonNull.INSTANCE));
    }

    public SerializableDataType<List<T>> list() {
        return list(this);
    }

    public SerializableDataType<List<T>> list(int min, int max) {
        return list(this);
    }

    public CompoundSerializableDataType<T> setRoot(boolean root) {
        // For base type, return a compound wrapper if possible
        if (this instanceof CompoundSerializableDataType<T> compound) {
            return compound.setRoot(root);
        }
        // Create a pass-through CompoundSerializableDataType
        SerializableDataType<T> self = this;
        CompoundSerializableDataType<T> wrapper = new CompoundSerializableDataType<>(
            new SerializableData(),
            data -> MapCodec.unit(null),
            data -> self.packetCodec()
        );
        return wrapper.setRoot(root);
    }

    public boolean isRoot() {
        return this instanceof CompoundSerializableDataType<?> compound && compound.isRoot();
    }

    public static <T> SerializableDataType<T> of(Codec<T> codec, StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec) {
        return new SerializableDataType<>(ClassUtil.castClass(Object.class),
            (buf, t) -> streamCodec.encode((net.minecraft.network.RegistryFriendlyByteBuf) buf, t),
            buf -> streamCodec.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf),
            json -> codec.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst(),
            t -> codec.encodeStart(JsonOps.INSTANCE, t).getOrThrow());
    }

    public static <T> SerializableDataType<T> lazy(java.util.function.Supplier<SerializableDataType<T>> supplier) {
        return new SerializableDataType<>(ClassUtil.castClass(Object.class),
            (buf, t) -> supplier.get().send(buf, t),
            buf -> supplier.get().receive(buf),
            json -> supplier.get().read(json),
            t -> {
                try {
                    return supplier.get().writeUnsafely(t);
                } catch (Exception e) {
                    return new com.google.gson.JsonObject();
                }
            });
    }

    public static <T> CompoundSerializableDataType<T> lazy(java.util.function.Supplier<CompoundSerializableDataType<T>> supplier, boolean compound) {
        return new CompoundSerializableDataType<>(
            new SerializableData(),
            data -> supplier.get().mapCodec(),
            data -> supplier.get().packetCodec()
        );
    }

    public static <T> SerializableDataType<T> recursive(Function<SerializableDataType<T>, SerializableDataType<T>> factory) {
        SerializableDataType<T>[] holder = new SerializableDataType[1];
        SerializableDataType<T> proxy = new SerializableDataType<>(ClassUtil.castClass(Object.class),
            (buf, t) -> holder[0].send(buf, t),
            buf -> holder[0].receive(buf),
            json -> holder[0].read(json),
            t -> {
                try {
                    return holder[0].writeUnsafely(t);
                } catch (Exception e) {
                    return new com.google.gson.JsonObject();
                }
            });
        holder[0] = factory.apply(proxy);
        return holder[0];
    }

    public static <T extends Enum<T>> SerializableDataType<EnumSet<T>> enumSet(SerializableDataType<T> enumDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(EnumSet.class),
            (buf, set) -> {
                buf.writeInt(set.size());
                for (T val : set) {
                    enumDataType.send(buf, val);
                }
            },
            buf -> {
                int count = buf.readInt();
                java.util.Set<T> tempSet = new java.util.LinkedHashSet<>();
                for (int i = 0; i < count; i++) {
                    tempSet.add(enumDataType.receive(buf));
                }
                return tempSet.isEmpty() ? EnumSet.noneOf(getEnumClass(tempSet)) : EnumSet.copyOf(tempSet);
            },
            json -> {
                java.util.Set<T> tempSet = new java.util.LinkedHashSet<>();
                if (json.isJsonArray()) {
                    for (JsonElement elem : json.getAsJsonArray()) {
                        tempSet.add(enumDataType.read(elem));
                    }
                } else {
                    tempSet.add(enumDataType.read(json));
                }
                return EnumSet.copyOf(tempSet);
            },
            set -> {
                JsonArray arr = new JsonArray();
                for (T val : set) {
                    arr.add(enumDataType.write(val));
                }
                return arr;
            });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Enum<T>> Class<T> getEnumClass(java.util.Set<T> set) {
        // This is only called when set is empty, so we need a default
        return (Class) Enum.class;
    }

    public static <T extends Number> SerializableDataType<T> boundNumber(SerializableDataType<T> numberDataType, T min, T max) {
        return numberDataType;
    }

    public static <K, V> SerializableDataType<java.util.Map<K, V>> map(SerializableDataType<K> keyType, SerializableDataType<V> valueType) {
        return new SerializableDataType<>(ClassUtil.castClass(java.util.Map.class),
            (buf, map) -> {
                buf.writeInt(map.size());
                map.forEach((k, v) -> {
                    keyType.send(buf, k);
                    valueType.send(buf, v);
                });
            },
            buf -> {
                int count = buf.readInt();
                java.util.Map<K, V> map = new java.util.LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    map.put(keyType.receive(buf), valueType.receive(buf));
                }
                return map;
            },
            json -> {
                java.util.Map<K, V> map = new java.util.LinkedHashMap<>();
                if (json.isJsonObject()) {
                    for (java.util.Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                        K key = keyType.read(new com.google.gson.JsonPrimitive(entry.getKey()));
                        V val = valueType.read(entry.getValue());
                        map.put(key, val);
                    }
                }
                return map;
            },
            map -> {
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                map.forEach((k, v) -> obj.add(keyType.write(k).getAsString(), valueType.write(v)));
                return obj;
            });
    }

    public static <T extends Enum<T>> SerializableDataType<T> enumValue(Class<T> dataClass, java.util.function.Supplier<com.google.common.collect.ImmutableMap<String, T>> additionalMapSupplier) {
        java.util.Map<String, T> additionalMap = additionalMapSupplier.get();
        HashMap<String, T> combined = new HashMap<>(additionalMap);
        for (T constant : dataClass.getEnumConstants()) {
            combined.put(constant.name().toLowerCase(java.util.Locale.ROOT), constant);
        }
        return enumValue(dataClass, combined);
    }

    // enumSet with Class parameter already exists below

    public static CompoundSerializableDataType compound(io.github.apace100.calio.registry.DataObjectFactory factory) {
        SerializableData data = factory.getSerializableData();
        return new CompoundSerializableDataType<>(
            data,
            sd -> sd.toMapCodecCompat(factory::fromData, (t, d) -> factory.toData(t, d)),
            sd -> sd.toStreamCodecCompat(factory::fromData, (t, d) -> factory.toData(t, d))
        );
    }

    public static <T> CompoundSerializableDataType<T> compound(
            SerializableData data,
            Function<SerializableData.Instance, T> fromData,
            BiFunction<T, SerializableData, SerializableData.Instance> toData) {
        return new CompoundSerializableDataType<>(
            data,
            sd -> sd.toMapCodecCompat(fromData, toData),
            sd -> sd.toStreamCodecCompat(fromData, toData)
        );
    }

    public static <T> SerializableDataType<List<T>> list(SerializableDataType<T> singleDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(List.class), (buf, list) -> {
            buf.writeInt(list.size());
            int i = 0;
            for(T elem : list) {
                try {
                    singleDataType.send(buf, elem);
                } catch(DataException e) {
                    throw e.prepend("[" + i + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.WRITING, "[" + i + "]", e);
                }
                i++;
            }
        }, (buf) -> {
            int count = buf.readInt();
            LinkedList<T> list = new LinkedList<>();
            for(int i = 0; i < count; i++) {
                try {
                    list.add(singleDataType.receive(buf));
                } catch(DataException e) {
                    throw e.prepend("[" + i + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.RECEIVING, "[" + i + "]", e);
                }
            }
            return list;
        }, (json) -> {
            LinkedList<T> list = new LinkedList<>();
            if(json.isJsonArray()) {
                int i = 0;
                for(JsonElement je : json.getAsJsonArray()) {
                    try {
                        list.add(singleDataType.read(je));
                    } catch(DataException e) {
                        throw e.prepend("[" + i + "]");
                    } catch(Exception e) {
                        throw new DataException(DataException.Phase.READING, "[" + i + "]", e);
                    }
                    i++;
                }
            } else {
                list.add(singleDataType.read(json));
            }
            return list;
        }, (list) -> {
            JsonArray array = new JsonArray();
            for (T value : list) {
                array.add(singleDataType.write.apply(value));
            }
            return array;
        });
    }

    public static <T> SerializableDataType<FilterableWeightedList<T>> weightedList(SerializableDataType<T> singleDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(FilterableWeightedList.class), (buf, list) -> {
            buf.writeInt(list.size());
            AtomicInteger i = new AtomicInteger();
            list.entryStream().forEach(entry -> {
                try {
                    singleDataType.send(buf, entry.data());
                    buf.writeInt(entry.weight());
                } catch(DataException e) {
                    throw e.prepend("[" + i.get() + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.WRITING, "[" + i.get() + "]", e);
                }
                i.getAndIncrement();
            });
        }, (buf) -> {
            int count = buf.readInt();
            FilterableWeightedList<T> list = new FilterableWeightedList<>();
            for (int i = 0; i < count; i++) {
                try {
                    T t = singleDataType.receive(buf);
                    int weight = buf.readInt();
                    list.add(t, weight);
                } catch(DataException e) {
                    throw e.prepend("[" + i + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.RECEIVING, "[" + i + "]", e);
                }
            }
            return list;
        }, (json) -> {
            FilterableWeightedList<T> list = new FilterableWeightedList<>();
            if (json.isJsonArray()) {
                int i = 0;
                for (JsonElement je : json.getAsJsonArray()) {
                    try {
                        JsonObject weightedObj = je.getAsJsonObject();
                        T elem = singleDataType.read(weightedObj.get("element"));
                        int weight = GsonHelper.getAsInt(weightedObj, "weight");
                        list.add(elem, weight);
                    } catch(DataException e) {
                        throw e.prepend("[" + i + "]");
                    } catch(Exception e) {
                        throw new DataException(DataException.Phase.READING, "[" + i + "]", e);
                    }
                    i++;
                }
            }
            return list;
        }, (list) -> {
            JsonArray array = new JsonArray();
            for (FilterableWeightedList.Entry<T> value : list.entryStream().toList()) {
                JsonObject listObject = new JsonObject();
                listObject.add("element", singleDataType.write.apply(value.data()));
                listObject.addProperty("weight", value.weight());
                array.add(listObject);
            }
            return array;
        });
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry) {
        return registry(dataClass, registry, false);
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, String defaultNamespace) {
        return registry(dataClass, registry, defaultNamespace, false);
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, boolean showPossibleValues) {
        return registry(dataClass, registry, Identifier.DEFAULT_NAMESPACE, showPossibleValues);
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, String defaultNamespace, boolean showPossibleValues) {
        return registry(dataClass, registry, defaultNamespace, (reg, id) -> {
            String possibleValues = showPossibleValues ? " Expected value to be any of " + String.join(", ", reg.keySet().stream().map(Identifier::toString).toList()) : "";
            return new RuntimeException("Type \"%s\" is not registered in registry \"%s\".%s".formatted(id, registry.key().registry(), possibleValues));
        });
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, BiFunction<Registry<T>, Identifier, RuntimeException> exception) {
        return registry(dataClass, registry, Identifier.DEFAULT_NAMESPACE, exception);
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, String defaultNamespace, BiFunction<Registry<T>, Identifier, RuntimeException> exception) {
        return wrap(
            dataClass,
            SerializableDataTypes.STRING,
            t -> Objects.requireNonNull(registry.getKey(t)).toString(),
            idString -> {
                Identifier id = DynamicIdentifier.of(idString, defaultNamespace);
                return registry.getOptional(id).orElseThrow(() -> exception.apply(registry, id));
            }
        );
    }

    // Overloads without Class<T> that accept IdentifierAlias (used by Apoli)
    @SuppressWarnings("unchecked")
    public static <T> SerializableDataType<T> registry(Registry<T> registry, String defaultNamespace, io.github.apace100.calio.util.IdentifierAlias aliases, BiFunction<Registry<T>, Identifier, String> errorMessage) {
        return wrap(
            (Class<T>) Object.class,
            SerializableDataTypes.STRING,
            t -> Objects.requireNonNull(registry.getKey(t)).toString(),
            idString -> {
                Identifier id = DynamicIdentifier.of(idString, defaultNamespace);
                // Try alias resolution
                Identifier resolved = aliases != null ? aliases.resolve(id) : id;
                return registry.getOptional(resolved)
                    .or(() -> registry.getOptional(id))
                    .orElseThrow(() -> new RuntimeException(errorMessage.apply(registry, id)));
            }
        );
    }

    @SuppressWarnings("unchecked")
    public static <T> SerializableDataType<T> registry(Registry<T> registry, String defaultNamespace) {
        return registry((Class<T>) Object.class, registry, defaultNamespace);
    }

    public static <T> SerializableDataType<T> compound(Class<T> dataClass, SerializableData data, Function<SerializableData.Instance, T> toInstance, BiFunction<SerializableData, T, SerializableData.Instance> toData) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> data.write(buf, toData.apply(data, t)),
            (buf) -> toInstance.apply(data.read(buf)),
            (json) -> toInstance.apply(data.read(json.getAsJsonObject())),
            (t) -> data.write(toData.apply(data, t)));
    }

    public static <T extends Enum<T>> SerializableDataType<T> enumValue(Class<T> dataClass) {
        return enumValue(dataClass, (HashMap<String, T>) null);
    }

    public static <T extends Enum<T>> SerializableDataType<T> enumValue(Class<T> dataClass, HashMap<String, T> additionalMap) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> buf.writeInt(t.ordinal()),
            (buf) -> dataClass.getEnumConstants()[buf.readInt()],
            (json) -> {
                if(json.isJsonPrimitive()) {
                    JsonPrimitive primitive = json.getAsJsonPrimitive();
                    if(primitive.isNumber()) {
                        int enumOrdinal = primitive.getAsInt();
                        T[] enumValues = dataClass.getEnumConstants();
                        if(enumOrdinal < 0 || enumOrdinal >= enumValues.length) {
                            throw new JsonSyntaxException("Expected to be in the range of 0 - " + (enumValues.length - 1));
                        }
                        return enumValues[enumOrdinal];
                    } else if(primitive.isString()) {
                        String enumName = primitive.getAsString();
                        try {
                            T t = Enum.valueOf(dataClass, enumName);
                            return t;
                        } catch(IllegalArgumentException e0) {
                            try {
                                T t = Enum.valueOf(dataClass, enumName.toUpperCase(Locale.ROOT));
                                return t;
                            } catch (IllegalArgumentException e1) {
                                try {
                                    if(additionalMap == null || !additionalMap.containsKey(enumName)) {
                                        throw new IllegalArgumentException();
                                    }
                                    T t = additionalMap.get(enumName);
                                    return t;
                                } catch (IllegalArgumentException e2) {
                                    T[] enumValues = dataClass.getEnumConstants();
                                    String stringOf = enumValues[0].name() + ", " + enumValues[0].name().toLowerCase(Locale.ROOT);
                                    for(int i = 1; i < enumValues.length; i++) {
                                        stringOf += ", " + enumValues[i].name() + ", " + enumValues[i].name().toLowerCase(Locale.ROOT);
                                    }
                                    throw new JsonSyntaxException("Expected value to be a string of: " + stringOf);
                                }
                            }
                        }
                    }
                }
                throw new JsonSyntaxException("Expected value to be either an integer or a string.");
            },
            (t) -> new JsonPrimitive(t.name()));
    }

    public static <V> SerializableDataType<Map<String, V>> map(SerializableDataType<V> valueDataType) {
        return new SerializableDataType<>(
            ClassUtil.castClass(Map.class),
            (buffer, map) -> buffer.writeMap(
                map,
                FriendlyByteBuf::writeUtf,
                valueDataType::send
            ),
            buffer -> buffer.readMap(
                FriendlyByteBuf::readUtf,
                valueDataType::receive
            ),
            jsonElement -> {

                if (!(jsonElement instanceof JsonObject jsonObject)) {
                    throw new JsonSyntaxException("Expected a JSON object.");
                }

                Map<String, V> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    map.put(entry.getKey(), valueDataType.read(entry.getValue()));
                }

                return map;

            },
            map -> {

                JsonObject jsonObject = new JsonObject();
                map.forEach((k, v) -> jsonObject.add(k, valueDataType.write(v)));

                return jsonObject;

            }
        );
    }

    public static <T> SerializableDataType<T> mapped(Class<T> dataClass, BiMap<String, T> map) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> buf.writeUtf(map.inverse().get(t)),
            (buf) -> map.get(buf.readUtf(32767)),
            (json) -> {
                if(json.isJsonPrimitive()) {
                    JsonPrimitive primitive = json.getAsJsonPrimitive();
                    if(primitive.isString()) {
                        String name = primitive.getAsString();
                        try {
                            if(map == null || !map.containsKey(name)) {
                                throw new IllegalArgumentException();
                            }
                            T t = map.get(name);
                            return t;
                        } catch (IllegalArgumentException e2) {
                            throw new JsonSyntaxException("Expected value to be a string of: " + map.keySet().stream().reduce((s0, s1) -> s0 + ", " + s1));
                        }
                    }
                }
                throw new JsonSyntaxException("Expected value to be a string.");
            },
            (t) -> new JsonPrimitive(map.inverse().get(t)));
    }

    public static <T, U> SerializableDataType<T> wrap(Class<T> dataClass, SerializableDataType<U> base, Function<T, U> toFunction, Function<U, T> fromFunction) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> base.send(buf, toFunction.apply(t)),
            (buf) -> fromFunction.apply(base.receive(buf)),
            (json) -> fromFunction.apply(base.read(json)),
            (t) -> base.write(toFunction.apply(t)));
    }

    public static <T> SerializableDataType<TagKey<T>> tag(ResourceKey<? extends Registry<T>> registryRef) {
        return wrap(
            ClassUtil.castClass(TagKey.class),
            SerializableDataTypes.IDENTIFIER,
            TagKey::location,
            id -> {

                TagKey<T> tagKey = TagKey.create(registryRef, id);
                Map<TagKey<?>, Collection<Holder<?>>> registryTags = Calio.REGISTRY_TAGS.get();

                if (registryTags != null && !registryTags.containsKey(tagKey)) {
                    throw new IllegalArgumentException("Tag \"" + id + "\" for registry \"" + registryRef.registry() + "\" doesn't exist.");
                }

                return tagKey;

            }
        );
    }

    public static <T> SerializableDataType<ResourceKey<T>> registryKey(ResourceKey<Registry<T>> registryRef) {
        return registryKey(registryRef, List.of());
    }

    public static <T> SerializableDataType<ResourceKey<T>> registryKey(ResourceKey<Registry<T>> registryRef, Collection<ResourceKey<T>> exemptions) {
        return wrap(
            ClassUtil.castClass(ResourceKey.class),
            SerializableDataTypes.IDENTIFIER,
            ResourceKey::identifier,
            id -> {

                ResourceKey<T> resourceKey = ResourceKey.create(registryRef, id);
                RegistryAccess dynamicRegistries = Calio.DYNAMIC_REGISTRIES.get();

                if (dynamicRegistries == null || exemptions.contains(resourceKey)) {
                    return resourceKey;
                }

                if (!dynamicRegistries.lookupOrThrow(registryRef).containsKey(resourceKey)) {
                    throw new IllegalArgumentException("Type \"" + id + "\" is not registered in registry \"" + registryRef.registry() + "\"");
                }

                return resourceKey;

            }
        );
    }

    public static <T extends Enum<T>> SerializableDataType<EnumSet<T>> enumSet(Class<T> enumClass, SerializableDataType<T> enumDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(EnumSet.class),
            (buf, set) -> {
                buf.writeInt(set.size());
                set.forEach(t -> buf.writeInt(t.ordinal()));
            },
            (buf) -> {
                int size = buf.readInt();
                EnumSet<T> set = EnumSet.noneOf(enumClass);
                T[] allValues = enumClass.getEnumConstants();
                for(int i = 0; i < size; i++) {
                    int ordinal = buf.readInt();
                    set.add(allValues[ordinal]);
                }
                return set;
            },
            (json) -> {
                EnumSet<T> set = EnumSet.noneOf(enumClass);
                if(json.isJsonPrimitive()) {
                    T t = enumDataType.read.apply(json);
                    set.add(t);
                } else
                if(json.isJsonArray()) {
                    JsonArray array = json.getAsJsonArray();
                    for (JsonElement jsonElement : array) {
                        T t = enumDataType.read.apply(jsonElement);
                        set.add(t);
                    }
                } else {
                    throw new RuntimeException("Expected enum set to be either an array or a primitive.");
                }
                return set;
            },
            (set) -> {
                JsonArray array = new JsonArray();
                for (T value : set) {
                    array.add(enumDataType.write.apply(value));
                }
                return array;
            });
    }

    public static <T extends Number> SerializableDataType<T> boundNumber(SerializableDataType<T> numberDataType, T min, T max, Function<T, BiFunction<T, T, T>> read) {
        return new SerializableDataType<>(
            numberDataType.dataClass,
            numberDataType.send,
            numberDataType.receive,
            jsonElement -> read.apply(numberDataType.read(jsonElement)).apply(min, max),
            numberDataType.write
        );
    }

    public static <T, U extends ArgumentType<T>> SerializableDataType<ArgumentWrapper<T>> argumentType(U argumentType) {
        return wrap(ClassUtil.castClass(ArgumentWrapper.class), SerializableDataTypes.STRING,
            ArgumentWrapper::rawArgument,
            str -> {
                try {
                    T t = argumentType.parse(new StringReader(str));
                    return new ArgumentWrapper<>(t, str);
                } catch (CommandSyntaxException e) {
                    throw new RuntimeException(e.getMessage());
                }
            });
    }

    public static <T> SerializableDataType<TagLike<T>> tagLike(Registry<T> registry) {
        return new SerializableDataType<>(
            ClassUtil.castClass(TagLike.class),
            (buf, tagLike) ->
                tagLike.write(buf),
            buf -> {

                TagLike<T> tagLike = new TagLike<>(registry);
                tagLike.read(buf);

                return tagLike;

            },
            jsonElement ->
                TagLike.fromJson(registry, jsonElement),
            TagLike::toJson
        );
    }

}
