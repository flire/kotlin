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

package org.jetbrains.kotlin.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.TreeElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationApplicability
import org.jetbrains.kotlin.lexer.JetKeywordToken
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.psi.stubs.KotlinAnnotationEntryStub
import org.jetbrains.kotlin.psi.stubs.KotlinPlaceHolderStub
import org.jetbrains.kotlin.psi.stubs.elements.JetStubElementTypes

public class JetAnnotationApplicability : JetElementImplStub<KotlinPlaceHolderStub<JetAnnotationApplicability>> {

    constructor(node: ASTNode): super(node)

    constructor(stub: KotlinPlaceHolderStub<JetAnnotationApplicability>) : super(stub, JetStubElementTypes.ANNOTATION_TARGET)

    override fun <R, D> accept(visitor: JetVisitor<R, D>, data: D) = visitor.visitAnnotationTarget(this, data)

    public fun getAnnotationApplicability(): AnnotationApplicability {
        val node = getFirstChild().getNode()
        return when (node.getElementType()) {
            JetTokens.FIELD_KEYWORD -> AnnotationApplicability.FIELD
            JetTokens.FILE_KEYWORD -> AnnotationApplicability.FILE
            JetTokens.GET_KEYWORD -> AnnotationApplicability.PROPERTY_GETTER
            JetTokens.SET_KEYWORD -> AnnotationApplicability.PROPERTY_SETTER
            else -> throw IllegalStateException("Unknown annotation target " + node.getText())
        }
    }

}