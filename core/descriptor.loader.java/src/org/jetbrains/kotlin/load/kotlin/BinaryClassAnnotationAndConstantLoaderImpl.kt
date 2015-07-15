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

package org.jetbrains.kotlin.load.kotlin

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.load.java.components.DescriptorResolverUtils
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.AnnotationDeserializer
import org.jetbrains.kotlin.serialization.deserialization.ErrorReporter
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import org.jetbrains.kotlin.serialization.deserialization.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.TypeUtils
import java.lang.annotation.ElementType
import java.util.ArrayList
import java.util.HashMap

public class BinaryClassAnnotationAndConstantLoaderImpl(
        private val module: ModuleDescriptor,
        storageManager: StorageManager,
        kotlinClassFinder: KotlinClassFinder,
        errorReporter: ErrorReporter
) : AbstractBinaryClassAnnotationAndConstantLoader<AnnotationDescriptor, ConstantValue<*>>(
        storageManager, kotlinClassFinder, errorReporter
) {
    private val annotationDeserializer = AnnotationDeserializer(module)
    private val factory = ConstantValueFactory(module.builtIns)

    override fun loadTypeAnnotation(proto: ProtoBuf.Annotation, nameResolver: NameResolver): AnnotationDescriptor =
            annotationDeserializer.deserializeAnnotation(proto, nameResolver)

    override fun loadConstant(desc: String, initializer: Any): ConstantValue<*>? {
        val normalizedValue: Any = if (desc in "ZBCS") {
            val intValue = initializer as Int
            when (desc) {
                "Z" -> intValue != 0
                "B" -> intValue.toByte()
                "C" -> intValue.toChar()
                "S" -> intValue.toShort()
                else -> throw AssertionError(desc)
            }
        }
        else {
            initializer
        }

        return factory.createConstantValue(normalizedValue)
    }

    override fun convertAnnotation(
            annotation: AnnotationDescriptor,
            result: MutableList<AnnotationDescriptor>
    ) = generateTargetAnnotationIfNeeded(annotation, result)

    private val targetNames = mapOf(Pair("PACKAGE", "PACKAGE"),
                                    Pair("TYPE", "CLASSIFIER"),
                                    Pair("ANNOTATION_TYPE", "ANNOTATION_CLASS"),
                                    Pair("TYPE_PARAMETER", "TYPE_PARAMETER"),
                                    Pair("FIELD", "FIELD"),
                                    Pair("LOCAL_VARIABLE", "LOCAL_VARIABLE"),
                                    Pair("PARAMETER", "VALUE_PARAMETER"),
                                    Pair("CONSTRUCTOR", "CONSTRUCTOR"),
                                    Pair("METHOD", "FUNCTION"),
                                    Pair("TYPE_USE", "TYPE")
    )

    private fun generateTargetAnnotationIfNeeded(
            annotation: AnnotationDescriptor,
            result: MutableList<AnnotationDescriptor>
    ): AnnotationDescriptor? {
        val classDescriptor = annotation.getType().getConstructor().getDeclarationDescriptor() as? ClassDescriptor
        if (classDescriptor?.let { DescriptorUtils.getFqName(it) } == JvmAnnotationNames.JAVA_TARGET_ANNOTATION.toUnsafe()) {
            for (existingAnnotation in result) {
                // If kotlin.annotation.target already exists => return
                val existingFqName = existingAnnotation.getType().getConstructor().getDeclarationDescriptor()?.let { DescriptorUtils.getFqName(it) }
                if (existingFqName == KotlinBuiltIns.FQ_NAMES.target.toUnsafe()) return null
            }
            // Generate kotlin.annotation.target, map arguments
            val kotlinTargetClassDescriptor = KotlinBuiltIns.getInstance().getAnnotationClassByName(KotlinBuiltIns.FQ_NAMES.target.shortName());
            val kotlinAnnotationTargetClassDescriptor = KotlinBuiltIns.getInstance().getAnnotationClassByName(
                    KotlinBuiltIns.FQ_NAMES.annotationTarget.shortName());
            val javaArguments = annotation.getAllValueArguments()
            val javaArgument = javaArguments.entrySet().firstOrNull()?.getValue() as? ArrayValue
            val kotlinTargets = ArrayList<ConstantValue<*>>()
            javaArgument?.value?.filterIsInstance<EnumValue>()?.forEach {
                val targetName = targetNames[it.value.getName().asString()]
                val enumClassifier = kotlinAnnotationTargetClassDescriptor.getUnsubstitutedInnerClassesScope().getClassifier(Name.identifier(targetName ?: ""))
                if (enumClassifier is ClassDescriptor) {
                    kotlinTargets.add(EnumValue(enumClassifier))
                }
            }
            val parameterDescriptor = DescriptorResolverUtils.getAnnotationParameterByName(Name.identifier("allowedTargets"),
                                                                                           kotlinTargetClassDescriptor)
            if (parameterDescriptor != null) {
                val parameterValue = ArrayValue(kotlinTargets, parameterDescriptor.getType())
                return AnnotationDescriptorImpl(kotlinTargetClassDescriptor.getDefaultType(),
                                                mapOf(Pair(parameterDescriptor, parameterValue)));
            }
        }
        return null
    }

    override fun loadAnnotation(
            annotationClassId: ClassId,
            result: MutableList<AnnotationDescriptor>
    ): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
        val annotationClass = resolveClass(annotationClassId)

        return object : KotlinJvmBinaryClass.AnnotationArgumentVisitor {
            private val arguments = HashMap<ValueParameterDescriptor, ConstantValue<*>>()

            override fun visit(name: Name?, value: Any?) {
                if (name != null) {
                    setArgumentValueByName(name, createConstant(name, value))
                }
            }

            override fun visitEnum(name: Name, enumClassId: ClassId, enumEntryName: Name) {
                setArgumentValueByName(name, enumEntryValue(enumClassId, enumEntryName))
            }

            override fun visitArray(name: Name): AnnotationArrayArgumentVisitor? {
                return object : KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor {
                    private val elements = ArrayList<ConstantValue<*>>()

                    override fun visit(value: Any?) {
                        elements.add(createConstant(name, value))
                    }

                    override fun visitEnum(enumClassId: ClassId, enumEntryName: Name) {
                        elements.add(enumEntryValue(enumClassId, enumEntryName))
                    }

                    override fun visitEnd() {
                        val parameter = DescriptorResolverUtils.getAnnotationParameterByName(name, annotationClass)
                        if (parameter != null) {
                            elements.trimToSize()
                            arguments[parameter] = factory.createArrayValue(elements, parameter.getType())
                        }
                    }
                }
            }

            override fun visitAnnotation(name: Name, classId: ClassId): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
                val list = ArrayList<AnnotationDescriptor>()
                val visitor = loadAnnotation(classId, list)!!
                return object: KotlinJvmBinaryClass.AnnotationArgumentVisitor by visitor {
                    override fun visitEnd() {
                        visitor.visitEnd()
                        setArgumentValueByName(name, AnnotationValue(list.single()))
                    }
                }
            }

            // NOTE: see analogous code in AnnotationDeserializer
            private fun enumEntryValue(enumClassId: ClassId, name: Name): ConstantValue<*> {
                val enumClass = resolveClass(enumClassId)
                if (enumClass.getKind() == ClassKind.ENUM_CLASS) {
                    val classifier = enumClass.getUnsubstitutedInnerClassesScope().getClassifier(name)
                    if (classifier is ClassDescriptor) {
                        return factory.createEnumValue(classifier)
                    }
                }
                return factory.createErrorValue("Unresolved enum entry: $enumClassId.$name")
            }

            override fun visitEnd() {
                result.add(AnnotationDescriptorImpl(annotationClass.getDefaultType(), arguments))
            }

            private fun createConstant(name: Name?, value: Any?): ConstantValue<*> {
                return factory.createConstantValue(value) ?:
                       factory.createErrorValue("Unsupported annotation argument: $name")
            }

            private fun setArgumentValueByName(name: Name, argumentValue: ConstantValue<*>) {
                val parameter = DescriptorResolverUtils.getAnnotationParameterByName(name, annotationClass)
                if (parameter != null) {
                    arguments[parameter] = argumentValue
                }
            }
        }
    }

    private fun resolveClass(classId: ClassId): ClassDescriptor {
        return module.findClassAcrossModuleDependencies(classId)
               ?: ErrorUtils.createErrorClass(classId.asSingleFqName().asString())
    }
}
