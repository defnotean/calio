package io.github.apace100.calio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.core.NonNullList;

import java.util.*;
import java.util.function.Function;

public class SerializationHelper {

    // Removed SHAPED_RECIPE_CODEC because shaped recipes natively serialize in modern Minecraft.

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static AttributeModifier readAttributeModifier(JsonElement jsonElement) {
        if(jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();
            String id = GsonHelper.getAsString(json, "id", "calio:unnamed_attribute_modifier");
            String operation = GsonHelper.getAsString(json, "operation").toUpperCase(Locale.ROOT);
            double value = GsonHelper.getAsFloat(json, "value");
            return new AttributeModifier(Identifier.parse(id), value, AttributeModifier.Operation.valueOf(operation));
        }
        throw new JsonSyntaxException("Attribute modifier needs to be a JSON object.");
    }

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static AttributeModifier readAttributeModifier(FriendlyByteBuf buf) {
        String modId = buf.readUtf(32767);
        double modValue = buf.readDouble();
        int operation = buf.readInt();
        return new AttributeModifier(Identifier.parse(modId), modValue, AttributeModifier.Operation.values()[Math.min(operation, AttributeModifier.Operation.values().length - 1)]);
    }

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static void writeAttributeModifier(FriendlyByteBuf buf, AttributeModifier modifier) {
        buf.writeUtf(modifier.id().toString());
        buf.writeDouble(modifier.amount());
        buf.writeInt(modifier.operation().ordinal());
    }

    public static MobEffectInstance readStatusEffect(JsonElement jsonElement) {
        if(jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();
            String effect = GsonHelper.getAsString(json, "effect");
            Identifier effectId = Identifier.tryParse(effect);
            final Identifier lookupId = effectId;
            Optional<Holder.Reference<MobEffect>> holderOptional = BuiltInRegistries.MOB_EFFECT.listElements()
                .filter(h -> h.key().identifier().equals(lookupId))
                .findFirst();
            if(!holderOptional.isPresent()) {
                throw new JsonSyntaxException("Error reading status effect: could not find status effect with id: " + effect);
            }
            int duration = GsonHelper.getAsInt(json, "duration", 100);
            int amplifier = GsonHelper.getAsInt(json, "amplifier", 0);
            boolean ambient = GsonHelper.getAsBoolean(json, "is_ambient", false);
            boolean showParticles = GsonHelper.getAsBoolean(json, "show_particles", true);
            boolean showIcon = GsonHelper.getAsBoolean(json, "show_icon", true);
            return new MobEffectInstance(holderOptional.get(), duration, amplifier, ambient, showParticles, showIcon);
        } else {
            throw new JsonSyntaxException("Expected status effect to be a json object.");
        }
    }

    public static MobEffectInstance readStatusEffect(FriendlyByteBuf buf) {
        Identifier effect = buf.readIdentifier();
        int duration = buf.readInt();
        int amplifier = buf.readInt();
        boolean ambient = buf.readBoolean();
        boolean showParticles = buf.readBoolean();
        boolean showIcon = buf.readBoolean();
        final Identifier lookupEffect = effect;
        Holder.Reference<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.listElements()
            .filter(h -> h.key().identifier().equals(lookupEffect))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not find status effect with id: " + lookupEffect));
        return new MobEffectInstance(holder, duration, amplifier, ambient, showParticles, showIcon);
    }

    public static void writeStatusEffect(FriendlyByteBuf buf, MobEffectInstance mobEffectInstance) {
        buf.writeIdentifier(BuiltInRegistries.MOB_EFFECT.getKey(mobEffectInstance.getEffect().value()));
        buf.writeInt(mobEffectInstance.getDuration());
        buf.writeInt(mobEffectInstance.getAmplifier());
        buf.writeBoolean(mobEffectInstance.isAmbient());
        buf.writeBoolean(mobEffectInstance.isVisible());
        buf.writeBoolean(mobEffectInstance.showIcon());
    }

    public static JsonElement writeStatusEffect(MobEffectInstance mobEffectInstance) {
        JsonObject jo = new JsonObject();
        jo.addProperty("effect", BuiltInRegistries.MOB_EFFECT.getKey(mobEffectInstance.getEffect().value()).toString());
        jo.addProperty("duration", mobEffectInstance.getDuration());
        jo.addProperty("amplifier", mobEffectInstance.getAmplifier());
        jo.addProperty("is_ambient", mobEffectInstance.isAmbient());
        jo.addProperty("show_particles", mobEffectInstance.isVisible());
        jo.addProperty("show_icon", mobEffectInstance.showIcon());
        return jo;
    }

    public static <T extends Enum<T>> HashMap<String, T> buildEnumMap(Class<T> enumClass, Function<T, String> enumToString) {
        HashMap<String, T> map = new HashMap<>();
        for (T enumConstant : enumClass.getEnumConstants()) {
            map.put(enumToString.apply(enumConstant), enumConstant);
        }
        return map;
    }
}
