package io.github.apace100.calio.data;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.apace100.calio.Calio;
import io.github.apace100.calio.ClassUtil;
import io.github.apace100.calio.SerializationHelper;
import io.github.apace100.calio.mixin.IngredientAccessor;
import io.github.apace100.calio.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.registries.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.tags.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.UseAnim;
import net.minecraft.util.GsonHelper;

import java.util.*;

@SuppressWarnings("unused")
public final class SerializableDataTypes {

    public static final SerializableDataType<Integer> INT = new SerializableDataType<>(
        Integer.class,
        FriendlyByteBuf::writeInt,
        FriendlyByteBuf::readInt,
        JsonElement::getAsInt,
        JsonPrimitive::new);

    public static final SerializableDataType<List<Integer>> INTS = SerializableDataType.list(INT);

    public static final SerializableDataType<Integer> POSITIVE_INT = SerializableDataType.boundNumber(
        INT, 1, Integer.MAX_VALUE,
        value -> (min, max) -> {

            if (value < min || value > max) {
                throw new IllegalArgumentException("Expected integer to be greater than 0! (current value: " + value + ")");
            }

            return value;

        }
    );

    public static final SerializableDataType<List<Integer>> POSITIVE_INTS = SerializableDataType.list(POSITIVE_INT);

    public static final SerializableDataType<Boolean> BOOLEAN = new SerializableDataType<>(
        Boolean.class,
        FriendlyByteBuf::writeBoolean,
        FriendlyByteBuf::readBoolean,
        JsonElement::getAsBoolean,
        JsonPrimitive::new);

    public static final SerializableDataType<Float> FLOAT = new SerializableDataType<>(
        Float.class,
        FriendlyByteBuf::writeFloat,
        FriendlyByteBuf::readFloat,
        JsonElement::getAsFloat,
        JsonPrimitive::new);

    public static final SerializableDataType<List<Float>> FLOATS = SerializableDataType.list(FLOAT);

    public static final SerializableDataType<Float> POSITIVE_FLOAT = SerializableDataType.boundNumber(
        FLOAT, 1F, Float.MAX_VALUE,
        value -> (min, max) -> {

            if (value < min || value > max) {
                throw new IllegalArgumentException("Expected float to be greater than 0! (current value: " + value + ")");
            }

            return value;

        }
    );

    public static final SerializableDataType<List<Float>> POSITIVE_FLOATS = SerializableDataType.list(POSITIVE_FLOAT);

    public static final SerializableDataType<Double> DOUBLE = new SerializableDataType<>(
        Double.class,
        FriendlyByteBuf::writeDouble,
        FriendlyByteBuf::readDouble,
        JsonElement::getAsDouble,
        JsonPrimitive::new);

    public static final SerializableDataType<List<Double>> DOUBLES = SerializableDataType.list(DOUBLE);

    public static final SerializableDataType<Double> POSITIVE_DOUBLE = SerializableDataType.boundNumber(
        DOUBLE, 1D, Double.MAX_VALUE,
        value -> (min, max) -> {

            if (value < min || value > max) {
                throw new IllegalArgumentException("Expected double to be greater than 0! (current value: " + value + ")");
            }

            return value;

        }
    );

    public static final SerializableDataType<String> STRING = new SerializableDataType<>(
        String.class,
        FriendlyByteBuf::writeUtf,
        (buf) -> buf.readUtf(32767),
        JsonElement::getAsString,
        JsonPrimitive::new);

    public static final SerializableDataType<List<String>> STRINGS = SerializableDataType.list(STRING);

    public static final SerializableDataType<Number> NUMBER = new SerializableDataType<>(
        Number.class,
        (buf, number) -> {
            if(number instanceof Double) {
                buf.writeByte(0);
                buf.writeDouble(number.doubleValue());
            } else if(number instanceof Float) {
                buf.writeByte(1);
                buf.writeFloat(number.floatValue());
            } else if(number instanceof Integer) {
                buf.writeByte(2);
                buf.writeInt(number.intValue());
            } else if(number instanceof Long) {
                buf.writeByte(3);
                buf.writeLong(number.longValue());
            } else {
                buf.writeByte(4);
                buf.writeUtf(number.toString());
            }
        },
        buf -> {
            byte type = buf.readByte();
            switch(type) {
                case 0:
                    return buf.readDouble();
                case 1:
                    return buf.readFloat();
                case 2:
                    return buf.readInt();
                case 3:
                    return buf.readLong();
                case 4:
                    return new LazilyParsedNumber(buf.readUtf());
            }
            throw new RuntimeException("Could not receive number, unexpected type id \"" + type + "\" (allowed range: [0-4])");
        },
        je -> {
            if(je.isJsonPrimitive()) {
                JsonPrimitive primitive = je.getAsJsonPrimitive();
                if(primitive.isNumber()) {
                    return primitive.getAsNumber();
                } else if(primitive.isBoolean()) {
                    return primitive.getAsBoolean() ? 1 : 0;
                }
            }
            throw new JsonParseException("Expected a primitive");
        },
        number -> {
            if(number instanceof Double) {
                return new JsonPrimitive(number.doubleValue());
            } else if(number instanceof Float) {
                return new JsonPrimitive(number.floatValue());
            } else if(number instanceof Integer) {
                return new JsonPrimitive(number.intValue());
            } else if(number instanceof Long) {
                return new JsonPrimitive(number.longValue());
            } else {
                return new JsonPrimitive(number.toString());
            }
        });

    public static final SerializableDataType<List<Number>> NUMBERS = SerializableDataType.list(NUMBER);

    public static final SerializableDataType<Vec3> VECTOR = new SerializableDataType<>(Vec3.class,
        (buf, vector3d) -> {
            buf.writeDouble(vector3d.x);
            buf.writeDouble(vector3d.y);
            buf.writeDouble(vector3d.z);
        },
        (buf -> new Vec3(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble())),
        (jsonElement -> {
            if(jsonElement.isJsonObject()) {
                JsonObject jo = jsonElement.getAsJsonObject();
                return new Vec3(
                    GsonHelper.getAsDouble(jo, "x", 0),
                    GsonHelper.getAsDouble(jo, "y", 0),
                    GsonHelper.getAsDouble(jo, "z", 0)
                );
            } else {
                throw new JsonParseException("Expected an object with x, y, and z fields.");
            }
        }),
        (vec3) -> {
            JsonObject jo = new JsonObject();
            jo.addProperty("x", vec3.x);
            jo.addProperty("y", vec3.y);
            jo.addProperty("z", vec3.z);
            return jo;
        });

    public static final SerializableDataType<ResourceLocation> IDENTIFIER = new SerializableDataType<>(
        ResourceLocation.class,
        FriendlyByteBuf::writeResourceLocation,
        FriendlyByteBuf::readResourceLocation,
        DynamicResourceLocation::of,
        identifier -> new JsonPrimitive(identifier.toString())
    );

    public static final SerializableDataType<List<ResourceLocation>> IDENTIFIERS = SerializableDataType.list(IDENTIFIER);

    // Enchantments are now data-driven and no longer in BuiltInRegistries.
    // Use a ResourceKey-based approach instead.
    public static final SerializableDataType<ResourceKey<Enchantment>> ENCHANTMENT = SerializableDataType.registryKey(Registries.ENCHANTMENT);

    private static final Set<ResourceKey<Level>> VANILLA_DIMENSIONS = Set.of(
        Level.OVERWORLD,
        Level.NETHER,
        Level.END
    );

    public static SerializableDataType<ResourceKey<Level>> DIMENSION = SerializableDataType.registryKey(Registries.DIMENSION, VANILLA_DIMENSIONS);

    public static final SerializableDataType<Attribute> ATTRIBUTE = SerializableDataType.registry(Attribute.class, BuiltInRegistries.ATTRIBUTE);

    public static final SerializableDataType<AttributeModifier.Operation> MODIFIER_OPERATION = SerializableDataType.enumValue(AttributeModifier.Operation.class);

    public static final SerializableDataType<AttributeModifier> ATTRIBUTE_MODIFIER = SerializableDataType.compound(AttributeModifier.class, new SerializableData()
            .add("id", IDENTIFIER, ResourceLocation.parse("calio:unnamed_attribute_modifier"))
            .add("operation", MODIFIER_OPERATION)
            .add("value", DOUBLE),
        data -> new AttributeModifier(
            data.<ResourceLocation>get("id"),
            data.getDouble("value"),
            data.get("operation")
        ),
        (serializableData, modifier) -> {
            SerializableData.Instance inst = serializableData.new Instance();
            inst.set("id", modifier.id());
            inst.set("value", modifier.amount());
            inst.set("operation", modifier.operation());
            return inst;
        });

    public static final SerializableDataType<List<AttributeModifier>> ATTRIBUTE_MODIFIERS =
        SerializableDataType.list(ATTRIBUTE_MODIFIER);

    public static final SerializableDataType<Item> ITEM = SerializableDataType.registry(Item.class, BuiltInRegistries.ITEM);

    public static final SerializableDataType<MobEffect> STATUS_EFFECT = SerializableDataType.registry(MobEffect.class, BuiltInRegistries.MOB_EFFECT);

    public static final SerializableDataType<List<MobEffect>> STATUS_EFFECTS =
        SerializableDataType.list(STATUS_EFFECT);

    public static final SerializableDataType<MobEffectInstance> STATUS_EFFECT_INSTANCE = new SerializableDataType<>(
        MobEffectInstance.class,
        SerializationHelper::writeStatusEffect,
        SerializationHelper::readStatusEffect,
        SerializationHelper::readStatusEffect,
        SerializationHelper::writeStatusEffect);

    public static final SerializableDataType<List<MobEffectInstance>> STATUS_EFFECT_INSTANCES =
        SerializableDataType.list(STATUS_EFFECT_INSTANCE);

    public static final SerializableDataType<TagKey<Item>> ITEM_TAG = SerializableDataType.tag(Registries.ITEM);

    public static final SerializableDataType<TagKey<Fluid>> FLUID_TAG = SerializableDataType.tag(Registries.FLUID);

    public static final SerializableDataType<TagKey<Block>> BLOCK_TAG = SerializableDataType.tag(Registries.BLOCK);

    public static final SerializableDataType<TagKey<EntityType<?>>> ENTITY_TAG = SerializableDataType.tag(Registries.ENTITY_TYPE);

    // In 1.21+, Ingredient inner classes (Value/ItemValue/TagValue) no longer exist.
    // Ingredient now uses codec-based serialization. We use Ingredient.CODEC directly.

    // An alternative version of an ingredient deserializer which allows `minecraft:air`
    public static final SerializableDataType<Ingredient> INGREDIENT = new SerializableDataType<>(
        Ingredient.class,
        (buffer, ingredient) -> Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient),
        (buffer) -> Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
        jsonElement -> Ingredient.CODEC.parse(JsonOps.INSTANCE, jsonElement)
            .resultOrPartial(Calio.LOGGER::error)
            .orElseThrow(() -> new RuntimeException("Failed to read ingredient json.")),
        ingredient -> Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, ingredient)
            .resultOrPartial(Calio.LOGGER::error)
            .orElseGet(JsonObject::new));

    // The regular vanilla Minecraft ingredient (same codec-based approach).
    public static final SerializableDataType<Ingredient> VANILLA_INGREDIENT = new SerializableDataType<>(
        Ingredient.class,
        (buffer, ingredient) -> Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient),
        (buffer) -> Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
        json -> Ingredient.CODEC.parse(JsonOps.INSTANCE, json)
            .resultOrPartial(Calio.LOGGER::error)
            .orElseThrow(() -> new RuntimeException("Failed to read vanilla ingredient json.")),
        ingredient -> Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, ingredient)
            .resultOrPartial(Calio.LOGGER::error)
            .orElseGet(JsonObject::new));

    public static final SerializableDataType<Block> BLOCK = SerializableDataType.registry(Block.class, BuiltInRegistries.BLOCK);

    public static final SerializableDataType<BlockState> BLOCK_STATE = SerializableDataType.wrap(BlockState.class, STRING,
        BlockStateParser::serialize,
        string -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), string, false).blockState();
            } catch (CommandSyntaxException e) {
                throw new JsonParseException(e);
            }
        });

    public static final SerializableDataType<ResourceKey<DamageType>> DAMAGE_TYPE = SerializableDataType.registryKey(Registries.DAMAGE_TYPE);

    public static final SerializableDataType<MobType> ENTITY_GROUP =
        SerializableDataType.mapped(MobType.class, HashBiMap.create(ImmutableMap.of(
            "default", MobType.UNDEFINED,
            "undead", MobType.UNDEAD,
            "arthropod", MobType.ARTHROPOD,
            "illager", MobType.ILLAGER,
            "aquatic", MobType.WATER
        )));

    public static final SerializableDataType<EquipmentSlot> EQUIPMENT_SLOT = SerializableDataType.enumValue(EquipmentSlot.class);

    public static final SerializableDataType<SoundEvent> SOUND_EVENT = SerializableDataType.wrap(
        SoundEvent.class,
        IDENTIFIER,
        SoundEvent::getLocation,
        SoundEvent::createVariableRangeEvent
    );

    public static final SerializableDataType<EntityType<?>> ENTITY_TYPE = SerializableDataType.registry(ClassUtil.castClass(EntityType.class), BuiltInRegistries.ENTITY_TYPE);

    public static final SerializableDataType<ParticleType<?>> PARTICLE_TYPE = SerializableDataType.registry(ClassUtil.castClass(ParticleType.class), BuiltInRegistries.PARTICLE_TYPE);

    // ParticleOptions uses codec-based approach (ParticleType.codec() returns MapCodec, .codec() converts to Codec)
    public static final SerializableDataType<ParticleOptions> PARTICLE_EFFECT = SerializableDataType.compound(ParticleOptions.class,
        new SerializableData()
            .add("type", PARTICLE_TYPE)
            .add("params", STRING, ""),
        dataInstance -> {
            ParticleType<? extends ParticleOptions> particleType = dataInstance.get("type");
            String params = dataInstance.getString("params");
            if (params.isEmpty()) {
                // For simple particle types that are also ParticleOptions
                if (particleType instanceof ParticleOptions simpleOptions) {
                    return simpleOptions;
                }
            }
            // Use the codec to decode particle options from JSON
            JsonObject particleJson = new JsonObject();
            particleJson.addProperty("type", BuiltInRegistries.PARTICLE_TYPE.getKey(particleType).toString());
            return particleType.codec().codec().parse(JsonOps.INSTANCE, particleJson)
                .resultOrPartial(Calio.LOGGER::error)
                .orElseThrow(() -> new RuntimeException("Failed to parse particle options"));
        },
        ((serializableData, particleEffect) -> {
            SerializableData.Instance data = serializableData.new Instance();
            data.set("type", particleEffect.getType());
            data.set("params", "");
            return data;
        }));

    public static final SerializableDataType<ParticleOptions> PARTICLE_EFFECT_OR_TYPE = new SerializableDataType<>(ParticleOptions.class,
        PARTICLE_EFFECT::send,
        PARTICLE_EFFECT::receive,
        jsonElement -> {
            if(jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()) {
                ParticleType<?> type = PARTICLE_TYPE.read(jsonElement);
                if(type instanceof ParticleOptions) {
                    return (ParticleOptions) type;
                }
                throw new RuntimeException("Expected either a string with a parameter-less particle effect, or an object.");
            } else if(jsonElement.isJsonObject()) {
                return PARTICLE_EFFECT.read(jsonElement);
            }
            throw new RuntimeException("Expected either a string with a parameter-less particle effect, or an object.");
        },
        PARTICLE_EFFECT::write);

    public static final SerializableDataType<CompoundTag> NBT = new SerializableDataType<>(
        CompoundTag.class,
        FriendlyByteBuf::writeNbt,
        FriendlyByteBuf::readNbt,
        jsonElement -> {

            if (!(jsonElement.isJsonObject()|| jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()))
                throw new JsonSyntaxException("Expected either a string or an object.");

            try {
                String stringifiedJsonElement = jsonElement.isJsonObject() ? jsonElement.getAsJsonObject().toString() : jsonElement.getAsJsonPrimitive().getAsString();
                return new TagParser(new StringReader(stringifiedJsonElement)).readStruct();
            }
            catch (CommandSyntaxException e) {
                throw new JsonSyntaxException("Could not parse NBT: " + e.getMessage());
            }

        },
        compoundTag -> NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, compoundTag)
    );

    public static final SerializableDataType<ItemStack> ITEM_STACK = SerializableDataType.compound(
        ItemStack.class,
        new SerializableData()
            .add("item", SerializableDataTypes.ITEM)
            .add("amount", SerializableDataTypes.INT, 1)
            .add("tag", SerializableDataTypes.NBT, null),
        data -> {

            ItemStack stack = data.<Item>get("item").getDefaultInstance();

            stack.setCount(data.get("amount"));
            data.<CompoundTag>ifPresent("tag", tag ->
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag)));

            return stack;

        },
        (serializableData, stack) -> {

            SerializableData.Instance data = serializableData.new Instance();

            data.set("item", stack.getItem());
            data.set("amount", stack.getCount());
            data.set("tag", stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                ? stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag()
                : null);

            return data;

        }
    );

    public static final SerializableDataType<List<ItemStack>> ITEM_STACKS = SerializableDataType.list(ITEM_STACK);

    public static final SerializableDataType<Component> TEXT = new SerializableDataType<>(Component.class,
        (buffer, text) -> buffer.writeUtf(Component.Serializer.toJson(text)),
        (buffer) -> Component.Serializer.fromJson(buffer.readUtf(32767)),
        Component.Serializer::fromJson,
        Component.Serializer::toJsonTree);

    public static final SerializableDataType<List<Component>> TEXTS = SerializableDataType.list(TEXT);

    // In 1.21+, RecipeSerializer.toNetwork()/fromNetwork() were removed in favor of StreamCodec.
    public static final SerializableDataType<RecipeHolder> RECIPE = new SerializableDataType<>(RecipeHolder.class,
        (buffer, recipe) -> {
            buffer.writeResourceLocation(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.value().getSerializer()));
            buffer.writeResourceLocation(recipe.id());
            // Use StreamCodec for network serialization
            recipe.value().getSerializer().streamCodec().encode(buffer, recipe.value());
        },
        (buffer) -> {
            ResourceLocation recipeSerializerId = buffer.readResourceLocation();
            ResourceLocation recipeId = buffer.readResourceLocation();
            RecipeSerializer<?> serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(recipeSerializerId);
            return new RecipeHolder<>(recipeId, serializer.streamCodec().decode(buffer));
        },
        (jsonElement) -> {
            if(!jsonElement.isJsonObject()) {
                throw new RuntimeException("Expected recipe to be a JSON object.");
            }
            JsonObject json = jsonElement.getAsJsonObject();
            ResourceLocation recipeSerializerId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "type"));
            ResourceLocation recipeId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "id"));
            RecipeSerializer<?> serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(recipeSerializerId);
            return new RecipeHolder<>(recipeId, serializer.codec().parse(JsonOps.INSTANCE, json).resultOrPartial(Calio.LOGGER::error).orElseThrow(() -> new RuntimeException("Failed to read recipe json.")));
        },
        recipe -> {
            JsonObject json = new JsonObject();
            json.addProperty("type", BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.value().getSerializer()).toString());
            json.addProperty("id", recipe.id().toString());
            recipe.value().getSerializer().codec().encodeStart(JsonOps.INSTANCE, recipe.value()).resultOrPartial(Calio.LOGGER::error).ifPresent(o -> {
                for (Map.Entry<String, JsonElement> j : ((JsonObject) o).entrySet()) {
                    json.add(j.getKey(), j.getValue());
                }
            });
            return json;
        });

    public static final SerializableDataType<GameEvent> GAME_EVENT = SerializableDataType.registry(GameEvent.class, BuiltInRegistries.GAME_EVENT);

    public static final SerializableDataType<List<GameEvent>> GAME_EVENTS =
        SerializableDataType.list(GAME_EVENT);

    public static final SerializableDataType<TagKey<GameEvent>> GAME_EVENT_TAG = SerializableDataType.tag(Registries.GAME_EVENT);

    public static final SerializableDataType<Fluid> FLUID = SerializableDataType.registry(Fluid.class, BuiltInRegistries.FLUID);

    public static final SerializableDataType<FogRenderer.FogMode> CAMERA_SUBMERSION_TYPE = SerializableDataType.enumValue(FogRenderer.FogMode.class);

    public static final SerializableDataType<InteractionHand> HAND = SerializableDataType.enumValue(InteractionHand.class);

    public static final SerializableDataType<EnumSet<InteractionHand>> HAND_SET = SerializableDataType.enumSet(InteractionHand.class, HAND);

    public static final SerializableDataType<EnumSet<EquipmentSlot>> EQUIPMENT_SLOT_SET = SerializableDataType.enumSet(EquipmentSlot.class, EQUIPMENT_SLOT);

    public static final SerializableDataType<InteractionResult> ACTION_RESULT = SerializableDataType.enumValue(InteractionResult.class);

    public static final SerializableDataType<UseAnim> USE_ACTION = SerializableDataType.enumValue(UseAnim.class);

    public static final SerializableDataType<MobEffectChance> STATUS_EFFECT_CHANCE =
        SerializableDataType.compound(MobEffectChance.class, new SerializableData()
            .add("effect", STATUS_EFFECT_INSTANCE)
            .add("chance", FLOAT, 1.0F),
            (data) -> {
                MobEffectChance sec = new MobEffectChance();
                sec.mobEffectInstance = data.get("effect");
                sec.chance = data.getFloat("chance");
                return sec;
            },
            (data, csei) -> {
                SerializableData.Instance inst = data.new Instance();
                inst.set("effect", csei.mobEffectInstance);
                inst.set("chance", csei.chance);
                return inst;
            });

    public static final SerializableDataType<List<MobEffectChance>> STATUS_EFFECT_CHANCES = SerializableDataType.list(STATUS_EFFECT_CHANCE);

    // FoodProperties is now a record in 1.21+: FoodProperties(int nutrition, float saturation, boolean canAlwaysEat)
    // The old .meat(), .fast(), .effect() builder methods and .isMeat(), .isFastFood(), .getEffects() accessors were removed.
    public static final SerializableDataType<FoodProperties> FOOD_COMPONENT = SerializableDataType.compound(FoodProperties.class, new SerializableData()
            .add("hunger", INT)
            .add("saturation", FLOAT)
            .add("always_edible", BOOLEAN, false),
        (data) -> {
            return new FoodProperties(data.getInt("hunger"), data.getFloat("saturation"), data.getBoolean("always_edible"));
        },
        (data, fc) -> {
            SerializableData.Instance inst = data.new Instance();
            inst.set("hunger", fc.nutrition());
            inst.set("saturation", fc.saturation());
            inst.set("always_edible", fc.canAlwaysEat());
            return inst;
        });

    public static final SerializableDataType<Direction> DIRECTION = SerializableDataType.enumValue(Direction.class);

    public static final SerializableDataType<EnumSet<Direction>> DIRECTION_SET = SerializableDataType.enumSet(Direction.class, DIRECTION);

    public static final SerializableDataType<Class<?>> CLASS = SerializableDataType.wrap(ClassUtil.castClass(Class.class), SerializableDataTypes.STRING,
        Class::getName,
        str -> {
            try {
                return Class.forName(str);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Specified class does not exist: \"" + str + "\".");
            }
        });

    public static final SerializableDataType<ClipContext.Block> SHAPE_TYPE = SerializableDataType.enumValue(ClipContext.Block.class);

    public static final SerializableDataType<ClipContext.Fluid> FLUID_HANDLING = SerializableDataType.enumValue(ClipContext.Fluid.class);

    public static final SerializableDataType<Explosion.BlockInteraction> DESTRUCTION_TYPE = SerializableDataType.enumValue(Explosion.BlockInteraction.class);

    public static final SerializableDataType<Direction.Axis> AXIS = SerializableDataType.enumValue(Direction.Axis.class);

    public static final SerializableDataType<EnumSet<Direction.Axis>> AXIS_SET = SerializableDataType.enumSet(Direction.Axis.class, AXIS);

    public static final SerializableDataType<ArgumentWrapper<NbtPathArgument.NbtPath>> NBT_PATH =
        SerializableDataType.argumentType(NbtPathArgument.nbtPath());

    public static final SerializableDataType<ClipContext.Block> RAYCAST_SHAPE_TYPE = SerializableDataType.enumValue(ClipContext.Block.class);

    public static final SerializableDataType<ClipContext.Fluid> RAYCAST_FLUID_HANDLING = SerializableDataType.enumValue(ClipContext.Fluid.class);

    public static final SerializableDataType<Stat<?>> STAT = SerializableDataType.compound(ClassUtil.castClass(Stat.class),
        new SerializableData()
            .add("type", SerializableDataType.registry(ClassUtil.castClass(StatType.class), BuiltInRegistries.STAT_TYPE))
            .add("id", SerializableDataTypes.IDENTIFIER),
        data -> {
            StatType statType = data.get("type");
            Registry<?> statRegistry = statType.getRegistry();
            ResourceLocation statId = data.get("id");
            if(statRegistry.containsKey(statId)) {
                Object statObject = statRegistry.get(statId);
                return statType.get(statObject);
            }
            throw new IllegalArgumentException("Desired stat \"" + statId + "\" does not exist in stat type ");
        },
        (data, stat) -> {
            SerializableData.Instance inst = data.new Instance();
            inst.set("type", stat.getType());
            Registry reg = stat.getType().getRegistry();
            ResourceLocation statId = reg.getKey(stat.getValue());
            inst.set("id", statId);
            return inst;
        });

    public static final SerializableDataType<TagKey<Biome>> BIOME_TAG = SerializableDataType.tag(Registries.BIOME);

    public static final SerializableDataType<TagLike<Item>> ITEM_TAG_LIKE = SerializableDataType.tagLike(BuiltInRegistries.ITEM);

    public static final SerializableDataType<TagLike<Block>> BLOCK_TAG_LIKE = SerializableDataType.tagLike(BuiltInRegistries.BLOCK);

    public static final SerializableDataType<TagLike<EntityType<?>>> ENTITY_TYPE_TAG_LIKE = SerializableDataType.tagLike(BuiltInRegistries.ENTITY_TYPE);
}
