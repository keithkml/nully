/*
 *  Copyright (c) 2005, Keith Lea
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  - Neither the name of the Joust Project nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.kano.nully.plugin.analysis.nulls.soot;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.annotations.NullParameterException;
import net.kano.nully.plugin.NullyTools;
import net.kano.nully.plugin.OffsetsTracker;
import net.kano.nully.annotations.UnexpectedNullValueException;
import net.kano.nully.plugin.SootTools;
import net.kano.nully.plugin.PsiTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.ThrowStmt;
import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Tag;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A class which prepares compiled Java methods to be passed to the null
 * checker. This class inserts runtime checks which correspond to the guarantees
 * specified by {@code NonNull} declarations in the code, which allows the null
 * checker to be smart about which values are null and which are not in terms of
 * non-null declared variables and return values.
 */
public class JimpleMethodPreprocessor {
    private static final Logger LOGGER
            = Logger.getInstance(JimpleMethodPreprocessor.class.getName());

    private final AnalysisContext context;

    public JimpleMethodPreprocessor(@NonNull AnalysisContext context) {
        this.context = context;
    }

    /**
     * Iterates through the methods specified in the associated {@code
     * AnalysisInfo} and preprocesses each one.
     */
    public void preprocessCode() {
        for (SootMethod method : context.getSootMethods()) {
            PsiMember member = NullyTools.getPsiMemberCopy(context, method);
            if (member == null) continue;
            preprocessMethod(member, method.retrieveActiveBody());
        }
    }

    /**
     * Preprocesses the given method.
     *
     * @param member the PSI method
     * @param body the method body
     */
    private void preprocessMethod(@NonNull PsiMember member, @NonNull Body body) {
        // keep iterating through the code until no more changes occur, up to
        // 1000000 times
        boolean changed = true;
        for (int i = 0; changed && i < 1000000; i++) {
            changed = false;
            for (Unit unit : (Collection<Unit>)body.getUnits()) {
                if (unit instanceof DefinitionStmt) {
                    DefinitionStmt defStmt = (DefinitionStmt) unit;
                    changed = fixAssignment(member, body, defStmt);
                }
                if (changed) break;
            }
        }
    }

    /**
     * Adds appropriate null check "assertions" after assignment to @NonNull
     * variables, to reflect the runtime checks that the precompiler inserts.
     *
     * @param member the method
     * @param body the method body
     * @param assignStmt an assignment statement
     * @return whether the code in {@code body} was changed
     */
    private boolean fixAssignment(@NonNull PsiMember member,
            @NonNull Body body, @NonNull DefinitionStmt assignStmt) {
        Value assignedValue = assignStmt.getRightOpBox().getValue();

        // STEP 0 - add null checks at the top of the method for @NonNull
        //          parameters (after the parameter ref assignment)
        if (assignedValue instanceof ParameterRef) {
            if (fixParameterRef(member, body, assignStmt)) return true;
        }

        // STEP 1 - add null checks before assignment if assignment
        //          to @NonNull method call result
        if (assignedValue instanceof InvokeExpr) {
            if (fixAssignmentOfNonNullMethodCall(body, assignStmt)) return true;
        }

        // STEP 2 - add null checks after assignment to @NonNull variable
        if (fixAssignmentToNonNullVariable(body, assignStmt)) return true;

        // STEP 3 - would be to check return values for nulls, but we don't need
        //          to because return values aren't used anywhere else in the
        //          method, so they only need to be tagged

        return false;
    }

    /**
     * Adds a null check at the top of the given method to throw an exception if
     * the given parameter is null, if the parameter is marked as non-null.
     *
     * @param memberCopy a method
     * @param body a method body
     * @param assignStmt an assignment statement which assigns a parameter
     *        reference to a parameter local
     * @return whether a null check was added
     */
    private boolean fixParameterRef(@NonNull PsiMember memberCopy, @NonNull Body body,
            @NonNull DefinitionStmt assignStmt) {
        if (!(memberCopy instanceof PsiMethod)) return false;
        PsiMethod method = (PsiMethod) memberCopy;

        // find the referenced parameter
        ParameterRef ref = (ParameterRef) assignStmt.getRightOp();
        int paramIndex = ref.getIndex();

        // find the corresponding PsiParameter to the method
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter[] params = parameterList.getParameters();
        PsiParameter paramCopy = params[paramIndex];
        PsiParameter param = context.getOriginalElement(paramCopy);
        if (!NullyTools.hasNonNullAnnotation(param)) return false;

        // create the exception constructor descriptor
        Type[] types = { RefType.v(String.class.getName()), IntType.v() };
        Value[] values = { StringConstant.v(param.getName()),
            IntConstant.v(parameterList.getParameterIndex(paramCopy)) };
        ExceptionConstructionDescriptor excon = new ExceptionConstructionDescriptor(
                NullParameterException.class.getName(), Arrays.asList(types),
                Arrays.asList(values));

        // and add the null check
        boolean added = addNullCheck(body, assignStmt, false, excon);
        return added;
    }

    /**
     * Adds a null check before the assignment of the return value of a non-null
     * method. If the called method is not marked as non-null, this method has
     * no effect.
     *
     * @param body a method
     * @param assignStmt the assignment statement
     * @return whether a null check was added
     */
    private boolean fixAssignmentOfNonNullMethodCall(@NonNull Body body,
            @NonNull DefinitionStmt assignStmt) {
        // find the referenced method call expression
        PsiMethodCallExpression call = getMethodCallExpression(assignStmt);
        if (call == null) return false;

        // find the called method
        PsiMethod referencedMethod = call.resolveMethod();
        if (referencedMethod == null) return false;
        PsiMethod referencedMethodOrig = context.getOriginalElement(referencedMethod);
        if (referencedMethodOrig != null) referencedMethod = referencedMethodOrig;

        // see if the called method is defined as non-null
        if (!NullyTools.hasNonNullAnnotation(referencedMethod)) return false;

        // if so, add a null check
        boolean added = addUnexpectedNullCheck(body, assignStmt, true);
        if (added) {
            LOGGER.debug("I fixed method call to "
                    + referencedMethod.getContainingClass().getQualifiedName()
                    + " " + referencedMethod.getName());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a null check after an assignment to a variable if it is marked as
     * non-null.
     *
     * @param body a method body
     * @param assignStmt an assignment statement
     * @return whether a null check was added
     */
    private boolean fixAssignmentToNonNullVariable(@NonNull Body body,
            @NonNull DefinitionStmt assignStmt) {
        // find the source position of the assigned variable
        ValueBox varBox = assignStmt.getLeftOpBox();
        SourceLnPosTag varSrcTag = SootTools.getSourceTag(varBox);
        if (varSrcTag == null) return false;

        // find the PSI variable
        PsiVariable var = getAssignedVariable(varSrcTag);

        // add a null check if necessary
        if (!NullyTools.hasNonNullAnnotation(var)) return false;

        boolean added = addUnexpectedNullCheck(body, assignStmt, false);
        if (added) {
            LOGGER.debug("I fixed assignment to " + var.getName());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a null check to the given method body for the given statement, if
     * such a check is necessary due to nonnull annotations on associated
     * elements. The inserted code will throw an instance of an exception
     * created using the given exception construction information.
     * <br /><br />
     * If {@code before} is {@code true}, the null check will be added before
     * the value is actually assigned to the corresponding variable. That is,
     * it will be impossible for the variable to hold a null value at any time.
     * <br /><br />
     * Otherwise, if {@code before} is {@code false}, the null check will be
     * added after the variable is assigned.
     * <br />
     * <br />
     * TODO: document why a check would want to specify before or after assignment execution
     *
     * @param body a method body
     * @param checkedStmt the statement for which a null check will be added
     * @param before whether to place the null check before the assignment
     * @param excon a description of what exception to throw when a null value
     *        is found at runtime
     * @return whether a null check was added
     */
    private boolean addNullCheck(@NonNull Body body,
            @NonNull DefinitionStmt checkedStmt, boolean before,
            @NonNull ExceptionConstructionDescriptor excon) {
        // if we've already fixed this assignment, there's nothing to do
        if (checkedStmt.hasTag(FixedNullAssignmentTag.TAG_NAME)) return false;

        Jimple jimple = Jimple.v();

        AssignStmt copyStmt = null;
        Chain locals = body.getLocals();

        Local holder;
        if (before) {
            // if we're inserting the null check before the assignment, we want
            // to create a temporary local which will hold the value while it's
            // being checked for nullness, so a null value is never present in
            // the actual variable being assigned.
            Local newLocal = jimple.newLocal(SootTools.getUnusedLocalName(locals),
                            checkedStmt.getLeftOp().getType());
            locals.add(newLocal);

            // 0. create the copy statement
            copyStmt = jimple.newAssignStmt(newLocal, checkedStmt.getRightOp());
            // copy tags over
            copyStmt.getRightOpBox().addAllTagsOf(checkedStmt.getRightOpBox());
            copyStmt.addTag(new FixedNullAssignmentTag());
//            copyStmt.addAllTagsOf(checkedStmt);

            // the value holder is now the new local
            holder = newLocal;
        } else {
            // we're inserting the check after the assignment, so nothing
            // special must be done regarding intermediate temporaries. the
            // value holder is simply the assigned variable itself.
            holder = (Local) checkedStmt.getLeftOp();
        }

        // 1. create the "if x != null" statement. the "target" property will be
        //    changed later, we use checkedStmt as a dummy.
        IfStmt ifStmt = jimple.newIfStmt(jimple.newNeExpr(holder, NullConstant.v()),
                        checkedStmt);

        // create the local to hold the exception to be thrown
        Local exceptionLocal = jimple.newLocal(SootTools.getUnusedLocalName(locals),
                        RefType.v(RuntimeException.class.getName()));
        locals.add(exceptionLocal);

        // 2. create the assignment statement for the to-be-thrown exception local
        Scene scene = Scene.v();
        SootClass exceptionClass = scene.getSootClass(excon.getClassName());
        AssignStmt assignStmt = jimple.newAssignStmt(exceptionLocal,
                        jimple.newNewExpr(RefType.v(exceptionClass)));

        // 3. create the "new xxx" instruction for creating a new instance of the
        // specified exception
        InvokeStmt specialInvokeExpr = jimple.newInvokeStmt(
                        jimple.newSpecialInvokeExpr(exceptionLocal,
                                scene.makeConstructorRef(exceptionClass,
                                        excon.getParameterTypes()), excon.getArguments()));

        // 4. create the throw statement for that local
        ThrowStmt throwStmt = jimple.newThrowStmt(exceptionLocal);

        // create a list of the above statements to insert
        List<Stmt> stmts = new ArrayList<Stmt>();
        if (copyStmt != null) stmts.add(copyStmt);
        stmts.add(ifStmt);
        stmts.add(assignStmt);
        stmts.add(specialInvokeExpr);
        stmts.add(throwStmt);

        // insert the statements
        PatchingChain units = body.getUnits();
        if (before) {
            // if we're inserting the check before the assignment, we need to
            // adjust the if statement to point to the original assignment
            // statement after the new checks, because the if statement will
            // be adjusted during insertBefore to point to the first inserted
            // statement.
            //
            // we call insertBefore so that old branches that ended up at the
            // assignment will end up at the checked assignment
            units.insertBefore(stmts, checkedStmt);
            ifStmt.setTarget(checkedStmt);

        } else {
            // if we're inserting the check after the assignment, we need to
            // set the if (x != null) to point to the next instruction after the
            // assignment and check. we can do this by pointing the if statement
            // at the statement after the original assignment, then inserting
            // the checks between them. Soot will ensure that the if statement
            // still points to the same instruction after the insertion, so
            // the if statement will point to the next instruction after the
            // null check
            ifStmt.setTarget((Unit) units.getSuccOf(checkedStmt));
            units.insertAfter(stmts, checkedStmt);
        }

        if (before) {
            // if we're inserting the check before the assignment, by creating a
            // new temporary intermediate local, we want to copy all of the tags
            // from the original assignment, to the assignment of the temp
            // local.
            ValueBox rightOpBox = checkedStmt.getRightOpBox();
            List<Tag> tags = new ArrayList<Tag>(rightOpBox.getTags());
            rightOpBox.setValue(holder);
            for (Tag tag : tags) rightOpBox.addTag(tag);

//            rightOpBox.addAllTagsOf(forStmt.getRightOpBox());
        }

        // we want to make sure we don't check the same assignment twice
        checkedStmt.addTag(new FixedNullAssignmentTag());

        return true;
    }

    /**
     * Adds a null check for the given assignment where null values result in
     * thrown {@link net.kano.nully.annotations.UnexpectedNullValueException}s, if a null
     * check is necessary based on nullness annotations.
     *
     * @param body a method body
     * @param assignStmt an assignment statement
     * @param before whether to insert the null check before the assignment
     * @return whether the method body was modified to include a null check
     */
    private boolean addUnexpectedNullCheck(@NonNull Body body,
            @NonNull DefinitionStmt assignStmt, boolean before) {
        ExceptionConstructionDescriptor excon = new ExceptionConstructionDescriptor(
                UnexpectedNullValueException.class.getName(),
                Collections.<Type>emptyList(), Collections.<Value>emptyList());
        return addNullCheck(body, assignStmt, before, excon);
    }

    /**
     * Returns the assigned variable in the {@link AnalysisContext#getFileCopy()}
     * corresponding to the given {@code varSrcTag}.
     *
     * @param varSrcTag a source tag for an {@link AssignStmt}
     * @return the assigned variable, if any
     */
    private PsiVariable getAssignedVariable(@NonNull SourceLnPosTag varSrcTag) {
        OffsetsTracker tracker = context.getTracker();
        PsiJavaFile fileCopy = context.getFileCopy();
        PsiElement varEl = tracker.getElementAtPosition(fileCopy, varSrcTag);
        PsiVariable varCopy = PsiTools.getReferencedVariable(varEl);
        return context.getOriginalElement(varCopy);
    }

    /**
     * Returns the method call expression in the {@link AnalysisContext#getFileCopy()}
     * corresponding to the given {@code assignStmt}.
     *
     * @param assignStmt an assignment statement
     * @return the method call which corresponds to the given assignment
     */
    private PsiMethodCallExpression getMethodCallExpression(@NonNull DefinitionStmt assignStmt) {
        if (!(assignStmt.getRightOp() instanceof InvokeExpr)) return null;

        // find the method call source tag
        SourceLnPosTag tag = findAssignedValueTag(assignStmt);
        if (tag == null) return null;

        // find the method call
        PsiJavaFile fileCopy = context.getFileCopy();
        OffsetsTracker tracker = context.getTracker();
        PsiElement rightEl = tracker.getElementAtPosition(fileCopy, tag);
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(rightEl,
                PsiMethodCallExpression.class, false);

        if (call == null) {
            // sometimes the source tag will point to the variable when the
            // initializer is a method call
            PsiVariable psiVar = PsiTreeUtil.getParentOfType(rightEl,
                    PsiVariable.class, false);
            if (psiVar == null) return null;

            PsiExpression initializer = psiVar.getInitializer();

            if (initializer == null) return null;

            call = PsiTools.getMethodCallChild(initializer);
        }
        return call;
    }

    /**
     * Returns the source line/column tag for the assigned value of the given assignment.
     *
     * @param assignStmt an assignment statement
     * @return the source line/column tag, if any
     */
    private static SourceLnPosTag findAssignedValueTag(@NonNull DefinitionStmt assignStmt) {
        // figure out which source tag is the right one to read
        SourceLnPosTag stmtSrcTag = SootTools.getSourceTag(assignStmt);
        SourceLnPosTag valueSrcTag = SootTools.getSourceTag(assignStmt.getRightOpBox());

        SourceLnPosTag actualTag;
        if (valueSrcTag != null) {
            actualTag = valueSrcTag;
        } else {
            actualTag = stmtSrcTag;
        }
        return actualTag;
    }

    /**
     * Describes how to invoke a class constructor.
     */
    private class ExceptionConstructionDescriptor {
        private final String className;
        private final List<Type> paramTypes;
        private final List<Value> args;

        public ExceptionConstructionDescriptor(@NonNull String className,
                @NonNull List<Type> paramTypes, @NonNull List<Value> paramArgs) {
            this.className = className;
            this.paramTypes = paramTypes;
            this.args = paramArgs;
        }

        public @NonNull String getClassName() {
            return className;
        }

        public @NonNull List<Type> getParameterTypes() {
            return paramTypes;
        }

        public @NonNull List<Value> getArguments() {
            return args;
        }
    }
}
