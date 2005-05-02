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

package net.kano.nully;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.SootMethod;
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

public class JimpleMethodInstrumenter {
    private MethodInfo info = null;

    public void instrumentSootCode(MethodInfo info) {
        this.info = info;
        for (SootMethod method : info.getSootMethods()) {
            PsiMethod psiMethod = NullyTools.getPsiMethod(info, method);
            if (psiMethod == null) continue;
            instrumentSootCode(psiMethod, method.retrieveActiveBody());
        }
    }

    private void instrumentSootCode(PsiMethod psiMethod, Body body) {
        Collection<Unit> units = body.getUnits();
        boolean changed = true;
        for (int i = 0; changed && i < 1000000; i++) {
            changed = false;
            for (Unit unit : units) {
                if (unit instanceof DefinitionStmt) {
                    DefinitionStmt defStmt = (DefinitionStmt) unit;
                    changed = fixAssignment(defStmt, body, psiMethod);
                }
                if (changed) break;
            }
        }
    }

/*
    private boolean fixReturn(ReturnStmt returnStmt) {
        PsiMethod method = (PsiMethod) NullyTools.getOriginalElement(methodCopy);
        PsiModifierList mods = method.getModifierList();
        if (mods.findAnnotation(NullyTools.ANNO_NONNULL) == null) return false;

        Jimple jimple = Jimple.v();

        // we copy the old return value of the assignment to a new variable, so
        // we can check its nullness
        Chain locals = body.getLocals();
        Local newLocal = jimple.newLocal(getUnusedLocalName(locals),
                        body.getMethod().getReturnType());
        locals.add(newLocal);
        AssignStmt copyStmt = jimple.newAssignStmt(newLocal, returnStmt.getOp());

        IfStmt ifStmt = jimple.newIfStmt(jimple.newNeExpr(newLocal, NullConstant.v()),
                        returnStmt);

        Local exceptionLocal = jimple.newLocal(getUnusedLocalName(locals),
                        RefType.v("java.lang.RuntimeException"));
        locals.add(exceptionLocal);

        Scene scene = Scene.v();
        String cls = NullyTools.CLASS_NULLRETURN;

        SootClass exceptionClass = scene.getSootClass(cls);
        AssignStmt assignStmt = jimple.newAssignStmt(exceptionLocal,
                        jimple.newNewExpr(RefType.v(exceptionClass)));

        InvokeStmt specialInvokeStmt = jimple.newInvokeStmt(
                        jimple.newSpecialInvokeExpr(exceptionLocal,
                                scene.makeConstructorRef(exceptionClass,
                                        Collections.EMPTY_LIST)));
        ThrowStmt throwStmt = jimple.newThrowStmt(exceptionLocal);

        List stmts = new ArrayList();
        stmts.add(copyStmt);
        stmts.add(ifStmt);
        stmts.add(assignStmt);
        stmts.add(specialInvokeStmt);
        stmts.add(throwStmt);

        PatchingChain units = body.getUnits();
        units.insertBefore(stmts, returnStmt);
        returnStmt.setOp(newLocal);
        return true;
    }
*/

    /**
     * Adds appropriate null check "assertions" after assignment to @NonNull
     * variables, to reflect the runtime checks that the precompiler inserts
     */
    private boolean fixAssignment(DefinitionStmt assignStmt, Body body,
            PsiMethod method) {
        ValueBox varBox = assignStmt.getLeftOpBox();
        ValueBox rightOpBox = assignStmt.getRightOpBox();

        SourceLnPosTag stmtSrcTag = NullyTools.getSourceTag(assignStmt);
        SourceLnPosTag varSrcTag = NullyTools.getSourceTag(varBox);
        SourceLnPosTag valueSrcTag = NullyTools.getSourceTag(rightOpBox);

        Value assignedValue = rightOpBox.getValue();

        // STEP 1 - add null checks right after assignment if assignment
        //          to @NonNull method call result
        OffsetsTracker tracker = info.getTracker();
        PsiJavaFile fileCopy = info.getFileCopy();
        if (assignedValue instanceof InvokeExpr) {

            SourceLnPosTag useTag;
            if (valueSrcTag != null) {
                useTag = valueSrcTag;
            } else {
                useTag = stmtSrcTag;
            }

            if (useTag == null) return false;

            PsiElement rightEl = tracker.getElementAtPosition(fileCopy, useTag);
            PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(rightEl,
                    PsiMethodCallExpression.class, false);
            if (call == null) {
                PsiVariable psiVar = PsiTreeUtil.getParentOfType(rightEl,
                                PsiVariable.class, false);
                if (psiVar == null) return false;

                PsiExpression initializer = psiVar.getInitializer();

                if (initializer == null) return false;
                if (initializer instanceof PsiMethodCallExpression) {
                    call = (PsiMethodCallExpression) initializer;
                } else {
                    call = PsiTreeUtil.getChildOfType(initializer,
                            PsiMethodCallExpression.class);
                }
            }

            PsiMethod referencedMethod = (PsiMethod) call.getMethodExpression().resolve();
            PsiMethod referencedMethodOrig = NullyTools.getOriginalElement(referencedMethod);
            if (referencedMethodOrig != null) referencedMethod = referencedMethodOrig;
            if (NullyTools.hasNonNullAnnotation(referencedMethod)) {
                if (addUnexpectedNullCheck(assignStmt, true, body)) {
                    System.out.println("I fixed method call to "
                            + referencedMethod.getContainingClass().getQualifiedName()
                            + " " + referencedMethod.getName());
                    return true;
                }
            }

        } else if (assignedValue instanceof ParameterRef) {
            //TODO: throw nullparamexception for params
            ParameterRef ref = (ParameterRef) assignedValue;
            PsiParameterList parameterList = method.getParameterList();
            int paramIndex = ref.getIndex();
            PsiParameter paramCopy = parameterList.getParameters()[paramIndex];
            PsiParameter param = NullyTools.getOriginalElement(paramCopy);
            if (NullyTools.hasNonNullAnnotation(param)) {
                //TODO: make sure the new exception code works, and inserts before/after correctly for this and for STEP2
                Type[] types = { RefType.v("java.lang.String"), IntType.v() };
                Value[] values = { StringConstant.v(param.getName()),
                                    IntConstant.v(parameterList.getParameterIndex(paramCopy)) };
                if (addNullCheck(assignStmt, false, NullyTools.CLASS_NULLPARAM,
                        Arrays.asList(types), Arrays.asList(values), body)) {
                    System.out.println("I fixed parameter reference " + param.getName());
                    return true;
                }
            }
        }

        if (varSrcTag == null) return false;

        // STEP 2 - add null checks if assignment to @NonNull variable
        PsiElement varEl = tracker.getElementAtPosition(fileCopy, varSrcTag);
        PsiVariable varCopy = NullyTools.getReferencedVariable(varEl);
        PsiVariable var = NullyTools.getOriginalElement(varCopy);
        if (NullyTools.hasNonNullAnnotation(var)) {
            if (addUnexpectedNullCheck(assignStmt, false, body)) {
                System.out.println("I fixed assignment to " + var.getName());
                return true;
            }
        }

        return false;
    }

    private boolean addUnexpectedNullCheck(DefinitionStmt assignStmt,
            boolean before, Body body) {
        return addNullCheck(assignStmt, before, NullyTools.CLASS_UNEXPECTEDNULL,
                Collections.<Type>emptyList(), Collections.<Value>emptyList(), body);
    }

    private boolean addNullCheck(DefinitionStmt forStmt, boolean before,
            String className,
            List<Type> constTypes,
            List<Value> constValues, Body body) {
        if (forStmt.hasTag("FixedNullAssignment")) return false;

        Jimple jimple = Jimple.v();

        // we copy the old RHS of the assignment to a new value, so the
        // assignment to the actual local is guaranteed not to be null
        AssignStmt copyStmt = null;
        Chain locals = body.getLocals();
        Local holder;
        if (before) {
            Local newLocal = jimple.newLocal(getUnusedLocalName(locals),
                            forStmt.getLeftOp().getType());
            locals.add(newLocal);
            copyStmt = jimple.newAssignStmt(newLocal, forStmt.getRightOp());
            // copy tags over
            copyStmt.getRightOpBox().addAllTagsOf(forStmt.getRightOpBox());
            copyStmt.addTag(new FixedNullAssignmentTag());
            holder = newLocal;
        } else {
            holder = (Local) forStmt.getLeftOp();
        }

        IfStmt ifStmt = jimple.newIfStmt(jimple.newNeExpr(holder, NullConstant.v()),
                        forStmt);

        Local exceptionLocal = jimple.newLocal(getUnusedLocalName(locals),
                        RefType.v("java.lang.RuntimeException"));
        locals.add(exceptionLocal);

        Scene scene = Scene.v();
        SootClass exceptionClass = scene.getSootClass(className);
        AssignStmt assignStmt = jimple.newAssignStmt(exceptionLocal,
                        jimple.newNewExpr(RefType.v(exceptionClass)));

        InvokeStmt specialInvokeExpr = jimple.newInvokeStmt(
                        jimple.newSpecialInvokeExpr(exceptionLocal,
                                scene.makeConstructorRef(exceptionClass,
                                        constTypes), constValues));
        ThrowStmt throwStmt = jimple.newThrowStmt(exceptionLocal);

        List<Stmt> stmts = new ArrayList<Stmt>();
        if (copyStmt != null) stmts.add(copyStmt);
        stmts.add(ifStmt);
        stmts.add(assignStmt);
        stmts.add(specialInvokeExpr);
        stmts.add(throwStmt);

        PatchingChain units = body.getUnits();
        if (before) {
            units.insertBefore(stmts, forStmt);
            ifStmt.setTarget(forStmt);
        } else {
            ifStmt.setTarget((Unit) units.getSuccOf(forStmt));
            units.insertAfter(stmts, forStmt);
        }
        if (before) {
            ValueBox rightOpBox = forStmt.getRightOpBox();
            List<Tag> tags = new ArrayList<Tag>(rightOpBox.getTags());
            rightOpBox.setValue(holder);
            for (Object tag1 : tags) {
                Tag tag = (Tag) tag1;
                rightOpBox.addTag(tag);
            }
//            rightOpBox.addAllTagsOf(forStmt.getRightOpBox());
        }

        forStmt.addTag(new FixedNullAssignmentTag());
        return true;
    }

    private String getUnusedLocalName(Chain locals) {
        int r = 0;
        for (;;r++) {
            String tryName = "$r" + r;
            boolean good = true;
            for (Object local1 : locals) {
                Local local = (Local) local1;
                if (local.getName().equals(tryName)) {
                    good = false;
                    break;
                }
            }
            if (good) return tryName;
        }
    }
}
