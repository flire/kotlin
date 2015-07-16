/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package kotlin.js

native
target(AnnotationTarget.CLASSIFIER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER,
       AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
public annotation class native(public val name: String = "")

native
target(AnnotationTarget.CLASSIFIER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class nativeGetter

native
target(AnnotationTarget.CLASSIFIER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class nativeSetter

native
target(AnnotationTarget.CLASSIFIER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class nativeInvoke

native
target(AnnotationTarget.CLASSIFIER, AnnotationTarget.FUNCTION)
public annotation class library(public val name: String = "")

native
target(AnnotationTarget.CLASSIFIER, AnnotationTarget.PROPERTY)
public annotation class enumerable()

// TODO make it "internal" or "fake"
native
target(AnnotationTarget.CLASSIFIER)
deprecated("Do not use this annotation: it is for internal use only")
public annotation class marker