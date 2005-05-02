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

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import static net.kano.nully.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import soot.Body;
import soot.Unit;
import soot.ValueBox;
import soot.SootMethod;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.tagkit.SourceLnPosTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NullValueProblemFinder implements ProblemFinder {
    private MethodInfo info = null;
    private List<PsiNullProblem> problems = null;

    public synchronized List<PsiNullProblem> findProblems(MethodInfo info) {
        this.info = info;
        this.problems = new ArrayList<PsiNullProblem>();
        for (SootMethod method : info.getSootMethods()) {
            PsiMethod psiMethod = NullyTools.getPsiMethod(info, method);
            if (psiMethod == null) continue;
            tagProblemsForMethod(method.retrieveActiveBody(),
                    psiMethod);
        }
        return problems;
    }

    private void tagProblemsForMethod(Body body, PsiMethod method) {
        for (Unit unit : (Collection<Unit>)body.getUnits()) {
            if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                ValueBox rightBox = assignStmt.getRightOpBox();
                if (rightBox.hasTag("MayBeNull")) {
                    findNullAssignments(assignStmt);
                }

                if (rightBox.getValue() instanceof InvokeExpr) {
                    findNullArgs(rightBox);
                }
            } else if (unit instanceof ReturnStmt) {
                tagNullReturn((ReturnStmt) unit, method);

            } else if (unit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) unit;
                findNullArgs(invokeStmt);
            }
        }
    }

    private void tagNullReturn(ReturnStmt retStmt, PsiMethod methodCopy) {
        ValueBox retBox = retStmt.getOpBox();
        if (!retBox.hasTag("MayBeNull")) return;

        PsiMethod method = NullyTools.getOriginalElement(methodCopy);
        if (!NullyTools.hasNonNullAnnotation(method)) return;

        SourceLnPosTag srcTag = NullyTools.getSourceTag(retBox);
        if (srcTag == null) return;

        int offset = info.getTracker().getOffset(srcTag.startLn(), srcTag.startPos());
        PsiElement el = info.getFileCopy().findElementAt(offset);
        PsiReturnStatement psiRetCopy = PsiTreeUtil.getParentOfType(el,
                PsiReturnStatement.class, false);
        if (psiRetCopy == null) return;

        PsiReturnStatement psiRet = NullyTools.getOriginalElement(psiRetCopy);
        //TODO: this psiRet == null thing is a hack to catch the "return null"s that are inserted when stripping
        if (psiRet == null) return;
        PsiExpression retVal = psiRet.getReturnValue();
        problems.add(new PsiNullProblem(NULL_RETURN_IN_NONNULL_METHOD, retVal));
    }

    private void findNullArgs(InvokeStmt invokeStmt) {
        SourceLnPosTag tag = NullyTools.getSourceTag(invokeStmt);
        if (tag == null) return;

        findNullArgs(invokeStmt.getInvokeExpr(), tag);
    }

    private void findNullArgs(ValueBox invokeBox) {
        InvokeExpr invokeExpr = (InvokeExpr) invokeBox.getValue();
        SourceLnPosTag srcTag = NullyTools.getSourceTag(invokeBox);
        if (srcTag == null) return;

        findNullArgs(invokeExpr, srcTag);
    }

    private void findNullArgs(InvokeExpr invokeExpr, SourceLnPosTag srcTag) {
        OffsetsTracker tracker = info.getTracker();
        int offset = tracker.getOffset(srcTag.startLn(), srcTag.startPos());

        PsiJavaFile fileCopy = info.getFileCopy();
        PsiElement el = fileCopy.findElementAt(offset);
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(el,
                PsiMethodCallExpression.class, false);
        if (call == null) {
            // sometimes Soot parses a variable assignment to method call as just
            // a method call, but it uses the variable assignment line/col, so
            // we need to detect that
            PsiVariable var = PsiTreeUtil.getParentOfType(el, PsiVariable.class);
            if (var == null) return;

            PsiExpression initializer = var.getInitializer();
            if (initializer == null) return;
            if (initializer instanceof PsiMethodCallExpression) {
                call = (PsiMethodCallExpression) initializer;
            } else {
                call = PsiTreeUtil.getChildOfType(
                                initializer, PsiMethodCallExpression.class);
            }
        }
        if (call == null) return;

        // find the referenced method
        PsiMethod referencedMethod = (PsiMethod) call.getMethodExpression().resolve();
        PsiMethod referencedMethodOrig = (PsiMethod) NullyTools.getOriginalElement(referencedMethod);
        if (referencedMethodOrig != null) referencedMethod = referencedMethodOrig;

        //TODO: fix method arg checks
        // look for non-null parameters which may be passed null
        PsiParameter[] params = referencedMethod.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            PsiParameter param = params[i];
            ValueBox argBox = invokeExpr.getArgBox(i);
            if (NullyTools.hasNonNullAnnotation(param)
                        && argBox.hasTag("MayBeNull")) {
                // we found a possibly null argument for nonnull parameter
                SourceLnPosTag argSrcTag = NullyTools.getSourceTag(argBox);
                if (argSrcTag == null) continue;

                int argOffset = tracker.getOffset(argSrcTag.startLn(), argSrcTag.startPos());
                PsiElement argel = fileCopy.findElementAt(argOffset);
                if (argel == null) continue;
                argel = findArgumentElementParent(call, argel);
                if (argel == null) continue;

                argel = NullyTools.getOriginalElement(argel);
                if (argel == null) continue;

                problems.add(new PsiNullProblem(NULL_ARGUMENT_FOR_NONNULL_PARAMETER, argel));
            }
        }
    }

    private static PsiElement findArgumentElementParent(PsiMethodCallExpression call,
            PsiElement argel) {
        for (;;) {
            PsiElement parent = argel.getParent();
            if (parent == null) {
                argel = null;
                break;
            }
            PsiElement grandparent = parent.getParent();
            if (grandparent == null) {
                argel = null;
                break;
            }
            if (parent instanceof PsiExpressionList && grandparent == call) break;
            argel = parent;
        }
        return argel;
    }

    private void findNullAssignments(AssignStmt assignStmt) {
        ValueBox rightBox = assignStmt.getRightOpBox();
        ValueBox varBox = assignStmt.getLeftOpBox();
        SourceLnPosTag srcTag = NullyTools.getSourceTag(varBox);
        if (srcTag == null) return;

        PsiJavaFile fileCopy = info.getFileCopy();

        OffsetsTracker tracker = info.getTracker();
        int offset = tracker.getOffset(srcTag.startLn(), srcTag.startPos());
        PsiElement leftEl = fileCopy.findElementAt(offset);
        PsiVariable variable = NullyTools.getReferencedVariable(leftEl);
        PsiVariable origVariable = NullyTools.getOriginalElement(variable);
        if (!NullyTools.hasNonNullAnnotation(origVariable)) return;

        SourceLnPosTag tag = NullyTools.getSourceTag(rightBox);
        int roff = tracker.getOffset(tag.startLn(), tag.startPos());
        PsiElement rightEl = fileCopy.findElementAt(roff);
        PsiAssignmentExpression assignment
                = PsiTreeUtil.getParentOfType(rightEl,
                        PsiAssignmentExpression.class, false);
        PsiElement hilite = null;
        if (assignment == null) {
            PsiLocalVariable var = PsiTreeUtil.getParentOfType(rightEl,
                            PsiLocalVariable.class, false);
            if (var != null) {
                PsiExpression initializer = var.getInitializer();
                hilite = NullyTools.getOriginalElement(initializer);
            }
        } else {
            PsiExpression initializer = assignment.getRExpression();
            hilite = NullyTools.getOriginalElement(initializer);
        }
        if (hilite != null) {
            problems.add(new PsiNullProblem(NULL_ASSIGNMENT_TO_NONNULL_VARIABLE, hilite));
        }
    }
}
