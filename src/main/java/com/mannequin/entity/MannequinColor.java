package com.mannequin.entity;

import net.minecraft.world.item.DyeColor;

/**
 * The solid-color palette a mannequin can be dyed with.
 *
 * <p>Each constant maps 1:1 to a vanilla {@link DyeColor}, so the palette is
 * driven by vanilla's own dye colors rather than hardcoded hex values. The
 * ordinal doubles as the compact id used for entity data sync and NBT
 * persistence, so constant order is load-bearing: append new colors at the end
 * and never reorder existing ones.
 */
public enum MannequinColor {

    WHITE(DyeColor.WHITE),
    ORANGE(DyeColor.ORANGE),
    MAGENTA(DyeColor.MAGENTA),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW),
    LIME(DyeColor.LIME),
    PINK(DyeColor.PINK),
    GRAY(DyeColor.GRAY),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY),
    CYAN(DyeColor.CYAN),
    PURPLE(DyeColor.PURPLE),
    BLUE(DyeColor.BLUE),
    BROWN(DyeColor.BROWN),
    GREEN(DyeColor.GREEN),
    RED(DyeColor.RED),
    BLACK(DyeColor.BLACK);

    private static final MannequinColor[] VALUES = values();

    private final DyeColor dye;

    MannequinColor(DyeColor dye) {
        this.dye = dye;
    }

    /**
     * @return the vanilla dye this palette entry mirrors
     */
    public DyeColor dye() {
        return dye;
    }

    /**
     * @return the opaque RGB color (0xRRGGBB) used as the mannequin's diffuse color
     */
    public int rgb() {
        return dye.getTextureDiffuseColor();
    }

    /**
     * @return the color as packed ARGB, ready to hand to the model renderer
     */
    public int argb() {
        return 0xFF000000 | rgb();
    }

    /**
     * Resolves a color from its persisted/networked ordinal.
     *
     * <p>Boundary-validation entry point: an out-of-range id (e.g. from a
     * downgraded or tampered save) falls back to {@link #WHITE} instead of
     * throwing.
     *
     * @param id the ordinal to resolve
     * @return the matching color, or {@link #WHITE} if {@code id} is out of range
     */
    public static MannequinColor byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : WHITE;
    }

    /**
     * Resolves the palette entry a dyed interaction should apply.
     *
     * @param dye the dye used on the mannequin
     * @return the matching color, or {@link #WHITE} if none is mapped
     */
    public static MannequinColor byDye(DyeColor dye) {
        for (MannequinColor color : VALUES) {
            if (color.dye == dye) {
                return color;
            }
        }
        return WHITE;
    }
}
