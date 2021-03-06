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

package org.jetbrains.kotlin.idea.ktSignature;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.idea.JetIcons;
import org.jetbrains.kotlin.idea.caches.resolve.JavaResolveExtension;
import org.jetbrains.kotlin.idea.caches.resolve.ResolvePackage;
import org.jetbrains.kotlin.idea.project.ProjectStructureUtil;
import org.jetbrains.kotlin.load.java.JavaBindingContext;
import org.jetbrains.kotlin.load.java.structure.impl.JavaConstructorImpl;
import org.jetbrains.kotlin.load.java.structure.impl.JavaFieldImpl;
import org.jetbrains.kotlin.load.java.structure.impl.JavaMethodImpl;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class KotlinSignatureInJavaMarkerProvider implements LineMarkerProvider {
    private static final String SHOW_MARKERS_PROPERTY = "kotlin.signature.markers.enabled";
    private static final Logger LOG = Logger.getInstance(KotlinSignatureInJavaMarkerProvider.class);

    private static final GutterIconNavigationHandler<PsiModifierListOwner> NAVIGATION_HANDLER = new GutterIconNavigationHandler<PsiModifierListOwner>() {
        @Override
        public void navigate(MouseEvent e, PsiModifierListOwner element) {
            if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)) {
                new EditSignatureAction(element).actionPerformed(DataManager.getInstance().getDataContext(e.getComponent()), e.getPoint());
            }
        }
    };

    @Override
    @Nullable
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        if (elements.isEmpty()) {
            return;
        }

        PsiElement firstElement = elements.get(0);
        Project project = firstElement.getProject();
        if (!isMarkersEnabled(project)) {
            return;
        }

        if (!ProjectStructureUtil.hasJvmKotlinModules(project)) {
            return;
        }

        Module module = ModuleUtilCore.findModuleForPsiElement(firstElement);
        if (module != null && !ProjectStructureUtil.isUsedInKotlinJavaModule(module)) {
            return;
        }

        markElements(elements, result, firstElement.getContainingFile());
    }

    private static void markElements(
            @NotNull List<PsiElement> elements,
            @NotNull Collection<LineMarkerInfo> result,
            @NotNull PsiFile psiFile
    ) {
        try {
            for (PsiElement element : elements) {
                markElement(element, result);
            }
        }
        catch (AssertionError error) {
            LOG.warn("Exception while collecting KotlinSignature markers:\n" + ExceptionUtil.getThrowableText(error));
        }
    }

    private static void markElement(@NotNull PsiElement element, @NotNull Collection<LineMarkerInfo> result) {
        Project project = element.getProject();
        PsiModifierListOwner annotationOwner = KotlinSignatureUtil.getAnalyzableAnnotationOwner(element);
        if (annotationOwner == null) {
            return;
        }

        JavaResolveExtension resolveExtension = JavaResolveExtension.INSTANCE$;
        BindingContext bindingContext = resolveExtension.getContext(project, annotationOwner);
        JavaDescriptorResolver javaDescriptorResolver = resolveExtension.getResolver(project, annotationOwner);

        DeclarationDescriptor memberDescriptor = getDescriptorForMember(javaDescriptorResolver, annotationOwner);

        if (memberDescriptor == null) return;

        List<String> errors = bindingContext.get(JavaBindingContext.LOAD_FROM_JAVA_SIGNATURE_ERRORS, memberDescriptor);
        boolean hasSignatureAnnotation = KotlinSignatureUtil.findKotlinSignatureAnnotation(annotationOwner) != null;

        if (errors != null || hasSignatureAnnotation) {
            result.add(new MyLineMarkerInfo((PsiModifierListOwner) element, errors, hasSignatureAnnotation));
        }
    }

    @Nullable
    private static DeclarationDescriptor getDescriptorForMember(
            @NotNull JavaDescriptorResolver javaDescriptorResolver,
            @NotNull PsiModifierListOwner member
    ) {
        if (member instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) member;
            if (method.isConstructor()) {
                return ResolvePackage.resolveConstructor(javaDescriptorResolver, new JavaConstructorImpl(method));
            }
            else {
                return ResolvePackage.resolveMethod(javaDescriptorResolver, new JavaMethodImpl(method));
            }
        }
        else if (member instanceof PsiField) {
            return ResolvePackage.resolveField(javaDescriptorResolver, new JavaFieldImpl((PsiField) member));
        }
        return null;
    }

    public static boolean isMarkersEnabled(@NotNull Project project) {
        return PropertiesComponent.getInstance(project).getBoolean(SHOW_MARKERS_PROPERTY, true);
    }

    public static void setMarkersEnabled(@NotNull Project project, boolean value) {
        PropertiesComponent.getInstance(project).setValue(SHOW_MARKERS_PROPERTY, Boolean.toString(value));
        KotlinSignatureUtil.refreshMarkers(project);
    }

    private static class MyLineMarkerInfo extends LineMarkerInfo<PsiModifierListOwner> {
        public MyLineMarkerInfo(PsiModifierListOwner element, @Nullable List<String> errors, boolean hasAnnotation) {
            super(element, element.getTextOffset(), errors != null ? AllIcons.Ide.Error : JetIcons.SMALL_LOGO, Pass.UPDATE_OVERRIDEN_MARKERS,
                  new TooltipProvider(errors), hasAnnotation ? NAVIGATION_HANDLER : null);
        }

        @Nullable
        @Override
        public GutterIconRenderer createGutterRenderer() {
            return new LineMarkerGutterIconRenderer<PsiModifierListOwner>(this) {
                @Nullable
                @Override
                public ActionGroup getPopupMenuActions() {
                    if (getNavigationHandler() == null) {
                        return null;
                    }

                    PsiModifierListOwner element = getElement();
                    assert element != null;

                    return new DefaultActionGroup(new EditSignatureAction(element), new DeleteSignatureAction(element));
                }
            };
        }
    }

    private static class TooltipProvider implements Function<PsiElement, String> {
        private final @Nullable List<String> errors;

        private TooltipProvider(@Nullable List<String> errors) {
            this.errors = errors;
        }

        @Nullable
        @Override
        public String fun(PsiElement element) {
            PsiAnnotation annotation = KotlinSignatureUtil.findKotlinSignatureAnnotation(element);

            if (annotation == null) return errorsString();

            String signature = KotlinSignatureUtil.getKotlinSignature(annotation);
            String text = "Alternative Kotlin signature is available for this method:\n" + StringUtil.escapeXml(signature);
            if (errors == null) {
                return text;
            }
            return text + "\nIt has the following " + StringUtil.pluralize("error", errors.size()) + ":\n" + errorsString();
        }

        @NotNull
        private String errorsString() {
            assert errors != null;
            return StringUtil.escapeXml(StringUtil.join(errors, "\n"));
        }
    }
}
