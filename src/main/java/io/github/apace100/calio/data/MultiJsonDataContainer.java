package io.github.apace100.calio.data;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;

public class MultiJsonDataContainer extends LinkedHashMap<ResourceLocation, LinkedHashMap<String, List<JsonElement>>> {

    public void forEach(Processor processor) {
        super.forEach((id, packedJsonData) ->
            packedJsonData.forEach((packName, jsonElements) ->
                jsonElements.forEach(jsonElement -> processor.process(packName, id, jsonElement))));
    }

    @FunctionalInterface
    public interface Processor {
        void process(String packName, ResourceLocation id, JsonElement jsonElement);
    }

}
