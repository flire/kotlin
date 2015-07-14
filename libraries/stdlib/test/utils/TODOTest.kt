package test.utils

import kotlin.*
import kotlin.test.*
import org.junit.Test as test

private class PartiallyImplementedClass {
    public val prop: String get() = TODO()

    internal fun method1() = TODO() as String
    public fun method2(): Int = TODO()

    public fun method3(switch: Boolean, value: String): String {
        if (!switch)
            TODO("what if false")
        else {
            if (value.length() < 3)
                throw TODO("write message")
            else
                return value
        }
    }
}

class TODOTest {
    private fun assertNotImplemented(block: () -> Unit) {
        assertTrue(fails(block) is UnsupportedOperationException)
    }

    private fun assertNotImplementedWithMessage(message: String, block: () -> Unit) {
        val e = fails(block)
        assertTrue(e is UnsupportedOperationException)
        assertTrue(message in e!!.getMessage()!!)
    }


    test fun usage() {
        val inst = PartiallyImplementedClass()

        assertNotImplemented { inst.prop }
        assertNotImplemented{ inst.method1() }
        assertNotImplemented { inst.method2() }
        assertNotImplementedWithMessage("what if false") { inst.method3(false, "test") }
        assertNotImplementedWithMessage("write message") { inst.method3(true, "t") }
        assertEquals("test", inst.method3(true, "test"))
    }
}
