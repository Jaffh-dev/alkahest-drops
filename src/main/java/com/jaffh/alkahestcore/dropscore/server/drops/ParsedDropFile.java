package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a valid file returned by {@link DropFileParser}, before its target is resolved by the module
 * that owns it (entity types for mobs, blocks for blocks).
 */
public sealed interface ParsedDropFile {

    /**
     * Returns the raw value of the file's target key.
     *
     * @return The target, such as {@code "minecraft:zombie"}. Never null.
     */
    String target();

    /**
     * Returns the id of the file.
     *
     * @return The file id. Never null.
     */
    ResourceLocation fileId();

    /**
     * A drop file.
     *
     * @param target Raw target. Never null.
     * @param file   Drops of the file. Never null.
     */
    record Drops(String target, DropFile file) implements ParsedDropFile {
        /**
         * Returns the id of the file.
         *
         * @return The file id.
         */
        @Override
        public ResourceLocation fileId() {
            return file.fileId();
        }
    }

    /**
     * A removal file.
     *
     * @param target Raw target. Never null.
     * @param rule   Removals of the file. Never null.
     */
    record Removals(String target, RemovalRule rule) implements ParsedDropFile {
        /**
         * Returns the id of the file.
         *
         * @return The file id.
         */
        @Override
        public ResourceLocation fileId() {
            return rule.fileId();
        }
    }
}
