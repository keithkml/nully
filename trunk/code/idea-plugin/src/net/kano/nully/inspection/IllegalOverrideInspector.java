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
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import net.kano.nully.ImportantSuperMethodInfo;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;
import net.kano.nully.OverrideType;
import net.kano.nully.analysis.IllegalOverrideFinder;
import net.kano.nully.analysis.IllegalOverrideProblem;
import net.kano.nully.analysis.IllegalParamOverrideProblem;
import net.kano.nully.analysis.IllegalReturnOverrideProblem;
import net.kano.nully.analysis.ViolatedParameter;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IllegalOverrideInspector
        extends ProblemFinderBasedInspector<IllegalOverrideFinder, IllegalOverrideProblem> {
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

    protected void addProblems(InspectionManager manager,
            List<ProblemDescriptor> problems, IllegalOverrideProblem problem) {
        if (problem instanceof IllegalParamOverrideProblem) {
            IllegalParamOverrideProblem paramProblem = (IllegalParamOverrideProblem) problem;
            Collection<ViolatedParameter> violated = paramProblem.getViolators();
            if (violated.isEmpty()) return;

            Map<PsiParameter,Set<PsiMethod>> violatedMethodsForParam
                    = new HashMap<PsiParameter, Set<PsiMethod>>();
            for (ViolatedParameter vp : violated) {
                PsiParameter subParam = vp.getSubParam();
                Set<PsiMethod> set = violatedMethodsForParam.get(subParam);
                if (set == null) {
                    set = new HashSet<PsiMethod>();
                    violatedMethodsForParam.put(subParam, set);
                }
                set.add(vp.getSuperMethod());
            }
            // this makes sure every parameter has the correct problem description
            for (Map.Entry<PsiParameter, Set<PsiMethod>> entry
                    : violatedMethodsForParam.entrySet()) {
                PsiParameter subParam = entry.getKey();
                Set<PsiMethod> violatedMethods = entry.getValue();
                PsiMethod method = paramProblem.getElement();
                ImportantSuperMethodInfo superInfo
                        = NullyTools.getImportantSuperMethod(method,
                        violatedMethods);
                PsiMethod superm = superInfo.getOverridden();
                OverrideType overrideType = superInfo.getType();

                String word = null;
                if (overrideType == OverrideType.IMPLEMENTS) {
                    word = "implemented";
                } else
                if (overrideType == OverrideType.OVERRIDES) word = "overridden";
                LOGGER.assertTrue(word != null);

                String superClassName = superm.getContainingClass().getName();
                problems.add(manager.createProblemDescriptor(
                        NullyTools.getNonnullAnnotation(subParam),
                        "Parameter is declared as @NonNull but " + word
                                + " parameter from " + superClassName + " is not; "
                                + "nullness constraint cannot be strengthened",
                        new RemoveAnnotationQuickFix(NonNull.class.getName()),
                        GENERIC_ERROR_OR_WARNING));
            }

        } else if (problem instanceof IllegalReturnOverrideProblem) {
            IllegalReturnOverrideProblem returnProblem = (IllegalReturnOverrideProblem) problem;
            PsiMethod method = returnProblem.getElement();
            ImportantSuperMethodInfo info = NullyTools.getImportantSuperMethod(method,
                    returnProblem.getBadSupers());

            PsiMethod overriddenMethod = info.getOverridden();
            String overriddenText;
            if (overriddenMethod != null) {
                String overridden = NullyTools.getQualifiedMemberName(overriddenMethod);
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
        if (overrideType == OverrideType.OVERRIDES) {
            word = "overrides";
        } else if (overrideType == OverrideType.IMPLEMENTS) {
            word = "implements";
        } else {
            LOGGER.error("Override type was " + overrideType);
            word = null;
        }
        return word;
    }
}
