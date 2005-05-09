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
// *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;
import net.kano.nully.analysis.AnalysisContext;
import net.kano.nully.analysis.nulls.CodeAnalyzer;
import net.kano.nully.analysis.nulls.NullValueProblemFinder;
import net.kano.nully.analysis.nulls.NullProblemType;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import net.kano.nully.analysis.nulls.NullValueProblem;
import net.kano.nully.analysis.nulls.psipreprocess.PreparerForSoot;

import java.util.EnumSet;
import java.util.List;

public class NullProblemInspector
        extends ProblemFinderBasedInspector<NullValueProblemFinder, NullValueProblem> {
    public String getDisplayName() {
        return "Possibly null value in @" + NonNull.class.getSimpleName() + " context";
    }

    public String getShortName() {
        return "NullyNullProblemCheck";
    }

    protected NullValueProblemFinder getFinderInstance() {
        return new NullValueProblemFinder();
    }

    protected EnumSet<InspectionType> getInspectionTypes() {
        return EnumSet.of(InspectionType.FILE);
    }

    protected void prepareContextForFile(AnalysisContext context,
            PsiJavaFile jfile) {
        PreparerForSoot preparer = new PreparerForSoot(context);
        context.setPreparer(preparer);
        preparer.prepareForFileAnalysis(jfile);

        CodeAnalyzer analyzer = new CodeAnalyzer();
        context.setAnalyzer(analyzer);
        analyzer.analyze(context);
    }

    protected void cleanUp(AnalysisContext context) {
        CodeAnalyzer analyzer = context.getAnalyzer();
        if (analyzer != null) analyzer.resetSoot();
        PreparerForSoot preparer = context.getPreparer();
        if (preparer != null) preparer.removeCopy(context.getFileOrig());
    }

    protected void addProblems(InspectionManager manager,
            List<ProblemDescriptor> problems, NullValueProblem problem) {
        PsiElement element = problem.getElement();
        NullProblemType type = problem.getType();
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

        if (desc != null) {
            problems.add(manager.createProblemDescriptor(element, desc, fix,
                    GENERIC_ERROR_OR_WARNING));
        }
    }
/*
    public ProblemDescriptor[] checkClass(PsiClass aClass,
            InspectionManager manager, boolean isOnTheFly) {
        PsiJavaFile jfile = getParentJavaFile(aClass);
        if (jfile == null) return null;

        List<PsiMember> okayEls = new ArrayList<PsiMember>();
        Collections.addAll(okayEls, aClass.getInitializers());

        return findNullProblems(jfile, okayEls, manager);
    }

    public ProblemDescriptor[] checkField(PsiField field,
            InspectionManager manager, boolean isOnTheFly) {
        PsiJavaFile jfile = getParentJavaFile(field);
        if (jfile == null) return null;

        return findNullProblems(jfile,
                Arrays.<PsiMember>asList(field), manager);
    }

    public ProblemDescriptor[] checkMethod(PsiMethod method,
            InspectionManager manager, boolean isOnTheFly) {
        PsiJavaFile jfile = getParentJavaFile(method);
        if (jfile == null) return null;

        return findNullProblems(jfile,
                Arrays.<PsiMember>asList(method), manager);
    }

    private static PsiJavaFile getParentJavaFile(PsiElement el) {
        PsiFile container = el.getContainingFile();
        PsiJavaFile jfile;
        if (!(container instanceof PsiJavaFile)) jfile = null;
        else jfile = (PsiJavaFile) container;
        return jfile;
    }*/
}
