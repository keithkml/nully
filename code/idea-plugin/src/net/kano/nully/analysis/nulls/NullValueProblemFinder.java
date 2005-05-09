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

package net.kano.nully.analysis.nulls;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;
import net.kano.nully.OffsetsTracker;
import net.kano.nully.analysis.AnalysisContext;
import net.kano.nully.analysis.ProblemFinder;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.tagkit.SourceLnPosTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NullValueProblemFinder implements ProblemFinder<NullValueProblem> {
    private static final Logger LOGGER
            = Logger.getInstance(NullValueProblemFinder.class.getName());

    private AnalysisContext context = null;
    private List<NullValueProblem> problems = null;

    public synchronized @NonNull Collection findProblems(
            @NonNull AnalysisContext context) {
        this.context = context;
        this.problems = new ArrayList<NullValueProblem>();
        for (SootMethod method : context.getSootMethods()) {
            PsiMember member = NullyTools.getPsiMemberCopy(context, method);
            if (member != null) {
                PsiMember origMember = context.getOriginalElement(member);
                if (!NullyTools.shouldCheckNulls(origMember)) continue;
            }

            tagProblemsForMember(method.retrieveActiveBody());
        }
        return problems;
    }

    private synchronized void tagProblemsForMember(@NonNull Body body) {
        for (Unit unit : (Collection<Unit>)body.getUnits()) {
            if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                ValueBox rightBox = assignStmt.getRightOpBox();
                if (NullyTools.hasMayBeNullTag(rightBox)) {
                    findNullAssignments(assignStmt);
                }

                if (rightBox.getValue() instanceof InvokeExpr) {
                    findNullArgs(assignStmt, rightBox);
                }
            } else if (unit instanceof ReturnStmt) {
                PsiElement el = context.getTracker().getElementAtPosition(context.getFileCopy(),
                        NullyTools.getSourceTag(unit));
                PsiMethod method = PsiTreeUtil.getParentOfType(el, PsiMethod.class, false);
                if (method != null) {
                    tagNullReturn((ReturnStmt) unit, method);
                } else {
                    LOGGER.debug("Couldn't find corresponding method for " + unit);
                }

            } else if (unit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) unit;
                findNullArgs(invokeStmt);
            }
        }
    }

    private synchronized void tagNullReturn(@NonNull ReturnStmt retStmt,
            @NonNull PsiMethod methodCopy) {
        ValueBox retBox = retStmt.getOpBox();
        if (!NullyTools.hasMayBeNullTag(retBox)) return;

        PsiMethod method = context.getOriginalElement(methodCopy);
        if (!NullyTools.hasNonNullAnnotation(method)) return;

        SourceLnPosTag srcTag = NullyTools.getSourceTag(retBox);
        if (srcTag == null) return;

        int offset = NullyTools.getOffset(context.getTracker(), srcTag);
        PsiElement el = context.getFileCopy().findElementAt(offset);
        PsiReturnStatement psiRetCopy = PsiTreeUtil.getParentOfType(el,
                PsiReturnStatement.class, false);
        if (psiRetCopy == null) return;

        PsiReturnStatement psiRet = context.getOriginalElement(psiRetCopy);
        // this psiRet == null thing is a hack to catch the "return null"s that
        // are inserted when stripping
        if (psiRet == null) return;
        PsiExpression retVal = psiRet.getReturnValue();
        problems.add(new NullValueProblem(NULL_RETURN_IN_NONNULL_METHOD, retVal));
    }

    private synchronized void findNullArgs(@NonNull InvokeStmt invokeStmt) {
        SourceLnPosTag tag = NullyTools.getSourceTag(invokeStmt);
        if (tag == null) return;

        findNullArgs(invokeStmt.getInvokeExpr(), tag);
    }

    private synchronized void findNullArgs(@NonNull Unit unit, @NonNull ValueBox invokeBox) {
        InvokeExpr invokeExpr = (InvokeExpr) invokeBox.getValue();
        SourceLnPosTag srcTag = NullyTools.getSourceTag(invokeBox);
        if (srcTag == null) srcTag = NullyTools.getSourceTag(unit);
        if (srcTag == null) return;

        findNullArgs(invokeExpr, srcTag);
    }

    private synchronized void findNullArgs(@NonNull InvokeExpr invokeExpr,
            @NonNull SourceLnPosTag srcTag) {
        OffsetsTracker tracker = context.getTracker();
        int offset = NullyTools.getOffset(tracker, srcTag);

        PsiJavaFile fileCopy = context.getFileCopy();
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
            call = NullyTools.getMethodCallChild(initializer);
        }
        if (call == null) return;

        // find the referenced method
        PsiMethod referencedMethod = call.resolveMethod();
        if (referencedMethod == null) return;
        PsiMethod referencedMethodOrig = context.getOriginalElement(referencedMethod);
        if (referencedMethodOrig != null) referencedMethod = referencedMethodOrig;

        // look for non-null parameters which may be passed null
        PsiParameter[] params = referencedMethod.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            PsiParameter param = params[i];
            ValueBox argBox = invokeExpr.getArgBox(i);
            if (NullyTools.hasNonNullAnnotation(param)
                        && NullyTools.hasMayBeNullTag(argBox)) {
                // we found a possibly null argument for nonnull parameter
                SourceLnPosTag argSrcTag = NullyTools.getSourceTag(argBox);
                if (argSrcTag == null) continue;

                int argOffset = NullyTools.getOffset(tracker, argSrcTag);
                PsiElement argel = fileCopy.findElementAt(argOffset);
                if (argel == null) continue;
                argel = findArgumentElementParent(call, argel);
                if (argel == null) continue;

                argel = context.getOriginalElement(argel);
                if (argel == null) continue;

                problems.add(new NullValueProblem(NULL_ARGUMENT_FOR_NONNULL_PARAMETER, argel));
            }
        }
    }

    private static synchronized PsiElement findArgumentElementParent(
            @NonNull PsiMethodCallExpression call,
            @NonNull PsiElement argel) {
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

    private synchronized void findNullAssignments(@NonNull AssignStmt assignStmt) {
        ValueBox rightBox = assignStmt.getRightOpBox();
        ValueBox varBox = assignStmt.getLeftOpBox();
        SourceLnPosTag srcTag = NullyTools.getSourceTag(varBox);
        if (srcTag == null) srcTag = NullyTools.getSourceTag(assignStmt);
        if (srcTag == null) return;

        PsiJavaFile fileCopy = context.getFileCopy();

        OffsetsTracker tracker = context.getTracker();
        int offset = NullyTools.getOffset(tracker, srcTag);
        PsiElement leftEl = fileCopy.findElementAt(offset);
        PsiVariable variable = NullyTools.getReferencedVariable(leftEl);
        if (variable == null) return;
        PsiVariable origVariable = context.getOriginalElement(variable);
        if (!NullyTools.hasNonNullAnnotation(origVariable)) return;

        SourceLnPosTag tag = NullyTools.getSourceTag(rightBox);
        int roff = NullyTools.getOffset(tracker, tag);
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
                hilite = context.getOriginalElement(initializer);
            }
        } else {
            PsiExpression initializer = assignment.getRExpression();
            hilite = context.getOriginalElement(initializer);
        }
        if (hilite != null) {
            problems.add(new NullValueProblem(NULL_ASSIGNMENT_TO_NONNULL_VARIABLE,
                    hilite));
        }
    }
}
