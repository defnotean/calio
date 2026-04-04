package io.github.apace100.calio.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.io.FilenameUtils;
import org.quiltmc.parsers.json.JsonFormat;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.gson.GsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 *  Similar to {@link net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener}, except it supports the JSON5 and JSONC spec.
 */
public abstract class CalioJsonDataLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalioJsonDataLoader.class);
    private static final Map<String, JsonFormat> VALID_EXTENSIONS = Util.make(new HashMap<>(), map -> {
        map.put(".json", JsonFormat.JSON);
        map.put(".json5", JsonFormat.JSON5);
        map.put(".jsonc", JsonFormat.JSONC);
    });

    private final String directoryName;
    private final Gson gson;

    public CalioJsonDataLoader(Gson gson, String directoryName) {
        this.gson = gson;
        this.directoryName = directoryName;
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {

        Map<ResourceLocation, JsonElement> result = new HashMap<>();
        manager.listResources(directoryName, this::hasValidExtension).forEach((fileId, resource) -> {

            ResourceLocation id = this.trim(fileId);
            String fileExtension = "." + FilenameUtils.getExtension(fileId.getPath());

            JsonFormat jsonFormat = VALID_EXTENSIONS.get(fileExtension);
            String packName = resource.sourcePackId();

            try (BufferedReader resourceReader = resource.openAsReader()) {

                GsonReader gsonReader = new GsonReader(JsonReader.create(resourceReader, jsonFormat));
                JsonElement jsonElement = gson.fromJson(gsonReader, JsonElement.class);

                if (jsonElement == null) {
                    throw new JsonParseException("JSON cannot be null! Caused by either the file being empty or a syntax error when being parsed by " + gsonReader);
                }

                JsonElement prevValue = result.put(id, jsonElement);
                if (prevValue != null) {
                    throw new IllegalStateException("Duplicate data file ignored with ID " + id);
                }

            } catch (Exception e) {
                String filePath = packName + "/.../" + fileId.getNamespace() + "/" + fileId.getPath();
                LOGGER.error("Couldn't parse data file \"{}\" from \"{}\": {}", id, filePath, e.getMessage());
            }

        });

        return result;

    }

    protected ResourceLocation trim(ResourceLocation fileId) {
        String path = FilenameUtils.removeExtension(fileId.getPath()).substring(directoryName.length() + 1);
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), path);
    }

    protected boolean hasValidExtension(ResourceLocation fileId) {
        return VALID_EXTENSIONS.keySet()
            .stream()
            .anyMatch(suffix -> fileId.getPath().endsWith(suffix));
    }

}
