package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

trait Trait {
    fun foo(delete: String)
}

class TraitImpl : Trait {
    override fun foo(delete: String) {
    assertEquals("123", delete)
    testRenamed("delete", { delete })
}
}

class TestDelegate : Trait by TraitImpl() {
    fun test() {
        foo("123")
    }
}

fun box(): String {
    TestDelegate().test()

    return "OK"
}