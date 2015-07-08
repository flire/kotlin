import kotlin.reflect.*

class A {
    fun mem() {}
    fun Int.memExt() {}
}

fun box(): String {
    val all = A::class.functions.map { it.name }.toSortedList()
    assert(all == listOf("equals", "hashCode", "mem", "memExt", "toString")) { "Fail functions: ${A::class.functions}" }

    val declared = A::class.declaredFunctions.map { it.name }.toSortedList()
    assert(declared == listOf("mem", "memExt")) { "Fail declaredFunctions: ${A::class.declaredFunctions}" }

    return "OK"
}
