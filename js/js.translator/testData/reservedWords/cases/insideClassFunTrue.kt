package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

class TestClass {
    fun `true`() { `true`() }

    fun test() {
        testNotRenamed("true", { ::`true` })
    }
}

fun box(): String {
    TestClass().test()

    return "OK"
}