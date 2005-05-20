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
import com.intellij.codeInspection.ProblemDescriptor;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.diagnostic.Logger;
import net.kano.nully.plugin.ImportantSuperMethodInfo;
import net.kano.nully.plugin.PsiTools;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.plugin.OverrideType;
import static net.kano.nully.plugin.OverrideType.IMPLEMENTS;
import static net.kano.nully.plugin.OverrideType.OVERRIDES;
import net.kano.nully.plugin.analysis.IllegalOverrideFinder;
import net.kano.nully.plugin.analysis.IllegalOverrideProblem;
import net.kano.nully.plugin.analysis.IllegalParamOverrideProblem;
import net.kano.nully.plugin.analysis.IllegalReturnOverrideProblem;
import net.kano.nully.plugin.analysis.AnalysisContext;

import java.util.EnumSet;
import java.util.List;

public class IllegalOverrideInspector
        extends ProblemFinderBasedInspector<IllegalOverrideFinder, IllegalOverrideProblem<?>> {
    private static final Logger LOGGER = Logger.getInstance(IllegalOverrideInspector.class.getName());

    public String getDisplayName() {
        return "Method overrides @" + NonNull.class.getSimpleName() + " method without "
                + "@" + NonNull.class.getSimpleName() + " declaration";
    }

    public String getShortName() {
        return "NullyIllegalOverride";
    }

    protected IllegalOverrideFinder getFinderInstance() {
        return new IllegalOverrideFinder();
    }

    protected EnumSet<InspectionType> getInspectionTypes() {
        return EnumSet.of(InspectionType.METHOD);
    }

    protected void addProblems(AnalysisContext context,
            InspectionManager manager,
            List<ProblemDescriptor> problems,
            IllegalOverrideProblem<?> problem) {
        IllegalOverrideProblem hack = problem;
        if (hack instanceof IllegalParamOverrideProblem) {
            IllegalParamOverrideProblem paramProblem = (IllegalParamOverrideProblem) hack;

            PsiAnnotation anno = paramProblem.getElement();
            PsiMethod method = PsiTreeUtil.getParentOfType(anno, PsiMethod.class);
            ImportantSuperMethodInfo superInfo
                    = PsiTools.getImportantSuperMethod(method,
                        paramProblem.getSuperMethods());
            PsiMethod superm = superInfo.getOverridden();
            OverrideType overrideType = superInfo.getType();

            String word = null;
            if (overrideType == IMPLEMENTS) {
                word = "implemented";
            } else if (overrideType == OVERRIDES) word = "overridden";
            LOGGER.assertTrue(word != null);

            String superClassName = superm.getContainingClass().getName();
            String annoName = anno.getNameReferenceElement().getReferenceName();
            problems.add(manager.createProblemDescriptor(anno,
                    "Parameter cannot be declared as @" + annoName + " when " + word
                            + " parameter from " + superClassName + " is not",
                    new RemoveAnnotationQuickFix(NonNull.class.getName()),
                    GENERIC_ERROR_OR_WARNING));

        } else if (hack instanceof IllegalReturnOverrideProblem) {
            IllegalReturnOverrideProblem returnProblem = (IllegalReturnOverrideProblem) hack;
            PsiMethod method = returnProblem.getElement();
            ImportantSuperMethodInfo info = PsiTools.getImportantSuperMethod(method,
                    returnProblem.getBadSupers());

            PsiMethod overriddenMethod = info.getOverridden();
            String overriddenText;
            if (overriddenMethod != null) {
                String overridden = PsiTools.getQualifiedMemberName(overriddenMethod);
                overriddenText = "method " + overridden;
            } else {
                overriddenText = "methods";
            }
            String word = getWord(info.getType());
            problems.add(manager.createProblemDescriptor(
                    method.getNameIdentifier(), "<HTML>Method illegally "
                            + word + " @" + NonNull.class.getSimpleName() + " "
                            + overriddenText + " without @" + NonNull.class.getSimpleName()
                            + " declaration",
                    new AddNonNullDeclarationFix(), GENERIC_ERROR_OR_WARNING));
        }
    }

    private static String getWord(OverrideType overrideType) {
        String word;
        if (overrideType == OVERRIDES) {
            word = "overrides";
        } else if (overrideType == IMPLEMENTS) {
            word = "implements";
        } else {
            LOGGER.error("Override type was " + overrideType);
            word = null;
        }
        return word;
    }
/*
    class Second<E extends Number> { }
    class Third extends Second<Integer> { }
    void x() {
        Second<?> f = null;
        if (f instanceof Third) {
            System.out.println("hi");
        }
    }
 */
}
