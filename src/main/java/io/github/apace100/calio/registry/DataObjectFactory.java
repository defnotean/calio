package io.github.apace100.calio.registry;

import io.github.apace100.calio.data.SerializableData;
import net.minecraft.resources.Identifier;

public interface DataObjectFactory<T> {

    SerializableData getData();
    T fromData(SerializableData.Instance instance);
    SerializableData.Instance toData(T t);

    // Compatibility aliases
    default SerializableData getSerializableData() {
        return getData();
    }

    default SerializableData.Instance toData(T t, SerializableData serializableData) {
        return toData(t);
    }

    static <T> DataObjectFactory<T> simple(
            SerializableData serializableData,
            java.util.function.Function<SerializableData.Instance, T> fromData,
            java.util.function.BiFunction<T, SerializableData, SerializableData.Instance> toData) {
        return new DataObjectFactory<>() {
            @Override
            public SerializableData getData() {
                return serializableData;
            }

            @Override
            public T fromData(SerializableData.Instance instance) {
                return fromData.apply(instance);
            }

            @Override
            public SerializableData.Instance toData(T t) {
                return toData.apply(t, serializableData);
            }

            @Override
            public SerializableData.Instance toData(T t, SerializableData sd) {
                return toData.apply(t, sd);
            }
        };
    }
}
