package io.geekya215.jlsm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Block {
    static final int SIZEOF_U16 = 2;

    private List<Byte> data;
    private List<Short> offsets;

    private Block() {
    }

    public List<Byte> getData() {
        return data;
    }

    public List<Short> getOffsets() {
        return offsets;
    }

    private Block(List<Byte> data, List<Short> offsets) {
        this.data = data;
        this.offsets = offsets;
    }

    public byte[] encode() {
        List<Byte> buf = new ArrayList<>(data);
        int offsetsLen = offsets.size();

        for (short offset : offsets) {
            buf.add((byte) (offset >> 8));
            buf.add((byte) offset);
        }

        buf.add((byte) (offsetsLen >> 8));
        buf.add((byte) offsetsLen);

        byte[] bytes = new byte[buf.size()];
        for (int i = 0; i < buf.size(); i++) {
            bytes[i] = buf.get(i);
        }

        return bytes;
    }

    public static Block decode(byte[] bytes) {
        int bytesLen = bytes.length;
        int offsetsLen =
            ((bytes[bytesLen - (SIZEOF_U16)] & 0xff) << 8) | (bytes[bytesLen - (SIZEOF_U16 - 1)] & 0xff);
        int dataEnd = bytesLen - SIZEOF_U16 - offsetsLen * SIZEOF_U16;

        List<Byte> data = new ArrayList<>();
        List<Short> offsets = new ArrayList<>();

        for (int i = dataEnd; i < bytesLen - SIZEOF_U16; i += 2) {
            short offset = (short) ((bytes[i] & 0xff) << 8 | bytes[i + 1] & 0xff);
            offsets.add(offset);
        }

        for (int i = 0; i < dataEnd; ++i) {
            data.add(bytes[i]);
        }

        return new Block(data, offsets);
    }

    public static class BlockBuilder {
        private final List<Byte> data;
        private final List<Short> offsets;
        private final int blockSize;

        public BlockBuilder(int blockSize) {
            this.data = new ArrayList<>();
            this.offsets = new ArrayList<>();
            this.blockSize = blockSize;
        }

        public int estimateSize() {
            return offsets.size() * SIZEOF_U16 + data.size() + SIZEOF_U16;
        }

        public boolean add(byte[] key, byte[] value) {

            int keyLen = key.length;
            int valLen = value.length;

            if (keyLen == 0) {
                throw new IllegalArgumentException("key must not be empty");
            }

            if (estimateSize() + keyLen + valLen + 3 * SIZEOF_U16 > blockSize) {
                return false;
            }

            offsets.add((short) data.size());

            data.add((byte) (keyLen >> 8));
            data.add((byte) keyLen);
            for (byte b : key) {
                data.add(b);
            }

            data.add((byte) (valLen >> 8));
            data.add((byte) valLen);
            for (byte b : value) {
                data.add(b);
            }

            return true;
        }

        public Block build() {
            if (offsets.isEmpty()) {
                throw new IllegalArgumentException("block should not be empty");
            }
            return new Block(data, offsets);
        }
    }

    public static class BlockIterator {
        private Block block;
        private List<Byte> key;
        private List<Byte> value;
        private int index;

        private BlockIterator() {
        }

        private BlockIterator(Block block) {
            this.block = block;
            this.key = new ArrayList<>();
            this.value = new ArrayList<>();
            this.index = 0;
        }

        public List<Byte> getKey() {
            return key;
        }

        public List<Byte> getValue() {
            return value;
        }

        public static BlockIterator createAndSeekFirst(Block block) {
            BlockIterator iter = new BlockIterator(block);
            iter.seekToFirst();
            return iter;
        }

        public static BlockIterator createAndSeekToKey(Block block, List<Byte> key) {
            BlockIterator iter = new BlockIterator(block);
            iter.seekToKey(key);
            return iter;
        }

        public boolean isValid() {
            return !key.isEmpty();
        }

        public void next() {
            index += 1;
            seekTo(index);
        }

        private void seekTo(int index) {
            if (index >= block.offsets.size()) {
                key.clear();
                value.clear();
                return;
            }
            seekToOffset(block.offsets.get(index));
            this.index = index;
        }

        private void seekToOffset(int offset) {
            List<Byte> entry = block.data.subList(offset, block.data.size());
            int keyLen = (((entry.get(0) & 0xff) << 8) | (entry.get(1) & 0xff));
            List<Byte> newKey = entry.subList(2, 2 + keyLen);
            this.key.clear();
            this.key.addAll(newKey);
            List<Byte> entry2 = entry.subList(2 + keyLen, entry.size());
            int valueLen = (((entry2.get(0) & 0xff) << 8) | (entry2.get(1) & 0xff));
            List<Byte> newValue = entry2.subList(2, 2 + valueLen);
            this.value.clear();
            this.value.addAll(newValue);
        }

        public void seekToFirst() {
            seekTo(0);
        }

        public void seekToKey(List<Byte> key) {
            int low = 0;
            int high = block.offsets.size();
            while (low < high) {
                int mid = low + (high - low) / 2;
                seekTo(mid);
                assert isValid();
                int cmp = compare(key);
                if (cmp < 0) {
                    low = mid + 1;
                } else if (cmp > 0) {
                    high = mid;
                } else {
                    return;
                }
            }
            seekTo(low);
        }

        private int compare(List<Byte> other) {
            Byte[] keyArr = key.toArray(new Byte[0]);
            Byte[] otherArr = other.toArray(new Byte[0]);
            return Arrays.compare(keyArr, otherArr);
        }
    }
}
