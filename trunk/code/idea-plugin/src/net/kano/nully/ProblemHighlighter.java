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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiSuperMethodUtil;
import static net.kano.nully.NullProblemType.INVALID_NONNULL_OVERRIDE;
import static net.kano.nully.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProblemHighlighter {
    public List<ProblemDescriptor> highlightProblems(MethodInfo info) {
        ProblemFinder finder = new NullValueProblemFinder();
        List<PsiNullProblem> psiProblems = finder.findProblems(info);
        List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
        InspectionManager mgr = info.getInspectionManager();
        for (PsiNullProblem psiProblem : psiProblems) {
            ProblemDescriptor prob = getProblem(mgr, psiProblem);

            if (prob != null) problems.add(prob);
        }

        return problems;
    }

    private ProblemDescriptor getProblem(InspectionManager mgr,
            PsiNullProblem psiProblem) {
        PsiElement element = psiProblem.getElement();
        NullProblemType type = psiProblem.getType();
        LocalQuickFix fix = null;

        String desc = null;
        if (type == NULL_ARGUMENT_FOR_NONNULL_PARAMETER) {
            PsiMethod method = NullyTools.getCalledMethod(element);
            desc = "<HTML>Argument passed to <B>" + method.getName()
                    + "()</B> may be illegally null";

        } else if (type == NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
            PsiVariable var = NullyTools.getAssignedVariable(element);
            desc = "<HTML>Value assigned to <B>" + var.getName()
                                + "</B> may be illegally null";

        } else if (type == NULL_RETURN_IN_NONNULL_METHOD) {
            desc = "Returned value may be ilegally null";

        } else if (type == INVALID_NONNULL_OVERRIDE) {
            //TODO: test override inspection & fix; implement for compiler
            PsiMethod method = (PsiMethod) element;
            List<PsiMethod> bad = new ArrayList<PsiMethod>();
            for (PsiMethod superm : PsiSuperMethodUtil.findSuperMethods(method)) {
                if (NullyTools.hasNonNullAnnotation(superm)) bad.add(superm);
            }
            boolean found = false;
            for (PsiMethod badMethod : bad) {
                if (!badMethod.getContainingClass().isInterface()) {
                    // we found the real method
                    desc = "<HTML>Method illegally overrides @NonNull method "
                            + getQualifiedMethodName(badMethod)
                            + " without @NonNull declaration";
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (bad.size() == 1) {
                    desc = "<HTML>Method illegally implements @NonNull method "
                            + getQualifiedMethodName(bad.get(0))
                            + " without @NonNull declaration";
                } else {
                    Set<PsiClass> badInImplementsList
                            = getBadClassesInImplementsList(method, bad);

                    if (!badInImplementsList.isEmpty()) {
                        // some bad superclass is in the implements list
                        if (badInImplementsList.size() == 1) {
                            desc = "<HTML>Method illegally implements @NonNull method "
                                    + getQualifiedMethodName(bad.get(0))
                                    + " without @NonNull declaration";
                        }
                    }
                    if (desc == null) {
                        desc = "<HTML>Method illegally implements @NonNull "
                                + "methods without @NonNull declaration";
                    }
                }
            }
            fix = new AddNonNullDeclarationFix();
        }

        if (desc == null) {
            return null;
        } else {
            return mgr.createProblemDescriptor(element, desc, fix,
                    GENERIC_ERROR_OR_WARNING);
        }
    }

    private static Set<PsiClass> getBadClassesInImplementsList(PsiMethod method,
            List<PsiMethod> bad) {
        Set<PsiClass> badSuperclasses = new HashSet<PsiClass>();
        for (PsiMethod psiMethod : bad) {
            badSuperclasses.add(psiMethod.getContainingClass());
        }
        Set<PsiClass> inImplementsList = new HashSet<PsiClass>();

        for (PsiClassType itype : method.getContainingClass()
                .getImplementsListTypes()) {
            inImplementsList.add(itype.resolve());
        }

        badSuperclasses.retainAll(inImplementsList);
        return badSuperclasses;
    }

    private static String getQualifiedMethodName(PsiMethod method) {
        return method.getContainingClass().getName() + "." + method.getName();
    }

}
