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

package kotlin.reflect.jvm.internal

import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import kotlin.reflect.KParameter

class KParameterImpl(
        callable: KCallableImpl<*>,
        override val index: Int
) : KParameter {
    private val descriptor: ParameterDescriptor by ReflectProperties.lazySoft<ParameterDescriptor> {
        val descriptor = callable.descriptor
        val dispatchReceiver = descriptor.getDispatchReceiverParameter()
        val extensionReceiver = descriptor.getExtensionReceiverParameter()

        when {
            dispatchReceiver != null -> when {
                extensionReceiver != null -> when (index) {
                    0 -> dispatchReceiver
                    1 -> extensionReceiver
                    else -> descriptor.getValueParameters()[index - 2]
                }
                else -> when (index) {
                    0 -> dispatchReceiver
                    else -> descriptor.getValueParameters()[index - 1]
                }
            }
            else -> when {
                extensionReceiver != null -> when (index) {
                    0 -> extensionReceiver
                    else -> descriptor.getValueParameters()[index - 1]
                }
                else -> {
                    descriptor.getValueParameters()[index]
                }
            }
        }
    }

    override val name: String? get() {
        val valueParameter = descriptor as? ValueParameterDescriptor ?: return null
        if (valueParameter.getContainingDeclaration().hasSynthesizedParameterNames()) return null
        val name = valueParameter.getName()
        return if (name.isSpecial()) null else name.asString()
    }
}
