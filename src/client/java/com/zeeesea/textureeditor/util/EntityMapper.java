package com.zeeesea.textureeditor.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.World;

public class EntityMapper {

    public static Entity getEntityFromItem(ItemStack stack, World world) {
        if (world == null) return null;
        Item item = stack.getItem();

        if (item instanceof SpawnEggItem) {
            Identifier id = BuiltInRegistries.ITEM.getId(item);
            String path = id.getPath();
            if (path.endsWith("_spawn_egg")) {
                String entityName = path.substring(0, path.length() - "_spawn_egg".length());
                Identifier entityId = new Identifier(id.getNamespace(), entityName);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);

                // Pig is default if not found, so check if name matches
                if (type != EntityType.PIG || entityName.equals("pig")) {
                    try {
                        return type.create(world, SpawnReason.COMMAND);
                    } catch (Exception e) {
                        System.out.println("[TextureEditor] Failed to create entity from spawn egg: " + entityId + " - " + e.getMessage());
                        return null;
                    }
                }
            }
        }

        if (item instanceof BoatItem) {
            try {
                return EntityType.OAK_BOAT.create(world, SpawnReason.COMMAND);
            } catch (Exception e) { return null; }
        }

        if (item instanceof MinecartItem) {
            try {
                return EntityType.MINECART.create(world, SpawnReason.COMMAND);
            } catch (Exception e) { return null; }
        }

        if (item instanceof ArmorStandItem) {
            try {
                return EntityType.ARMOR_STAND.create(world, SpawnReason.COMMAND);
            } catch (Exception e) { return null; }
        }

        // Elytra -> no entity, but we support it via item
        return null;
    }

    public static boolean hasEntityMode(ItemStack stack) {
        try {
            return getEntityFromItem(stack, Minecraft.getInstance().world) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the corresponding ItemStack for an entity (reverse mapping).
     * Returns null if no item form exists.
     */
    public static ItemStack getItemFromEntity(Entity entity) {
        if (entity instanceof BoatEntity) {
            return new ItemStack(Items.OAK_BOAT);
        }
        if (entity instanceof ChestBoatEntity) {
            return new ItemStack(Items.OAK_CHEST_BOAT);
        }
        if (entity instanceof MinecartEntity) {
            return new ItemStack(Items.MINECART);
        }
        if (entity instanceof ArmorStandEntity) {
            return new ItemStack(Items.ARMOR_STAND);
        }
        // For spawn egg mobs, try to find the spawn egg
        EntityType<?> type = entity.getType();
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getId(type);
        Identifier eggId = new Identifier(entityId.getNamespace(), entityId.getPath() + "_spawn_egg");
        Item eggItem = BuiltInRegistries.ITEM.get(eggId);
        if (eggItem != Items.AIR) {
            return new ItemStack(eggItem);
        }
        return null;
    }

    /**
     * Check whether an entity has an item form that can be edited in ItemEditorScreen.
     */
    public static boolean hasItemMode(Entity entity) {
        return getItemFromEntity(entity) != null;
    }
}

