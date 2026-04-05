package io.github.apace100.calio.data;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.*;
import io.github.apace100.calio.Calio;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class SerializableData {

    // Should be set to the current namespace of the file that is being read. Allows using * in identifiers.
    // Made ThreadLocal for thread safety during concurrent data loading.
    public static final ThreadLocal<String> CURRENT_NAMESPACE = new ThreadLocal<>();

    // Should be set to the current path of the file that is being read. Allows using * in identifiers.
    // Made ThreadLocal for thread safety during concurrent data loading.
    public static final ThreadLocal<String> CURRENT_PATH = new ThreadLocal<>();

    private final LinkedHashMap<String, Field<?>> dataFields = new LinkedHashMap<>();
    private final List<Function<Instance, DataResult<Instance>>> validators = new ArrayList<>();
    private boolean root = false;

    public SerializableData add(String name, SerializableDataType<?> type) {
        dataFields.put(name, new Field<>(type));
        return this;
    }

    public <T> SerializableData add(String name, SerializableDataType<T> type, T defaultValue) {
        dataFields.put(name, new Field<>(type, defaultValue));
        return this;
    }

    public <T> SerializableData addFunctionedDefault(String name, SerializableDataType<T> type, Function<Instance, T> defaultFunction) {
        dataFields.put(name, new Field<>(type, defaultFunction));
        return this;
    }

    public void write(FriendlyByteBuf buffer, Instance instance) {
        dataFields.forEach((name, field) -> {
            try {

                boolean isPresent = instance.get(name) != null;
                if (field.hasDefault() && field.getDefault(instance) == null) {
                    buffer.writeBoolean(isPresent);
                }

                if (isPresent) {
                    field.dataType.send(buffer, instance.get(name));
                }

            } catch(DataException e) {
                throw e.prepend(name);
            } catch(Exception e) {
                throw new DataException(DataException.Phase.WRITING, name, e);
            }
        });
    }

    public <T> JsonObject write(Instance instance) {

        JsonObject jsonObject = new JsonObject();
        dataFields.forEach((name, field) -> instance.ifPresent(name, o -> {
            try {
                jsonObject.add(name, field.dataType.writeUnsafely(o));
            } catch (Exception e) {
                Calio.LOGGER.error("There was a problem serializing field {} with data type {} to JSON (skipping): {}", name, o.getClass(), e.getMessage());
            }
        }));

        return jsonObject;

    }

    public Instance read(FriendlyByteBuf buffer) {

        Instance instance = new Instance();
        dataFields.forEach((name, field) -> {
            try {

                boolean isPresent = true;
                if (field.hasDefault() && field.getDefault(instance) == null) {
                    isPresent = buffer.readBoolean();
                }

                instance.set(name, isPresent ? field.dataType.receive(buffer) : null);

            } catch (DataException e) {
                throw e.prepend(name);
            } catch (Exception e) {
                throw new DataException(DataException.Phase.RECEIVING, name, e);
            }
        });

        validators.forEach(validator -> {
            DataResult<Instance> result = validator.apply(instance);
            result.error().ifPresent(e -> {
                throw new RuntimeException(e.message());
            });
        });

        return instance;

    }

    public Instance read(JsonObject jsonObject) {

        Instance instance = new Instance();
        dataFields.forEach((name, field) -> {
            try {

                if (jsonObject.has(name)) {
                    instance.set(name, field.dataType.read(jsonObject.get(name)));
                } else if (field.hasDefault()) {
                    instance.set(name, field.getDefault(instance));
                } else {
                    throw new JsonSyntaxException("JSON requires field: " + name);
                }

            } catch (DataException e) {
                throw e.prepend(name);
            } catch (Exception e) {
                throw new DataException(DataException.Phase.READING, name, e);
            }
        });

        validators.forEach(validator -> {
            DataResult<Instance> result = validator.apply(instance);
            result.error().ifPresent(e -> {
                throw new JsonSyntaxException(e.message());
            });
        });

        return instance;

    }

    public SerializableData validate(Function<Instance, DataResult<Instance>> validator) {
        this.validators.add(validator);
        return this;
    }

    public boolean isRoot() {
        return root;
    }

    public SerializableData markRoot() {
        this.root = true;
        return this;
    }

    public SerializableData setRoot(boolean root) {
        this.root = root;
        return this;
    }

    public Instance instance() {
        return new Instance();
    }

    public <I> Stream<I> keys(DynamicOps<I> ops) {
        return dataFields.keySet().stream().map(ops::createString);
    }

    public <I> DataResult<Instance> decode(DynamicOps<I> ops, MapLike<I> input) {
        Instance instance = new Instance();
        for (Map.Entry<String, Field<?>> entry : dataFields.entrySet()) {
            String name = entry.getKey();
            Field<?> field = entry.getValue();
            try {
                I value = input.get(name);
                if (value != null) {
                    instance.set(name, decodeField(field, ops, value));
                } else if (field.hasDefault()) {
                    instance.set(name, field.getDefault(instance));
                }
            } catch (Exception e) {
                return DataResult.error(() -> "Failed to decode field '" + name + "': " + e.getMessage());
            }
        }
        for (Function<Instance, DataResult<Instance>> validator : validators) {
            DataResult<Instance> result = validator.apply(instance);
            if (result.error().isPresent()) {
                return result;
            }
        }
        return DataResult.success(instance);
    }

    @SuppressWarnings("unchecked")
    private <I, T> T decodeField(Field<T> field, DynamicOps<I> ops, I value) {
        if (ops instanceof com.mojang.serialization.JsonOps) {
            com.google.gson.JsonElement json = (com.google.gson.JsonElement) value;
            return field.getDataType().read(json);
        }
        // Fallback: convert to JSON via Dynamic
        com.mojang.serialization.Dynamic<I> dynamic = new com.mojang.serialization.Dynamic<>(ops, value);
        com.google.gson.JsonElement json = dynamic.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue();
        return field.getDataType().read(json);
    }

    public <I> RecordBuilder<I> encode(Instance instance, DynamicOps<I> ops, RecordBuilder<I> prefix) {
        for (Map.Entry<String, Field<?>> entry : dataFields.entrySet()) {
            String name = entry.getKey();
            Field<?> field = entry.getValue();
            if (instance.data.containsKey(name) && instance.data.get(name) != null) {
                try {
                    I encoded = encodeField(field, ops, instance.get(name));
                    prefix.add(name, encoded);
                } catch (Exception ignored) {
                }
            }
        }
        return prefix;
    }

    @SuppressWarnings("unchecked")
    private <I, T> I encodeField(Field<T> field, DynamicOps<I> ops, Object value) throws Exception {
        com.google.gson.JsonElement json = field.getDataType().writeUnsafely(value);
        return com.mojang.serialization.JsonOps.INSTANCE.convertTo(ops, json);
    }

    public void send(FriendlyByteBuf buffer, Instance instance) {
        write(buffer, instance);
    }

    public Instance receive(FriendlyByteBuf buffer) {
        return read(buffer);
    }

    public SerializableData copy() {

        SerializableData copy = new SerializableData();
        copy.dataFields.putAll(dataFields);
        copy.validators.addAll(this.validators);
        copy.root = this.root;

        return copy;

    }

    @SuppressWarnings("unchecked")
    public <T> MapCodec<T> toMapCodecCompat(Function<Instance, T> fromData, BiFunction<T, SerializableData, Instance> toData) {
        SerializableData self = this;
        return new MapCodec<>() {
            @Override
            public <I> Stream<I> keys(DynamicOps<I> ops) {
                return self.keys(ops);
            }

            @Override
            public <I> DataResult<T> decode(DynamicOps<I> ops, MapLike<I> input) {
                return self.decode(ops, input).map(fromData);
            }

            @Override
            public <I> RecordBuilder<I> encode(T input, DynamicOps<I> ops, RecordBuilder<I> prefix) {
                Instance instance = toData.apply(input, self);
                return self.encode(instance, ops, prefix);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public <T> StreamCodec<RegistryFriendlyByteBuf, T> toStreamCodecCompat(Function<Instance, T> fromData, BiFunction<T, SerializableData, Instance> toData) {
        SerializableData self = this;
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                return fromData.apply(self.read(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                self.write(buf, toData.apply(value, self));
            }
        };
    }

    public Iterable<String> getFieldNames() {
        return ImmutableSet.copyOf(dataFields.keySet());
    }

    public Field<?> getField(String fieldName) {
        if(!dataFields.containsKey(fieldName)) {
            throw new IllegalArgumentException("SerializableData contains no field with name \"" + fieldName + "\".");
        } else {
            return dataFields.get(fieldName);
        }
    }

    public class Instance {

        private final HashMap<String, Object> data = new HashMap<>();

        public boolean isPresent(String name) {

            if (dataFields.containsKey(name)) {

                Field<?> field = dataFields.get(name);

                if (field.hasDefault() && field.getDefault(this) == null) {
                    return get(name) != null;
                }

            }

            return data.containsKey(name);

        }

        public <T> void ifPresent(String name, Consumer<T> consumer) {
            if (isPresent(name)) {
                consumer.accept(get(name));
            }
        }

        public Instance set(String name, Object value) {
            this.data.put(name, value);
            return this;
        }

        public void validate() throws Exception {
            for (Function<Instance, DataResult<Instance>> validator : validators) {
                DataResult<Instance> result = validator.apply(this);
                if (result.error().isPresent()) {
                    throw new Exception(result.error().get().message());
                }
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String name) {

            if(!data.containsKey(name)) {
                throw new RuntimeException("Tried to get field \"" + name + "\" from data, which did not exist.");
            }

            return (T) data.get(name);
        }

        public int getInt(String name) {
            return get(name);
        }

        public boolean getBoolean(String name) {
            return get(name);
        }

        public float getFloat(String name) {
            return get(name);
        }

        public double getDouble(String name) {
            return get(name);
        }

        public String getString(String name) {
            return get(name);
        }

        public Identifier getId(String name) {
            return get(name);
        }

        public AttributeModifier getModifier(String name) {
            return get(name);
        }

    }

    public static class Field<T> {
        private final SerializableDataType<T> dataType;
        private final T defaultValue;
        private final Function<Instance, T> defaultFunction;
        private final boolean hasDefault;
        private final boolean hasDefaultFunction;

        public Field(SerializableDataType<T> dataType) {
            this.dataType = dataType;
            this.defaultValue = null;
            this.defaultFunction = null;
            this.hasDefault = false;
            this.hasDefaultFunction = false;
        }

        public Field(SerializableDataType<T> dataType, T defaultValue) {
            this.dataType = dataType;
            this.defaultValue = defaultValue;
            this.defaultFunction = null;
            this.hasDefault = true;
            this.hasDefaultFunction = false;
        }

        public Field(SerializableDataType<T> dataType, Function<Instance, T> defaultFunction) {
            this.dataType = dataType;
            this.defaultValue = null;
            this.defaultFunction = defaultFunction;
            this.hasDefault = false;
            this.hasDefaultFunction = true;
        }

        public boolean hasDefault() {
            return hasDefault || hasDefaultFunction;
        }

        public T getDefault(Instance dataInstance) {
            if (hasDefaultFunction && defaultFunction != null) {
                return defaultFunction.apply(dataInstance);
            } else if (hasDefault) {
                return defaultValue;
            } else {
                throw new IllegalStateException("Tried to access default value of serializable data entry, when no default was provided.");
            }
        }

        public SerializableDataType<T> getDataType() {
            return dataType;
        }

    }

}
