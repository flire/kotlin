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

package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.refactoring.memberPullUp.PullUpData
import com.intellij.refactoring.memberPullUp.PullUpHelper
import com.intellij.refactoring.memberPullUp.PullUpHelperFactory
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.psi.JetClassOrObject
import org.jetbrains.kotlin.psi.JetNamedDeclaration

public class KotlinPullUpHelperFactory : PullUpHelperFactory {
    private fun PullUpData.toKotlinPullUpData(): KotlinPullUpData? {
        if (!getSourceClass().isInheritor(getTargetClass(), true)) return null
        val sourceClass = getSourceClass().unwrapped as? JetClassOrObject ?: return null
        val targetClass = getTargetClass().unwrapped as? JetClass ?: return null
        val membersToMove = getMembersToMove().map { it.namedUnwrappedElement as? JetNamedDeclaration }.filterNotNull()
        return KotlinPullUpData(this, sourceClass, targetClass, membersToMove)
    }

    override fun createPullUpHelper(data: PullUpData): PullUpHelper<*> {
        return KotlinPullUpHelper(data.toKotlinPullUpData() ?: return EmptyPullUpHelper)
    }
}
