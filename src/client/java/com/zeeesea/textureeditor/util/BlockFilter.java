package com.zeeesea.textureeditor.util;

import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Filters blocks to only allow editing of full-cube blocks and simple flat 2D-texture blocks
 * (like flowers, mushrooms, grass, saplings, etc.)
 * Excludes complex 3D blocks like beds, chests, stairs, slabs, trapdoors, armor stands, etc.
 * that don't display properly in the editor.
 */
public class BlockFilter {

    /**
     * Returns true if the block is suitable for texture editing.
     * This includes full cubes and simple cross/flat models.
     */
    public static boolean isEditableBlock(BlockState state) {
        Block block = state.getBlock();

        // Exclude air
        if (block instanceof AirBlock) return false;

        // Exclude known problematic 3D block types
        if (block instanceof BedBlock) return false;
        if (block instanceof ChestBlock) return false;
        if (block instanceof EnderChestBlock) return false;
        if (block instanceof TrappedChestBlock) return false;
        if (block instanceof StairsBlock) return false;
        if (block instanceof SlabBlock) return false;
        if (block instanceof TrapdoorBlock) return false;
        if (block instanceof DoorBlock) return false;
        if (block instanceof FenceBlock) return false;
        if (block instanceof FenceGateBlock) return false;
        if (block instanceof WallBlock) return false;
        if (block instanceof SignBlock) return false;
        if (block instanceof WallSignBlock) return false;
        if (block instanceof HangingSignBlock) return false;
        if (block instanceof WallHangingSignBlock) return false;
        if (block instanceof BannerBlock) return false;
        if (block instanceof WallBannerBlock) return false;
        if (block instanceof SkullBlock) return false;
        if (block instanceof WallSkullBlock) return false;
        if (block instanceof ShulkerBoxBlock) return false;
        if (block instanceof PistonBlock) return false;
        if (block instanceof PistonHeadBlock) return false;
        if (block instanceof PistonExtensionBlock) return false;
        if (block instanceof AnvilBlock) return false;
        if (block instanceof BrewingStandBlock) return false;
        if (block instanceof CauldronBlock) return false;
        if (block instanceof LavaCauldronBlock) return false;
        if (block instanceof HopperBlock) return false;
        if (block instanceof LecternBlock) return false;
        if (block instanceof BellBlock) return false;
        if (block instanceof GrindstoneBlock) return false;
        if (block instanceof StonecutterBlock) return false;
        if (block instanceof CampfireBlock) return false;
        if (block instanceof LanternBlock) return false;
        if (block instanceof ChainBlock) return false;
        if (block instanceof ConduitBlock) return false;
        if (block instanceof EndPortalFrameBlock) return false;
        if (block instanceof EnchantingTableBlock) return false;
        if (block instanceof DecoratedPotBlock) return false;
        if (block instanceof CandleBlock) return false;
        if (block instanceof CandleCakeBlock) return false;
        if (block instanceof AmethystClusterBlock) return false;
        if (block instanceof LightningRodBlock) return false;
        if (block instanceof PointedDripstoneBlock) return false;
        if (block instanceof BigDripleafBlock) return false;
        if (block instanceof SmallDripleafBlock) return false;
        if (block instanceof PitcherCropBlock) return false;
        if (block instanceof CocoaBlock) return false;
        if (block instanceof EndRodBlock) return false;
        if (block instanceof ChorusPlantBlock) return false;
        if (block instanceof ChorusFlowerBlock) return false;
        if (block instanceof DragonEggBlock) return false;
        if (block instanceof ButtonBlock) return false;
        if (block instanceof LeverBlock) return false;
        if (block instanceof PressurePlateBlock) return false;
        if (block instanceof WeightedPressurePlateBlock) return false;
        if (block instanceof TripwireHookBlock) return false;
        if (block instanceof TripwireBlock) return false;
        if (block instanceof DaylightDetectorBlock) return false;
        if (block instanceof ComparatorBlock) return false;
        if (block instanceof RepeaterBlock) return false;
        if (block instanceof FlowerPotBlock) return false;
        if (block instanceof AbstractRailBlock) return false;
        if (block instanceof RodBlock) return false;
        if (block instanceof WallTorchBlock) return false;
        if (block instanceof WallRedstoneTorchBlock) return false;

        // Exclude fluid blocks
        if (block instanceof FluidBlock) return false;

        // Allow all other blocks (full cubes, cross models like flowers/grass, ores, etc.)
        return true;
    }

    /**
     * Returns true if the block should be shown in the browse list.
     * Same as isEditableBlock but also checks the item stack.
     */
    public static boolean isEditableBlockId(Identifier blockId) {
        Block block = Registries.BLOCK.get(blockId);
        return isEditableBlock(block.getDefaultState());
    }
}
