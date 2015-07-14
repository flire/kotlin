/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeUtils
import java.lang.annotation.ElementType
import java.util.*

public object AnnotationTargetChecker {

    // NOTE: this enum must have the same entries with kotlin.annotation.AnnotationTarget
    public enum class Target(val description: String, val mapped: ElementType? = null, val isDefault: Boolean = true) {
        PACKAGE("package", ElementType.PACKAGE),
        CLASSIFIER("classifier", ElementType.TYPE),
        ANNOTATION_CLASS("annotation class", ElementType.ANNOTATION_TYPE),
        TYPE_PARAMETER("type parameter", null, false),
        PROPERTY("property"),
        FIELD("field", ElementType.FIELD),
        LOCAL_VARIABLE("local variable", ElementType.LOCAL_VARIABLE),
        VALUE_PARAMETER("value parameter", ElementType.PARAMETER),
        CONSTRUCTOR("constructor", ElementType.CONSTRUCTOR),
        FUNCTION("function", ElementType.METHOD),
        PROPERTY_GETTER("getter", ElementType.METHOD),
        PROPERTY_SETTER("setter", ElementType.METHOD),
        TYPE("type usage", null, false),
        EXPRESSION("expression", null, false),
        FILE("file", null, false);

        companion object {

            private val map = HashMap<String, Target>()
            init {
                for (target in Target.values()) {
                    map[target.name()] = target
                }
            }

            fun valueOrNull(name: String): Target? = map[name]
        }
    }

    private val DEFAULT_TARGET_LIST = Target.values().filter { it.isDefault }.toSet()

    private val ALL_TARGET_LIST = Target.values().toSet()

    public fun check(annotated: JetAnnotated, trace: BindingTrace) {
        if (annotated is JetTypeParameter) return // TODO: support type parameter annotations
        val actualTargets = getActualTargetList(annotated, trace)
        for (entry in annotated.getAnnotationEntries()) {
            checkAnnotationEntry(entry, actualTargets, trace)
        }
        if (annotated is JetCallableDeclaration) {
            annotated.getTypeReference()?.let { check(it, trace) }
        }
        if (annotated is JetFunction) {
            for (parameter in annotated.getValueParameters()) {
                if (!parameter.hasValOrVar()) {
                    check(parameter, trace)
                    if (annotated is JetFunctionLiteral) {
                        parameter.getTypeReference()?.let { check(it, trace) }
                    }
                }
            }
        }
        if (annotated is JetClassOrObject) {
            for (initializer in annotated.getAnonymousInitializers()) {
                check(initializer, trace)
            }
        }
    }

    public fun checkExpression(expression: JetExpression, trace: BindingTrace) {
        for (entry in expression.getAnnotationEntries()) {
            checkAnnotationEntry(entry, listOf(Target.EXPRESSION), trace)
        }
        if (expression is JetFunctionLiteralExpression) {
            for (parameter in expression.getValueParameters()) {
                parameter.getTypeReference()?.let { check(it, trace) }
            }
        }
    }

    public fun possibleTargetList(classDescriptor: ClassDescriptor): Set<Target>? {
        val targetEntryDescriptor = classDescriptor.getAnnotations().findAnnotation(KotlinBuiltIns.FQ_NAMES.target)
                                    ?: return null
        val valueArguments = targetEntryDescriptor.getAllValueArguments()
        val valueArgument = valueArguments.entrySet().firstOrNull()?.getValue() as? ArrayValue ?: return null
        return valueArgument.value.filterIsInstance<EnumValue>().map {
            Target.valueOrNull(it.value.getName().asString())
        }.filterNotNull().toSet()
    }

    private fun possibleTargetList(entry: JetAnnotationEntry, trace: BindingTrace): Set<Target> {
        val descriptor = trace.get(BindingContext.ANNOTATION, entry) ?: return DEFAULT_TARGET_LIST
        // For descriptor with error type, all targets are considered as possible
        if (descriptor.getType().isError()) return ALL_TARGET_LIST
        val classDescriptor = TypeUtils.getClassDescriptor(descriptor.getType()) ?: return DEFAULT_TARGET_LIST
        return possibleTargetList(classDescriptor) ?: DEFAULT_TARGET_LIST
    }

    private fun checkAnnotationEntry(entry: JetAnnotationEntry, actualTargets: List<Target>, trace: BindingTrace) {
        val possibleTargets = possibleTargetList(entry, trace)
        for (actualTarget in actualTargets) {
            if (actualTarget in possibleTargets) return
        }
        trace.report(Errors.WRONG_ANNOTATION_TARGET.on(entry, actualTargets.firstOrNull()?.description ?: "unidentified target"))
    }

    private fun getActualTargetList(annotated: JetAnnotated, trace: BindingTrace): List<Target> {
        if (annotated is JetClassOrObject) {
            if (annotated is JetEnumEntry) return listOf(Target.PROPERTY, Target.FIELD)
            return if (isAnnotation(annotated, trace)) listOf(Target.ANNOTATION_CLASS, Target.CLASSIFIER) else listOf(Target.CLASSIFIER)
        }
        if (annotated is JetProperty) {
            return if (annotated.isLocal()) listOf(Target.LOCAL_VARIABLE) else listOf(Target.PROPERTY, Target.FIELD)
        }
        if (annotated is JetParameter) {
            return if (annotated.hasValOrVar()) listOf(Target.PROPERTY, Target.FIELD) else listOf(Target.VALUE_PARAMETER)
        }
        if (annotated is JetConstructor<*>) return listOf(Target.CONSTRUCTOR)
        if (annotated is JetFunction) return listOf(Target.FUNCTION)
        if (annotated is JetPropertyAccessor) {
            return if (annotated.isGetter()) listOf(Target.PROPERTY_GETTER) else listOf(Target.PROPERTY_SETTER)
        }
        if (annotated is JetPackageDirective) return listOf(Target.PACKAGE)
        if (annotated is JetTypeReference) return listOf(Target.TYPE)
        if (annotated is JetFile) return listOf(Target.FILE)
        if (annotated is JetTypeParameter) return listOf(Target.TYPE_PARAMETER)
        return listOf()
    }

    private fun isAnnotation(annotated: JetClassOrObject, trace: BindingTrace): Boolean {
        val descriptor = trace.get(BindingContext.CLASS, annotated) ?: return false
        return descriptor.getKind() == ClassKind.ANNOTATION_CLASS
    }
}