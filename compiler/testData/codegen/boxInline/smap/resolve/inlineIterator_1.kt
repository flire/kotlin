import zzz.*

fun box(): String {
    var p = 0
    for (i in A(5)) {
        p += i
    }

    return if (p == 15) "OK" else "fail: $p"
}

//SMAP
//inlineIterator_1.kt
//Kotlin
//*S Kotlin
//*F
//+ 1 inlineIterator_1.kt
//InlineIterator_1
//+ 2 inlineIterator_2.kt
//zzz/InlineIterator_2
//*L
//1#1,24:1
//5#2:25
//*E