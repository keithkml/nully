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

package net.kano.nully.plugin.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.plugin.PsiTools;
import net.kano.nully.plugin.SootTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import net.kano.nully.plugin.analysis.nulls.CodeAnalyzer;
import net.kano.nully.plugin.analysis.nulls.NullAnalysisProblemFinder;
import net.kano.nully.plugin.analysis.nulls.NullProblem;
import net.kano.nully.plugin.analysis.nulls.NullProblemType;
import static net.kano.nully.plugin.analysis.nulls.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.plugin.analysis.nulls.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.plugin.analysis.nulls.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import net.kano.nully.plugin.analysis.nulls.NullValueProblem;
import net.kano.nully.plugin.analysis.nulls.NullableDereferenceProblem;
import net.kano.nully.plugin.analysis.nulls.DefinitelyNullDereferenceProblem;
import net.kano.nully.plugin.analysis.nulls.NullDereferenceProblem;
import net.kano.nully.plugin.analysis.nulls.psipreprocess.PreparerForSoot;
import org.jdom.DataConversionException;
import org.jdom.Element;

import javax.swing.JComponent;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class NewNullProblemInspector
        extends ProblemFinderBasedInspector<NullAnalysisProblemFinder, NullProblem> {
    private static final Logger LOGGER
            = Logger.getInstance(NewNullProblemInspector.class.getName());

    private NullInspectorOptions options = new NullInspectorOptions();
    private static final String PROP_ONLY_NULLABLE = "onlyNullable";
    private static final String EL_NULL_OPTIONS = "nullOptions";

    public String getDisplayName() {
        return "Possibly null value in @" + NonNull.class.getSimpleName() + " context";
    }

    public void readSettings(Element element) throws InvalidDataException {
        Element optel = element.getChild(EL_NULL_OPTIONS);
        if (optel == null) return;
        
        try {
            options.setOnlyNullable(optel.getAttribute(PROP_ONLY_NULLABLE).getBooleanValue());
        } catch (DataConversionException e) {
            LOGGER.error(e);
            throw new InvalidDataException();
        }
    }

    public void writeSettings(Element element) throws WriteExternalException {
        Element optel = new Element(EL_NULL_OPTIONS);
        optel.setAttribute(PROP_ONLY_NULLABLE, Boolean.toString(options.isOnlyNullable()));
        element.addContent(optel);
    }

    public JComponent createOptionsPanel() {
        return new NullInspectorOptionsPanel(options);
    }

    public String getShortName() {
        return "NullyNullProblemCheck";
    }

    protected NullAnalysisProblemFinder getFinderInstance() {
        return new NullAnalysisProblemFinder();
    }

    protected Set<InspectionType> getInspectionTypes() {
        return EnumSet.of(InspectionType.FILE);
    }

    protected void prepareContext(AnalysisContext context, PsiJavaFile jfile) {
        context.setOptions(options);

        PreparerForSoot preparer = new PreparerForSoot(context);
        context.setPreparer(preparer);
        preparer.prepareForFileAnalysis(jfile);

        SootTools.lockSootGlobally();

        context.setFileOrig(jfile);

        CodeAnalyzer analyzer = new CodeAnalyzer();
        context.setAnalyzer(analyzer);
        analyzer.analyze(context);

        System.out.println("temp");
    }

    protected void cleanUp(AnalysisContext context) {
        try {
            CodeAnalyzer analyzer = context.getAnalyzer();
            if (analyzer != null) analyzer.resetSoot();

            PreparerForSoot preparer = context.getPreparer();
            if (preparer != null) preparer.removeCopy(context.getFileOrig());
        } finally {
            SootTools.unlockSootGlobally();
        }
    }

    protected void addProblems(AnalysisContext context,
            InspectionManager manager,
            List<ProblemDescriptor> problems, NullProblem problem) {
        PsiElement element = problem.getElement();
        String desc = null;
        LocalQuickFix fix = null;
        boolean onlyNullable = context.getOptions().isOnlyNullable();
        if (problem instanceof NullValueProblem) {
            NullValueProblem nvProblem = (NullValueProblem) problem;

            if (nvProblem.isDefinitelyNull() || !onlyNullable
                    || SootTools.hasNullableTag(nvProblem.getValue())) {
                NullProblemType type = nvProblem.getType();

                String maybeString = nvProblem.isDefinitelyNull() ? "is" : "may be";
                if (type == NULL_ARGUMENT_FOR_NONNULL_PARAMETER) {
                    PsiMethod method = PsiTools.getCalledMethod(element);
                    desc = "<HTML>Argument passed to <B>" + method.getName()
                            + "()</B> " + maybeString + " illegally null";

                } else if (type == NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
                    PsiVariable var = PsiTools.getAssignedVariable(element);
                    desc = "<HTML>Value assigned to <B>" + var.getName()
                                        + "</B> " + maybeString + " illegally null";

                } else if (type == NULL_RETURN_IN_NONNULL_METHOD) {
                    desc = "Returned value " + maybeString + " ilegally null";
                }
            }

        } else if (problem instanceof NullableDereferenceProblem
                || problem instanceof DefinitelyNullDereferenceProblem) {
            NullDereferenceProblem nullableProblem = (NullDereferenceProblem) problem;
            String problemDesc = getRefProblemDescription(nullableProblem);
            String useDesc = getUseDescription(element);
            String mayStr = nullableProblem.isDefinitelyNull() ? "will" : "may";
            desc = "<HTML>" + problemDesc + "; " + useDesc + " "+mayStr
                    +" produce NullPointerException";
        }

        if (desc != null) {
            problems.add(manager.createProblemDescriptor(element, desc, fix,
                    GENERIC_ERROR_OR_WARNING));
        }
    }

    private String getRefProblemDescription(NullDereferenceProblem problem) {
        PsiExpression ref = problem.getReferenceExpression();
        String maybeStr = problem.isDefinitelyNull() ? "is" : "may be";
        String problemDesc = "Value " + maybeStr + " null";
        if (ref instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) ref;
            problemDesc = "<B>" + call.resolveMethod().getName() + "()</B> may return null";

        } else if (ref instanceof PsiReferenceExpression) {
            PsiReferenceExpression refExp = (PsiReferenceExpression) ref;
            PsiElement resolved = refExp.resolve();
            if (resolved instanceof PsiVariable) {
                PsiVariable var = (PsiVariable) resolved;
                problemDesc = "Value of <B>" + var.getName() + "</B> " + maybeStr + " null";
            }
        }
        return problemDesc;
    }

    private String getUseDescription(PsiElement use) {
        String useDesc = "use";
        if (use instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression exp = (PsiMethodCallExpression) use;
            useDesc = "<B>" + exp.resolveMethod().getName() + "()</B> call";
        } else if (use instanceof PsiReferenceExpression) {
            PsiReferenceExpression refExp = (PsiReferenceExpression) use;
            PsiElement referenced = refExp.resolve();
            if (referenced instanceof PsiField) {
                PsiField field = (PsiField) referenced;
                useDesc = "<B>" + field.getName() + "</B> reference";
            }
        }
        return useDesc;
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
