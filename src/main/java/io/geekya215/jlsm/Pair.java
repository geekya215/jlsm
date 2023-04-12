package io.geekya215.jlsm;

public record Pair<A, B>(A _1, B _2) {

    public static <A, B> Pair<A, B> create(A _1, B _2) {
        return new Pair<>(_1, _2);
    }
}
