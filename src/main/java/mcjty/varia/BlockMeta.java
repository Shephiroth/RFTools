package mcjty.varia;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public class BlockMeta {
    private final Block block;
    private final byte meta;

    public static final BlockMeta STONE = new BlockMeta(Blocks.stone, 0);

    public BlockMeta(Block block, byte meta) {
        this.block = block;
        this.meta = meta;
    }

    public BlockMeta(Block block, int meta) {
        this.block = block;
        this.meta = (byte)meta;
    }

    public Block getBlock() {
        return block;
    }

    public byte getMeta() {
        return meta;
    }
}
