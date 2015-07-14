fun foo() {
    val aaabbbccc = 1
    <caret>val aaacccddd = 1
    val aaadddeee = 1
}

// INVOCATION_COUNT: 1
// EXIST: aaabbbccc
// ABSENT: aaacccddd, aaadddeee