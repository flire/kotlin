// WITH_RUNTIME
interface I

class Z<T>

open class A<T: I, U: I, V>

class C<W: I> {
    inner class <caret>B<X: I, Y>: A<X, I, Z<Y>>() {
        // INFO: {"checked": "true"}
        fun foo<S>(x1: X, x2: Z<X>, y1: Y, y2: Z<Y>, w1: W, w2: Z<W>, s1: S, s2: Z<S>) {

        }
    }
}