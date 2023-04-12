package io.geekya215.jlsm;

import java.util.List;

public final class FileObject {
    private List<Byte> data;

    public FileObject(List<Byte> data) {
        this.data = data;
    }

    public List<Byte> read(int offset, int len) {
        return data.subList(offset, offset + len);
    }

    public int size() {
        return data.size();
    }

    public static FileObject create(List<Byte> data) {
        return new FileObject(data);
    }
}
