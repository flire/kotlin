import builders.*
import kotlin.InlineOption.*

fun test(): String {
    var res = "Fail"

    call {
        res = "OK"
    }

    return res
}


fun box(): String {
    return test()
}
//NO_CHECK_LAMBDA_INLINING

//SMAP
//lambda_1.kt
//Kotlin
//*S Kotlin
//*F
//+ 1 lambda_1.kt
//Lambda_1
//+ 2 lambda_2.kt
//builders/Lambda_2
//*L
//1#1,46:1
//4#2:47
//*E
//
//SMAP
//lambda_2.kt
//Kotlin
//*S Kotlin
//*F
//+ 1 lambda_2.kt
//builders/Lambda_2$call$1
//+ 2 lambda_1.kt
//Lambda_1
//*L
//1#1,18:1
//8#2:19
//*E