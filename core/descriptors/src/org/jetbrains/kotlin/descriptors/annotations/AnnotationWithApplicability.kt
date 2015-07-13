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

package org.jetbrains.kotlin.descriptors.annotations

import org.jetbrains.kotlin.name.FqName

public class AnnotationWithApplicability(val annotation: AnnotationDescriptor, val applicability: AnnotationApplicability)

public class UseSiteTargetedAnnotations(
        private val original: Annotations,
        private val annotated: Annotated,
        private val acceptedApplicability: AnnotationApplicability
) : Annotations {

    override fun isEmpty() = original.isEmpty()

    override fun findAnnotation(fqName: FqName) = original.findAnnotation(fqName)

    override fun findExternalAnnotation(fqName: FqName) = original.findExternalAnnotation(fqName)

    private fun getAdditionalAnnotationsWithApplicability() = annotated.getAnnotations()
            .getAnnotationsWithApplicability().filter { it.applicability == acceptedApplicability }

    override fun getAnnotationsWithApplicability(): List<AnnotationWithApplicability> {
        return original.getAnnotationsWithApplicability() + getAdditionalAnnotationsWithApplicability()
    }

    override fun getAllAnnotations(): List<Pair<AnnotationDescriptor, AnnotationApplicability?>> {
        return original.getAllAnnotations() + getAdditionalAnnotationsWithApplicability().map { it.annotation to it.applicability }
    }

    override fun iterator() = original.iterator()
}