package io.github.apace100.calio.registry;

import io.github.apace100.calio.data.SerializableData;
import net.minecraft.resources.ResourceLocation;

public interface DataObjectFactory<T> {

    SerializableData getData();
    T fromData(SerializableData.Instance instance);
    SerializableData.Instance toData(T t);
}
