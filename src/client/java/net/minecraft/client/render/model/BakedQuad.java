package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public record BakedQuad(
    Vector3fc position0,
    Vector3fc position1,
    Vector3fc position2,
    Vector3fc position3,
    long packedUV0,
    long packedUV1,
    long packedUV2,
    long packedUV3,
    int tintIndex,
    Direction face,
    Sprite sprite,
    boolean shade,
    int lightEmission
) {
    public static final int NUM_VERTICES = 4;

    public boolean hasTint() {
        return this.tintIndex != -1;
    }

    public Vector3fc getPosition(int index) {
        return switch (index) {
            case 0 -> this.position0;
            case 1 -> this.position1;
            case 2 -> this.position2;
            case 3 -> this.position3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public long getTexcoords(int index) {
        return switch (index) {
            case 0 -> this.packedUV0;
            case 1 -> this.packedUV1;
            case 2 -> this.packedUV2;
            case 3 -> this.packedUV3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public Sprite getSprite() {
        return this.sprite;
    }
}

