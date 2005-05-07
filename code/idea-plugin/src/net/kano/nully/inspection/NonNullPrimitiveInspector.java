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

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;

import java.util.List;
import java.util.ArrayList;

public class NonNullPrimitiveInspector extends AbstractNullyInspection {
    private static final Logger LOGGER
            = Logger.getInstance(NonNullPrimitiveInspector.class.getName());

    public String getDisplayName() {
        return "@" + NonNull.class.getSimpleName() + " used with primitive type";
    }

    public String getShortName() {
        return "NullyNonNullPrimitive";
    }

    public ProblemDescriptor[] checkClass(PsiClass aClass,
            InspectionManager manager, boolean isOnTheFly) {
        NonNullPrimitiveDeclarationVisitor visitor = new NonNullPrimitiveDeclarationVisitor();
        aClass.accept(visitor);
        List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
        for (PsiModifierListOwner element : visitor.getBadElements()) {
            PsiAnnotation anno = NullyTools.getNonnullAnnotation(element);
            String desc;
            if (element instanceof PsiVariable) {
                PsiVariable psiVariable = (PsiVariable) element;

                String typeStr = getVariableTypeString(psiVariable);

                PsiType type = psiVariable.getType();
                desc = "@" + NonNull.class.getSimpleName() + " does not apply "
                        + "to " + type.getPresentableText() + " " + typeStr;

            } else if (element instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) element;
                desc = "@" + NonNull.class.getSimpleName() + " does not apply "
                        + "to " + method.getReturnType().getPresentableText()
                        + " return value";
                
            } else {
                LOGGER.error("invalid element " + element);
                continue;
            }
            problems.add(manager.createProblemDescriptor(anno,
                    desc, new RemoveNonNullQuickFix(),
                    GENERIC_ERROR_OR_WARNING));
        }
        return problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    private static String getVariableTypeString(PsiVariable psiVariable) {
        String typeStr;
        if (psiVariable instanceof PsiParameter) {
            typeStr = "parameter";
        } else if (psiVariable instanceof PsiLocalVariable) {
            typeStr = "variable";
        } else {
            typeStr = "variable";
            LOGGER.error("PsiVariable was " + psiVariable.getClass().getName());
        }
        return typeStr;
    }
}
