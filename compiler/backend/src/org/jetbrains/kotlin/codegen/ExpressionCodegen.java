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

package org.jetbrains.kotlin.codegen;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.Stack;
import kotlin.KotlinPackage;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.backend.common.CodegenUtil;
import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.codegen.binding.CalculatedClosure;
import org.jetbrains.kotlin.codegen.binding.CodegenBinding;
import org.jetbrains.kotlin.codegen.context.*;
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension;
import org.jetbrains.kotlin.codegen.inline.*;
import org.jetbrains.kotlin.codegen.intrinsics.IntrinsicMethod;
import org.jetbrains.kotlin.codegen.intrinsics.IntrinsicMethods;
import org.jetbrains.kotlin.codegen.intrinsics.JavaClassProperty;
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsnsPackage;
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter;
import org.jetbrains.kotlin.codegen.state.GenerationState;
import org.jetbrains.kotlin.codegen.state.JetTypeMapper;
import org.jetbrains.kotlin.codegen.when.SwitchCodegen;
import org.jetbrains.kotlin.codegen.when.SwitchCodegenUtil;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.impl.ScriptCodeDescriptor;
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils;
import org.jetbrains.kotlin.diagnostics.Errors;
import org.jetbrains.kotlin.jvm.RuntimeAssertionInfo;
import org.jetbrains.kotlin.jvm.bindingContextSlices.BindingContextSlicesPackage;
import org.jetbrains.kotlin.lexer.JetTokens;
import org.jetbrains.kotlin.load.java.JvmAbi;
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor;
import org.jetbrains.kotlin.load.java.descriptors.SamConstructorDescriptor;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.BindingContextUtils;
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils;
import org.jetbrains.kotlin.resolve.DescriptorUtils;
import org.jetbrains.kotlin.resolve.annotations.AnnotationsPackage;
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.CallResolverUtilPackage;
import org.jetbrains.kotlin.resolve.calls.model.*;
import org.jetbrains.kotlin.resolve.calls.util.CallMaker;
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject;
import org.jetbrains.kotlin.resolve.constants.CompileTimeConstant;
import org.jetbrains.kotlin.resolve.constants.ConstantValue;
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator;
import org.jetbrains.kotlin.resolve.descriptorUtil.DescriptorUtilPackage;
import org.jetbrains.kotlin.resolve.inline.InlineUtil;
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodParameterKind;
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodParameterSignature;
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature;
import org.jetbrains.kotlin.resolve.scopes.receivers.*;
import org.jetbrains.kotlin.types.JetType;
import org.jetbrains.kotlin.types.TypeProjection;
import org.jetbrains.kotlin.types.TypeUtils;
import org.jetbrains.kotlin.types.checker.JetTypeChecker;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter;

import java.util.*;

import static org.jetbrains.kotlin.builtins.KotlinBuiltIns.isInt;
import static org.jetbrains.kotlin.codegen.AsmUtil.*;
import static org.jetbrains.kotlin.codegen.JvmCodegenUtil.*;
import static org.jetbrains.kotlin.codegen.binding.CodegenBinding.*;
import static org.jetbrains.kotlin.load.java.JvmAnnotationNames.KotlinSyntheticClass;
import static org.jetbrains.kotlin.psi.PsiPackage.JetPsiFactory;
import static org.jetbrains.kotlin.resolve.BindingContext.*;
import static org.jetbrains.kotlin.resolve.BindingContextUtils.*;
import static org.jetbrains.kotlin.resolve.DescriptorUtils.*;
import static org.jetbrains.kotlin.resolve.calls.callUtil.CallUtilPackage.getResolvedCall;
import static org.jetbrains.kotlin.resolve.calls.callUtil.CallUtilPackage.getResolvedCallWithAssert;
import static org.jetbrains.kotlin.resolve.descriptorUtil.DescriptorUtilPackage.getBuiltIns;
import static org.jetbrains.kotlin.resolve.jvm.AsmTypes.*;
import static org.jetbrains.kotlin.resolve.jvm.diagnostics.DiagnosticsPackage.OtherOrigin;
import static org.jetbrains.kotlin.resolve.jvm.diagnostics.DiagnosticsPackage.TraitImpl;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

public class ExpressionCodegen extends JetVisitor<StackValue, StackValue> implements LocalLookup {
    private final GenerationState state;
    final JetTypeMapper typeMapper;
    private final BindingContext bindingContext;

    public final InstructionAdapter v;
    public final FrameMap myFrameMap;
    private final MethodContext context;
    private final Type returnType;

    private final CodegenStatementVisitor statementVisitor = new CodegenStatementVisitor(this);
    private final MemberCodegen<?> parentCodegen;
    private final TailRecursionCodegen tailRecursionCodegen;
    public final CallGenerator defaultCallGenerator = new CallGenerator.DefaultCallGenerator(this);

    private final Stack<BlockStackElement> blockStackElements = new Stack<BlockStackElement>();

    /*
     * When we create a temporary variable to hold some value not to compute it many times
     * we put it into this map to emit access to that variable instead of evaluating the whole expression
     */
    public final Map<JetElement, StackValue> tempVariables = Maps.newHashMap();

    private int myLastLineNumber = -1;
    private boolean shouldMarkLineNumbers = true;
    private int finallyDepth = 0;

    public ExpressionCodegen(
            @NotNull MethodVisitor mv,
            @NotNull FrameMap frameMap,
            @NotNull Type returnType,
            @NotNull MethodContext context,
            @NotNull GenerationState state,
            @NotNull MemberCodegen<?> parentCodegen
    ) {
        this.state = state;
        this.typeMapper = state.getTypeMapper();
        this.bindingContext = state.getBindingContext();
        this.v = new InstructionAdapter(mv);
        this.myFrameMap = frameMap;
        this.context = context;
        this.returnType = returnType;
        this.parentCodegen = parentCodegen;
        this.tailRecursionCodegen = new TailRecursionCodegen(context, this, this.v, state);
    }

    static class BlockStackElement {
    }

    static class LoopBlockStackElement extends BlockStackElement {
        final Label continueLabel;
        final Label breakLabel;
        public final JetSimpleNameExpression targetLabel;

        LoopBlockStackElement(Label breakLabel, Label continueLabel, JetSimpleNameExpression targetLabel) {
            this.breakLabel = breakLabel;
            this.continueLabel = continueLabel;
            this.targetLabel = targetLabel;
        }
    }

    static class FinallyBlockStackElement extends BlockStackElement {
        List<Label> gaps = new ArrayList<Label>();

        final JetTryExpression expression;

        FinallyBlockStackElement(JetTryExpression expression) {
            this.expression = expression;
        }

        private void addGapLabel(Label label){
            gaps.add(label);
        }
    }

    @NotNull
    public GenerationState getState() {
        return state;
    }

    @NotNull
    public BindingContext getBindingContext() {
        return bindingContext;
    }

    @NotNull
    public MemberCodegen<?> getParentCodegen() {
        return parentCodegen;
    }

    @NotNull
    public ObjectLiteralResult generateObjectLiteral(@NotNull JetObjectLiteralExpression literal) {
        JetObjectDeclaration objectDeclaration = literal.getObjectDeclaration();

        ClassDescriptor classDescriptor = bindingContext.get(CLASS, objectDeclaration);
        assert classDescriptor != null;

        Type asmType = asmTypeForAnonymousClass(bindingContext, objectDeclaration);
        ClassBuilder classBuilder = state.getFactory().newVisitor(
                OtherOrigin(objectDeclaration, classDescriptor),
                asmType,
                literal.getContainingFile()
        );

        ClassContext objectContext = context.intoAnonymousClass(classDescriptor, this, OwnerKind.IMPLEMENTATION);

        MemberCodegen literalCodegen = new ImplementationBodyCodegen(
                objectDeclaration, objectContext, classBuilder, state, getParentCodegen()
        );
        literalCodegen.generate();

        addReifiedParametersFromSignature(literalCodegen, classDescriptor);
        propagateChildReifiedTypeParametersUsages(literalCodegen.getReifiedTypeParametersUsages());

        return new ObjectLiteralResult(
                literalCodegen.getReifiedTypeParametersUsages().wereUsedReifiedParameters(),
                classDescriptor
        );
    }

    private static void addReifiedParametersFromSignature(@NotNull MemberCodegen member, @NotNull ClassDescriptor descriptor) {
        for (JetType type : descriptor.getTypeConstructor().getSupertypes()) {
            for (TypeProjection supertypeArgument : type.getArguments()) {
                TypeParameterDescriptor parameterDescriptor = TypeUtils.getTypeParameterDescriptorOrNull(supertypeArgument.getType());
                if (parameterDescriptor != null && parameterDescriptor.isReified()) {
                    member.getReifiedTypeParametersUsages().addUsedReifiedParameter(parameterDescriptor.getName().asString());
                }
            }
        }
    }

    private static class ObjectLiteralResult {
        private final boolean wereReifiedMarkers;
        private final ClassDescriptor classDescriptor;

        public ObjectLiteralResult(boolean wereReifiedMarkers, @NotNull ClassDescriptor classDescriptor) {
            this.wereReifiedMarkers = wereReifiedMarkers;
            this.classDescriptor = classDescriptor;
        }
    }

    @NotNull
    private StackValue castToRequiredTypeOfInterfaceIfNeeded(
            StackValue inner,
            @NotNull ClassDescriptor provided,
            @NotNull ClassDescriptor required
    ) {
        if (!isInterface(provided) && isInterface(required)) {
            return StackValue.coercion(inner, asmType(required.getDefaultType()));
        }

        return inner;
    }

    public StackValue genQualified(StackValue receiver, JetElement selector) {
        return genQualified(receiver, selector, this);
    }

    private StackValue genQualified(StackValue receiver, JetElement selector, JetVisitor<StackValue, StackValue> visitor) {
        if (tempVariables.containsKey(selector)) {
            throw new IllegalStateException("Inconsistent state: expression saved to a temporary variable is a selector");
        }
        if (!(selector instanceof JetBlockExpression)) {
            markStartLineNumber(selector);
        }
        try {
            if (selector instanceof JetExpression) {
                StackValue samValue = genSamInterfaceValue((JetExpression) selector, visitor);
                if (samValue != null) {
                    return samValue;
                }
            }

            StackValue stackValue = selector.accept(visitor, receiver);

            RuntimeAssertionInfo runtimeAssertionInfo = null;
            if (selector instanceof JetExpression) {
                runtimeAssertionInfo = bindingContext.get(BindingContextSlicesPackage.getRUNTIME_ASSERTION_INFO(), (JetExpression) selector);
            }

            return genNotNullAssertions(state, stackValue, runtimeAssertionInfo);
        }
        catch (ProcessCanceledException e) {
            throw e;
        }
        catch (CompilationException e) {
            throw e;
        }
        catch (Throwable error) {
            String message = error.getMessage();
            throw new CompilationException(message != null ? message : "null", error, selector);
        }
    }

    public StackValue gen(JetElement expr) {
        StackValue tempVar = tempVariables.get(expr);
        return tempVar != null ? tempVar : genQualified(StackValue.none(), expr);
    }

    public void gen(JetElement expr, Type type) {
        StackValue value = Type.VOID_TYPE.equals(type) ? genStatement(expr) : gen(expr);
        value.put(type, v);
    }

    public StackValue genLazy(JetElement expr, Type type) {
        StackValue value = gen(expr);
        return StackValue.coercion(value, type);
    }

    private StackValue genStatement(JetElement statement) {
        return genQualified(StackValue.none(), statement, statementVisitor);
    }

    @Override
    public StackValue visitClass(@NotNull JetClass klass, StackValue data) {
        return visitClassOrObject(klass);
    }

    private StackValue visitClassOrObject(JetClassOrObject declaration) {
        ClassDescriptor descriptor = bindingContext.get(CLASS, declaration);
        assert descriptor != null;

        Type asmType = asmTypeForAnonymousClass(bindingContext, declaration);
        ClassBuilder classBuilder = state.getFactory().newVisitor(OtherOrigin(declaration, descriptor), asmType, declaration.getContainingFile());

        ClassContext objectContext = context.intoAnonymousClass(descriptor, this, OwnerKind.IMPLEMENTATION);
        new ImplementationBodyCodegen(declaration, objectContext, classBuilder, state, getParentCodegen()).generate();

        if (declaration instanceof JetClass && ((JetClass) declaration).isInterface()) {
            Type traitImplType = state.getTypeMapper().mapTraitImpl(descriptor);
            ClassBuilder traitImplBuilder = state.getFactory().newVisitor(TraitImpl(declaration, descriptor), traitImplType, declaration.getContainingFile());
            ClassContext traitImplContext = context.intoAnonymousClass(descriptor, this, OwnerKind.TRAIT_IMPL);
            new TraitImplBodyCodegen(declaration, traitImplContext, traitImplBuilder, state, parentCodegen).generate();
        }

        return StackValue.none();
    }

    @Override
    public StackValue visitObjectDeclaration(@NotNull JetObjectDeclaration declaration, StackValue data) {
        return visitClassOrObject(declaration);
    }

    @Override
    public StackValue visitExpression(@NotNull JetExpression expression, StackValue receiver) {
        throw new UnsupportedOperationException("Codegen for " + expression + " is not yet implemented");
    }

    @Override
    public StackValue visitSuperExpression(@NotNull JetSuperExpression expression, StackValue data) {
        return StackValue.thisOrOuter(this, getSuperCallLabelTarget(expression), true, true);
    }

    @NotNull
    private ClassDescriptor getSuperCallLabelTarget(JetSuperExpression expression) {
        PsiElement labelPsi = bindingContext.get(LABEL_TARGET, expression.getTargetLabel());
        ClassDescriptor labelTarget = (ClassDescriptor) bindingContext.get(DECLARATION_TO_DESCRIPTOR, labelPsi);
        DeclarationDescriptor descriptor = bindingContext.get(REFERENCE_TARGET, expression.getInstanceReference());
        // "super<descriptor>@labelTarget"
        if (labelTarget != null) {
            return labelTarget;
        }
        assert descriptor instanceof ClassDescriptor : "Don't know how to generate super-call to not a class";
        return getParentContextSubclassOf((ClassDescriptor) descriptor, context).getThisDescriptor();
    }

    @NotNull
    private Type asmType(@NotNull JetType type) {
        return typeMapper.mapType(type);
    }

    @NotNull
    public Type expressionType(@Nullable JetExpression expression) {
        JetType type = expressionJetType(expression);
        return type == null ? Type.VOID_TYPE : asmType(type);
    }

    @Nullable
    public JetType expressionJetType(@Nullable JetExpression expression) {
        return expression != null ? bindingContext.getType(expression) : null;
    }

    @Override
    public StackValue visitParenthesizedExpression(@NotNull JetParenthesizedExpression expression, StackValue receiver) {
        return genQualified(receiver, expression.getExpression());
    }

    @Override
    public StackValue visitAnnotatedExpression(@NotNull JetAnnotatedExpression expression, StackValue receiver) {
        return genQualified(receiver, expression.getBaseExpression());
    }

    private static boolean isEmptyExpression(@Nullable JetElement expr) {
        if (expr == null) {
            return true;
        }
        if (expr instanceof JetBlockExpression) {
            JetBlockExpression blockExpression = (JetBlockExpression) expr;
            List<JetExpression> statements = blockExpression.getStatements();
            if (statements.size() == 0 || statements.size() == 1 && isEmptyExpression(statements.get(0))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public StackValue visitIfExpression(@NotNull JetIfExpression expression, StackValue receiver) {
        return generateIfExpression(expression, false);
    }

    /* package */ StackValue generateIfExpression(@NotNull final JetIfExpression expression, final boolean isStatement) {
        final Type asmType = isStatement ? Type.VOID_TYPE : expressionType(expression);
        final StackValue condition = gen(expression.getCondition());

        final JetExpression thenExpression = expression.getThen();
        final JetExpression elseExpression = expression.getElse();

        if (isEmptyExpression(thenExpression)) {
            if (isEmptyExpression(elseExpression)) {
                return StackValue.coercion(condition, asmType);
            }
            return generateSingleBranchIf(condition, expression, elseExpression, false, isStatement);
        }
        else {
            if (isEmptyExpression(elseExpression)) {
                return generateSingleBranchIf(condition, expression, thenExpression, true, isStatement);
            }
        }

        return StackValue.operation(asmType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                Label elseLabel = new Label();
                BranchedValue.Companion.condJump(condition, elseLabel, true, v);

                Label end = new Label();

                gen(thenExpression, asmType);

                v.goTo(end);
                v.mark(elseLabel);

                gen(elseExpression, asmType);

                markLineNumber(expression, isStatement);
                v.mark(end);
                return Unit.INSTANCE$;
            }
        });
    }

    @Override
    public StackValue visitWhileExpression(@NotNull JetWhileExpression expression, StackValue receiver) {
        Label condition = new Label();
        v.mark(condition);

        Label end = new Label();
        blockStackElements.push(new LoopBlockStackElement(end, condition, targetLabel(expression)));

        StackValue conditionValue = gen(expression.getCondition());
        BranchedValue.Companion.loopJump(conditionValue, end, true, v);

        generateLoopBody(expression.getBody());

        v.goTo(condition);

        v.mark(end);

        blockStackElements.pop();

        return StackValue.onStack(Type.VOID_TYPE);
    }


    @Override
    public StackValue visitDoWhileExpression(@NotNull JetDoWhileExpression expression, StackValue receiver) {
        Label beginLoopLabel = new Label();
        v.mark(beginLoopLabel);

        Label breakLabel = new Label();
        Label continueLabel = new Label();

        blockStackElements.push(new LoopBlockStackElement(breakLabel, continueLabel, targetLabel(expression)));

        PseudoInsnsPackage.fakeAlwaysFalseIfeq(v, continueLabel);

        JetExpression body = expression.getBody();
        JetExpression condition = expression.getCondition();
        StackValue conditionValue;

        if (body instanceof JetBlockExpression) {
            // If body's a block, it can contain variable declarations which may be used in the condition of a do-while loop.
            // We handle this case separately because otherwise such variable will be out of the frame map after the block ends
            List<JetExpression> doWhileStatements = ((JetBlockExpression) body).getStatements();

            List<JetExpression> statements = new ArrayList<JetExpression>(doWhileStatements.size() + 1);
            statements.addAll(doWhileStatements);
            statements.add(condition);

            conditionValue = generateBlock(statements, false, continueLabel, null);
        }
        else {
            if (body != null) {
                gen(body, Type.VOID_TYPE);
            }
            v.mark(continueLabel);
            conditionValue = gen(condition);
        }

        BranchedValue.Companion.loopJump(conditionValue, beginLoopLabel, false, v);
        v.mark(breakLabel);

        blockStackElements.pop();
        return StackValue.none();
    }

    @Override
    public StackValue visitForExpression(@NotNull JetForExpression forExpression, StackValue receiver) {
        // Is it a "1..2" or so
        RangeCodegenUtil.BinaryCall binaryCall = RangeCodegenUtil.getRangeAsBinaryCall(forExpression);
        if (binaryCall != null) {
            ResolvedCall<?> resolvedCall = getResolvedCall(binaryCall.op, bindingContext);
            if (resolvedCall != null) {
                if (RangeCodegenUtil.isOptimizableRangeTo(resolvedCall.getResultingDescriptor())) {
                    generateForLoop(new ForInRangeLiteralLoopGenerator(forExpression, binaryCall));
                    return StackValue.none();
                }
            }
        }

        JetExpression loopRange = forExpression.getLoopRange();
        assert loopRange != null;
        JetType loopRangeType = bindingContext.getType(loopRange);
        assert loopRangeType != null;
        Type asmLoopRangeType = asmType(loopRangeType);
        if (asmLoopRangeType.getSort() == Type.ARRAY) {
            generateForLoop(new ForInArrayLoopGenerator(forExpression));
            return StackValue.none();
        }

        if (RangeCodegenUtil.isRange(loopRangeType)) {
            generateForLoop(new ForInRangeInstanceLoopGenerator(forExpression));
            return StackValue.none();
        }

        if (RangeCodegenUtil.isProgression(loopRangeType)) {
            generateForLoop(new ForInProgressionExpressionLoopGenerator(forExpression));
            return StackValue.none();
        }

        generateForLoop(new IteratorForLoopGenerator(forExpression));
        return StackValue.none();
    }

    private OwnerKind contextKind() {
        return context.getContextKind();
    }

    private void generateForLoop(AbstractForLoopGenerator generator) {
        Label loopExit = new Label();
        Label loopEntry = new Label();
        Label continueLabel = new Label();

        generator.beforeLoop();
        generator.checkEmptyLoop(loopExit);

        v.mark(loopEntry);
        generator.checkPreCondition(loopExit);

        // Some forms of for-loop can be optimized as post-condition loops.
        PseudoInsnsPackage.fakeAlwaysFalseIfeq(v, continueLabel);

        generator.beforeBody();
        blockStackElements.push(new LoopBlockStackElement(loopExit, continueLabel, targetLabel(generator.forExpression)));
        generator.body();
        blockStackElements.pop();
        v.mark(continueLabel);
        generator.afterBody(loopExit);

        v.goTo(loopEntry);

        v.mark(loopExit);
        generator.afterLoop();
    }

    private abstract class AbstractForLoopGenerator {

        // for (e : E in c) {...}
        protected final JetForExpression forExpression;
        private final Label bodyStart = new Label();
        private final Label bodyEnd = new Label();
        private final List<Runnable> leaveVariableTasks = Lists.newArrayList();

        protected final JetType elementType;
        protected final Type asmElementType;

        protected int loopParameterVar;

        private AbstractForLoopGenerator(@NotNull JetForExpression forExpression) {
            this.forExpression = forExpression;
            this.elementType = getElementType(forExpression);
            this.asmElementType = asmType(elementType);
        }

        @NotNull
        private JetType getElementType(JetForExpression forExpression) {
            JetExpression loopRange = forExpression.getLoopRange();
            assert loopRange != null;
            ResolvedCall<FunctionDescriptor> nextCall = getNotNull(bindingContext,
                                                                   LOOP_RANGE_NEXT_RESOLVED_CALL, loopRange,
                                                                   "No next() function " + DiagnosticUtils.atLocation(loopRange));
            //noinspection ConstantConditions
            return nextCall.getResultingDescriptor().getReturnType();
        }

        public void beforeLoop() {
            JetParameter loopParameter = forExpression.getLoopParameter();
            if (loopParameter != null) {
                // E e = tmp<iterator>.next()
                final VariableDescriptor parameterDescriptor = bindingContext.get(VALUE_PARAMETER, loopParameter);
                @SuppressWarnings("ConstantConditions") final Type asmTypeForParameter = asmType(parameterDescriptor.getType());
                loopParameterVar = myFrameMap.enter(parameterDescriptor, asmTypeForParameter);
                scheduleLeaveVariable(new Runnable() {
                    @Override
                    public void run() {
                        myFrameMap.leave(parameterDescriptor);
                        v.visitLocalVariable(parameterDescriptor.getName().asString(),
                                             asmTypeForParameter.getDescriptor(), null,
                                             bodyStart, bodyEnd,
                                             loopParameterVar);
                    }
                });
            }
            else {
                JetMultiDeclaration multiParameter = forExpression.getMultiParameter();
                assert multiParameter != null;

                // E tmp<e> = tmp<iterator>.next()
                loopParameterVar = createLoopTempVariable(asmElementType);
            }
        }

        public abstract void checkEmptyLoop(@NotNull Label loopExit);

        public abstract void checkPreCondition(@NotNull Label loopExit);

        public void beforeBody() {
            v.mark(bodyStart);

            assignToLoopParameter();

            if (forExpression.getLoopParameter() == null) {
                JetMultiDeclaration multiParameter = forExpression.getMultiParameter();
                assert multiParameter != null;

                generateMultiVariables(multiParameter.getEntries());
            }
        }

        private void generateMultiVariables(List<JetMultiDeclarationEntry> entries) {
            for (JetMultiDeclarationEntry variableDeclaration : entries) {
                final VariableDescriptor componentDescriptor = bindingContext.get(VARIABLE, variableDeclaration);

                @SuppressWarnings("ConstantConditions") final Type componentAsmType = asmType(componentDescriptor.getReturnType());
                final int componentVarIndex = myFrameMap.enter(componentDescriptor, componentAsmType);
                scheduleLeaveVariable(new Runnable() {
                    @Override
                    public void run() {
                        myFrameMap.leave(componentDescriptor);
                        v.visitLocalVariable(componentDescriptor.getName().asString(),
                                             componentAsmType.getDescriptor(), null,
                                             bodyStart, bodyEnd,
                                             componentVarIndex);
                    }
                });

                ResolvedCall<FunctionDescriptor> resolvedCall = bindingContext.get(COMPONENT_RESOLVED_CALL, variableDeclaration);
                assert resolvedCall != null : "Resolved call is null for " + variableDeclaration.getText();
                Call call = makeFakeCall(new TransientReceiver(elementType));

                StackValue value = invokeFunction(call, resolvedCall, StackValue.local(loopParameterVar, asmElementType));
                StackValue.local(componentVarIndex, componentAsmType).store(value, v);
            }
        }

        protected abstract void assignToLoopParameter();

        protected abstract void increment(@NotNull Label loopExit);

        public void body() {
            generateLoopBody(forExpression.getBody());
        }

        private void scheduleLeaveVariable(Runnable runnable) {
            leaveVariableTasks.add(runnable);
        }

        protected int createLoopTempVariable(final Type type) {
            int varIndex = myFrameMap.enterTemp(type);
            scheduleLeaveVariable(new Runnable() {
                @Override
                public void run() {
                    myFrameMap.leaveTemp(type);
                }
            });
            return varIndex;
        }

        public void afterBody(@NotNull Label loopExit) {
            markStartLineNumber(forExpression);

            increment(loopExit);

            v.mark(bodyEnd);
        }

        public void afterLoop() {
            for (Runnable task : Lists.reverse(leaveVariableTasks)) {
                task.run();
            }
        }

        // This method consumes range/progression from stack
        // The result is stored to local variable
        protected void generateRangeOrProgressionProperty(Type loopRangeType, String getterName, Type elementType, int varToStore) {
            Type boxedType = boxType(elementType);
            v.invokevirtual(loopRangeType.getInternalName(), getterName, "()" + boxedType.getDescriptor(), false);
            StackValue.coerce(boxedType, elementType, v);
            v.store(varToStore, elementType);
        }
    }

    private void generateLoopBody(@Nullable JetExpression body) {
        if (body != null) {
            gen(body, Type.VOID_TYPE);
        }
    }

    private class IteratorForLoopGenerator extends AbstractForLoopGenerator {

        private int iteratorVarIndex;
        private final ResolvedCall<FunctionDescriptor> iteratorCall;
        private final ResolvedCall<FunctionDescriptor> nextCall;
        private final Type asmTypeForIterator;

        private IteratorForLoopGenerator(@NotNull JetForExpression forExpression) {
            super(forExpression);

            JetExpression loopRange = forExpression.getLoopRange();
            assert loopRange != null;
            this.iteratorCall = getNotNull(bindingContext,
                                           LOOP_RANGE_ITERATOR_RESOLVED_CALL, loopRange,
                                           "No .iterator() function " + DiagnosticUtils.atLocation(loopRange));

            JetType iteratorType = iteratorCall.getResultingDescriptor().getReturnType();
            assert iteratorType != null;
            this.asmTypeForIterator = asmType(iteratorType);

            this.nextCall = getNotNull(bindingContext,
                                       LOOP_RANGE_NEXT_RESOLVED_CALL, loopRange,
                                       "No next() function " + DiagnosticUtils.atLocation(loopRange));
        }

        @Override
        public void beforeLoop() {
            super.beforeLoop();

            // Iterator<E> tmp<iterator> = c.iterator()

            iteratorVarIndex = createLoopTempVariable(asmTypeForIterator);

            StackValue.local(iteratorVarIndex, asmTypeForIterator).store(invokeFunction(iteratorCall, StackValue.none()), v);
        }

        @Override
        public void checkEmptyLoop(@NotNull Label loopExit) {
        }

        @Override
        public void checkPreCondition(@NotNull Label loopExit) {
            // tmp<iterator>.hasNext()

            JetExpression loopRange = forExpression.getLoopRange();
            @SuppressWarnings("ConstantConditions") ResolvedCall<FunctionDescriptor> hasNextCall = getNotNull(bindingContext,
                                                                      LOOP_RANGE_HAS_NEXT_RESOLVED_CALL, loopRange,
                                                                      "No hasNext() function " + DiagnosticUtils.atLocation(loopRange));
            @SuppressWarnings("ConstantConditions") Call fakeCall = makeFakeCall(new TransientReceiver(iteratorCall.getResultingDescriptor().getReturnType()));
            StackValue result = invokeFunction(fakeCall, hasNextCall, StackValue.local(iteratorVarIndex, asmTypeForIterator));
            result.put(result.type, v);

            FunctionDescriptor hasNext = hasNextCall.getResultingDescriptor();
            JetType type = hasNext.getReturnType();
            assert type != null && JetTypeChecker.DEFAULT.isSubtypeOf(type, getBuiltIns(hasNext).getBooleanType());

            Type asmType = asmType(type);
            StackValue.coerce(asmType, Type.BOOLEAN_TYPE, v);
            v.ifeq(loopExit);
        }

        @Override
        protected void assignToLoopParameter() {
            @SuppressWarnings("ConstantConditions") Call fakeCall =
                    makeFakeCall(new TransientReceiver(iteratorCall.getResultingDescriptor().getReturnType()));
            StackValue value = invokeFunction(fakeCall, nextCall, StackValue.local(iteratorVarIndex, asmTypeForIterator));
            //noinspection ConstantConditions
            StackValue.local(loopParameterVar, asmType(nextCall.getResultingDescriptor().getReturnType())).store(value, v);
        }

        @Override
        protected void increment(@NotNull Label loopExit) {
        }
    }

    private class ForInArrayLoopGenerator extends AbstractForLoopGenerator {
        private int indexVar;
        private int arrayVar;
        private final JetType loopRangeType;

        private ForInArrayLoopGenerator(@NotNull JetForExpression forExpression) {
            super(forExpression);
            loopRangeType = bindingContext.getType(forExpression.getLoopRange());
        }

        @Override
        public void beforeLoop() {
            super.beforeLoop();

            indexVar = createLoopTempVariable(Type.INT_TYPE);

            JetExpression loopRange = forExpression.getLoopRange();
            StackValue value = gen(loopRange);
            Type asmLoopRangeType = asmType(loopRangeType);
            if (value instanceof StackValue.Local && value.type.equals(asmLoopRangeType)) {
                arrayVar = ((StackValue.Local) value).index; // no need to copy local variable into another variable
            }
            else {
                arrayVar = createLoopTempVariable(OBJECT_TYPE);
                value.put(asmLoopRangeType, v);
                v.store(arrayVar, OBJECT_TYPE);
            }

            v.iconst(0);
            v.store(indexVar, Type.INT_TYPE);
        }

        @Override
        public void checkEmptyLoop(@NotNull Label loopExit) {
        }

        @Override
        public void checkPreCondition(@NotNull Label loopExit) {
            v.load(indexVar, Type.INT_TYPE);
            v.load(arrayVar, OBJECT_TYPE);
            v.arraylength();
            v.ificmpge(loopExit);
        }

        @Override
        protected void assignToLoopParameter() {
            Type arrayElParamType;
            if (KotlinBuiltIns.isArray(loopRangeType)) {
                arrayElParamType = boxType(asmElementType);
            }
            else {
                arrayElParamType = asmElementType;
            }

            v.load(arrayVar, OBJECT_TYPE);
            v.load(indexVar, Type.INT_TYPE);
            v.aload(arrayElParamType);
            StackValue.onStack(arrayElParamType).put(asmElementType, v);
            v.store(loopParameterVar, asmElementType);
        }

        @Override
        protected void increment(@NotNull Label loopExit) {
            v.iinc(indexVar, 1);
        }
    }

    private abstract class AbstractForInProgressionOrRangeLoopGenerator extends AbstractForLoopGenerator {
        protected int endVar;

        // For integer progressions instead of comparing loopParameterVar with endVar at the beginning of an iteration we check whether
        // loopParameterVar == finalVar at the end of the iteration (and also if there should be any iterations at all, before the loop)
        protected final boolean isIntegerProgression;

        private AbstractForInProgressionOrRangeLoopGenerator(@NotNull JetForExpression forExpression) {
            super(forExpression);

            switch (asmElementType.getSort()) {
                case Type.INT:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.LONG:
                    isIntegerProgression = true;
                    break;

                case Type.DOUBLE:
                case Type.FLOAT:
                    isIntegerProgression = false;
                    break;

                default:
                    throw new IllegalStateException("Unexpected range element type: " + asmElementType);
            }
        }

        @Override
        public void beforeLoop() {
            super.beforeLoop();

            endVar = createLoopTempVariable(asmElementType);
        }

        // Index of the local variable holding the actual last value of the loop parameter.
        // For ranges it equals end, for progressions it's a function of start, end and increment
        protected abstract int getFinalVar();

        protected void checkPostCondition(@NotNull Label loopExit) {
            int finalVar = getFinalVar();
            assert isIntegerProgression && finalVar != -1 :
                    "Post-condition should be checked only in case of integer progressions, finalVar = " + finalVar;

            v.load(loopParameterVar, asmElementType);
            v.load(finalVar, asmElementType);
            if (asmElementType.getSort() == Type.LONG) {
                v.lcmp();
                v.ifeq(loopExit);
            }
            else {
                v.ificmpeq(loopExit);
            }
        }
    }

    private abstract class AbstractForInRangeLoopGenerator extends AbstractForInProgressionOrRangeLoopGenerator {
        private AbstractForInRangeLoopGenerator(@NotNull JetForExpression forExpression) {
            super(forExpression);
        }

        @Override
        public void beforeLoop() {
            super.beforeLoop();

            storeRangeStartAndEnd();
        }

        protected abstract void storeRangeStartAndEnd();

        @Override
        protected int getFinalVar() {
            return endVar;
        }

        @Override
        public void checkPreCondition(@NotNull Label loopExit) {
            if (isIntegerProgression) return;

            v.load(loopParameterVar, asmElementType);
            v.load(endVar, asmElementType);

            v.cmpg(asmElementType);
            v.ifgt(loopExit);
        }

        @Override
        public void checkEmptyLoop(@NotNull Label loopExit) {
            if (!isIntegerProgression) return;

            v.load(loopParameterVar, asmElementType);
            v.load(endVar, asmElementType);
            if (asmElementType.getSort() == Type.LONG) {
                v.lcmp();
                v.ifgt(loopExit);
            }
            else {
                v.ificmpgt(loopExit);
            }
        }

        @Override
        protected void assignToLoopParameter() {
        }

        @Override
        protected void increment(@NotNull Label loopExit) {
            if (isIntegerProgression) {
                checkPostCondition(loopExit);
            }

            if (asmElementType == Type.INT_TYPE) {
                v.iinc(loopParameterVar, 1);
            }
            else {
                v.load(loopParameterVar, asmElementType);
                genIncrement(asmElementType, 1, v);
                v.store(loopParameterVar, asmElementType);
            }
        }
    }

    private class ForInRangeLiteralLoopGenerator extends AbstractForInRangeLoopGenerator {
        private final RangeCodegenUtil.BinaryCall rangeCall;

        private ForInRangeLiteralLoopGenerator(
                @NotNull JetForExpression forExpression,
                @NotNull RangeCodegenUtil.BinaryCall rangeCall
        ) {
            super(forExpression);
            this.rangeCall = rangeCall;
        }

        @Override
        protected void storeRangeStartAndEnd() {
            gen(rangeCall.left, asmElementType);
            v.store(loopParameterVar, asmElementType);

            gen(rangeCall.right, asmElementType);
            v.store(endVar, asmElementType);
        }
    }

    private class ForInRangeInstanceLoopGenerator extends AbstractForInRangeLoopGenerator {
        private ForInRangeInstanceLoopGenerator(@NotNull JetForExpression forExpression) {
            super(forExpression);
        }

        @Override
        protected void storeRangeStartAndEnd() {
            JetType loopRangeType = bindingContext.getType(forExpression.getLoopRange());
            assert loopRangeType != null;
            Type asmLoopRangeType = asmType(loopRangeType);
            gen(forExpression.getLoopRange(), asmLoopRangeType);
            v.dup();

            generateRangeOrProgressionProperty(asmLoopRangeType, "getStart", asmElementType, loopParameterVar);
            generateRangeOrProgressionProperty(asmLoopRangeType, "getEnd", asmElementType, endVar);
        }
    }

    private class ForInProgressionExpressionLoopGenerator extends AbstractForInProgressionOrRangeLoopGenerator {
        private int incrementVar;
        private Type incrementType;

        private int finalVar;

        private ForInProgressionExpressionLoopGenerator(@NotNull JetForExpression forExpression) {
            super(forExpression);
        }

        @Override
        protected int getFinalVar() {
            return finalVar;
        }

        @Override
        public void beforeLoop() {
            super.beforeLoop();

            incrementVar = createLoopTempVariable(asmElementType);

            JetType loopRangeType = bindingContext.getType(forExpression.getLoopRange());
            assert loopRangeType != null;
            Type asmLoopRangeType = asmType(loopRangeType);

            Collection<VariableDescriptor> incrementProp = loopRangeType.getMemberScope().getProperties(Name.identifier("increment"));
            assert incrementProp.size() == 1 : loopRangeType + " " + incrementProp.size();
            incrementType = asmType(incrementProp.iterator().next().getType());

            gen(forExpression.getLoopRange(), asmLoopRangeType);
            v.dup();
            v.dup();

            generateRangeOrProgressionProperty(asmLoopRangeType, "getStart", asmElementType, loopParameterVar);
            generateRangeOrProgressionProperty(asmLoopRangeType, "getEnd", asmElementType, endVar);
            generateRangeOrProgressionProperty(asmLoopRangeType, "getIncrement", incrementType, incrementVar);

            storeFinalVar();
        }

        private void storeFinalVar() {
            if (!isIntegerProgression) {
                finalVar = -1;
                return;
            }

            v.load(loopParameterVar, asmElementType);
            v.load(endVar, asmElementType);
            v.load(incrementVar, incrementType);

            Type methodParamType = asmElementType.getSort() == Type.LONG ? Type.LONG_TYPE : Type.INT_TYPE;
            v.invokestatic("kotlin/internal/InternalPackage", "getProgressionFinalElement",
                           Type.getMethodDescriptor(methodParamType, methodParamType, methodParamType, methodParamType), false);

            finalVar = createLoopTempVariable(asmElementType);
            v.store(finalVar, asmElementType);
        }

        @Override
        public void checkPreCondition(@NotNull Label loopExit) {
            if (isIntegerProgression) return;

            v.load(loopParameterVar, asmElementType);
            v.load(endVar, asmElementType);
            v.load(incrementVar, incrementType);

            Label negativeIncrement = new Label();
            Label afterIf = new Label();

            if (incrementType.getSort() == Type.DOUBLE) {
                v.dconst(0.0);
            }
            else {
                v.fconst(0.0f);
            }
            v.cmpl(incrementType);
            v.ifle(negativeIncrement); // if increment < 0, jump

            // increment > 0
            v.cmpg(asmElementType); // if loop parameter is NaN, exit from loop, as well
            v.ifgt(loopExit);
            v.goTo(afterIf);

            // increment < 0
            v.mark(negativeIncrement);
            v.cmpl(asmElementType); // if loop parameter is NaN, exit from loop, as well
            v.iflt(loopExit);
            v.mark(afterIf);
        }

        @Override
        public void checkEmptyLoop(@NotNull Label loopExit) {
            if (!isIntegerProgression) return;

            v.load(loopParameterVar, asmElementType);
            v.load(endVar, asmElementType);
            v.load(incrementVar, incrementType);

            Label negativeIncrement = new Label();
            Label afterIf = new Label();

            if (asmElementType.getSort() == Type.LONG) {
                v.lconst(0L);
                v.lcmp();
                v.ifle(negativeIncrement); // if increment < 0, jump

                // increment > 0
                v.lcmp();
                v.ifgt(loopExit);
                v.goTo(afterIf);

                // increment < 0
                v.mark(negativeIncrement);
                v.lcmp();
                v.iflt(loopExit);
                v.mark(afterIf);
            }
            else {
                v.ifle(negativeIncrement); // if increment < 0, jump

                // increment > 0
                v.ificmpgt(loopExit);
                v.goTo(afterIf);

                // increment < 0
                v.mark(negativeIncrement);
                v.ificmplt(loopExit);
                v.mark(afterIf);
            }
        }

        @Override
        protected void assignToLoopParameter() {
        }

        @Override
        protected void increment(@NotNull Label loopExit) {
            if (isIntegerProgression) {
                checkPostCondition(loopExit);
            }

            v.load(loopParameterVar, asmElementType);
            v.load(incrementVar, asmElementType);
            v.add(asmElementType);

            if (asmElementType == Type.BYTE_TYPE || asmElementType == Type.SHORT_TYPE || asmElementType == Type.CHAR_TYPE) {
                StackValue.coerce(Type.INT_TYPE, asmElementType, v);
            }

            v.store(loopParameterVar, asmElementType);
        }
    }


    @Override
    public StackValue visitBreakExpression(@NotNull JetBreakExpression expression, StackValue receiver) {
        return generateBreakOrContinueExpression(expression, true, new Label());
    }

    @Override
    public StackValue visitContinueExpression(@NotNull JetContinueExpression expression, StackValue receiver) {
        return generateBreakOrContinueExpression(expression, false, new Label());
    }

    @NotNull
    private StackValue generateBreakOrContinueExpression(
            @NotNull JetExpressionWithLabel expression,
            boolean isBreak,
            @NotNull Label afterBreakContinueLabel
    ) {
        assert expression instanceof JetContinueExpression || expression instanceof JetBreakExpression;

        if (!blockStackElements.isEmpty()) {
            BlockStackElement stackElement = blockStackElements.peek();

            if (stackElement instanceof FinallyBlockStackElement) {
                FinallyBlockStackElement finallyBlockStackElement = (FinallyBlockStackElement) stackElement;
                //noinspection ConstantConditions
                genFinallyBlockOrGoto(finallyBlockStackElement, null, afterBreakContinueLabel);
            }
            else if (stackElement instanceof LoopBlockStackElement) {
                LoopBlockStackElement loopBlockStackElement = (LoopBlockStackElement) stackElement;
                JetSimpleNameExpression labelElement = expression.getTargetLabel();
                //noinspection ConstantConditions
                if (labelElement == null ||
                    loopBlockStackElement.targetLabel != null &&
                    labelElement.getReferencedName().equals(loopBlockStackElement.targetLabel.getReferencedName())) {
                    Label label = isBreak ? loopBlockStackElement.breakLabel : loopBlockStackElement.continueLabel;
                    PseudoInsnsPackage.fixStackAndJump(v, label);
                    v.mark(afterBreakContinueLabel);
                    return StackValue.none();
                }
            }
            else {
                throw new UnsupportedOperationException("Wrong BlockStackElement in processing stack");
            }

            blockStackElements.pop();
            StackValue result = generateBreakOrContinueExpression(expression, isBreak, afterBreakContinueLabel);
            blockStackElements.push(stackElement);
            return result;
        }

        throw new UnsupportedOperationException("Target label for break/continue not found");
    }

    private StackValue generateSingleBranchIf(
            final StackValue condition,
            final JetIfExpression ifExpression,
            final JetExpression expression,
            final boolean inverse,
            final boolean isStatement
    ) {
        Type targetType = isStatement ? Type.VOID_TYPE : expressionType(ifExpression);
        return StackValue.operation(targetType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                Label elseLabel = new Label();
                BranchedValue.Companion.condJump(condition, elseLabel, inverse, v);

                if (isStatement) {
                    gen(expression, Type.VOID_TYPE);
                    v.mark(elseLabel);
                }
                else {
                    Type targetType = expressionType(ifExpression);
                    gen(expression, targetType);
                    Label end = new Label();
                    v.goTo(end);

                    v.mark(elseLabel);
                    StackValue.putUnitInstance(v);

                    markStartLineNumber(ifExpression);
                    v.mark(end);
                }
                return null;
            }
        });
    }

    @Override
    public StackValue visitConstantExpression(@NotNull JetConstantExpression expression, StackValue receiver) {
        ConstantValue<?> compileTimeValue = getCompileTimeConstant(expression, bindingContext);
        assert compileTimeValue != null;
        return StackValue.constant(compileTimeValue.getValue(), expressionType(expression));
    }

    @Nullable
    public static ConstantValue<?> getCompileTimeConstant(@NotNull JetExpression expression, @NotNull BindingContext bindingContext) {
        CompileTimeConstant<?> compileTimeValue = ConstantExpressionEvaluator.getConstant(expression, bindingContext);
        if (compileTimeValue == null) {
            return null;
        }
        JetType expectedType = bindingContext.getType(expression);
        return compileTimeValue.toConstantValue(expectedType);
    }

    @Override
    public StackValue visitStringTemplateExpression(@NotNull JetStringTemplateExpression expression, StackValue receiver) {
        StringBuilder constantValue = new StringBuilder("");
        final JetStringTemplateEntry[] entries = expression.getEntries();

        if (entries.length == 1 && entries[0] instanceof JetStringTemplateEntryWithExpression) {
            JetExpression expr = entries[0].getExpression();
            return genToString(gen(expr), expressionType(expr));
        }

        for (JetStringTemplateEntry entry : entries) {
            if (entry instanceof JetLiteralStringTemplateEntry) {
                constantValue.append(entry.getText());
            }
            else if (entry instanceof JetEscapeStringTemplateEntry) {
                constantValue.append(((JetEscapeStringTemplateEntry) entry).getUnescapedValue());
            }
            else {
                constantValue = null;
                break;
            }
        }
        if (constantValue != null) {
            Type type = expressionType(expression);
            return StackValue.constant(constantValue.toString(), type);
        }
        else {
            return StackValue.operation(JAVA_STRING_TYPE, new Function1<InstructionAdapter, Unit>() {
                @Override
                public Unit invoke(InstructionAdapter v) {
                    genStringBuilderConstructor(v);
                    for (JetStringTemplateEntry entry : entries) {
                        if (entry instanceof JetStringTemplateEntryWithExpression) {
                            invokeAppend(entry.getExpression());
                        }
                        else {
                            String text = entry instanceof JetEscapeStringTemplateEntry
                                          ? ((JetEscapeStringTemplateEntry) entry).getUnescapedValue()
                                          : entry.getText();
                            v.aconst(text);
                            genInvokeAppendMethod(v, JAVA_STRING_TYPE);
                        }
                    }
                    v.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                    return Unit.INSTANCE$;
                }
            });
        }
    }

    @Override
    public StackValue visitBlockExpression(@NotNull JetBlockExpression expression, StackValue receiver) {
        return generateBlock(expression, false);
    }

    @Override
    public StackValue visitNamedFunction(@NotNull JetNamedFunction function, StackValue data) {
        return visitNamedFunction(function, data, false);
    }

    public StackValue visitNamedFunction(@NotNull JetNamedFunction function, StackValue data, boolean isStatement) {
        assert data == StackValue.none();

        if (JetPsiUtil.isScriptDeclaration(function)) {
            return StackValue.none();
        }

        StackValue closure = genClosure(function, null, KotlinSyntheticClass.Kind.LOCAL_FUNCTION);
        if (isStatement) {
            DeclarationDescriptor descriptor = bindingContext.get(DECLARATION_TO_DESCRIPTOR, function);
            int index = lookupLocalIndex(descriptor);
            closure.put(OBJECT_TYPE, v);
            v.store(index, OBJECT_TYPE);
            return StackValue.none();
        }
        else {
            return closure;
        }
    }

    @Override
    public StackValue visitFunctionLiteralExpression(@NotNull JetFunctionLiteralExpression expression, StackValue receiver) {
        if (Boolean.TRUE.equals(bindingContext.get(BLOCK, expression))) {
            return gen(expression.getFunctionLiteral().getBodyExpression());
        }
        else {
            return genClosure(expression.getFunctionLiteral(), null, KotlinSyntheticClass.Kind.ANONYMOUS_FUNCTION);
        }
    }

    @NotNull
    private StackValue genClosure(
            JetDeclarationWithBody declaration,
            @Nullable SamType samType,
            @NotNull KotlinSyntheticClass.Kind kind
    ) {
        FunctionDescriptor descriptor = bindingContext.get(FUNCTION, declaration);
        assert descriptor != null : "Function is not resolved to descriptor: " + declaration.getText();

        return genClosure(
                declaration, descriptor, new FunctionGenerationStrategy.FunctionDefault(state, descriptor, declaration), samType, kind, null
        );
    }

    @NotNull
    private StackValue genClosure(
            @NotNull JetElement declaration,
            @NotNull FunctionDescriptor descriptor,
            @NotNull FunctionGenerationStrategy strategy,
            @Nullable SamType samType,
            @NotNull KotlinSyntheticClass.Kind kind,
            @Nullable FunctionDescriptor functionReferenceTarget
    ) {
        ClassBuilder cv = state.getFactory().newVisitor(
                OtherOrigin(declaration, descriptor),
                asmTypeForAnonymousClass(bindingContext, descriptor),
                declaration.getContainingFile()
        );

        ClosureCodegen closureCodegen = new ClosureCodegen(
                state, declaration, samType, context.intoClosure(descriptor, this, typeMapper), kind,
                functionReferenceTarget, strategy, parentCodegen, cv
        );

        closureCodegen.generate();

        if (closureCodegen.getReifiedTypeParametersUsages().wereUsedReifiedParameters()) {
            ReifiedTypeInliner.putNeedClassReificationMarker(v);
            propagateChildReifiedTypeParametersUsages(closureCodegen.getReifiedTypeParametersUsages());
        }

        return closureCodegen.putInstanceOnStack(this);
    }

    @Override
    public StackValue visitObjectLiteralExpression(@NotNull JetObjectLiteralExpression expression, StackValue receiver) {
        final ObjectLiteralResult objectLiteralResult = generateObjectLiteral(expression);
        final ClassDescriptor classDescriptor = objectLiteralResult.classDescriptor;
        final Type type = typeMapper.mapType(classDescriptor);

        return StackValue.operation(type, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                if (objectLiteralResult.wereReifiedMarkers) {
                    ReifiedTypeInliner.putNeedClassReificationMarker(v);
                }
                v.anew(type);
                v.dup();

                pushClosureOnStack(classDescriptor, true, defaultCallGenerator);

                ConstructorDescriptor primaryConstructor = classDescriptor.getUnsubstitutedPrimaryConstructor();
                assert primaryConstructor != null : "There should be primary constructor for object literal";
                ResolvedCall<ConstructorDescriptor> superCall = getDelegationConstructorCall(bindingContext, primaryConstructor);
                if (superCall != null) {
                    // For an anonymous object, we should also generate all non-default arguments that it captures for its super call
                    ConstructorDescriptor superConstructor = superCall.getResultingDescriptor();
                    ConstructorDescriptor constructorToCall = SamCodegenUtil.resolveSamAdapter(superConstructor);
                    List<ValueParameterDescriptor> superValueParameters = superConstructor.getValueParameters();
                    int params = superValueParameters.size();
                    List<Type> superMappedTypes = typeMapper.mapToCallableMethod(constructorToCall).getValueParameterTypes();
                    assert superMappedTypes.size() >= params : String
                            .format("Incorrect number of mapped parameters vs arguments: %d < %d for %s",
                                    superMappedTypes.size(), params, classDescriptor);

                    List<ResolvedValueArgument> valueArguments = new ArrayList<ResolvedValueArgument>(params);
                    List<ValueParameterDescriptor> valueParameters = new ArrayList<ValueParameterDescriptor>(params);
                    List<Type> mappedTypes = new ArrayList<Type>(params);
                    for (ValueParameterDescriptor parameter : superValueParameters) {
                        ResolvedValueArgument argument = superCall.getValueArguments().get(parameter);
                        if (!(argument instanceof DefaultValueArgument)) {
                            valueArguments.add(argument);
                            valueParameters.add(parameter);
                            mappedTypes.add(superMappedTypes.get(parameter.getIndex()));
                        }
                    }
                    ArgumentGenerator argumentGenerator =
                            new CallBasedArgumentGenerator(ExpressionCodegen.this, defaultCallGenerator, valueParameters, mappedTypes);

                    argumentGenerator.generate(valueArguments);
                }

                Collection<ConstructorDescriptor> constructors = classDescriptor.getConstructors();
                assert constructors.size() == 1 : "Unexpected number of constructors for class: " + classDescriptor + " " + constructors;
                ConstructorDescriptor constructorDescriptor = KotlinPackage.single(constructors);

                JvmMethodSignature constructor = typeMapper.mapSignature(SamCodegenUtil.resolveSamAdapter(constructorDescriptor));
                v.invokespecial(type.getInternalName(), "<init>", constructor.getAsmMethod().getDescriptor(), false);
                return Unit.INSTANCE$;
            }
        });
    }

    public void pushClosureOnStack(@NotNull ClassDescriptor classDescriptor, boolean putThis, @NotNull CallGenerator callGenerator) {
        CalculatedClosure closure = bindingContext.get(CLOSURE, classDescriptor);
        if (closure == null) return;

        int paramIndex = 0;

        if (putThis) {
            ClassDescriptor captureThis = closure.getCaptureThis();
            if (captureThis != null) {
                StackValue thisOrOuter = generateThisOrOuter(captureThis, false);
                assert !isPrimitive(thisOrOuter.type) : "This or outer should be non primitive: " + thisOrOuter.type;
                callGenerator.putCapturedValueOnStack(thisOrOuter, thisOrOuter.type, paramIndex++);
            }
        }

        JetType captureReceiver = closure.getCaptureReceiverType();
        if (captureReceiver != null) {
            Type asmType = typeMapper.mapType(captureReceiver);
            StackValue.Local capturedReceiver = StackValue.local(AsmUtil.getReceiverIndex(context, context.getContextDescriptor()), asmType);
            callGenerator.putCapturedValueOnStack(capturedReceiver, capturedReceiver.type, paramIndex++);
        }

        for (Map.Entry<DeclarationDescriptor, EnclosedValueDescriptor> entry : closure.getCaptureVariables().entrySet()) {
            Type sharedVarType = typeMapper.getSharedVarType(entry.getKey());
            if (sharedVarType == null) {
                sharedVarType = typeMapper.mapType((VariableDescriptor) entry.getKey());
            }
            StackValue capturedVar = lookupOuterValue(entry.getValue());
            callGenerator.putCapturedValueOnStack(capturedVar, sharedVarType, paramIndex++);
        }


        ClassDescriptor superClass = DescriptorUtilPackage.getSuperClassNotAny(classDescriptor);
        if (superClass != null) {
            pushClosureOnStack(
                    superClass,
                    putThis && closure.getCaptureThis() == null,
                    callGenerator
            );
        }
    }

    /* package */ StackValue generateBlock(@NotNull JetBlockExpression expression, boolean isStatement) {
        if (expression.getParent() instanceof JetNamedFunction) {
            // For functions end of block should be end of function label
            return generateBlock(expression.getStatements(), isStatement, null, context.getMethodEndLabel());
        }
        return generateBlock(expression.getStatements(), isStatement, null, null);
    }

    @NotNull
    public StackValue lookupOuterValue(EnclosedValueDescriptor d) {
        DeclarationDescriptor descriptor = d.getDescriptor();
        for (LocalLookup.LocalLookupCase aCase : LocalLookup.LocalLookupCase.values()) {
            if (aCase.isCase(descriptor)) {
                return aCase.outerValue(d, this);
            }
        }
        throw new IllegalStateException("Can't get outer value in " + this + " for " + d);
    }

    private StackValue generateBlock(
            List<JetExpression> statements,
            boolean isStatement,
            Label labelBeforeLastExpression,
            @Nullable final Label labelBlockEnd
    ) {
        final Label blockEnd = labelBlockEnd != null ? labelBlockEnd : new Label();

        final List<Function<StackValue, Void>> leaveTasks = Lists.newArrayList();

        StackValue answer = StackValue.none();

        for (Iterator<JetExpression> iterator = statements.iterator(); iterator.hasNext(); ) {
            JetExpression possiblyLabeledStatement = iterator.next();

            JetElement statement = JetPsiUtil.safeDeparenthesize(possiblyLabeledStatement, true);

            if (statement instanceof JetNamedDeclaration) {
                JetNamedDeclaration declaration = (JetNamedDeclaration) statement;
                if (JetPsiUtil.isScriptDeclaration(declaration)) {
                    continue;
                }
            }

            putDescriptorIntoFrameMap(statement);

            boolean isExpression = !iterator.hasNext() && !isStatement;
            if (isExpression && labelBeforeLastExpression != null) {
                v.mark(labelBeforeLastExpression);
            }

            StackValue result = isExpression ? gen(possiblyLabeledStatement) : genStatement(possiblyLabeledStatement);

            if (!iterator.hasNext()) {
                answer = result;
            }
            else {
                result.put(Type.VOID_TYPE, v);
            }

            addLeaveTaskToRemoveDescriptorFromFrameMap(statement, blockEnd, leaveTasks);
        }

        return new StackValueWithLeaveTask(answer, new Function1<StackValue, Unit>() {
            @Override
            public Unit invoke(StackValue value) {
                if (labelBlockEnd == null) {
                    v.mark(blockEnd);
                }
                for (Function<StackValue, Void> task : Lists.reverse(leaveTasks)) {
                    task.fun(value);
                }
                return Unit.INSTANCE$;
            }
        });
    }

    @NotNull
    private Type getVariableType(@NotNull VariableDescriptor variableDescriptor) {
        Type sharedVarType = typeMapper.getSharedVarType(variableDescriptor);
        return sharedVarType != null ? sharedVarType : asmType(variableDescriptor.getType());
    }

    private static boolean isSharedVarType(@NotNull Type type) {
        return type.getSort() == Type.OBJECT && type.getInternalName().startsWith(REF_TYPE_PREFIX);
    }


    private void putDescriptorIntoFrameMap(@NotNull JetElement statement) {
        if (statement instanceof JetMultiDeclaration) {
            JetMultiDeclaration multiDeclaration = (JetMultiDeclaration) statement;
            for (JetMultiDeclarationEntry entry : multiDeclaration.getEntries()) {
                putLocalVariableIntoFrameMap(entry);
            }
        }

        if (statement instanceof JetVariableDeclaration) {
            putLocalVariableIntoFrameMap((JetVariableDeclaration) statement);
        }

        if (statement instanceof JetNamedFunction) {
            DeclarationDescriptor descriptor = bindingContext.get(DECLARATION_TO_DESCRIPTOR, statement);
            assert descriptor instanceof FunctionDescriptor : "Couldn't find function declaration in binding context " + statement.getText();
            Type type = asmTypeForAnonymousClass(bindingContext, (FunctionDescriptor) descriptor);
            myFrameMap.enter(descriptor, type);
        }
    }

    private void putLocalVariableIntoFrameMap(@NotNull JetVariableDeclaration statement) {
        VariableDescriptor variableDescriptor = bindingContext.get(VARIABLE, statement);
        assert variableDescriptor != null : "Couldn't find variable declaration in binding context " + statement.getText();

        Type type = getVariableType(variableDescriptor);
        int index = myFrameMap.enter(variableDescriptor, type);

        if (isSharedVarType(type)) {
            v.anew(type);
            v.dup();
            v.invokespecial(type.getInternalName(), "<init>", "()V", false);
            v.store(index, OBJECT_TYPE);
        }
    }

    private void addLeaveTaskToRemoveDescriptorFromFrameMap(
            @NotNull JetElement statement,
            @NotNull Label blockEnd,
            @NotNull List<Function<StackValue, Void>> leaveTasks
    ) {
        if (statement instanceof JetMultiDeclaration) {
            JetMultiDeclaration multiDeclaration = (JetMultiDeclaration) statement;
            for (JetMultiDeclarationEntry entry : multiDeclaration.getEntries()) {
                addLeaveTaskToRemoveLocalVariableFromFrameMap(entry, blockEnd, leaveTasks);
            }
        }

        if (statement instanceof JetVariableDeclaration) {
            addLeaveTaskToRemoveLocalVariableFromFrameMap((JetVariableDeclaration) statement, blockEnd, leaveTasks);
        }

        if (statement instanceof JetNamedFunction) {
            addLeaveTaskToRemoveNamedFunctionFromFrameMap((JetNamedFunction) statement, blockEnd, leaveTasks);
        }
    }

    private void addLeaveTaskToRemoveLocalVariableFromFrameMap(
            @NotNull JetVariableDeclaration statement,
            final Label blockEnd,
            @NotNull List<Function<StackValue, Void>> leaveTasks
    ) {
        final VariableDescriptor variableDescriptor = bindingContext.get(VARIABLE, statement);
        assert variableDescriptor != null;

        final Type type = getVariableType(variableDescriptor);

        final Label scopeStart = new Label();
        v.mark(scopeStart);

        leaveTasks.add(new Function<StackValue, Void>() {
            @Override
            public Void fun(StackValue answer) {
                int index = myFrameMap.leave(variableDescriptor);

                if (isSharedVarType(type)) {
                    v.aconst(null);
                    v.store(index, OBJECT_TYPE);
                }
                v.visitLocalVariable(variableDescriptor.getName().asString(), type.getDescriptor(), null, scopeStart, blockEnd, index);
                return null;
            }
        });
    }

    private void addLeaveTaskToRemoveNamedFunctionFromFrameMap(
            @NotNull final JetNamedFunction statement,
            final Label blockEnd,
            @NotNull List<Function<StackValue, Void>> leaveTasks
    ) {
        final FunctionDescriptor functionDescriptor = (FunctionDescriptor) bindingContext.get(DECLARATION_TO_DESCRIPTOR, statement);
        assert functionDescriptor != null;

        final Type type = asmTypeForAnonymousClass(bindingContext, functionDescriptor);

        final Label scopeStart = new Label();
        v.mark(scopeStart);

        leaveTasks.add(new Function<StackValue, Void>() {
            @Override
            public Void fun(StackValue answer) {
                int index = myFrameMap.leave(functionDescriptor);

                assert !functionDescriptor.getName().isSpecial() : "Local variable should be generated only for function with name: " + statement.getText();
                v.visitLocalVariable(functionDescriptor.getName().asString() + "$", type.getDescriptor(), null, scopeStart, blockEnd, index);
                return null;
            }
        });
    }

    public boolean isShouldMarkLineNumbers() {
        return shouldMarkLineNumbers;
    }

    public void setShouldMarkLineNumbers(boolean shouldMarkLineNumbers) {
        this.shouldMarkLineNumbers = shouldMarkLineNumbers;
    }

    public void markStartLineNumber(@NotNull JetElement element) {
        markLineNumber(element, false);
    }

    public void markLineNumber(@NotNull JetElement statement, boolean markEndOffset) {
        if (!shouldMarkLineNumbers) return;

        Integer lineNumber = CodegenUtil.getLineNumberForElement(statement, markEndOffset);
        if (lineNumber == null || lineNumber == myLastLineNumber) {
            return;
        }
        myLastLineNumber = lineNumber;

        Label label = new Label();
        v.visitLabel(label);
        v.visitLineNumber(lineNumber, label);
    }

    //we should generate additional linenumber info after inline call only if it used as argument
    public void markLineNumberAfterInlineIfNeeded() {
        if (!shouldMarkLineNumbers) {
            //if it used as general argument
            if (myLastLineNumber > -1) {
                Label label = new Label();
                v.visitLabel(label);
                v.visitLineNumber(myLastLineNumber, label);
            }
        } else {
            //if it used as argument of infix call (in this case lineNumber for simple inlineCall also would be reset)
            myLastLineNumber = -1;
        }
    }

    private void doFinallyOnReturn(@NotNull Label afterReturnLabel) {
        if(!blockStackElements.isEmpty()) {
            BlockStackElement stackElement = blockStackElements.peek();
            if (stackElement instanceof FinallyBlockStackElement) {
                FinallyBlockStackElement finallyBlockStackElement = (FinallyBlockStackElement) stackElement;
                genFinallyBlockOrGoto(finallyBlockStackElement, null, afterReturnLabel);
            }
            else if (stackElement instanceof LoopBlockStackElement) {

            } else {
                throw new UnsupportedOperationException("Wrong BlockStackElement in processing stack");
            }

            blockStackElements.pop();
            doFinallyOnReturn(afterReturnLabel);
            blockStackElements.push(stackElement);
        }
    }

    public boolean hasFinallyBlocks() {
        for (BlockStackElement element : blockStackElements) {
            if (element instanceof FinallyBlockStackElement) {
                return true;
            }
        }
        return false;
    }

    private void genFinallyBlockOrGoto(
            @Nullable FinallyBlockStackElement finallyBlockStackElement,
            @Nullable Label tryCatchBlockEnd,
            @Nullable Label afterJumpLabel
    ) {
        if (finallyBlockStackElement != null) {
            finallyDepth++;
            assert finallyBlockStackElement.gaps.size() % 2 == 0 : "Finally block gaps are inconsistent";

            BlockStackElement topOfStack = blockStackElements.pop();
            assert topOfStack == finallyBlockStackElement : "Top element of stack doesn't equals processing finally block";

            JetTryExpression jetTryExpression = finallyBlockStackElement.expression;
            Label finallyStart = new Label();
            v.mark(finallyStart);
            finallyBlockStackElement.addGapLabel(finallyStart);
            if (InlineCodegenUtil.isFinallyMarkerRequired(context)) {
                InlineCodegenUtil.generateFinallyMarker(v, finallyDepth, true);
            }
            //noinspection ConstantConditions
            gen(jetTryExpression.getFinallyBlock().getFinalExpression(), Type.VOID_TYPE);

            if (InlineCodegenUtil.isFinallyMarkerRequired(context)) {
                InlineCodegenUtil.generateFinallyMarker(v, finallyDepth, false);
            }
        }

        if (tryCatchBlockEnd != null) {
            v.goTo(tryCatchBlockEnd);
        }

        if (finallyBlockStackElement != null) {
            finallyDepth--;
            Label finallyEnd = afterJumpLabel != null ? afterJumpLabel : new Label();
            if (afterJumpLabel == null) {
                v.mark(finallyEnd);
            }
            finallyBlockStackElement.addGapLabel(finallyEnd);

            blockStackElements.push(finallyBlockStackElement);
        }
    }

    @Override
    public StackValue visitReturnExpression(@NotNull JetReturnExpression expression, StackValue receiver) {
        JetExpression returnedExpression = expression.getReturnedExpression();
        CallableMemberDescriptor descriptor = getContext().getContextDescriptor();
        NonLocalReturnInfo nonLocalReturn = getNonLocalReturnInfo(descriptor, expression);
        boolean isNonLocalReturn = nonLocalReturn != null;
        if (isNonLocalReturn && !state.isInlineEnabled()) {
            state.getDiagnostics().report(Errors.NON_LOCAL_RETURN_IN_DISABLED_INLINE.on(expression));
            genThrow(v, "java/lang/UnsupportedOperationException",
                     "Non-local returns are not allowed with inlining disabled");
            return StackValue.none();
        }

        Type returnType = isNonLocalReturn ? nonLocalReturn.returnType : this.returnType;
        if (returnedExpression != null) {
            gen(returnedExpression, returnType);
        }

        Label afterReturnLabel = new Label();
        generateFinallyBlocksIfNeeded(returnType, afterReturnLabel);

        if (isNonLocalReturn) {
            InlineCodegenUtil.generateGlobalReturnFlag(v, nonLocalReturn.labelName);
        }
        v.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
        v.mark(afterReturnLabel);

        return StackValue.none();
    }

    public void generateFinallyBlocksIfNeeded(Type returnType, @NotNull Label afterReturnLabel) {
        if (hasFinallyBlocks()) {
            if (!Type.VOID_TYPE.equals(returnType)) {
                int returnValIndex = myFrameMap.enterTemp(returnType);
                StackValue.Local localForReturnValue = StackValue.local(returnValIndex, returnType);
                localForReturnValue.store(StackValue.onStack(returnType), v);
                doFinallyOnReturn(afterReturnLabel);
                localForReturnValue.put(returnType, v);
                myFrameMap.leaveTemp(returnType);
            }
            else {
                doFinallyOnReturn(afterReturnLabel);
            }
        }
    }

    @Nullable
    private NonLocalReturnInfo getNonLocalReturnInfo(@NotNull CallableMemberDescriptor descriptor, @NotNull JetReturnExpression expression) {
        //call inside lambda
        if (isFunctionLiteral(descriptor) || isFunctionExpression(descriptor)) {
            if (expression.getLabelName() == null) {
                if (isFunctionLiteral(descriptor)) {
                    //non labeled return couldn't be local in lambda
                    FunctionDescriptor containingFunction =
                            BindingContextUtils.getContainingFunctionSkipFunctionLiterals(descriptor, true).getFirst();
                    //FIRST_FUN_LABEL to prevent clashing with existing labels
                    return new NonLocalReturnInfo(typeMapper.mapReturnType(containingFunction), InlineCodegenUtil.FIRST_FUN_LABEL);
                } else {
                    //local
                    return null;
                }
            }

            PsiElement element = bindingContext.get(LABEL_TARGET, expression.getTargetLabel());
            if (element != DescriptorToSourceUtils.getSourceFromDescriptor(context.getContextDescriptor())) {
                DeclarationDescriptor elementDescriptor = typeMapper.getBindingContext().get(DECLARATION_TO_DESCRIPTOR, element);
                assert element != null : "Expression should be not null " + expression.getText();
                assert elementDescriptor != null : "Descriptor should be not null: " + element.getText();
                return new NonLocalReturnInfo(typeMapper.mapReturnType((CallableDescriptor) elementDescriptor), expression.getLabelName());
            }
        }
        return null;
    }

    public void returnExpression(JetExpression expr) {
        boolean isBlockedNamedFunction = expr instanceof JetBlockExpression && expr.getParent() instanceof JetNamedFunction;

        // If generating body for named block-bodied function, generate it as sequence of statements
        gen(expr, isBlockedNamedFunction ? Type.VOID_TYPE : returnType);

        // If it does not end with return we should return something
        // because if we don't there can be VerifyError (specific cases with Nothing-typed expressions)
        if (!endsWithReturn(expr)) {
            markLineNumber(expr, true);

            if (isBlockedNamedFunction && !Type.VOID_TYPE.equals(expressionType(expr))) {
                StackValue.none().put(returnType, v);
            }

            v.areturn(returnType);
        }
    }

    private static boolean endsWithReturn(JetElement bodyExpression) {
        if (bodyExpression instanceof JetBlockExpression) {
            List<JetExpression> statements = ((JetBlockExpression) bodyExpression).getStatements();
            return statements.size() > 0 && statements.get(statements.size() - 1) instanceof JetReturnExpression;
        }

        return bodyExpression instanceof JetReturnExpression;
    }

    @Override
    public StackValue visitSimpleNameExpression(@NotNull JetSimpleNameExpression expression, @NotNull StackValue receiver) {
        ResolvedCall<?> resolvedCall = getResolvedCall(expression, bindingContext);

        DeclarationDescriptor descriptor;
        if (resolvedCall == null) {
            descriptor = bindingContext.get(REFERENCE_TARGET, expression);
        }
        else {
            if (resolvedCall instanceof VariableAsFunctionResolvedCall) {
                VariableAsFunctionResolvedCall call = (VariableAsFunctionResolvedCall) resolvedCall;
                resolvedCall = call.getVariableCall();
            }
            receiver = StackValue.receiver(resolvedCall, receiver, this, null);
            descriptor = resolvedCall.getResultingDescriptor();
            if (descriptor instanceof FakeCallableDescriptorForObject) {
                descriptor = ((FakeCallableDescriptorForObject) descriptor).getReferencedDescriptor();
            }
        }

        assert descriptor != null : "Couldn't find descriptor for '" + expression.getText() + "'";
        descriptor = descriptor.getOriginal();

        if (descriptor instanceof CallableMemberDescriptor) {
            CallableMemberDescriptor memberDescriptor = DescriptorUtils.unwrapFakeOverride((CallableMemberDescriptor) descriptor);

            IntrinsicMethod intrinsic = state.getIntrinsics().getIntrinsic(memberDescriptor);
            if (intrinsic instanceof JavaClassProperty) {
                //TODO: intrinsic properties (see intermediateValueForProperty)
                Type returnType = typeMapper.mapType(memberDescriptor);
                return ((JavaClassProperty) intrinsic).generate(returnType, receiver);
            }
        }

        if (descriptor instanceof PropertyDescriptor) {
            PropertyDescriptor propertyDescriptor = (PropertyDescriptor) descriptor;

            Collection<ExpressionCodegenExtension> codegenExtensions = ExpressionCodegenExtension.Companion.getInstances(state.getProject());
            if (!codegenExtensions.isEmpty() && resolvedCall != null) {
                ExpressionCodegenExtension.Context context = new ExpressionCodegenExtension.Context(typeMapper, v);
                JetType returnType = propertyDescriptor.getReturnType();
                for (ExpressionCodegenExtension extension : codegenExtensions) {
                    if (returnType != null) {
                        StackValue value = extension.applyProperty(receiver, resolvedCall, context);
                        if (value != null) return value;
                    }
                }
            }

            boolean directToField =
                    expression.getReferencedNameElementType() == JetTokens.FIELD_IDENTIFIER && contextKind() != OwnerKind.TRAIT_IMPL;
            JetExpression r = getReceiverForSelector(expression);
            boolean isSuper = r instanceof JetSuperExpression;
            propertyDescriptor = accessiblePropertyDescriptor(propertyDescriptor);

            if (directToField) {
                receiver = StackValue.receiverWithoutReceiverArgument(receiver);
            }

            return intermediateValueForProperty(propertyDescriptor, directToField, isSuper ? (JetSuperExpression) r : null, receiver);
        }

        if (descriptor instanceof ClassDescriptor) {
            ClassDescriptor classDescriptor = (ClassDescriptor) descriptor;
            if (isObject(classDescriptor)) {
                return StackValue.singleton(classDescriptor, typeMapper);
            }
            if (isEnumEntry(classDescriptor)) {
                DeclarationDescriptor enumClass = classDescriptor.getContainingDeclaration();
                assert DescriptorUtils.isEnumClass(enumClass) : "Enum entry should be declared in enum class: " + descriptor;
                Type type = typeMapper.mapType((ClassDescriptor) enumClass);
                return StackValue.field(type, type, descriptor.getName().asString(), true, StackValue.none(), classDescriptor);
            }
            ClassDescriptor companionObjectDescriptor = classDescriptor.getCompanionObjectDescriptor();
            if (companionObjectDescriptor != null) {
                return StackValue.singleton(companionObjectDescriptor, typeMapper);
            }
            return StackValue.none();
        }

        StackValue localOrCaptured = findLocalOrCapturedValue(descriptor);
        if (localOrCaptured != null) {
            return localOrCaptured;
        }

        DeclarationDescriptor container = descriptor.getContainingDeclaration();
        if (descriptor instanceof ValueParameterDescriptor && container instanceof ScriptCodeDescriptor) {
            ScriptCodeDescriptor scriptCodeDescriptor = (ScriptCodeDescriptor) container;
            ScriptDescriptor scriptDescriptor = (ScriptDescriptor) scriptCodeDescriptor.getContainingDeclaration();
            Type scriptClassType = asmTypeForScriptDescriptor(bindingContext, scriptDescriptor);
            ValueParameterDescriptor valueParameterDescriptor = (ValueParameterDescriptor) descriptor;
            ClassDescriptor scriptClass = bindingContext.get(CLASS_FOR_SCRIPT, scriptDescriptor);
            //noinspection ConstantConditions
            StackValue script = StackValue.thisOrOuter(this, scriptClass, false, false);
            Type fieldType = typeMapper.mapType(valueParameterDescriptor);
            return StackValue.field(fieldType, scriptClassType, valueParameterDescriptor.getName().getIdentifier(), false, script,
                                    valueParameterDescriptor);
        }

        throw new UnsupportedOperationException("don't know how to generate reference " + descriptor);
    }

    @Nullable
    public StackValue findLocalOrCapturedValue(@NotNull DeclarationDescriptor descriptor) {
        int index = lookupLocalIndex(descriptor);
        if (index >= 0) {
            return stackValueForLocal(descriptor, index);
        }

        return findCapturedValue(descriptor);
    }

    @Nullable
    public StackValue findCapturedValue(@NotNull DeclarationDescriptor descriptor) {
        if (context instanceof ConstructorContext) {
            return lookupCapturedValueInConstructorParameters(descriptor);
        }

        return context.lookupInContext(descriptor, StackValue.LOCAL_0, state, false);
    }

    @Nullable
    private StackValue lookupCapturedValueInConstructorParameters(@NotNull DeclarationDescriptor descriptor) {
        StackValue parentResult = context.lookupInContext(descriptor, StackValue.LOCAL_0, state, false);
        if (context.closure == null || parentResult == null) return parentResult;

        int parameterOffsetInConstructor = context.closure.getCapturedParameterOffsetInConstructor(descriptor);
        // when captured parameter is singleton
        // see compiler/testData/codegen/box/objects/objectInLocalAnonymousObject.kt (fun local() captured in A)
        if (parameterOffsetInConstructor == -1) return parentResult;

        assert parentResult instanceof StackValue.Field || parentResult instanceof StackValue.FieldForSharedVar
                : "Part of closure should be either Field or FieldForSharedVar";

        if (parentResult instanceof StackValue.FieldForSharedVar) {
            return StackValue.shared(parameterOffsetInConstructor, parentResult.type);
        }

        return StackValue.local(parameterOffsetInConstructor, parentResult.type);
    }

    private StackValue stackValueForLocal(DeclarationDescriptor descriptor, int index) {
        if (descriptor instanceof VariableDescriptor) {
            Type sharedVarType = typeMapper.getSharedVarType(descriptor);
            JetType outType = ((VariableDescriptor) descriptor).getType();
            if (sharedVarType != null) {
                return StackValue.shared(index, asmType(outType));
            }
            else {
                return StackValue.local(index, asmType(outType));
            }
        }
        else {
            return StackValue.local(index, OBJECT_TYPE);
        }
    }

    @Override
    public boolean lookupLocal(DeclarationDescriptor descriptor) {
        return lookupLocalIndex(descriptor) != -1;
    }

    public int lookupLocalIndex(DeclarationDescriptor descriptor) {
        return myFrameMap.getIndex(descriptor);
    }

    @Nullable
    private static JetType getPropertyDelegateType(@NotNull PropertyDescriptor descriptor, @NotNull BindingContext bindingContext) {
        PropertyGetterDescriptor getter = descriptor.getGetter();
        if (getter != null) {
            Call call = bindingContext.get(DELEGATED_PROPERTY_CALL, getter);
            return call != null ? call.getExplicitReceiver().getType() : null;
        }
        return null;
    }

    @NotNull
    public StackValue.Property intermediateValueForProperty(
            @NotNull PropertyDescriptor propertyDescriptor,
            boolean forceField,
            @Nullable JetSuperExpression superExpression,
            @NotNull StackValue receiver
    ) {
        return intermediateValueForProperty(propertyDescriptor, forceField, superExpression, MethodKind.GENERAL, receiver);
    }

    public StackValue.Property intermediateValueForProperty(
            @NotNull PropertyDescriptor propertyDescriptor,
            boolean forceField,
            @Nullable JetSuperExpression superExpression,
            @NotNull MethodKind methodKind,
            StackValue receiver
    ) {
        DeclarationDescriptor containingDeclaration = propertyDescriptor.getContainingDeclaration();

        boolean isBackingFieldInAnotherClass = AsmUtil.isPropertyWithBackingFieldInOuterClass(propertyDescriptor);
        boolean isStaticBackingField = DescriptorUtils.isStaticDeclaration(propertyDescriptor) ||
                                       AsmUtil.isInstancePropertyWithStaticBackingField(propertyDescriptor);
        boolean isSuper = superExpression != null;
        boolean isExtensionProperty = propertyDescriptor.getExtensionReceiverParameter() != null;

        JetType delegateType = getPropertyDelegateType(propertyDescriptor, bindingContext);
        boolean isDelegatedProperty = delegateType != null;

        CallableMethod callableGetter = null;
        CallableMethod callableSetter = null;

        boolean skipPropertyAccessors = forceField && !isBackingFieldInAnotherClass;

        CodegenContext backingFieldContext = context.getParentContext();
        boolean changeOwnerOnTypeMapping = isBackingFieldInAnotherClass;

        if (isBackingFieldInAnotherClass && forceField) {
            backingFieldContext = context.findParentContextWithDescriptor(containingDeclaration.getContainingDeclaration());
            int flags = AsmUtil.getVisibilityForSpecialPropertyBackingField(propertyDescriptor, isDelegatedProperty);
            skipPropertyAccessors = (flags & ACC_PRIVATE) == 0 || methodKind == MethodKind.SYNTHETIC_ACCESSOR || methodKind == MethodKind.INITIALIZER;
            if (!skipPropertyAccessors) {
                propertyDescriptor = (PropertyDescriptor) backingFieldContext.getAccessor(propertyDescriptor, true, delegateType);
                changeOwnerOnTypeMapping = changeOwnerOnTypeMapping && !(propertyDescriptor instanceof AccessorForPropertyBackingFieldInOuterClass);
            }
        }

        if (!skipPropertyAccessors) {
            if (couldUseDirectAccessToProperty(propertyDescriptor, true, isDelegatedProperty, context)) {
                callableGetter = null;
            }
            else {
                if (isSuper && !isInterface(containingDeclaration)) {
                    ClassDescriptor owner = getSuperCallLabelTarget(superExpression);
                    CodegenContext c = context.findParentContextWithDescriptor(owner);
                    assert c != null : "Couldn't find a context for a super-call: " + propertyDescriptor;
                    if (c != context.getParentContext()) {
                        propertyDescriptor = (PropertyDescriptor) c.getAccessor(propertyDescriptor);
                    }
                }

                propertyDescriptor = accessiblePropertyDescriptor(propertyDescriptor);

                PropertyGetterDescriptor getter = propertyDescriptor.getGetter();
                if (getter != null) {
                    callableGetter = typeMapper.mapToCallableMethod(getter, isSuper || MethodKind.SYNTHETIC_ACCESSOR == methodKind, context);
                }
            }

            if (propertyDescriptor.isVar()) {
                PropertySetterDescriptor setter = propertyDescriptor.getSetter();
                if (setter != null) {
                    if (couldUseDirectAccessToProperty(propertyDescriptor, false, isDelegatedProperty, context)) {
                        callableSetter = null;
                    }
                    else {
                        callableSetter = typeMapper.mapToCallableMethod(setter, isSuper || MethodKind.SYNTHETIC_ACCESSOR == methodKind, context);
                    }
                }
            }
        }

        propertyDescriptor = DescriptorUtils.unwrapFakeOverride(propertyDescriptor);
        Type backingFieldOwner = typeMapper.mapOwner(changeOwnerOnTypeMapping ? propertyDescriptor.getContainingDeclaration() : propertyDescriptor,
                                    isCallInsideSameModuleAsDeclared(propertyDescriptor, context, state.getOutDirectory()));

        String fieldName;
        if (isExtensionProperty && !isDelegatedProperty) {
            fieldName = null;
        }
        else if (propertyDescriptor.getContainingDeclaration() == backingFieldContext.getContextDescriptor()) {
            assert backingFieldContext instanceof FieldOwnerContext
                    : "Actual context is " + backingFieldContext + " but should be instance of FieldOwnerContext";
            fieldName = ((FieldOwnerContext) backingFieldContext).getFieldName(propertyDescriptor, isDelegatedProperty);
        }
        else {
            fieldName = JvmAbi.getDefaultFieldNameForProperty(propertyDescriptor.getName(), isDelegatedProperty);
        }

        return StackValue.property(propertyDescriptor, backingFieldOwner,
                            typeMapper.mapType(isDelegatedProperty && forceField ? delegateType : propertyDescriptor.getOriginal().getType()),
                            isStaticBackingField, fieldName, callableGetter, callableSetter, state, receiver);

    }

    @Override
    public StackValue visitCallExpression(@NotNull JetCallExpression expression, StackValue receiver) {
        ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression, bindingContext);
        FunctionDescriptor descriptor = accessibleFunctionDescriptor(resolvedCall);

        if (descriptor instanceof ConstructorDescriptor) {
            return generateNewCall(expression, resolvedCall);
        }

        if (descriptor.getOriginal() instanceof SamConstructorDescriptor) {
            JetExpression argumentExpression = bindingContext.get(SAM_CONSTRUCTOR_TO_ARGUMENT, expression);
            assert argumentExpression != null : "Argument expression is not saved for a SAM constructor: " + descriptor;
            return genSamInterfaceValue(argumentExpression, this);
        }

        return invokeFunction(resolvedCall, receiver);
    }

    @Nullable
    private StackValue genSamInterfaceValue(
            @NotNull JetExpression probablyParenthesizedExpression,
            @NotNull final JetVisitor<StackValue, StackValue> visitor
    ) {
        final JetExpression expression = JetPsiUtil.deparenthesize(probablyParenthesizedExpression);
        final SamType samType = bindingContext.get(SAM_VALUE, probablyParenthesizedExpression);
        if (samType == null || expression == null) return null;

        if (expression instanceof JetFunctionLiteralExpression) {
            return genClosure(((JetFunctionLiteralExpression) expression).getFunctionLiteral(), samType,
                              KotlinSyntheticClass.Kind.SAM_LAMBDA);
        }

        if (expression instanceof JetNamedFunction) {
            return genClosure((JetNamedFunction) expression, samType, KotlinSyntheticClass.Kind.SAM_LAMBDA);
        }

        final Type asmType =
                state.getSamWrapperClasses().getSamWrapperClass(samType, expression.getContainingJetFile(), getParentCodegen());

        return StackValue.operation(asmType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                v.anew(asmType);
                v.dup();

                Type functionType = typeMapper.mapType(samType.getKotlinFunctionType());
                expression.accept(visitor, StackValue.none()).put(functionType, v);

                Label ifNonNull = new Label();
                Label afterAll = new Label();

                v.dup();
                v.ifnonnull(ifNonNull);

                // if null: pop function value and wrapper objects, put null
                v.pop();
                v.pop2();
                v.aconst(null);
                v.goTo(afterAll);

                v.mark(ifNonNull);
                v.invokespecial(asmType.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, functionType), false);

                v.mark(afterAll);
                return null;
            }
        });
    }

    @NotNull
    private PropertyDescriptor accessiblePropertyDescriptor(@NotNull PropertyDescriptor propertyDescriptor) {
        return context.accessiblePropertyDescriptor(propertyDescriptor);
    }

    @NotNull
    private FunctionDescriptor accessibleFunctionDescriptor(@NotNull ResolvedCall<?> resolvedCall) {
        FunctionDescriptor descriptor = (FunctionDescriptor) resolvedCall.getResultingDescriptor();
        // $default method is not private, so you need no accessor to call it
        return usesDefaultArguments(resolvedCall) ? descriptor : context.accessibleFunctionDescriptor(descriptor);
    }

    private static boolean usesDefaultArguments(@NotNull ResolvedCall<?> resolvedCall) {
        List<ResolvedValueArgument> valueArguments = resolvedCall.getValueArgumentsByIndex();
        if (valueArguments == null) return false;

        for (ResolvedValueArgument argument : valueArguments) {
            if (argument instanceof DefaultValueArgument) return true;
        }

        return false;
    }

    @NotNull
    public StackValue invokeFunction(@NotNull ResolvedCall<?> resolvedCall, @NotNull StackValue receiver) {
        return invokeFunction(resolvedCall.getCall(), resolvedCall, receiver);
    }

    @NotNull
    public StackValue invokeFunction(@NotNull Call call, @NotNull ResolvedCall<?> resolvedCall, @NotNull StackValue receiver) {
        FunctionDescriptor fd = accessibleFunctionDescriptor(resolvedCall);
        JetSuperExpression superCallExpression = CallResolverUtilPackage.getSuperCallExpression(call);
        boolean superCall = superCallExpression != null;

        if (superCall && !isInterface(fd.getContainingDeclaration())) {
            ClassDescriptor owner = getSuperCallLabelTarget(superCallExpression);
            CodegenContext c = context.findParentContextWithDescriptor(owner);
            assert c != null : "Couldn't find a context for a super-call: " + fd;
            if (c != context.getParentContext()) {
                fd = (FunctionDescriptor) c.getAccessor(fd);
            }
        }

        Callable callable = resolveToCallable(fd, superCall, resolvedCall);

        return callable.invokeMethodWithArguments(resolvedCall, receiver, this);
    }

    // Find the first parent of the current context which corresponds to a subclass of a given class
    @NotNull
    private static CodegenContext getParentContextSubclassOf(ClassDescriptor descriptor, CodegenContext context) {
        CodegenContext c = context;
        while (true) {
            if (c instanceof ClassContext && DescriptorUtils.isSubclass(c.getThisDescriptor(), descriptor)) {
                return c;
            }
            c = c.getParentContext();
            assert c != null;
        }
    }

    @NotNull
    Callable resolveToCallable(@NotNull FunctionDescriptor fd, boolean superCall, @NotNull ResolvedCall resolvedCall) {
        IntrinsicMethod intrinsic = state.getIntrinsics().getIntrinsic(fd);
        if (intrinsic != null) {
            return intrinsic.toCallable(fd, superCall, resolvedCall, this);
        }

        return resolveToCallableMethod(fd, superCall, context);
    }

    @NotNull
    private CallableMethod resolveToCallableMethod(@NotNull FunctionDescriptor fd, boolean superCall, @NotNull CodegenContext context) {
        return typeMapper.mapToCallableMethod(SamCodegenUtil.resolveSamAdapter(fd), superCall, context);
    }

    public void invokeMethodWithArguments(
            @NotNull Callable callableMethod,
            @NotNull ResolvedCall<?> resolvedCall,
            @NotNull StackValue receiver
    ) {
        CallGenerator callGenerator = getOrCreateCallGenerator(resolvedCall);
        CallableDescriptor descriptor = resolvedCall.getResultingDescriptor();

        assert callGenerator == defaultCallGenerator || !tailRecursionCodegen.isTailRecursion(resolvedCall) :
                "Tail recursive method can't be inlined: " + descriptor;

        ArgumentGenerator argumentGenerator = new CallBasedArgumentGenerator(this, callGenerator, descriptor.getValueParameters(),
                                                                             callableMethod.getValueParameterTypes());

        invokeMethodWithArguments(callableMethod, resolvedCall, receiver, callGenerator, argumentGenerator);
    }

    public void invokeMethodWithArguments(
            @NotNull Callable callableMethod,
            @NotNull ResolvedCall<?> resolvedCall,
            @NotNull StackValue receiver,
            @NotNull CallGenerator callGenerator,
            @NotNull ArgumentGenerator argumentGenerator
    ) {
        callableMethod.beforeParameterGeneration(v, null);

        if (!(resolvedCall.getResultingDescriptor() instanceof ConstructorDescriptor)) { // otherwise already
            receiver = StackValue.receiver(resolvedCall, receiver, this, callableMethod);

            Collection<ExpressionCodegenExtension> codegenExtensions = ExpressionCodegenExtension.Companion.getInstances(state.getProject());
            if (!codegenExtensions.isEmpty()) {
                ExpressionCodegenExtension.Context context = new ExpressionCodegenExtension.Context(typeMapper, v);
                for (ExpressionCodegenExtension extension : codegenExtensions) {
                    if (extension.applyFunction(receiver, resolvedCall, context)) return;
                }
            }

            receiver.put(receiver.type, v);
        }

        callGenerator.putHiddenParams();

        List<ResolvedValueArgument> valueArguments = resolvedCall.getValueArgumentsByIndex();
        assert valueArguments != null : "Failed to arrange value arguments by index: " + resolvedCall.getResultingDescriptor();

        List<Integer> masks = argumentGenerator.generate(valueArguments);

        if (tailRecursionCodegen.isTailRecursion(resolvedCall)) {
            tailRecursionCodegen.generateTailRecursion(resolvedCall);
            return;
        }

        for (int mask : masks) {
            callGenerator.putValueIfNeeded(null, Type.INT_TYPE, StackValue.constant(mask, Type.INT_TYPE));
        }

        // Extra constructor marker argument
        if (callableMethod instanceof CallableMethod) {
            List<JvmMethodParameterSignature> callableParameters = ((CallableMethod) callableMethod).getValueParameters();
            for (JvmMethodParameterSignature parameter: callableParameters) {
                if (parameter.getKind() == JvmMethodParameterKind.CONSTRUCTOR_MARKER) {
                    callGenerator.putValueIfNeeded(null, parameter.getAsmType(), StackValue.constant(null, parameter.getAsmType()));
                }
            }
        }

        callGenerator.genCall(callableMethod, resolvedCall, !masks.isEmpty(), this);
    }

    @NotNull
    protected CallGenerator getOrCreateCallGenerator(
            @NotNull CallableDescriptor descriptor,
            @Nullable JetElement callElement,
            @Nullable ReifiedTypeParameterMappings reifiedTypeParameterMappings
    ) {
        if (callElement == null) return defaultCallGenerator;

        // We should inline callable containing reified type parameters even if inline is disabled
        // because they may contain something to reify and straight call will probably fail at runtime
        boolean isInline = (state.isInlineEnabled() || DescriptorUtils.containsReifiedTypeParameters(descriptor)) &&
                           InlineUtil.isInline(descriptor);

        if (!isInline) return defaultCallGenerator;

        SimpleFunctionDescriptor original = DescriptorUtils.unwrapFakeOverride((SimpleFunctionDescriptor) descriptor.getOriginal());
        return new InlineCodegen(this, state, original, callElement, reifiedTypeParameterMappings);
    }

    @NotNull
    public CallGenerator getOrCreateCallGenerator(@NotNull FunctionDescriptor descriptor, @Nullable JetNamedFunction function) {
        return getOrCreateCallGenerator(descriptor, function, null);
    }

    @NotNull
    private CallGenerator getOrCreateCallGenerator(@NotNull ResolvedCall<?> resolvedCall) {
        Map<TypeParameterDescriptor, JetType> typeArguments = resolvedCall.getTypeArguments();
        ReifiedTypeParameterMappings mappings = new ReifiedTypeParameterMappings();
        for (Map.Entry<TypeParameterDescriptor, JetType> entry : typeArguments.entrySet()) {
            TypeParameterDescriptor key = entry.getKey();
            if (!key.isReified()) continue;

            TypeParameterDescriptor parameterDescriptor = TypeUtils.getTypeParameterDescriptorOrNull(entry.getValue());
            if (parameterDescriptor == null) {
                // type is not generic
                BothSignatureWriter signatureWriter = new BothSignatureWriter(BothSignatureWriter.Mode.TYPE);
                Type type = typeMapper.mapTypeParameter(entry.getValue(), signatureWriter);

                mappings.addParameterMappingToType(
                        key.getName().getIdentifier(),
                        type,
                        signatureWriter.toString()
                );
            }
            else {
                mappings.addParameterMappingToNewParameter(
                        key.getName().getIdentifier(),
                        parameterDescriptor.getName().getIdentifier()
                );
            }
        }
        return getOrCreateCallGenerator(
                resolvedCall.getResultingDescriptor(), resolvedCall.getCall().getCallElement(), mappings
        );
    }

    @NotNull
    public StackValue generateReceiverValue(@NotNull ReceiverValue receiverValue) {
        if (receiverValue instanceof ClassReceiver) {
            ClassDescriptor receiverDescriptor = ((ClassReceiver) receiverValue).getDeclarationDescriptor();
            if (DescriptorUtils.isCompanionObject(receiverDescriptor)) {
                CallableMemberDescriptor contextDescriptor = context.getContextDescriptor();
                if (contextDescriptor instanceof FunctionDescriptor && receiverDescriptor == contextDescriptor.getContainingDeclaration()) {
                    return StackValue.LOCAL_0;
                }
                else {
                    return StackValue.singleton(receiverDescriptor, typeMapper);
                }
            }
            else {
                return StackValue.thisOrOuter(this, receiverDescriptor, false, false);
            }
        }
        else if (receiverValue instanceof ScriptReceiver) {
            // SCRIPT: generate script
            return generateScript((ScriptReceiver) receiverValue);
        }
        else if (receiverValue instanceof ExtensionReceiver) {
            return generateReceiver(((ExtensionReceiver) receiverValue).getDeclarationDescriptor());
        }
        else if (receiverValue instanceof ExpressionReceiver) {
            return gen(((ExpressionReceiver) receiverValue).getExpression());
        }
        else {
            throw new UnsupportedOperationException("Unsupported receiver value: " + receiverValue);
        }
    }

    @Nullable
    private static JetExpression getReceiverForSelector(PsiElement expression) {
        if (expression.getParent() instanceof JetDotQualifiedExpression && !isReceiver(expression)) {
            JetDotQualifiedExpression parent = (JetDotQualifiedExpression) expression.getParent();
            return parent.getReceiverExpression();
        }
        return null;
    }

    @NotNull
    private StackValue generateReceiver(@NotNull CallableDescriptor descriptor) {
        return context.generateReceiver(descriptor, state, false);
    }

    // SCRIPT: generate script, move to ScriptingUtil
    private StackValue generateScript(@NotNull ScriptReceiver receiver) {
        CodegenContext cur = context;
        StackValue result = StackValue.LOCAL_0;
        boolean inStartConstructorContext = cur instanceof ConstructorContext;
        while (cur != null) {
            if (!inStartConstructorContext) {
                cur = getNotNullParentContextForMethod(cur);
            }

            if (cur instanceof ScriptContext) {
                ScriptContext scriptContext = (ScriptContext) cur;

                ScriptDescriptor receiverDeclarationDescriptor = receiver.getDeclarationDescriptor();
                if (scriptContext.getScriptDescriptor() == receiverDeclarationDescriptor) {
                    //TODO lazy
                    return result;
                }
                else {
                    Type currentScriptType = asmTypeForScriptDescriptor(bindingContext, scriptContext.getScriptDescriptor());
                    Type classType = asmTypeForScriptDescriptor(bindingContext, receiverDeclarationDescriptor);
                    String fieldName = scriptContext.getScriptFieldName(receiverDeclarationDescriptor);
                    return StackValue.field(classType, currentScriptType, fieldName, false, result, receiverDeclarationDescriptor);
                }
            }

            result = cur.getOuterExpression(result, false);

            if (inStartConstructorContext) {
                cur = getNotNullParentContextForMethod(cur);
                inStartConstructorContext = false;
            }

            cur = cur.getParentContext();
        }

        throw new UnsupportedOperationException();
    }

    @NotNull
    public StackValue generateThisOrOuter(@NotNull ClassDescriptor calleeContainingClass, boolean isSuper) {
        boolean isSingleton = calleeContainingClass.getKind().isSingleton();
        if (isSingleton) {
            if (calleeContainingClass.equals(context.getThisDescriptor()) &&
                !AnnotationsPackage.isPlatformStaticInObjectOrClass(context.getContextDescriptor())) {
                return StackValue.local(0, typeMapper.mapType(calleeContainingClass));
            }
            else {
                return StackValue.singleton(calleeContainingClass, typeMapper);
            }
        }

        CodegenContext cur = context;
        Type type = asmType(calleeContainingClass.getDefaultType());
        StackValue result = StackValue.local(0, type);
        boolean inStartConstructorContext = cur instanceof ConstructorContext;
        while (cur != null) {
            ClassDescriptor thisDescriptor = cur.getThisDescriptor();

            if (!isSuper && thisDescriptor == calleeContainingClass) {
                return result;
            }

            if (isSuper && DescriptorUtils.isSubclass(thisDescriptor, calleeContainingClass)) {
                return castToRequiredTypeOfInterfaceIfNeeded(result, thisDescriptor, calleeContainingClass);
            }

            //for constructor super call we should access to outer instance through parameter in locals, in other cases through field for captured outer
            if (inStartConstructorContext) {
                result = cur.getOuterExpression(result, false);
                cur = getNotNullParentContextForMethod(cur);
                inStartConstructorContext = false;
            }
            else {
                cur = getNotNullParentContextForMethod(cur);
                result = cur.getOuterExpression(result, false);
            }

            cur = cur.getParentContext();
        }

        throw new UnsupportedOperationException();
    }

    @NotNull
    private static CodegenContext getNotNullParentContextForMethod(CodegenContext cur) {
        if (cur instanceof MethodContext) {
            cur = cur.getParentContext();
        }
        assert cur != null;
        return cur;
    }


    private static boolean isReceiver(PsiElement expression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof JetQualifiedExpression) {
            JetExpression receiverExpression = ((JetQualifiedExpression) parent).getReceiverExpression();
            return expression == receiverExpression;
        }
        return false;
    }

    public void genVarargs(@NotNull VarargValueArgument valueArgument, @NotNull JetType outType) {
        Type type = asmType(outType);
        assert type.getSort() == Type.ARRAY;
        Type elementType = correctElementType(type);
        List<ValueArgument> arguments = valueArgument.getArguments();
        int size = arguments.size();

        boolean hasSpread = false;
        for (int i = 0; i != size; ++i) {
            if (arguments.get(i).getSpreadElement() != null) {
                hasSpread = true;
                break;
            }
        }

        if (hasSpread) {
            if (size == 1) {
                gen(arguments.get(0).getArgumentExpression(), type);
            }
            else {
                String owner;
                String addDescriptor;
                String toArrayDescriptor;
                boolean arrayOfReferences = KotlinBuiltIns.isArray(outType);
                if (arrayOfReferences) {
                    owner = "kotlin/jvm/internal/SpreadBuilder";
                    addDescriptor = "(Ljava/lang/Object;)V";
                    toArrayDescriptor = "([Ljava/lang/Object;)[Ljava/lang/Object;";
                }
                else {
                    String spreadBuilderClassName = AsmUtil.asmPrimitiveTypeToLangPrimitiveType(elementType).getTypeName().getIdentifier() + "SpreadBuilder";
                    owner = "kotlin/jvm/internal/" + spreadBuilderClassName;
                    addDescriptor = "(" + elementType.getDescriptor() + ")V";
                    toArrayDescriptor = "()" + type.getDescriptor();
                }
                v.anew(Type.getObjectType(owner));
                v.dup();
                v.iconst(size);
                v.invokespecial(owner, "<init>", "(I)V", false);
                for (int i = 0; i != size; ++i) {
                    v.dup();
                    ValueArgument argument = arguments.get(i);
                    if (argument.getSpreadElement() != null) {
                        gen(argument.getArgumentExpression(), OBJECT_TYPE);
                        v.invokevirtual(owner, "addSpread", "(Ljava/lang/Object;)V", false);
                    }
                    else {
                        gen(argument.getArgumentExpression(), elementType);
                        v.invokevirtual(owner, "add", addDescriptor, false);
                    }
                }
                if (arrayOfReferences) {
                    v.dup();
                    v.invokevirtual(owner, "size", "()I", false);
                    newArrayInstruction(outType);
                    v.invokevirtual(owner, "toArray", toArrayDescriptor, false);
                    v.checkcast(type);
                }
                else {
                    v.invokevirtual(owner, "toArray", toArrayDescriptor, false);
                }
            }
        }
        else {
            v.iconst(arguments.size());
            newArrayInstruction(outType);
            for (int i = 0; i != size; ++i) {
                v.dup();
                StackValue rightSide = gen(arguments.get(i).getArgumentExpression());
                StackValue.arrayElement(elementType, StackValue.onStack(type), StackValue.constant(i, Type.INT_TYPE)).store(rightSide, v);
            }
        }
    }

    public int indexOfLocal(JetReferenceExpression lhs) {
        DeclarationDescriptor declarationDescriptor = bindingContext.get(REFERENCE_TARGET, lhs);
        if (isVarCapturedInClosure(bindingContext, declarationDescriptor)) {
            return -1;
        }
        return lookupLocalIndex(declarationDescriptor);
    }

    @Override
    public StackValue visitClassLiteralExpression(@NotNull JetClassLiteralExpression expression, StackValue data) {
        JetType type = bindingContext.getType(expression);
        assert type != null;

        assert state.getReflectionTypes().getkClass().getTypeConstructor().equals(type.getConstructor())
                : "::class expression should be type checked to a KClass: " + type;

        return generateClassLiteralReference(typeMapper, KotlinPackage.single(type.getArguments()).getType());
    }

    @Override
    public StackValue visitCallableReferenceExpression(@NotNull JetCallableReferenceExpression expression, StackValue data) {
        ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression.getCallableReference(), bindingContext);
        FunctionDescriptor functionDescriptor = bindingContext.get(FUNCTION, expression);
        if (functionDescriptor != null) {
            FunctionReferenceGenerationStrategy strategy = new FunctionReferenceGenerationStrategy(state, functionDescriptor, resolvedCall);
            return genClosure(expression, functionDescriptor, strategy, null, KotlinSyntheticClass.Kind.CALLABLE_REFERENCE_WRAPPER,
                              (FunctionDescriptor) resolvedCall.getResultingDescriptor());
        }

        VariableDescriptor variableDescriptor = bindingContext.get(VARIABLE, expression);
        if (variableDescriptor != null) {
            return generatePropertyReference(expression, variableDescriptor, resolvedCall);
        }

        throw new UnsupportedOperationException("Unsupported callable reference expression: " + expression.getText());
    }

    @NotNull
    private StackValue generatePropertyReference(
            @NotNull JetCallableReferenceExpression expression,
            @NotNull VariableDescriptor variableDescriptor,
            @NotNull ResolvedCall<?> resolvedCall
    ) {
        ClassDescriptor classDescriptor = CodegenBinding.anonymousClassForCallable(bindingContext, variableDescriptor);

        ClassBuilder classBuilder = state.getFactory().newVisitor(
                OtherOrigin(expression),
                typeMapper.mapClass(classDescriptor),
                expression.getContainingFile()
        );

        @SuppressWarnings("unchecked")
        PropertyReferenceCodegen codegen = new PropertyReferenceCodegen(
                state, parentCodegen, context.intoAnonymousClass(classDescriptor, this, OwnerKind.IMPLEMENTATION),
                expression, classBuilder, classDescriptor, (ResolvedCall<VariableDescriptor>) resolvedCall
        );
        codegen.generate();

        return codegen.putInstanceOnStack();
    }

    @NotNull
    public static StackValue generateClassLiteralReference(@NotNull final JetTypeMapper typeMapper, @NotNull final JetType type) {
        return StackValue.operation(K_CLASS_TYPE, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                Type classAsmType = typeMapper.mapType(type);
                ClassifierDescriptor descriptor = type.getConstructor().getDeclarationDescriptor();
                //noinspection ConstantConditions
                ModuleDescriptor module = DescriptorUtils.getContainingModule(descriptor);
                // Instantiate annotation classes as foreign due to bug in JDK 6 and 7:
                // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6857918
                if (descriptor instanceof JavaClassDescriptor || module == module.getBuiltIns().getBuiltInsModule() ||
                        DescriptorUtils.isAnnotationClass(descriptor)) {
                    putJavaLangClassInstance(v, classAsmType);
                    wrapJavaClassIntoKClass(v);
                }
                else {
                    v.getstatic(classAsmType.getInternalName(), JvmAbi.KOTLIN_CLASS_FIELD_NAME, K_CLASS_TYPE.getDescriptor());
                }

                return Unit.INSTANCE$;
            }
        });
    }

    @Override
    public StackValue visitDotQualifiedExpression(@NotNull JetDotQualifiedExpression expression, StackValue receiver) {
        StackValue receiverValue = StackValue.none(); //gen(expression.getReceiverExpression())
        return genQualified(receiverValue, expression.getSelectorExpression());
    }

    private StackValue generateExpressionWithNullFallback(@NotNull JetExpression expression, @NotNull Label ifnull) {
        JetExpression deparenthesized = JetPsiUtil.deparenthesize(expression);
        assert deparenthesized != null : "Unexpected empty expression";

        expression = deparenthesized;
        Type type = expressionType(expression);

        if (expression instanceof JetSafeQualifiedExpression && !isPrimitive(type)) {
            return StackValue.coercion(generateSafeQualifiedExpression((JetSafeQualifiedExpression) expression, ifnull), type);
        }
        else {
            return genLazy(expression, type);
        }
    }

    private StackValue generateSafeQualifiedExpression(@NotNull JetSafeQualifiedExpression expression, @NotNull Label ifNull) {
        JetExpression receiver = expression.getReceiverExpression();
        JetExpression selector = expression.getSelectorExpression();

        Type receiverType = expressionType(receiver);
        StackValue receiverValue = generateExpressionWithNullFallback(receiver, ifNull);

        //Do not optimize for primitives cause in case of safe call extension receiver should be generated before dispatch one
        StackValue newReceiver = new StackValue.SafeCall(receiverType, receiverValue, isPrimitive(receiverType) ? null : ifNull);
        return genQualified(newReceiver, selector);
    }

    @Override
    public StackValue visitSafeQualifiedExpression(@NotNull JetSafeQualifiedExpression expression, StackValue unused) {
        Label ifnull = new Label();
        Type type = boxType(expressionType(expression));

        StackValue value = generateSafeQualifiedExpression(expression, ifnull);
        StackValue newReceiver = StackValue.coercion(value, type);
        StackValue result;

        if (!isPrimitive(expressionType(expression.getReceiverExpression()))) {
            result = new StackValue.SafeFallback(type, ifnull, newReceiver);
        } else {
            result = newReceiver;
        }

        return result;
    }

    @Override
    public StackValue visitBinaryExpression(@NotNull JetBinaryExpression expression, @NotNull StackValue receiver) {
        JetSimpleNameExpression reference = expression.getOperationReference();
        IElementType opToken = reference.getReferencedNameElementType();
        if (opToken == JetTokens.EQ) {
            return generateAssignmentExpression(expression);
        }
        else if (JetTokens.AUGMENTED_ASSIGNMENTS.contains(opToken)) {
            return generateAugmentedAssignment(expression);
        }
        else if (opToken == JetTokens.ANDAND) {
            return generateBooleanAnd(expression);
        }
        else if (opToken == JetTokens.OROR) {
            return generateBooleanOr(expression);
        }
        else if (opToken == JetTokens.EQEQ || opToken == JetTokens.EXCLEQ ||
                 opToken == JetTokens.EQEQEQ || opToken == JetTokens.EXCLEQEQEQ) {
            return generateEquals(expression.getLeft(), expression.getRight(), opToken);
        }
        else if (opToken == JetTokens.LT || opToken == JetTokens.LTEQ ||
                 opToken == JetTokens.GT || opToken == JetTokens.GTEQ) {
            return generateComparison(expression, receiver);
        }
        else if (opToken == JetTokens.ELVIS) {
            return generateElvis(expression);
        }
        else if (opToken == JetTokens.IN_KEYWORD || opToken == JetTokens.NOT_IN) {
            return generateIn(StackValue.expression(expressionType(expression.getLeft()), expression.getLeft(), this),
                              expression.getRight(), reference);
        }
        else {
            ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression, bindingContext);
            FunctionDescriptor descriptor = (FunctionDescriptor) resolvedCall.getResultingDescriptor();

            if (descriptor instanceof ConstructorDescriptor) {
                return generateConstructorCall(resolvedCall, expressionType(expression));
            }

            return invokeFunction(resolvedCall, receiver);
        }
    }

    private StackValue generateIn(final StackValue leftValue, JetExpression rangeExpression, final JetSimpleNameExpression operationReference) {
        final JetExpression deparenthesized = JetPsiUtil.deparenthesize(rangeExpression);

        assert deparenthesized != null : "For with empty range expression";

        return StackValue.operation(Type.BOOLEAN_TYPE, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                if (isIntRangeExpr(deparenthesized) && AsmUtil.isIntPrimitive(leftValue.type)) {
                    genInIntRange(leftValue, (JetBinaryExpression) deparenthesized);
                }
                else {
                    ResolvedCall<? extends CallableDescriptor> resolvedCall = getResolvedCallWithAssert(operationReference, bindingContext);
                    StackValue result = invokeFunction(resolvedCall.getCall(), resolvedCall, StackValue.none());
                    result.put(result.type, v);
                }
                if (operationReference.getReferencedNameElementType() == JetTokens.NOT_IN) {
                    genInvertBoolean(v);
                }
                return null;
            }
        });
    }

    private void genInIntRange(StackValue leftValue, JetBinaryExpression rangeExpression) {
        v.iconst(1);
        // 1
        leftValue.put(Type.INT_TYPE, v);
        // 1 l
        v.dup2();
        // 1 l 1 l

        //noinspection ConstantConditions
        gen(rangeExpression.getLeft(), Type.INT_TYPE);
        // 1 l 1 l r
        Label lok = new Label();
        v.ificmpge(lok);
        // 1 l 1
        v.pop();
        v.iconst(0);
        v.mark(lok);
        // 1 l c
        v.dupX2();
        // c 1 l c
        v.pop();
        // c 1 l

        gen(rangeExpression.getRight(), Type.INT_TYPE);
        // c 1 l r
        Label rok = new Label();
        v.ificmple(rok);
        // c 1
        v.pop();
        v.iconst(0);
        v.mark(rok);
        // c c

        v.and(Type.INT_TYPE);
    }

    private StackValue generateBooleanAnd(JetBinaryExpression expression) {
        return StackValue.and(gen(expression.getLeft()), gen(expression.getRight()));
    }

    private StackValue generateBooleanOr(JetBinaryExpression expression) {
        return StackValue.or(gen(expression.getLeft()), gen(expression.getRight()));
    }

    private StackValue generateEquals(JetExpression left, JetExpression right, IElementType opToken) {
        Type leftType = expressionType(left);
        Type rightType = expressionType(right);

        if (JetPsiUtil.isNullConstant(left)) {
            return genCmpWithNull(right, opToken);
        }

        if (JetPsiUtil.isNullConstant(right)) {
            return genCmpWithNull(left, opToken);
        }

        if (isIntZero(left, leftType) && isIntPrimitive(rightType)) {
            return genCmpWithZero(right, opToken);
        }

        if (isIntZero(right, rightType) && isIntPrimitive(leftType)) {
            return genCmpWithZero(left, opToken);
        }

        if (isPrimitive(leftType) != isPrimitive(rightType)) {
            leftType = boxType(leftType);
            rightType = boxType(rightType);
        }

        StackValue leftValue = genLazy(left, leftType);
        StackValue rightValue = genLazy(right, rightType);

        if (opToken == JetTokens.EQEQEQ || opToken == JetTokens.EXCLEQEQEQ) {
            // TODO: always casting to the type of the left operand in case of primitives looks wrong
            Type operandType = isPrimitive(leftType) ? leftType : OBJECT_TYPE;
            return StackValue.cmp(opToken, operandType, leftValue, rightValue);
        }

        return genEqualsForExpressionsOnStack(opToken, leftValue, rightValue);
    }

    private boolean isIntZero(JetExpression expr, Type exprType) {
        ConstantValue<?> exprValue = getCompileTimeConstant(expr, bindingContext);
        return isIntPrimitive(exprType) && exprValue != null && Integer.valueOf(0).equals(exprValue.getValue());
    }

    private StackValue genCmpWithZero(JetExpression exp, IElementType opToken) {
        return StackValue.compareIntWithZero(gen(exp), (JetTokens.EQEQ == opToken || JetTokens.EQEQEQ == opToken) ? IFNE : IFEQ);
    }

    private StackValue genCmpWithNull(JetExpression exp, IElementType opToken) {
        return StackValue.compareWithNull(gen(exp), (JetTokens.EQEQ == opToken || JetTokens.EQEQEQ == opToken) ? IFNONNULL : IFNULL);
    }

    private StackValue generateElvis(@NotNull final JetBinaryExpression expression) {
        JetExpression left = expression.getLeft();

        final Type exprType = expressionType(expression);
        final Type leftType = expressionType(left);

        final Label ifNull = new Label();


        assert left != null : "left expression in elvis should be not null: " + expression.getText();
        final StackValue value = generateExpressionWithNullFallback(left, ifNull);

        if (isPrimitive(leftType)) {
            return value;
        }

        return StackValue.operation(exprType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                value.put(value.type, v);
                v.dup();

                v.ifnull(ifNull);
                StackValue.onStack(leftType).put(exprType, v);

                Label end = new Label();
                v.goTo(end);

                v.mark(ifNull);
                v.pop();
                gen(expression.getRight(), exprType);
                v.mark(end);
                return null;
            }
        });
    }

    private StackValue generateComparison(JetBinaryExpression expression, StackValue receiver) {
        ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression, bindingContext);

        JetExpression left = expression.getLeft();
        JetExpression right = expression.getRight();

        Type type;
        StackValue leftValue;
        StackValue rightValue;
        Type leftType = expressionType(left);
        Type rightType = expressionType(right);
        if (isPrimitive(leftType) && isPrimitive(rightType)) {
            type = comparisonOperandType(leftType, rightType);
            leftValue = gen(left);
            rightValue = gen(right);
        }
        else {
            type = Type.INT_TYPE;
            leftValue = invokeFunction(resolvedCall, receiver);
            rightValue = StackValue.constant(0, type);
        }
        return StackValue.cmp(expression.getOperationToken(), type, leftValue, rightValue);
    }

    private StackValue generateAssignmentExpression(JetBinaryExpression expression) {
        StackValue stackValue = gen(expression.getLeft());
        JetExpression right = expression.getRight();
        assert right != null : expression.getText();
        stackValue.store(gen(right), v);
        return StackValue.none();
    }

    private StackValue generateAugmentedAssignment(JetBinaryExpression expression) {
        ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression, bindingContext);
        FunctionDescriptor descriptor = (FunctionDescriptor) resolvedCall.getResultingDescriptor();
        Callable callable = resolveToCallable(descriptor, false, resolvedCall);
        JetExpression lhs = expression.getLeft();
        Type lhsType = expressionType(lhs);

        boolean keepReturnValue = Boolean.TRUE.equals(bindingContext.get(VARIABLE_REASSIGNMENT, expression))
                || !KotlinBuiltIns.isUnit(descriptor.getReturnType());

        callAugAssignMethod(expression, resolvedCall, callable, lhsType, keepReturnValue);

        return StackValue.none();
    }

    private void callAugAssignMethod(
            @NotNull JetBinaryExpression expression,
            @NotNull ResolvedCall<?> resolvedCall,
            @NotNull Callable callable,
            @NotNull Type lhsType,
            boolean keepReturnValue
    ) {
        StackValue value = gen(expression.getLeft());
        if (keepReturnValue) {
            value = StackValue.complexWriteReadReceiver(value);
        }
        value.put(lhsType, v);
        StackValue receiver = StackValue.onStack(lhsType);

        callable.invokeMethodWithArguments(resolvedCall, receiver, this).put(callable.getReturnType(), v);

        if (keepReturnValue) {
            value.store(StackValue.onStack(callable.getReturnType()), v, true);
        }
    }

    public void invokeAppend(JetExpression expr) {
        if (expr instanceof JetBinaryExpression) {
            JetBinaryExpression binaryExpression = (JetBinaryExpression) expr;
            if (binaryExpression.getOperationToken() == JetTokens.PLUS) {
                JetExpression left = binaryExpression.getLeft();
                JetExpression right = binaryExpression.getRight();
                Type leftType = expressionType(left);

                if (leftType.equals(JAVA_STRING_TYPE)) {
                    invokeAppend(left);
                    invokeAppend(right);
                    return;
                }
            }
        }
        Type exprType = expressionType(expr);
        gen(expr, exprType);
        genInvokeAppendMethod(v, exprType.getSort() == Type.ARRAY ? OBJECT_TYPE : exprType);
    }

    @Nullable
    private static JetSimpleNameExpression targetLabel(JetExpression expression) {
        if (expression.getParent() instanceof JetLabeledExpression) {
            return ((JetLabeledExpression) expression.getParent()).getTargetLabel();
        }
        return null;
    }

    @Override
    public StackValue visitLabeledExpression(
            @NotNull JetLabeledExpression expression, StackValue receiver
    ) {
        return genQualified(receiver, expression.getBaseExpression());
    }

    @Override
    public StackValue visitPrefixExpression(@NotNull JetPrefixExpression expression, @NotNull StackValue receiver) {
        DeclarationDescriptor originalOperation = bindingContext.get(REFERENCE_TARGET, expression.getOperationReference());
        ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression, bindingContext);
        CallableDescriptor op = resolvedCall.getResultingDescriptor();

        assert op instanceof FunctionDescriptor || originalOperation == null : String.valueOf(op);
        String operationName = originalOperation == null ? "" : originalOperation.getName().asString();
        if (!(operationName.equals("inc") || operationName.equals("dec"))) {
            return invokeFunction(resolvedCall, receiver);
        }

        int increment = operationName.equals("inc") ? 1 : -1;
        Type type = expressionType(expression.getBaseExpression());
        StackValue value = gen(expression.getBaseExpression());
        return StackValue.preIncrement(type, value, increment, resolvedCall, this);
    }

    @Override
    public StackValue visitPostfixExpression(@NotNull final JetPostfixExpression expression, StackValue receiver) {
        if (expression.getOperationReference().getReferencedNameElementType() == JetTokens.EXCLEXCL) {
            final StackValue base = genQualified(receiver, expression.getBaseExpression());
            if (isPrimitive(base.type)) {
                return base;
            } else {
                return StackValue.operation(base.type, new Function1<InstructionAdapter, Unit>() {
                    @Override
                    public Unit invoke(InstructionAdapter v) {
                        base.put(base.type, v);
                        v.dup();
                        Label ok = new Label();
                        v.ifnonnull(ok);
                        v.invokestatic(IntrinsicMethods.INTRINSICS_CLASS_NAME, "throwNpe", "()V", false);
                        v.mark(ok);
                        return null;
                    }
                });
            }
        }

        DeclarationDescriptor originalOperation = bindingContext.get(REFERENCE_TARGET, expression.getOperationReference());
        String originalOperationName = originalOperation != null ? originalOperation.getName().asString() : null;
        final ResolvedCall<?> resolvedCall = getResolvedCallWithAssert(expression, bindingContext);
        DeclarationDescriptor op = resolvedCall.getResultingDescriptor();
        if (!(op instanceof FunctionDescriptor) || originalOperation == null) {
            throw new UnsupportedOperationException("Don't know how to generate this postfix expression: " + originalOperationName + " " + op);
        }


        final Type asmResultType = expressionType(expression);
        final Type asmBaseType = expressionType(expression.getBaseExpression());

        DeclarationDescriptor cls = op.getContainingDeclaration();

        final int increment;
        if (originalOperationName.equals("inc")) {
            increment = 1;
        }
        else if (originalOperationName.equals("dec")) {
            increment = -1;
        }
        else {
            throw new UnsupportedOperationException("Unsupported postfix operation: " + originalOperationName + " " + op);
        }

        final boolean isPrimitiveNumberClassDescriptor = isPrimitiveNumberClassDescriptor(cls);
        if (isPrimitiveNumberClassDescriptor && AsmUtil.isPrimitive(asmBaseType)) {
            JetExpression operand = expression.getBaseExpression();
            // Optimization for j = i++, when j and i are Int without any smart cast: we just work with primitive int
            if (operand instanceof JetReferenceExpression && asmResultType == Type.INT_TYPE &&
                bindingContext.get(BindingContext.SMARTCAST, operand) == null) {
                int index = indexOfLocal((JetReferenceExpression) operand);
                if (index >= 0) {
                    return StackValue.postIncrement(index, increment);
                }
            }
        }

        return StackValue.operation(asmBaseType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                StackValue value = gen(expression.getBaseExpression());
                value = StackValue.complexWriteReadReceiver(value);

                Type type = expressionType(expression.getBaseExpression());
                value.put(type, v); // old value

                value.dup(v, true);

                Type storeType;
                if (isPrimitiveNumberClassDescriptor && AsmUtil.isPrimitive(asmBaseType)) {
                    genIncrement(asmResultType, increment, v);
                    storeType = type;
                }
                else {
                    StackValue result = invokeFunction(resolvedCall, StackValue.onStack(type));
                    result.put(result.type, v);
                    storeType = result.type;
                }

                value.store(StackValue.onStack(storeType), v, true);
                return Unit.INSTANCE$;
            }
        });
    }

    @Override
    public StackValue visitProperty(@NotNull JetProperty property, StackValue receiver) {
        JetExpression initializer = property.getInitializer();
        if (initializer == null) {
            return StackValue.none();
        }
        initializeLocalVariable(property, gen(initializer));
        return StackValue.none();
    }

    @Override
    public StackValue visitMultiDeclaration(@NotNull JetMultiDeclaration multiDeclaration, StackValue receiver) {
        JetExpression initializer = multiDeclaration.getInitializer();
        if (initializer == null) return StackValue.none();

        JetType initializerType = bindingContext.getType(initializer);
        assert initializerType != null;

        Type initializerAsmType = asmType(initializerType);

        TransientReceiver initializerAsReceiver = new TransientReceiver(initializerType);

        int tempVarIndex = myFrameMap.enterTemp(initializerAsmType);

        gen(initializer, initializerAsmType);
        v.store(tempVarIndex, initializerAsmType);
        StackValue.Local local = StackValue.local(tempVarIndex, initializerAsmType);

        for (JetMultiDeclarationEntry variableDeclaration : multiDeclaration.getEntries()) {
            ResolvedCall<FunctionDescriptor> resolvedCall = bindingContext.get(COMPONENT_RESOLVED_CALL, variableDeclaration);
            assert resolvedCall != null : "Resolved call is null for " + variableDeclaration.getText();
            Call call = makeFakeCall(initializerAsReceiver);
            initializeLocalVariable(variableDeclaration, invokeFunction(call, resolvedCall, local));
        }

        if (initializerAsmType.getSort() == Type.OBJECT || initializerAsmType.getSort() == Type.ARRAY) {
            v.aconst(null);
            v.store(tempVarIndex, initializerAsmType);
        }
        myFrameMap.leaveTemp(initializerAsmType);

        return StackValue.none();
    }

    private void initializeLocalVariable(
            @NotNull JetVariableDeclaration variableDeclaration,
            @NotNull StackValue initializer
    ) {
        VariableDescriptor variableDescriptor = bindingContext.get(VARIABLE, variableDeclaration);

        if (JetPsiUtil.isScriptDeclaration(variableDeclaration)) {
            return;
        }
        int index = lookupLocalIndex(variableDescriptor);

        if (index < 0) {
            throw new IllegalStateException("Local variable not found for " + variableDescriptor);
        }

        Type sharedVarType = typeMapper.getSharedVarType(variableDescriptor);
        assert variableDescriptor != null;

        Type varType = asmType(variableDescriptor.getType());

        StackValue storeTo;
        // SCRIPT: Variable at the top of the script is generated as field
        if (JetPsiUtil.isScriptDeclaration(variableDeclaration)) {
            JetScript scriptPsi = JetPsiUtil.getScript(variableDeclaration);
            assert scriptPsi != null;
            Type scriptClassType = asmTypeForScriptPsi(bindingContext, scriptPsi);
            storeTo = StackValue.field(varType, scriptClassType, variableDeclaration.getName(), false, StackValue.LOCAL_0, variableDescriptor);
        }
        else if (sharedVarType == null) {
            storeTo = StackValue.local(index, varType);
        }
        else {
            storeTo = StackValue.shared(index, varType);
        }

        storeTo.store(initializer, v);
    }

    @NotNull
    private StackValue generateNewCall(@NotNull JetCallExpression expression, @NotNull ResolvedCall<?> resolvedCall) {
        Type type = expressionType(expression);
        if (type.getSort() == Type.ARRAY) {
            //noinspection ConstantConditions
            return generateNewArray(expression, bindingContext.getType(expression));
        }

        return generateConstructorCall(resolvedCall, type);
    }

    @NotNull
    public ConstructorDescriptor getConstructorDescriptor(@NotNull ResolvedCall<?> resolvedCall) {
        FunctionDescriptor accessibleDescriptor = accessibleFunctionDescriptor(resolvedCall);
        assert accessibleDescriptor instanceof ConstructorDescriptor :
                "getConstructorDescriptor must be called only for constructors: " + accessibleDescriptor;
        return (ConstructorDescriptor) accessibleDescriptor;
    }

    @NotNull
    public StackValue generateConstructorCall(@NotNull final ResolvedCall<?> resolvedCall, @NotNull final Type objectType) {
        return StackValue.functionCall(objectType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                v.anew(objectType);
                v.dup();

                ConstructorDescriptor constructor = getConstructorDescriptor(resolvedCall);

                ReceiverParameterDescriptor dispatchReceiver = constructor.getDispatchReceiverParameter();
                if (dispatchReceiver != null) {
                    Type receiverType = typeMapper.mapType(dispatchReceiver.getType());
                    generateReceiverValue(resolvedCall.getDispatchReceiver()).put(receiverType, v);
                }

                // Resolved call to local class constructor doesn't have dispatchReceiver, so we need to generate closure on stack
                // See StackValue.receiver for more info
                pushClosureOnStack(constructor.getContainingDeclaration(), dispatchReceiver == null, defaultCallGenerator);

                constructor = SamCodegenUtil.resolveSamAdapter(constructor);
                CallableMethod method = typeMapper.mapToCallableMethod(constructor);
                invokeMethodWithArguments(method, resolvedCall, StackValue.none());

                return Unit.INSTANCE$;
            }
        });
    }

    public StackValue generateNewArray(@NotNull JetCallExpression expression, @NotNull final JetType arrayType) {
        assert expression.getValueArguments().size() == 1 : "Size argument expected";

        final JetExpression sizeExpression = expression.getValueArguments().get(0).getArgumentExpression();
        Type type = typeMapper.mapType(arrayType);

        return StackValue.operation(type, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                gen(sizeExpression, Type.INT_TYPE);
                newArrayInstruction(arrayType);
                return Unit.INSTANCE$;
            }
        });
    }

    public void newArrayInstruction(@NotNull JetType arrayType) {
        if (KotlinBuiltIns.isArray(arrayType)) {
            JetType elementJetType = arrayType.getArguments().get(0).getType();
            putReifierMarkerIfTypeIsReifiedParameter(
                    elementJetType,
                    ReifiedTypeInliner.NEW_ARRAY_MARKER_METHOD_NAME
            );
            v.newarray(boxType(asmType(elementJetType)));
        }
        else {
            Type type = typeMapper.mapType(arrayType);
            v.newarray(correctElementType(type));
        }
    }

    @Override
    public StackValue visitArrayAccessExpression(@NotNull JetArrayAccessExpression expression, StackValue receiver) {
        JetExpression array = expression.getArrayExpression();
        JetType type = array != null ? bindingContext.getType(array) : null;
        Type arrayType = expressionType(array);
        List<JetExpression> indices = expression.getIndexExpressions();
        FunctionDescriptor operationDescriptor = (FunctionDescriptor) bindingContext.get(REFERENCE_TARGET, expression);
        assert operationDescriptor != null;
        if (arrayType.getSort() == Type.ARRAY &&
            indices.size() == 1 &&
            isInt(operationDescriptor.getValueParameters().get(0).getType())) {
            assert type != null;
            Type elementType;
            if (KotlinBuiltIns.isArray(type)) {
                JetType jetElementType = type.getArguments().get(0).getType();
                elementType = boxType(asmType(jetElementType));
            }
            else {
                elementType = correctElementType(arrayType);
            }
            StackValue arrayValue = genLazy(array, arrayType);
            StackValue index = genLazy(indices.get(0), Type.INT_TYPE);

            return StackValue.arrayElement(elementType, arrayValue, index);
        }
        else {
            ResolvedCall<FunctionDescriptor> resolvedSetCall = bindingContext.get(INDEXED_LVALUE_SET, expression);
            ResolvedCall<FunctionDescriptor> resolvedGetCall = bindingContext.get(INDEXED_LVALUE_GET, expression);

            boolean isGetter = "get".equals(operationDescriptor.getName().asString());

            Callable callable = resolveToCallable(operationDescriptor, false, isGetter ? resolvedGetCall : resolvedSetCall);
            Callable callableMethod = resolveToCallableMethod(operationDescriptor, false, context);
            Type[] argumentTypes = callableMethod.getParameterTypes();

            StackValue collectionElementReceiver = createCollectionElementReceiver(
                    expression, receiver, operationDescriptor, isGetter, resolvedGetCall, resolvedSetCall, callable
            );

            Type elementType = isGetter ? callableMethod.getReturnType() : ArrayUtil.getLastElement(argumentTypes);
            return StackValue.collectionElement(collectionElementReceiver, elementType, resolvedGetCall, resolvedSetCall, this);
        }
    }

    @NotNull
    private StackValue createCollectionElementReceiver(
            @NotNull JetArrayAccessExpression expression,
            @NotNull StackValue receiver,
            @NotNull FunctionDescriptor operationDescriptor,
            boolean isGetter,
            ResolvedCall<FunctionDescriptor> resolvedGetCall,
            ResolvedCall<FunctionDescriptor> resolvedSetCall,
            @NotNull Callable callable
    ) {
        ResolvedCall<FunctionDescriptor> resolvedCall = isGetter ? resolvedGetCall : resolvedSetCall;
        assert resolvedCall != null : "couldn't find resolved call: " + expression.getText();

        ArgumentGenerator argumentGenerator = new CallBasedArgumentGenerator(
                this, defaultCallGenerator, resolvedCall.getResultingDescriptor().getValueParameters(), callable.getValueParameterTypes()
        );

        List<ResolvedValueArgument> valueArguments = resolvedCall.getValueArgumentsByIndex();
        assert valueArguments != null : "Failed to arrange value arguments by index: " + operationDescriptor;

        if (!isGetter) {
            assert valueArguments.size() >= 2 : "Setter call should have at least 2 arguments: " + operationDescriptor;
            // Skip generation of the right hand side of an indexed assignment, which is the last value argument
            valueArguments.remove(valueArguments.size() - 1);
        }

        return new StackValue.CollectionElementReceiver(
                callable, receiver, resolvedGetCall, resolvedSetCall, isGetter, this, argumentGenerator, valueArguments
        );
    }

    @Override
    public StackValue visitThrowExpression(@NotNull JetThrowExpression expression, StackValue receiver) {
        gen(expression.getThrownExpression(), JAVA_THROWABLE_TYPE);
        v.athrow();
        return StackValue.none();
    }

    @Override
    public StackValue visitThisExpression(@NotNull JetThisExpression expression, StackValue receiver) {
        DeclarationDescriptor descriptor = bindingContext.get(REFERENCE_TARGET, expression.getInstanceReference());
        if (descriptor instanceof ClassDescriptor) {
            //TODO rewrite with context.lookupInContext()
            return StackValue.thisOrOuter(this, (ClassDescriptor) descriptor, false, true);
        }
        if (descriptor instanceof CallableDescriptor) {
            return generateReceiver((CallableDescriptor) descriptor);
        }
        throw new UnsupportedOperationException("Neither this nor receiver: " + descriptor);
    }

    @Override
    public StackValue visitTryExpression(@NotNull JetTryExpression expression, StackValue receiver) {
        return generateTryExpression(expression, false);
    }

    public StackValue generateTryExpression(final JetTryExpression expression, final boolean isStatement) {
        /*
The "returned" value of try expression with no finally is either the last expression in the try block or the last expression in the catch block
(or blocks).
         */

        JetType jetType = bindingContext.getType(expression);
        assert jetType != null;
        final Type expectedAsmType = isStatement ? Type.VOID_TYPE : asmType(jetType);

        return StackValue.operation(expectedAsmType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                JetFinallySection finallyBlock = expression.getFinallyBlock();
                FinallyBlockStackElement finallyBlockStackElement = null;
                if (finallyBlock != null) {
                    finallyBlockStackElement = new FinallyBlockStackElement(expression);
                    blockStackElements.push(finallyBlockStackElement);
                }

                //PseudoInsnsPackage.saveStackBeforeTryExpr(v);

                Label tryStart = new Label();
                v.mark(tryStart);
                v.nop(); // prevent verify error on empty try

                gen(expression.getTryBlock(), expectedAsmType);

                int savedValue = -1;
                if (!isStatement) {
                    savedValue = myFrameMap.enterTemp(expectedAsmType);
                    v.store(savedValue, expectedAsmType);
                }

                Label tryEnd = new Label();
                v.mark(tryEnd);

                //do it before finally block generation
                List<Label> tryBlockRegions = getCurrentCatchIntervals(finallyBlockStackElement, tryStart, tryEnd);

                Label end = new Label();

                genFinallyBlockOrGoto(finallyBlockStackElement, end, null);

                List<JetCatchClause> clauses = expression.getCatchClauses();
                for (int i = 0, size = clauses.size(); i < size; i++) {
                    JetCatchClause clause = clauses.get(i);

                    Label clauseStart = new Label();
                    v.mark(clauseStart);

                    VariableDescriptor descriptor = bindingContext.get(VALUE_PARAMETER, clause.getCatchParameter());
                    assert descriptor != null;
                    Type descriptorType = asmType(descriptor.getType());
                    myFrameMap.enter(descriptor, descriptorType);
                    int index = lookupLocalIndex(descriptor);
                    v.store(index, descriptorType);

                    gen(clause.getCatchBody(), expectedAsmType);

                    if (!isStatement) {
                        v.store(savedValue, expectedAsmType);
                    }

                    myFrameMap.leave(descriptor);

                    Label clauseEnd = new Label();
                    v.mark(clauseEnd);

                    v.visitLocalVariable(descriptor.getName().asString(), descriptorType.getDescriptor(), null, clauseStart, clauseEnd,
                                         index);

                    genFinallyBlockOrGoto(finallyBlockStackElement, i != size - 1 || finallyBlock != null ? end : null, null);

                    generateExceptionTable(clauseStart, tryBlockRegions, descriptorType.getInternalName());
                }


                //for default catch clause
                if (finallyBlock != null) {
                    Label defaultCatchStart = new Label();
                    v.mark(defaultCatchStart);
                    int savedException = myFrameMap.enterTemp(JAVA_THROWABLE_TYPE);
                    v.store(savedException, JAVA_THROWABLE_TYPE);

                    Label defaultCatchEnd = new Label();
                    v.mark(defaultCatchEnd);

                    //do it before finally block generation
                    //javac also generates entry in exception table for default catch clause too!!!! so defaultCatchEnd as end parameter
                    List<Label> defaultCatchRegions = getCurrentCatchIntervals(finallyBlockStackElement, tryStart, defaultCatchEnd);


                    genFinallyBlockOrGoto(finallyBlockStackElement, null, null);

                    v.load(savedException, JAVA_THROWABLE_TYPE);
                    myFrameMap.leaveTemp(JAVA_THROWABLE_TYPE);

                    v.athrow();

                    generateExceptionTable(defaultCatchStart, defaultCatchRegions, null);
                }

                markLineNumber(expression, isStatement);
                v.mark(end);

                if (!isStatement) {
                    v.load(savedValue, expectedAsmType);
                    myFrameMap.leaveTemp(expectedAsmType);
                }

                if (finallyBlock != null) {
                    blockStackElements.pop();
                }
                return Unit.INSTANCE$;
            }
        });
    }

    private void generateExceptionTable(@NotNull Label catchStart, @NotNull List<Label> catchedRegions, @Nullable String exception) {
        for (int i = 0; i < catchedRegions.size(); i += 2) {
            Label startRegion = catchedRegions.get(i);
            Label endRegion = catchedRegions.get(i+1);
            v.visitTryCatchBlock(startRegion, endRegion, catchStart, exception);
        }
    }

    @NotNull
    private static List<Label> getCurrentCatchIntervals(
            @Nullable FinallyBlockStackElement finallyBlockStackElement,
            @NotNull Label blockStart,
            @NotNull Label blockEnd
    ) {
        List<Label> gapsInBlock =
                finallyBlockStackElement != null ? new ArrayList<Label>(finallyBlockStackElement.gaps) : Collections.<Label>emptyList();
        assert gapsInBlock.size() % 2 == 0;
        List<Label> blockRegions = new ArrayList<Label>(gapsInBlock.size() + 2);
        blockRegions.add(blockStart);
        blockRegions.addAll(gapsInBlock);
        blockRegions.add(blockEnd);
        return blockRegions;
    }

    @Override
    public StackValue visitBinaryWithTypeRHSExpression(@NotNull JetBinaryExpressionWithTypeRHS expression, StackValue receiver) {
        JetExpression left = expression.getLeft();
        final IElementType opToken = expression.getOperationReference().getReferencedNameElementType();
        if (opToken == JetTokens.COLON) {
            return gen(left);
        }

        final JetType rightType = bindingContext.get(TYPE, expression.getRight());
        assert rightType != null;

        final StackValue value = genQualified(receiver, left);

        return StackValue.operation(boxType(asmType(rightType)), new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                value.put(boxType(value.type), v);

                if (value.type == Type.VOID_TYPE) {
                    StackValue.putUnitInstance(v);
                }

                if (opToken != JetTokens.AS_SAFE) {
                    if (!TypeUtils.isNullableType(rightType)) {
                        v.dup();
                        Label nonnull = new Label();
                        v.ifnonnull(nonnull);
                        genThrow(v, "kotlin/TypeCastException", "null cannot be cast to non-null type " +
                                                                DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(rightType));
                        v.mark(nonnull);
                    }
                }
                else {
                    v.dup();
                    generateInstanceOfInstruction(rightType);
                    Label ok = new Label();
                    v.ifne(ok);
                    v.pop();
                    v.aconst(null);
                    v.mark(ok);
                }

                generateCheckCastInstruction(rightType);
                return Unit.INSTANCE$;
            }
        });
    }

    @Override
    public StackValue visitIsExpression(@NotNull JetIsExpression expression, StackValue receiver) {
        StackValue match = StackValue.expression(OBJECT_TYPE, expression.getLeftHandSide(), this);
        return generateIsCheck(match, expression.getTypeReference(), expression.isNegated());
    }

    private StackValue generateExpressionMatch(StackValue expressionToMatch, JetExpression patternExpression) {
        if (expressionToMatch != null) {
            Type subjectType = expressionToMatch.type;
            markStartLineNumber(patternExpression);
            JetType condJetType = bindingContext.getType(patternExpression);
            Type condType;
            if (isNumberPrimitive(subjectType) || subjectType.getSort() == Type.BOOLEAN) {
                assert condJetType != null;
                condType = asmType(condJetType);
                if (!(isNumberPrimitive(condType) || condType.getSort() == Type.BOOLEAN)) {
                    subjectType = boxType(subjectType);
                }
            }
            else {
                condType = OBJECT_TYPE;
            }
            StackValue condition = genLazy(patternExpression, condType);
            return genEqualsForExpressionsOnStack(JetTokens.EQEQ, StackValue.coercion(expressionToMatch, subjectType), condition);
        }
        else {
            return gen(patternExpression);
        }
    }

    private StackValue generateIsCheck(StackValue expressionToMatch, JetTypeReference typeReference, boolean negated) {
        JetType jetType = bindingContext.get(TYPE, typeReference);
        markStartLineNumber(typeReference);
        StackValue value = generateInstanceOf(expressionToMatch, jetType, false);
        return negated ? StackValue.not(value) : value;
    }

    private StackValue generateInstanceOf(final StackValue expressionToGen, final JetType jetType, final boolean leaveExpressionOnStack) {
        return StackValue.operation(Type.BOOLEAN_TYPE, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                expressionToGen.put(OBJECT_TYPE, v);
                if (leaveExpressionOnStack) {
                    v.dup();
                }
                if (jetType.isMarkedNullable()) {
                    Label nope = new Label();
                    Label end = new Label();

                    v.dup();
                    v.ifnull(nope);
                    generateInstanceOfInstruction(jetType);
                    v.goTo(end);
                    v.mark(nope);
                    v.pop();
                    v.iconst(1);
                    v.mark(end);
                }
                else {
                    generateInstanceOfInstruction(jetType);
                }
                return null;
            }
        });
    }

    private void generateInstanceOfInstruction(@NotNull JetType jetType) {
        Type type = boxType(asmType(jetType));
        putReifierMarkerIfTypeIsReifiedParameter(jetType, ReifiedTypeInliner.INSTANCEOF_MARKER_METHOD_NAME);
        v.instanceOf(type);
    }

    @NotNull
    private StackValue generateCheckCastInstruction(@NotNull JetType jetType) {
        Type type = boxType(asmType(jetType));
        putReifierMarkerIfTypeIsReifiedParameter(jetType, ReifiedTypeInliner.CHECKCAST_MARKER_METHOD_NAME);
        v.checkcast(type);
        return StackValue.onStack(type);
    }

    public void putReifierMarkerIfTypeIsReifiedParameter(@NotNull JetType type, @NotNull String markerMethodName) {
        TypeParameterDescriptor typeParameterDescriptor = TypeUtils.getTypeParameterDescriptorOrNull(type);
        if (typeParameterDescriptor != null && typeParameterDescriptor.isReified()) {
            if (typeParameterDescriptor.getContainingDeclaration() != context.getContextDescriptor()) {
                parentCodegen.getReifiedTypeParametersUsages().
                        addUsedReifiedParameter(typeParameterDescriptor.getName().asString());
            }

            v.visitLdcInsn(typeParameterDescriptor.getName().asString());
            v.invokestatic(
                    IntrinsicMethods.INTRINSICS_CLASS_NAME, markerMethodName,
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false
            );
        }
    }

    public void propagateChildReifiedTypeParametersUsages(@NotNull ReifiedTypeParametersUsages usages) {
        parentCodegen.getReifiedTypeParametersUsages().propagateChildUsagesWithinContext(usages, context);
    }

    @Override
    public StackValue visitWhenExpression(@NotNull JetWhenExpression expression, StackValue receiver) {
        return generateWhenExpression(expression, false);
    }

    public StackValue generateWhenExpression(final JetWhenExpression expression, final boolean isStatement) {
        final JetExpression expr = expression.getSubjectExpression();
        final Type subjectType = expressionType(expr);

        final Type resultType = isStatement ? Type.VOID_TYPE : expressionType(expression);

        return StackValue.operation(resultType, new Function1<InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(InstructionAdapter v) {
                SwitchCodegen switchCodegen =
                        SwitchCodegenUtil.buildAppropriateSwitchCodegenIfPossible(expression, isStatement, ExpressionCodegen.this);
                if (switchCodegen != null) {
                    switchCodegen.generate();
                    return Unit.INSTANCE$;
                }

                int subjectLocal = expr != null ? myFrameMap.enterTemp(subjectType) : -1;
                if (subjectLocal != -1) {
                    gen(expr, subjectType);
                    tempVariables.put(expr, StackValue.local(subjectLocal, subjectType));
                    v.store(subjectLocal, subjectType);
                }

                Label end = new Label();
                boolean hasElse = JetPsiUtil.checkWhenExpressionHasSingleElse(expression);

                Label nextCondition = null;
                for (JetWhenEntry whenEntry : expression.getEntries()) {
                    if (nextCondition != null) {
                        v.mark(nextCondition);
                    }
                    nextCondition = new Label();
                    FrameMap.Mark mark = myFrameMap.mark();
                    Label thisEntry = new Label();
                    if (!whenEntry.isElse()) {
                        JetWhenCondition[] conditions = whenEntry.getConditions();
                        for (int i = 0; i < conditions.length; i++) {
                            StackValue conditionValue = generateWhenCondition(subjectType, subjectLocal, conditions[i]);
                            BranchedValue.Companion.condJump(conditionValue, nextCondition, true, v);
                            if (i < conditions.length - 1) {
                                v.goTo(thisEntry);
                                v.mark(nextCondition);
                                nextCondition = new Label();
                            }
                        }
                    }

                    v.visitLabel(thisEntry);
                    gen(whenEntry.getExpression(), resultType);
                    mark.dropTo();
                    if (!whenEntry.isElse()) {
                        v.goTo(end);
                    }
                }
                if (!hasElse && nextCondition != null) {
                    v.mark(nextCondition);
                    if (!isStatement) {
                        putUnitInstanceOntoStackForNonExhaustiveWhen(expression);
                    }
                }

                markLineNumber(expression, isStatement);
                v.mark(end);

                myFrameMap.leaveTemp(subjectType);
                tempVariables.remove(expr);
                return null;
            }
        });
    }

    public void putUnitInstanceOntoStackForNonExhaustiveWhen(
            @NotNull JetWhenExpression expression
    ) {
        if (Boolean.TRUE.equals(bindingContext.get(BindingContext.EXHAUSTIVE_WHEN, expression))) {
            // when() is supposed to be exhaustive
            genThrow(v, "kotlin/NoWhenBranchMatchedException", null);
        }
        else {
            // non-exhaustive when() with no else -> Unit must be expected
            StackValue.putUnitInstance(v);
        }
    }

    private StackValue generateWhenCondition(Type subjectType, int subjectLocal, JetWhenCondition condition) {
        if (condition instanceof JetWhenConditionInRange) {
            JetWhenConditionInRange conditionInRange = (JetWhenConditionInRange) condition;
            return generateIn(StackValue.local(subjectLocal, subjectType),
                              conditionInRange.getRangeExpression(),
                              conditionInRange.getOperationReference());
        }
        StackValue.Local match = subjectLocal == -1 ? null : StackValue.local(subjectLocal, subjectType);
        if (condition instanceof JetWhenConditionIsPattern) {
            JetWhenConditionIsPattern patternCondition = (JetWhenConditionIsPattern) condition;
            return generateIsCheck(match, patternCondition.getTypeReference(), patternCondition.isNegated());
        }
        else if (condition instanceof JetWhenConditionWithExpression) {
            JetExpression patternExpression = ((JetWhenConditionWithExpression) condition).getExpression();
            return generateExpressionMatch(match, patternExpression);
        }
        else {
            throw new UnsupportedOperationException("unsupported kind of when condition");
        }
    }

    private boolean isIntRangeExpr(JetExpression rangeExpression) {
        if (rangeExpression instanceof JetBinaryExpression) {
            JetBinaryExpression binaryExpression = (JetBinaryExpression) rangeExpression;
            if (binaryExpression.getOperationReference().getReferencedNameElementType() == JetTokens.RANGE) {
                JetType jetType = bindingContext.getType(rangeExpression);
                assert jetType != null;
                DeclarationDescriptor descriptor = jetType.getConstructor().getDeclarationDescriptor();
                return getBuiltIns(descriptor).getIntegralRanges().contains(descriptor);
            }
        }
        return false;
    }

    private Call makeFakeCall(ReceiverValue initializerAsReceiver) {
        JetSimpleNameExpression fake = JetPsiFactory(state.getProject()).createSimpleName("fake");
        return CallMaker.makeCall(fake, initializerAsReceiver);
    }

    @Override
    public String toString() {
        return context.getContextDescriptor().toString();
    }

    @NotNull
    public FrameMap getFrameMap() {
        return myFrameMap;
    }

    @NotNull
    public MethodContext getContext() {
        return context;
    }

    @NotNull
    public NameGenerator getInlineNameGenerator() {
        NameGenerator nameGenerator = getParentCodegen().getInlineNameGenerator();
        Name name = context.getContextDescriptor().getName();
        return nameGenerator.subGenerator((name.isSpecial() ? "$special" : name.asString()) + "$$inlined" );
    }

    public Type getReturnType() {
        return returnType;
    }

    public Stack<BlockStackElement> getBlockStackElements() {
        return new Stack<BlockStackElement>(blockStackElements);
    }

    public void addBlockStackElementsForNonLocalReturns(@NotNull Stack<BlockStackElement> elements, int finallyDepth) {
        blockStackElements.addAll(elements);
        this.finallyDepth = finallyDepth;
    }

    private static class NonLocalReturnInfo {

        final Type returnType;

        final String labelName;

        private NonLocalReturnInfo(Type type, String name) {
            returnType = type;
            labelName = name;
        }
    }
}
