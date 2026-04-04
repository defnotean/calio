package io.github.apace100.calio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.apace100.calio.access.ExtraShapedRecipeData;
import io.github.apace100.calio.mixin.ShapedRecipeAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.core.NonNullList;

import java.util.*;
import java.util.function.Function;

public class SerializationHelper {

    public static Codec<ShapedRecipe> SHAPED_RECIPE_CODEC = ShapedRecipe.Serializer.RawShapedRecipe.CODEC.flatXmap(
        rawShapedRecipe -> {

            String[] unpaddedPattern = ShapedRecipeAccessor.callRemovePadding(rawShapedRecipe.pattern());

            int width = unpaddedPattern[0].length();
            int height = unpaddedPattern.length;

            NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
            Set<String> patternKeys = new HashSet<>(rawShapedRecipe.key().keySet());

            for (int sliceIndex = 0; sliceIndex < unpaddedPattern.length; ++sliceIndex) {

                String patternSlice = unpaddedPattern[sliceIndex];

                for (int keyIndex = 0; keyIndex < patternSlice.length(); ++keyIndex) {

                    String patternKey = patternSlice.substring(keyIndex, keyIndex + 1);
                    Ingredient ingredient = patternKey.equals(" ") ? Ingredient.EMPTY : rawShapedRecipe.key().get(patternKey);

                    if (ingredient == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + patternKey + "' but it's not defined in the key!");
                    }

                    patternKeys.remove(patternKey);
                    ingredients.set(keyIndex + width * sliceIndex, ingredient);

                }

            }

            if (!patternKeys.isEmpty()) {
                return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + patternKeys);
            }

            ShapedRecipe shapedRecipe = new ShapedRecipe(
                rawShapedRecipe.group(),
                rawShapedRecipe.category(),
                width,
                height,
                ingredients,
                rawShapedRecipe.result(),
                rawShapedRecipe.showNotification()
            );

            if (shapedRecipe instanceof ExtraShapedRecipeData extraShapedRecipeData) {

                extraShapedRecipeData.calio$setKeyMapping(rawShapedRecipe.key());
                extraShapedRecipeData.calio$setPattern(rawShapedRecipe.pattern());

                extraShapedRecipeData.calio$setResult(rawShapedRecipe.result());

            }

            return DataResult.success(shapedRecipe);

        },
        shapedRecipe -> {

            if (!(shapedRecipe instanceof ExtraShapedRecipeData extraShapedRecipeData)) {
                return DataResult.error(() -> "Cannot serialize ShapedRecipe with missing key, pattern and result data.");
            }

            ShapedRecipe.Serializer.RawShapedRecipe rawShapedRecipe = new ShapedRecipe.Serializer.RawShapedRecipe(
                shapedRecipe.getGroup(),
                shapedRecipe.category(),
                extraShapedRecipeData.calio$getKeyMapping(),
                extraShapedRecipeData.calio$getPattern(),
                extraShapedRecipeData.calio$getResult(),
                shapedRecipe.showNotification()
            );

            return DataResult.success(rawShapedRecipe);

        }
    );

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
        return new AttributeModifier(Identifier.parse(modId), modValue, AttributeModifier.Operation.fromValue(operation));
    }

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static void writeAttributeModifier(FriendlyByteBuf buf, AttributeModifier modifier) {
        buf.writeUtf(modifier.id().toString());
        buf.writeDouble(modifier.amount());
        buf.writeInt(modifier.operation().toValue());
    }

    public static MobEffectInstance readStatusEffect(JsonElement jsonElement) {
        if(jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();
            String effect = GsonHelper.getAsString(json, "effect");
            Identifier effectId = Identifier.tryParse(effect);
            Optional<Holder.Reference<MobEffect>> holderOptional = BuiltInRegistries.MOB_EFFECT.getHolder(effectId);
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
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BuiltInRegistries.MOB_EFFECT.get(effect));
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
