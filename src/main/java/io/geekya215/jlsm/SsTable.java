package io.geekya215.jlsm;

import java.util.ArrayList;
import java.util.List;

public class SsTable {
    static final int SIZEOF_U16 = 2;
    static final int SIZEOF_U32 = 4;

    private int id;
    private FileObject fileObject;
    private List<BlockMeta> blockMetas;
    private int blockMetaOffset;

    public SsTable(int id, FileObject fileObject, List<BlockMeta> blockMetas, int blockMetaOffset) {
        this.id = id;
        this.fileObject = fileObject;
        this.blockMetas = blockMetas;
        this.blockMetaOffset = blockMetaOffset;
    }

    public static class SsTableBuilder {
        private Block.BlockBuilder builder;
        private List<Byte> firstKey;
        private List<Byte> data;

        private List<BlockMeta> metas;
        private final int blockSize;

        public SsTableBuilder(int blockSize) {
            this.builder = new Block.BlockBuilder(blockSize);
            this.firstKey = new ArrayList<>();
            this.data = new ArrayList<>();
            this.metas = new ArrayList<>();
            this.blockSize = blockSize;
        }

        public void add(List<Byte> key, List<Byte> value) {
            if (builder.add(key, value)) {
                return;
            }

            finishBlock();

            assert builder.add(key, value);
            firstKey = key;
        }

        public int estimateSize() {
            return data.size();
        }

        void finishBlock() {
            byte[] encodedBlock = builder.build().encode();
            metas.add(new BlockMeta(
                data.size(),
                firstKey
            ));

            builder = new Block.BlockBuilder(blockSize);
            firstKey = new ArrayList<>();

            for (byte b : encodedBlock) {
                data.add(b);
            }
        }

        public SsTable build(int id) {
            finishBlock();
            int metaOffset = data.size();
            BlockMeta.encodeBlockMeta(this.metas, this.data);
            this.data.add((byte) ((metaOffset >> 24) & 0xff));
            this.data.add((byte) ((metaOffset >> 16) & 0xff));
            this.data.add((byte) ((metaOffset >> 8) & 0xff));
            this.data.add((byte) (metaOffset & 0xff));

            FileObject file = FileObject.create(data);

            return new SsTable(id, file, metas, metaOffset);
        }
    }

    public static class BlockMeta {
        private final int offset;
        private final List<Byte> firstKey;

        public BlockMeta(int offset, List<Byte> firstKey) {
            this.offset = offset;
            this.firstKey = firstKey;
        }

        public static void encodeBlockMeta(List<BlockMeta> blockMetas, List<Byte> buf) {
            int estimateSize = 0;
            for (BlockMeta meta : blockMetas) {
                estimateSize += SIZEOF_U32;
                estimateSize += SIZEOF_U16;
                estimateSize += meta.firstKey.size();
            }

            int originalLen = buf.size();
            for (BlockMeta meta : blockMetas) {
                buf.add((byte) ((meta.offset >> 24) & 0xff));
                buf.add((byte) ((meta.offset >> 16) & 0xff));
                buf.add((byte) ((meta.offset >> 8) & 0xff));
                buf.add((byte) (meta.offset & 0xff));

                int firstLen = meta.firstKey.size();
                buf.add((byte) ((firstLen >> 8) & 0xff));
                buf.add((byte) (firstLen & 0xff));

                buf.addAll(meta.firstKey);
            }

            assert estimateSize == buf.size() - originalLen;
        }

        public static List<BlockMeta> decodeBlockMeta(List<Byte> buf) {
            List<BlockMeta> blockMetas = new ArrayList<>();
            for (int i = 0; i < buf.size(); ) {
                int o1 = (buf.get(i++) & 0xff) << 24;
                int o2 = (buf.get(i++) & 0xff) << 16;
                int o3 = (buf.get(i++) & 0xff) << 8;
                int o4 = (buf.get(i++) & 0xff);
                int offset = o1 | o2 | o3 | o4;
                i += 4;

                int k1 = (buf.get(i++) & 0xff) << 8;
                int k2 = (buf.get(i++) & 0xff);

                int keyLen = k1 | k2;
                List<Byte> firstKey = buf.subList(i, i + keyLen);
                i++;
                blockMetas.add(new BlockMeta(offset, firstKey));
            }
            return blockMetas;
        }
    }
}
