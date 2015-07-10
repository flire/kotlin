annotation class Ann

class CustomDelegate {
    public fun get(thisRef: Any?, prop: PropertyMetadata): String = prop.name
}

<!INAPPLICABLE_PROPERTY_TARGET!>@property:Ann<!>
class SomeClass {

    <!INAPPLICABLE_PROPERTY_TARGET!>@property:Ann<!>
    constructor(<!UNUSED_PARAMETER!>s<!>: String) {

    }

    @property:Ann
    protected val p1: String = ""

    @property:[Ann]
    protected val p2: String = ""

    @property:Ann
    protected var p3: String = ""

    @property:Ann
    protected val p4: String by CustomDelegate()

    @property:Ann
    var propertyWithCustomSetter: Int
        get() = 5
        set(v) {}

    <!INAPPLICABLE_PROPERTY_TARGET!>@property:Ann<!>
    fun annotationOnFunction(a: Int) = a + 5

    fun anotherFun() {
        <!INAPPLICABLE_PROPERTY_TARGET!>@property:Ann<!>
        val <!UNUSED_VARIABLE!>localVariable<!> = 5
    }

}