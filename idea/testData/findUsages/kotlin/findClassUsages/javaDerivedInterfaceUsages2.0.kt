// PSI_ELEMENT: org.jetbrains.kotlin.psi.JetClass
// OPTIONS: derivedInterfaces
interface <caret>X {

}

open class A: X {

}

open class C: Y {

}

interface Z: A {

}
