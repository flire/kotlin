fun foo() {
    <caret>val aaabbbccc = 1
}

// INVOCATION_COUNT: 1
// ABSENT: aaabbbccc