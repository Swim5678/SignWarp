package com.swim.signwarp.utils;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Utility methods for blocks.
 */
public class BlockUtils {
    /**
     * A list of all 6 block faces.
     */
    public final static BlockFace[] BLOCK_FACES = {
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    /**
     * Check if the chunk of the given block is loaded.
     *
     * @param block The block to use
     * @return true if the chunk is loaded, false otherwise
     */
    public static boolean isChunkLoaded(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() / 16, block.getZ() / 16);
    }
}
