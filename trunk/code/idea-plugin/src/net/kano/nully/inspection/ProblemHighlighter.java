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

package net.kano.nully.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import net.kano.nully.NullyTools;
import net.kano.nully.analysis.AnalysisInfo;
import net.kano.nully.analysis.NullProblemType;
import static net.kano.nully.analysis.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.analysis.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.analysis.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import net.kano.nully.analysis.ProblemFinder;
import net.kano.nully.analysis.PsiNullProblem;

import java.util.ArrayList;
import java.util.List;

public class ProblemHighlighter {
    private static final Logger LOGGER = Logger.getInstance(ProblemHighlighter.class.getName());

    public List<ProblemDescriptor> highlightProblems(AnalysisInfo info,
            InspectionManager mgr, List<ProblemFinder> finders) {
        List<PsiNullProblem> psiProblems = new ArrayList<PsiNullProblem>();

        for (ProblemFinder finder : finders) {
            psiProblems.addAll(finder.findProblems(info));
        }

        List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
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

        }

        if (desc == null) {
            return null;
        } else {
            return mgr.createProblemDescriptor(element, desc, fix,
                    GENERIC_ERROR_OR_WARNING);
        }
    }

}
