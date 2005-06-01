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

package net.kano.nully.plugin.analysis.nulls;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.plugin.NullyTools;
import net.kano.nully.plugin.PsiTools;
import net.kano.nully.plugin.SootTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import net.kano.nully.plugin.analysis.ProblemFinder;
import static net.kano.nully.plugin.analysis.nulls.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.plugin.analysis.nulls.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.plugin.analysis.nulls.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import net.kano.nully.plugin.analysis.nulls.soot.MayBeNullTag;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NullValueProblemFinder implements ProblemFinder<NullValueProblem> {
    private static final Logger LOGGER
            = Logger.getInstance(NullValueProblemFinder.class.getName());

    private AnalysisContext context = null;
    private List<NullValueProblem> problems = null;

    public synchronized @NonNull Collection<NullValueProblem> findProblems(
            @NonNull AnalysisContext context) {
        this.context = context;
        this.problems = new ArrayList<NullValueProblem>();
        for (SootMethod method : context.getSootMethods()) {
            PsiMember member = NullyTools.getPsiMemberCopy(context, method);
            if (member != null) {
                PsiMember origMember = context.getOriginalElement(member);
                if (!NullyTools.shouldCheckNulls(origMember, context.getCheckLevels())) continue;
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
                MayBeNullTag tag = SootTools.getMayBeNullTag(rightBox);
                if (tag != null) {
                    findNullAssignments(assignStmt, tag);
                }

                if (rightBox.getValue() instanceof InvokeExpr) {
                    findNullArgs(assignStmt, rightBox);
                }
            } else if (unit instanceof ReturnStmt) {
                PsiElement el = SootTools.getPsiElement(unit);
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
        MayBeNullTag tag = SootTools.getMayBeNullTag(retBox);
        if (tag == null) return;

        PsiMethod method = context.getOriginalElement(methodCopy);
        if (!NullyTools.hasNonNullAnnotation(method)) return;

        PsiElement el = SootTools.getPsiElement(retBox);
        if (el == null) return;
        PsiReturnStatement psiRetCopy = PsiTreeUtil.getParentOfType(el,
                PsiReturnStatement.class, false);
        if (psiRetCopy == null) return;

        PsiReturnStatement psiRet = context.getOriginalElement(psiRetCopy);
        // this psiRet == null thing is a hack to catch the "return null"s that
        // are inserted when stripping
        if (psiRet == null) return;
        PsiExpression retVal = psiRet.getReturnValue();
        problems.add(new NullValueProblem(retVal, NULL_RETURN_IN_NONNULL_METHOD,
                retBox, tag.isDefinitelyNull()));
    }

    private synchronized void findNullArgs(@NonNull InvokeStmt invokeStmt) {
        PsiElement el = SootTools.getPsiElement(invokeStmt);
        if (el == null) return;

        findNullArgs(invokeStmt.getInvokeExpr(), el);
    }

    private synchronized void findNullArgs(@NonNull Unit unit, @NonNull ValueBox invokeBox) {
        InvokeExpr invokeExpr = (InvokeExpr) invokeBox.getValue();
        PsiElement el = SootTools.getPsiElement(invokeBox);
        if (el == null) el = SootTools.getPsiElement(unit);
        if (el == null) return;

        findNullArgs(invokeExpr, el);
    }

    private synchronized void findNullArgs(@NonNull InvokeExpr invokeExpr,
            @NonNull PsiElement el) {
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
            call = PsiTools.getMethodCallChild(initializer);
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
            if (NullyTools.hasNonNullAnnotation(param)) {
                MayBeNullTag tag = SootTools.getMayBeNullTag(argBox);
                if (tag != null) {
                    // we found a possibly null argument for nonnull parameter
                    PsiElement argel = SootTools.getPsiElement(argBox);
                    if (argel == null) continue;

                    argel = findArgumentElementParent(call, argel);
                    if (argel == null) continue;

                    argel = context.getOriginalElement(argel);
                    if (argel == null) continue;

                    problems.add(new NullValueProblem(argel,
                            NULL_ARGUMENT_FOR_NONNULL_PARAMETER, argBox,
                            tag.isDefinitelyNull()));
                }
            }
        }
    }

    private static PsiElement findArgumentElementParent(
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

    private synchronized void findNullAssignments(@NonNull AssignStmt assignStmt,
            MayBeNullTag tag) {
        ValueBox rightBox = assignStmt.getRightOpBox();
        ValueBox varBox = assignStmt.getLeftOpBox();
        PsiElement leftEl = SootTools.getPsiElement(varBox);
        if (leftEl == null) leftEl = SootTools.getPsiElement(assignStmt);
        if (leftEl == null) return;

        PsiVariable variable = PsiTools.getReferencedVariable(leftEl);
        if (variable == null) return;
        PsiVariable origVariable = context.getOriginalElement(variable);
        if (origVariable == null) origVariable = variable;
        if (!NullyTools.hasNonNullAnnotation(origVariable)) return;

        PsiElement rightEl = SootTools.getPsiElement(rightBox);
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
            problems.add(new NullValueProblem(hilite,
                    NULL_ASSIGNMENT_TO_NONNULL_VARIABLE, rightBox,
                    tag.isDefinitelyNull()));
        }
    }
}
