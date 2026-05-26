package dev.simulated_team.aero_reformation.content.items.ender_compass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class EnderCompassRecipe extends ShapelessRecipe {

    public EnderCompassRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) return false;

        String channel = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof EnderCompassItem) {
                EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                if (data.hasChannel()) {
                    if (channel == null) {
                        channel = data.channel();
                    } else if (!channel.equals(data.channel())) {
                        return false;
                    }
                }
            }
        }
        // Single compass clear: always allowed. Two compasses: channel conflict already checked.
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int compassCount = countCompasses(input);
        if (compassCount == 1) {
            // Clear channel
            ItemStack result = new ItemStack(AeroBlocks.ENDER_COMPASS.get());
            result.set(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            return result;
        }
        // Two compasses: assign channel, inherit target from whichever has one
        String channel = findChannel(input);
        java.util.Optional<net.minecraft.core.GlobalPos> existingTarget = findTarget(input);
        ItemStack result = new ItemStack(AeroBlocks.ENDER_COMPASS.get(), 2);
        result.set(AeroDataComponents.ENDER_COMPASS, new EnderCompassData(channel, existingTarget));
        return result;
    }

    private java.util.Optional<net.minecraft.core.GlobalPos> findTarget(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof EnderCompassItem) {
                EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                if (data.target().isPresent()) return data.target();
            }
        }
        return java.util.Optional.empty();
    }

    private int countCompasses(CraftingInput input) {
        int c = 0;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).getItem() instanceof EnderCompassItem) c++;
        }
        return c;
    }

    private String findChannel(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof EnderCompassItem) {
                EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                if (data.hasChannel()) return data.channel();
            }
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements RecipeSerializer<EnderCompassRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<EnderCompassRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter((EnderCompassRecipe r) -> r.getGroup()),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter((EnderCompassRecipe r) -> r.category()),
                        ItemStack.CODEC.fieldOf("result").forGetter((EnderCompassRecipe r) -> r.getResultItem(null)),
                        Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").xmap(NonNullList::copyOf, i -> i).forGetter((EnderCompassRecipe r) -> r.getIngredients())
                ).apply(instance, EnderCompassRecipe::new));

        @Override
        public MapCodec<EnderCompassRecipe> codec() { return CODEC; }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EnderCompassRecipe> streamCodec() {
            return StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, EnderCompassRecipe r) {
            buf.writeUtf(r.getGroup());
            CraftingBookCategory.STREAM_CODEC.encode(buf, r.category());
            ItemStack.STREAM_CODEC.encode(buf, r.getResultItem(null));
            buf.writeVarInt(r.getIngredients().size());
            for (Ingredient i : r.getIngredients()) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, i);
        }

        private static EnderCompassRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            String group = buf.readUtf();
            CraftingBookCategory cat = CraftingBookCategory.STREAM_CODEC.decode(buf);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            int count = buf.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int i = 0; i < count; i++) ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            return new EnderCompassRecipe(group, cat, result, ingredients);
        }
    }
}
