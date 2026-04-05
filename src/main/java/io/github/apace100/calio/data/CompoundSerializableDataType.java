package io.github.apace100.calio.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import io.github.apace100.calio.ClassUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Extended SerializableDataType that supports MapCodec-based encoding/decoding.
 * Provides root tracking and fluent API for compound data structures.
 */
public class CompoundSerializableDataType<T> extends SerializableDataType<T> {

    private final SerializableData serializableData;
    private final Function<SerializableData, MapCodec<T>> mapCodecFactory;
    private final Function<SerializableData, StreamCodec<RegistryFriendlyByteBuf, T>> packetCodecFactory;
    private boolean root;

    @SuppressWarnings("unchecked")
    public CompoundSerializableDataType(
            SerializableData serializableData,
            Function<SerializableData, MapCodec<T>> mapCodecFactory,
            Function<SerializableData, StreamCodec<RegistryFriendlyByteBuf, T>> packetCodecFactory) {
        super((Class<T>) Object.class,
            (buf, t) -> {},
            buf -> null,
            json -> null,
            t -> null);
        this.serializableData = serializableData;
        this.mapCodecFactory = mapCodecFactory;
        this.packetCodecFactory = packetCodecFactory;
        this.root = false;
    }

    public CompoundSerializableDataType<T> setRoot(boolean root) {
        this.root = root;
        return this;
    }

    public boolean isRoot() {
        return root;
    }

    public MapCodec<T> mapCodec() {
        SerializableData data = root ? serializableData.copy().markRoot() : serializableData;
        return mapCodecFactory.apply(data);
    }

    public Codec<T> codec() {
        return mapCodec().codec();
    }

    public StreamCodec<RegistryFriendlyByteBuf, T> packetCodec() {
        SerializableData data = root ? serializableData.copy().markRoot() : serializableData;
        return packetCodecFactory.apply(data);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(FriendlyByteBuf buffer, Object value) {
        packetCodec().encode((RegistryFriendlyByteBuf) buffer, (T) value);
    }

    @Override
    public T receive(FriendlyByteBuf buffer) {
        return packetCodec().decode((RegistryFriendlyByteBuf) buffer);
    }

    @Override
    public T read(com.google.gson.JsonElement jsonElement) {
        return codec().decode(JsonOps.INSTANCE, jsonElement)
            .getOrThrow()
            .getFirst();
    }

    @Override
    public com.google.gson.JsonElement write(T value) {
        return codec().encodeStart(JsonOps.INSTANCE, value)
            .getOrThrow();
    }

    public <I> DataResult<I> write(com.mojang.serialization.DynamicOps<I> ops, T value) {
        return codec().encodeStart(ops, value);
    }

    public SerializableDataType<List<T>> list() {
        return SerializableDataType.list(this);
    }

    public SerializableDataType<List<T>> list(int min, int max) {
        return SerializableDataType.list(this);
    }
}
