import io.geekya215.jlsm.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class BlockTest {
    byte[] keyOf(int i) {
        return String.format("key_%03d", i * 5).getBytes();
    }

    byte[] valueOf(int i) {
        return String.format("value_%010d", i).getBytes();
    }

    List<Byte> bytesToList(byte[] bytes) {
        List<Byte> list = new ArrayList<>(bytes.length);
        for (byte b : bytes) {
            list.add(b);
        }
        return list;
    }

    @Test
    void testBlockBuildSingleKey() {
        Block.BlockBuilder builder = new Block.BlockBuilder(16);
        Assertions.assertTrue(builder.add("233".getBytes(), "23333".getBytes()));
        builder.build();
    }

    @Test
    void testBlockBuildFull() {
        Block.BlockBuilder builder = new Block.BlockBuilder(16);
        Assertions.assertTrue(builder.add("11".getBytes(), "11".getBytes()));
        Assertions.assertFalse(builder.add("22".getBytes(), "22".getBytes()));
        builder.build();
    }

    Block generateBlock() {
        Block.BlockBuilder builder = new Block.BlockBuilder(10000);
        for (int i = 0; i < 100; i++) {
            byte[] key = keyOf(i);
            byte[] value = valueOf(i);
            Assertions.assertTrue(builder.add(key, value));
        }

        return builder.build();
    }

    @Test
    void testBlockBuildAll() {
        generateBlock();
    }

    @Test
    void testBlockEncode() {
        Block block = generateBlock();
        block.encode();
    }

    @Test
    void testBlockDecode() {
        Block block = generateBlock();
        byte[] encode = block.encode();
        Block decodeBlock = Block.decode(encode);
        Assertions.assertEquals(block.getOffsets(), decodeBlock.getOffsets());
        Assertions.assertEquals(block.getData(), decodeBlock.getData());
    }

    @Test
    void testBlockIterator() {
        Block block = generateBlock();
        Block.BlockIterator iter = Block.BlockIterator.createAndSeekFirst(block);
        for (int i = 0; i < 100; i++) {
            List<Byte> key = iter.getKey();
            Assertions.assertEquals(bytesToList(keyOf(i)), key);
            List<Byte> value = iter.getValue();
            Assertions.assertEquals(bytesToList(valueOf(i)), value);
            iter.next();
        }
    }

    @Test
    void testBlockSeekKey() {
        Block block = generateBlock();
        Block.BlockIterator iter = Block.BlockIterator.createAndSeekToKey(block, bytesToList(keyOf(0)));
        for (int offset = 1; offset < 6; offset++) {
            for (int i = 0; i < 100; i++) {
                List<Byte> key = iter.getKey();
                Assertions.assertEquals(bytesToList(keyOf(i)), key);
                List<Byte> value = iter.getValue();
                Assertions.assertEquals(bytesToList(valueOf(i)), value);
                iter.seekToKey(bytesToList(String.format("key_%03d", i * 5 + offset).getBytes()));
            }
            iter.seekToKey(bytesToList("k".getBytes()));
        }
    }
}
