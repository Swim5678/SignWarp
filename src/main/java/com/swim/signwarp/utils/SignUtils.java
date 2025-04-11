package com.swim.signwarp.utils;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SignUtils {
    /**
     * Check whether one of the given blocks has a sign attached to it or is a sign itself.
     *
     * @param blocks The list of blocks to check
     * @return true if a sign was found, false otherwise
     */
    public static boolean hasBlockSign(List<Block> blocks) {
        return hasBlockSign(blocks, null);
    }

    /**
     * Check whether one of the given blocks has a sign attached to it or is a sign itself.
     *
     * @param blocks    The list of blocks to check
     * @param predicate Optional predicate to check the sign (i.e. to check sign content)
     * @return true if a sign was found matching the optional predicate, false otherwise
     */
    public static boolean hasBlockSign(List<Block> blocks, Predicate<Sign> predicate) {
        for (Block block : blocks) {
            if (hasBlockSign(block, predicate)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether the block has a sign attached to it or is a sign itself.
     *
     * @param block The block to check
     * @return true if a sign was found, false otherwise
     */
    public static boolean hasBlockSign(Block block) {
        return hasBlockSign(block, null);
    }

    /**
     * Check whether the block has a sign attached to it or is a sign itself.
     *
     * @param block     The block to check
     * @param predicate Optional predicate to check the sign (i.e. to check sign content)
     * @return true if a sign was found matching the optional predicate, false otherwise
     */
    public static boolean hasBlockSign(Block block, Predicate<Sign> predicate) {
        // Check whether this block is a sign
        if (Tag.ALL_SIGNS.isTagged(block.getType())) {
            if (predicate == null) {
                return true;
            }

            if (predicate.test((Sign) block.getState())) {
                return true;
            }
        }

        // Check each block face for a sign
        for (BlockFace blockFace : BlockUtils.BLOCK_FACES) {
            if (checkSignAttachedToBlockFace(block, blockFace, predicate)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether the specified block face has a sign attached to it.
     *
     * @param block     The block to check
     * @param blockFace The block face of the block to check
     * @return true if a sign was found, false otherwise
     */
    public static boolean checkSignAttachedToBlockFace(Block block, BlockFace blockFace) {
        return checkSignAttachedToBlockFace(block, blockFace, null);
    }

    /**
     * Check whether the specified block face has a sign attached to it.
     *
     * @param block     The block to check
     * @param blockFace The block face of the block to check
     * @param predicate Optional predicate to check the sign (i.e. to check sign content)
     * @return true if a sign was found matching the optional predicate, false otherwise
     */
    public static boolean checkSignAttachedToBlockFace(Block block, BlockFace blockFace, Predicate<Sign> predicate) {
        Block faceBlock = block.getRelative(blockFace);
        Material faceBlockType = faceBlock.getType();

        // Check block face for wall sign
        if (Tag.WALL_SIGNS.isTagged(faceBlockType)) {
            Sign signBlock = (Sign) faceBlock.getState();
            BlockFace attachedFace = ((WallSign) signBlock.getBlockData()).getFacing();
            if (blockFace.equals(attachedFace) && (predicate == null || predicate.test(signBlock))) {
                return true;
            }
        }

        // If block face is UP, check for standing sign
        if (blockFace.equals(BlockFace.UP) && Tag.STANDING_SIGNS.isTagged(faceBlockType)) {
            if (predicate == null) {
                return true;
            }

            return predicate.test((Sign) faceBlock.getState());
        }

        return false;
    }

    /**
     * Get the sign from the specified block.
     *
     * @param block The block to use
     * @return The sign instance, null otherwise
     */
    public static Sign getSignFromBlock(Block block) {
        // TODO: Use Tag.ALL_SIGNS.isTagged(block.getType())
        BlockData blockData = block.getBlockData();

        if (!(blockData instanceof WallSign) && !(blockData instanceof org.bukkit.block.data.type.Sign)) {
            return null;
        }

        BlockState blockState = block.getState();

        if (!(blockState instanceof Sign)) {
            return null;
        }

        return (Sign) blockState;
    }

    /**
     * Get the block the sign is attached to.
     *
     * @param signBlock The block of the sign
     * @return The block the sign is attached to or null if the sign block is not a wall sign
     */
    public static Block getSignFromAttachedBlock(Block signBlock) {
        BlockData blockData = signBlock.getBlockData();

        if (!(blockData instanceof WallSign sign)) {
            return null;
        }

        return signBlock.getRelative(sign.getFacing().getOppositeFace());
    }

    /**
     * Returns all signs attached to the specified block.
     *
     * @param block The block to use
     * @return A list of signs attached to the specified block
     */
    public static List<Sign> getSignsAttachedToBlock(Block block) {
        return getSignsAttachedToBlock(block, null);
    }

    /**
     * Returns all signs attached to the specified block.
     *
     * @param block     The block to use
     * @param predicate Optional predicate to check the sign and filter the returned list (i.e. to check sign content)
     * @return A list of signs attached to the specified block
     */
    public static List<Sign> getSignsAttachedToBlock(Block block, Predicate<Sign> predicate) {
        List<Sign> signs = new ArrayList<>();

        for (BlockFace blockFace : BlockUtils.BLOCK_FACES) {
            Block faceBlock = block.getRelative(blockFace);
            Material faceBlockType = faceBlock.getType();

            if (!Tag.WALL_SIGNS.isTagged(faceBlockType)) {
                continue;
            }

            Sign signBlock = (Sign) faceBlock.getState();
            BlockFace attachedFace = ((WallSign) signBlock.getBlockData()).getFacing();

            if (!blockFace.equals(attachedFace)) {
                continue;
            }

            if (predicate != null && !predicate.test(signBlock)) {
                continue;
            }

            signs.add(signBlock);
        }

        return signs;
    }
}
