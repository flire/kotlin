package kotlin

//
// NOTE THIS FILE IS AUTO-GENERATED by the GenerateStandardLib.kt
// See: https://github.com/JetBrains/kotlin/tree/master/libraries/stdlib
//

import kotlin.platform.*
import java.util.*

import java.util.Collections // TODO: it's temporary while we have java.util.Collections in js

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <T, R, V> Array<out T>.merge(array: Array<out R>, transform: (T, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> BooleanArray.merge(array: Array<out R>, transform: (Boolean, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> ByteArray.merge(array: Array<out R>, transform: (Byte, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> CharArray.merge(array: Array<out R>, transform: (Char, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> DoubleArray.merge(array: Array<out R>, transform: (Double, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> FloatArray.merge(array: Array<out R>, transform: (Float, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> IntArray.merge(array: Array<out R>, transform: (Int, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> LongArray.merge(array: Array<out R>, transform: (Long, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> ShortArray.merge(array: Array<out R>, transform: (Short, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <T, R, V> Iterable<T>.merge(array: Array<out R>, transform: (T, R) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(collectionSizeOrDefault(10))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> BooleanArray.merge(array: BooleanArray, transform: (Boolean, Boolean) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> ByteArray.merge(array: ByteArray, transform: (Byte, Byte) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> CharArray.merge(array: CharArray, transform: (Char, Char) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> DoubleArray.merge(array: DoubleArray, transform: (Double, Double) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> FloatArray.merge(array: FloatArray, transform: (Float, Float) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> IntArray.merge(array: IntArray, transform: (Int, Int) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> LongArray.merge(array: LongArray, transform: (Long, Long) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <V> ShortArray.merge(array: ShortArray, transform: (Short, Short) -> V): List<V> {
    val first = iterator()
    val second = array.iterator()
    val list = ArrayList<V>(Math.min(size(), array.size()))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <T, R, V> Array<out T>.merge(other: Iterable<R>, transform: (T, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> BooleanArray.merge(other: Iterable<R>, transform: (Boolean, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> ByteArray.merge(other: Iterable<R>, transform: (Byte, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> CharArray.merge(other: Iterable<R>, transform: (Char, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> DoubleArray.merge(other: Iterable<R>, transform: (Double, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> FloatArray.merge(other: Iterable<R>, transform: (Float, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> IntArray.merge(other: Iterable<R>, transform: (Int, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> LongArray.merge(other: Iterable<R>, transform: (Long, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <R, V> ShortArray.merge(other: Iterable<R>, transform: (Short, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(size())
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a list of values built from elements of both collections with same indexes using provided [transform]. List has length of shortest collection.
 */
public inline fun <T, R, V> Iterable<T>.merge(other: Iterable<R>, transform: (T, R) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<V>(collectionSizeOrDefault(10))
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    return list
}

/**
 * Returns a sequence of values built from elements of both collections with same indexes using provided [transform]. Resulting sequence has length of shortest input sequences.
 */
public fun <T, R, V> Sequence<T>.merge(sequence: Sequence<R>, transform: (T, R) -> V): Sequence<V> {
    return MergingSequence(this, sequence, transform)
}

/**
 * Returns a list containing all elements of the original collection except the elements contained in the given [array].
 */
public fun <T> Iterable<T>.minus(array: Array<out T>): List<T> {
    if (array.isEmpty()) return this.toList()
    val other = array.toHashSet()
    return this.filterNot { it in other }
}

/**
 * Returns a sequence containing all elements of original sequence except the elements contained in the given [array].
 */
public fun <T> Sequence<T>.minus(array: Array<out T>): Sequence<T> {
    if (array.isEmpty()) return this
    return object: Sequence<T> {
        override fun iterator(): Iterator<T> {
            val other = array.toHashSet()
            return this@minus.filterNot { it in other }.iterator()
        }
    }
}

/**
 * Returns a set containing all elements of the original set except the elements contained in the given [array].
 */
public fun <T> Set<T>.minus(array: Array<out T>): Set<T> {
    val result = LinkedHashSet<T>(this)
    result.removeAll(array)
    return result
}

/**
 * Returns a list containing all elements of the original collection except the elements contained in the given [collection].
 */
public fun <T> Iterable<T>.minus(collection: Iterable<T>): List<T> {
    val other = collection.convertToSetForSetOperationWith(this)
    if (other.isEmpty())
        return this.toList()
    return this.filterNot { it in other }
}

/**
 * Returns a sequence containing all elements of original sequence except the elements contained in the given [collection].
 */
public fun <T> Sequence<T>.minus(collection: Iterable<T>): Sequence<T> {
    return object: Sequence<T> {
        override fun iterator(): Iterator<T> {
            val other = collection.convertToSetForSetOperation()
            return this@minus.filterNot { it in other }.iterator()
        }
    }
}

/**
 * Returns a set containing all elements of the original set except the elements contained in the given [collection].
 */
public fun <T> Set<T>.minus(collection: Iterable<T>): Set<T> {
    val other = collection.convertToSetForSetOperationWith(this)
    if (other.isEmpty())
        return this.toSet()
    if (other is Set)
        return this.filterNotTo(LinkedHashSet<T>()) { it in other }
    val result = LinkedHashSet<T>(this)
    result.removeAll(other)
    return result
}

/**
 * Returns a list containing all elements of the original collection without the first occurrence of the given [element].
 */
public fun <T> Iterable<T>.minus(element: T): List<T> {
    val result = ArrayList<T>(collectionSizeOrDefault(10))
    var removed = false
    return this.filterTo(result) { if (!removed && it == element) { removed = true; false } else true }
}

/**
 * Returns a sequence containing all elements of the original sequence without the first occurrence of the given [element].
 */
public fun <T> Sequence<T>.minus(element: T): Sequence<T> {
    return object: Sequence<T> {
        override fun iterator(): Iterator<T> {
            var removed = false
            return this@minus.filter { if (!removed && it == element) { removed = true; false } else true }.iterator()
        }
    }
}

/**
 * Returns a set containing all elements of the original set except the given [element].
 */
public fun <T> Set<T>.minus(element: T): Set<T> {
    val result = LinkedHashSet<T>(mapCapacity(size()))
    var removed = false
    return this.filterTo(result) { if (!removed && it == element) { removed = true; false } else true }
}

/**
 * Returns a list containing all elements of the original collection except the elements contained in the given [sequence].
 */
public fun <T> Iterable<T>.minus(sequence: Sequence<T>): List<T> {
    val other = sequence.toHashSet()
    if (other.isEmpty())
        return this.toList()
    return this.filterNot { it in other }
}

/**
 * Returns a sequence containing all elements of original sequence except the elements contained in the given [sequence].
 */
public fun <T> Sequence<T>.minus(sequence: Sequence<T>): Sequence<T> {
    return object: Sequence<T> {
        override fun iterator(): Iterator<T> {
            val other = sequence.toHashSet()
            if (other.isEmpty())
                return this@minus.iterator()
            else
                return this@minus.filterNot { it in other }.iterator()
        }
    }
}

/**
 * Returns a set containing all elements of the original set except the elements contained in the given [sequence].
 */
public fun <T> Set<T>.minus(sequence: Sequence<T>): Set<T> {
    val result = LinkedHashSet<T>(this)
    result.removeAll(sequence)
    return result
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun <T> Array<out T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun BooleanArray.partition(predicate: (Boolean) -> Boolean): Pair<List<Boolean>, List<Boolean>> {
    val first = ArrayList<Boolean>()
    val second = ArrayList<Boolean>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun ByteArray.partition(predicate: (Byte) -> Boolean): Pair<List<Byte>, List<Byte>> {
    val first = ArrayList<Byte>()
    val second = ArrayList<Byte>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun CharArray.partition(predicate: (Char) -> Boolean): Pair<List<Char>, List<Char>> {
    val first = ArrayList<Char>()
    val second = ArrayList<Char>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun DoubleArray.partition(predicate: (Double) -> Boolean): Pair<List<Double>, List<Double>> {
    val first = ArrayList<Double>()
    val second = ArrayList<Double>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun FloatArray.partition(predicate: (Float) -> Boolean): Pair<List<Float>, List<Float>> {
    val first = ArrayList<Float>()
    val second = ArrayList<Float>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun IntArray.partition(predicate: (Int) -> Boolean): Pair<List<Int>, List<Int>> {
    val first = ArrayList<Int>()
    val second = ArrayList<Int>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun LongArray.partition(predicate: (Long) -> Boolean): Pair<List<Long>, List<Long>> {
    val first = ArrayList<Long>()
    val second = ArrayList<Long>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun ShortArray.partition(predicate: (Short) -> Boolean): Pair<List<Short>, List<Short>> {
    val first = ArrayList<Short>()
    val second = ArrayList<Short>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun <T> Iterable<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun <T> Sequence<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}

/**
 * Splits original collection into pair of collections,
 * where *first* collection contains elements for which [predicate] yielded `true`,
 * while *second* collection contains elements for which [predicate] yielded `false`.
 */
public inline fun String.partition(predicate: (Char) -> Boolean): Pair<String, String> {
    val first = StringBuilder()
    val second = StringBuilder()
    for (element in this) {
        if (predicate(element)) {
            first.append(element)
        } else {
            second.append(element)
        }
    }
    return Pair(first.toString(), second.toString())
}

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [array].
 */
public fun <T> Collection<T>.plus(array: Array<out T>): List<T> {
    val result = ArrayList<T>(this.size() + array.size())
    result.addAll(this)
    result.addAll(array)
    return result
}

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [array].
 */
public fun <T> Iterable<T>.plus(array: Array<out T>): List<T> {
    if (this is Collection) return this.plus(array)
    val result = ArrayList<T>()
    result.addAll(this)
    result.addAll(array)
    return result
}

/**
 * Returns a sequence containing all elements of original sequence and then all elements of the given [array].
 */
public fun <T> Sequence<T>.plus(array: Array<out T>): Sequence<T> {
    return this.plus(array.asList())
}

/**
 * Returns a set containing all elements both of the original set and the given [array].
 */
public fun <T> Set<T>.plus(array: Array<out T>): Set<T> {
    val result = LinkedHashSet<T>(mapCapacity(this.size() + array.size()))
    result.addAll(this)
    result.addAll(array)
    return result
}

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [collection].
 */
public fun <T> Collection<T>.plus(collection: Iterable<T>): List<T> {
    if (collection is Collection) {
        val result = ArrayList<T>(this.size() + collection.size())
        result.addAll(this)
        result.addAll(collection)
        return result
    } else {
        val result = ArrayList<T>(this)
        result.addAll(collection)
        return result
    }
}

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [collection].
 */
public fun <T> Iterable<T>.plus(collection: Iterable<T>): List<T> {
    if (this is Collection) return this.plus(collection)
    val result = ArrayList<T>()
    result.addAll(this)
    result.addAll(collection)
    return result
}

/**
 * Returns a sequence containing all elements of original sequence and then all elements of the given [collection].
 */
public fun <T> Sequence<T>.plus(collection: Iterable<T>): Sequence<T> {
    return sequenceOf(this, collection.asSequence()).flatten()
}

/**
 * Returns a set containing all elements both of the original set and the given [collection].
 */
public fun <T> Set<T>.plus(collection: Iterable<T>): Set<T> {
    val result = LinkedHashSet<T>(mapCapacity(collection.collectionSizeOrNull()?.let { this.size() + it } ?: this.size() * 2))
    result.addAll(this)
    result.addAll(collection)
    return result
}

/**
 * Returns a list containing all elements of the original collection and then the given [element].
 */
public fun <T> Collection<T>.plus(element: T): List<T> {
    val result = ArrayList<T>(size() + 1)
    result.addAll(this)
    result.add(element)
    return result
}

/**
 * Returns a list containing all elements of the original collection and then the given [element].
 */
public fun <T> Iterable<T>.plus(element: T): List<T> {
    if (this is Collection) return this.plus(element)
    val result = ArrayList<T>()
    result.addAll(this)
    result.add(element)
    return result
}

/**
 * Returns a sequence containing all elements of the original sequence and then the given [element].
 */
public fun <T> Sequence<T>.plus(element: T): Sequence<T> {
    return sequenceOf(this, sequenceOf(element)).flatten()
}

/**
 * Returns a set containing all elements of the original set and then the given [element].
 */
public fun <T> Set<T>.plus(element: T): Set<T> {
    val result = LinkedHashSet<T>(mapCapacity(size() + 1))
    result.addAll(this)
    result.add(element)
    return result
}

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [sequence].
 */
public fun <T> Collection<T>.plus(sequence: Sequence<T>): List<T> {
    val result = ArrayList<T>(this.size() + 10)
    result.addAll(this)
    result.addAll(sequence)
    return result
}

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [sequence].
 */
public fun <T> Iterable<T>.plus(sequence: Sequence<T>): List<T> {
    val result = ArrayList<T>()
    result.addAll(this)
    result.addAll(sequence)
    return result
}

/**
 * Returns a sequence containing all elements of original sequence and then all elements of the given [sequence].
 */
public fun <T> Sequence<T>.plus(sequence: Sequence<T>): Sequence<T> {
    return sequenceOf(this, sequence).flatten()
}

/**
 * Returns a set containing all elements both of the original set and the given [sequence].
 */
public fun <T> Set<T>.plus(sequence: Sequence<T>): Set<T> {
    val result = LinkedHashSet<T>(mapCapacity(this.size() * 2))
    result.addAll(this)
    result.addAll(sequence)
    return result
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <T, R> Array<out T>.zip(array: Array<out R>): List<Pair<T, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> BooleanArray.zip(array: Array<out R>): List<Pair<Boolean, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> ByteArray.zip(array: Array<out R>): List<Pair<Byte, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> CharArray.zip(array: Array<out R>): List<Pair<Char, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> DoubleArray.zip(array: Array<out R>): List<Pair<Double, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> FloatArray.zip(array: Array<out R>): List<Pair<Float, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> IntArray.zip(array: Array<out R>): List<Pair<Int, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> LongArray.zip(array: Array<out R>): List<Pair<Long, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> ShortArray.zip(array: Array<out R>): List<Pair<Short, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <T, R> Iterable<T>.zip(array: Array<out R>): List<Pair<T, R>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun BooleanArray.zip(array: BooleanArray): List<Pair<Boolean, Boolean>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun ByteArray.zip(array: ByteArray): List<Pair<Byte, Byte>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun CharArray.zip(array: CharArray): List<Pair<Char, Char>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun DoubleArray.zip(array: DoubleArray): List<Pair<Double, Double>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun FloatArray.zip(array: FloatArray): List<Pair<Float, Float>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun IntArray.zip(array: IntArray): List<Pair<Int, Int>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun LongArray.zip(array: LongArray): List<Pair<Long, Long>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun ShortArray.zip(array: ShortArray): List<Pair<Short, Short>> {
    return merge(array) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <T, R> Array<out T>.zip(other: Iterable<R>): List<Pair<T, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> BooleanArray.zip(other: Iterable<R>): List<Pair<Boolean, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> ByteArray.zip(other: Iterable<R>): List<Pair<Byte, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> CharArray.zip(other: Iterable<R>): List<Pair<Char, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> DoubleArray.zip(other: Iterable<R>): List<Pair<Double, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> FloatArray.zip(other: Iterable<R>): List<Pair<Float, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> IntArray.zip(other: Iterable<R>): List<Pair<Int, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> LongArray.zip(other: Iterable<R>): List<Pair<Long, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <R> ShortArray.zip(other: Iterable<R>): List<Pair<Short, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from elements of both collections with same indexes. List has length of shortest collection.
 */
public fun <T, R> Iterable<T>.zip(other: Iterable<R>): List<Pair<T, R>> {
    return merge(other) { t1, t2 -> t1 to t2 }
}

/**
 * Returns a list of pairs built from characters of both strings with same indexes. List has length of shortest collection.
 */
public fun String.zip(other: String): List<Pair<Char, Char>> {
    val first = iterator()
    val second = other.iterator()
    val list = ArrayList<Pair<Char, Char>>(length())
    while (first.hasNext() && second.hasNext()) {
        list.add(first.next() to second.next())
    }
    return list
}

/**
 * Returns a sequence of pairs built from elements of both collections with same indexes.
 * Resulting sequence has length of shortest input sequences.
 */
public fun <T, R> Sequence<T>.zip(sequence: Sequence<R>): Sequence<Pair<T, R>> {
    return MergingSequence(this, sequence) { t1, t2 -> t1 to t2 }
}

