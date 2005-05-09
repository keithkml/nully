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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import net.kano.nully.analysis.AnalysisContext;
import net.kano.nully.analysis.NullyPsiProblem;
import net.kano.nully.analysis.ProblemFinder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public abstract class ProblemFinderBasedInspector<F extends ProblemFinder<P>,
        P extends NullyPsiProblem> extends AbstractNullyInspection {
    protected static final Logger LOGGER
            = Logger.getInstance(IllegalOverrideInspector.class.getName());

    protected abstract F getFinderInstance();
    protected abstract EnumSet<InspectionType> getInspectionTypes();

    private static PsiJavaFile getParentJavaFile(PsiElement el) {
        PsiFile container = el.getContainingFile();
        PsiJavaFile jfile;
        if (!(container instanceof PsiJavaFile)) {
            jfile = null;
        } else {
            jfile = (PsiJavaFile) container;
        }
        return jfile;
    }

    public ProblemDescriptor[] checkFile(PsiFile file,
            InspectionManager manager, boolean isOnTheFly) {
        if (!(file instanceof PsiJavaFile)) return null;
        if (!getInspectionTypes().contains(InspectionType.FILE)) return null;

        AnalysisContext context = new AnalysisContext();
        PsiJavaFile jfile = (PsiJavaFile) file;
        try {
            prepareContextForFile(context, jfile);

            return findProblems(context, manager);
        } finally {
            cleanUp(context);
        }
    }

    public ProblemDescriptor[] checkMethod(PsiMethod method,
            InspectionManager manager, boolean isOnTheFly) {
        if (!getInspectionTypes().contains(InspectionType.METHOD)) return null;

        PsiJavaFile jfile = getParentJavaFile(method);
        if (jfile == null) return null;

        AnalysisContext context = new AnalysisContext();
        try {
            prepareContextForMethod(context, jfile, method);

            return findProblems(context, manager);
        } finally {
            cleanUp(context);
        }
    }

    public ProblemDescriptor[] checkField(PsiField field,
            InspectionManager manager, boolean isOnTheFly) {
        if (!getInspectionTypes().contains(InspectionType.FIELD)) return null;

        PsiJavaFile jfile = getParentJavaFile(field);
        if (jfile == null) return null;

        AnalysisContext context = new AnalysisContext();
        try {
            prepareContextForField(context, jfile);

            ProblemDescriptor[] problems = findProblems(context, manager);
            return problems;
        } finally {
            cleanUp(context);
        }
    }

    protected void cleanUp(AnalysisContext context) {
    }

    private ProblemDescriptor[] findProblems(AnalysisContext context,
            InspectionManager manager) {
        List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
        for (P problem : getFinderInstance().findProblems(context)) {
            addProblems(manager, problems, problem);
        }
        return problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    protected void prepareContextForFile(AnalysisContext context,
            PsiJavaFile jfile) {
        prepareContext(context, jfile);
    }

    private void prepareContextForField(AnalysisContext context,
            PsiJavaFile jfile) {
        prepareContext(context, jfile);
    }

    protected void prepareContextForMethod(AnalysisContext context,
            PsiJavaFile jfile, PsiMethod method) {
        prepareContext(context, jfile);
    }

    protected void prepareContext(AnalysisContext context, PsiJavaFile jfile) {
        context.setFileOrig(jfile);
    }

    protected abstract void addProblems(InspectionManager manager,
            List<ProblemDescriptor> problems,
            P problem);

    protected static enum InspectionType { METHOD, FIELD, FILE }
}
