// See also KT-6299
public open class Outer private constructor() {
    companion object {
        inline fun foo() = Outer()
    }
}

fun box(): String {
    val outer = Outer.foo()
    return "OK"
}