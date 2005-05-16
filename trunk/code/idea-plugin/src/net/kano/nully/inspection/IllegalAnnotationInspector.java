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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import net.kano.nully.NonNull;
import net.kano.nully.NullyInstrumented;
import net.kano.nully.NullyTools;
import net.kano.nully.analysis.IllegalAnnotationFinder;
import net.kano.nully.analysis.IllegalAnnotationProblem;
import net.kano.nully.analysis.NullyInstrumentedProblem;
import net.kano.nully.analysis.PrimitiveAnnotationProblem;

import java.util.EnumSet;
import java.util.List;

public class IllegalAnnotationInspector
        extends ProblemFinderBasedInspector<IllegalAnnotationFinder,
                IllegalAnnotationProblem> {
    
    public String getDisplayName() {
        return "Illegal nullness annotation";
    }

    public String getShortName() {
        return "NullyIllegalAnnotation";
    }

    protected IllegalAnnotationFinder getFinderInstance() {
        return new IllegalAnnotationFinder();
    }

    protected EnumSet<InspectionType> getInspectionTypes() {
        return EnumSet.of(InspectionType.FILE);
    }

    protected void addProblems(InspectionManager manager,
            List<ProblemDescriptor> problems, IllegalAnnotationProblem problem) {
        PsiAnnotation anno = problem.getElement();
        PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(anno,
                PsiModifierListOwner.class);

        String annoName = anno.getNameReferenceElement().getQualifiedName();
        String desc;
        if (problem instanceof PrimitiveAnnotationProblem) {
            if (owner instanceof PsiVariable) {
                PsiVariable psiVariable = (PsiVariable) owner;

                String typeStr = NullyTools.getVariableTypeString(psiVariable);

                PsiType type = psiVariable.getType();
                desc = "@" + annoName + " cannot be used with "
                        + type.getPresentableText() + " " + typeStr;

            } else if (owner instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) owner;
                desc = "@" + annoName + " cannot be used for "
                        + method.getReturnType().getPresentableText()
                        + " return value";

            } else {
                throw new IllegalStateException("element was " + owner);
            }
        } else if (problem instanceof NullyInstrumentedProblem) {
            desc = "@" + NullyInstrumented.class.getSimpleName() + " may only be "
                    + "inserted by Nully";
        } else {
            return;
        }

        problems.add(manager.createProblemDescriptor(anno,
                desc, new RemoveAnnotationQuickFix(NonNull.class.getName()),
                GENERIC_ERROR_OR_WARNING));
    }
}
