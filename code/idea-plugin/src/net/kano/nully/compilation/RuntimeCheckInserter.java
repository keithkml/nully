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

package net.kano.nully.compilation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.NonNullTools;
import net.kano.nully.NullyTools;
import net.kano.nully.NonNull;
import net.kano.nully.analysis.nulls.NullProblemType;
import net.kano.nully.analysis.nulls.NullValueProblem;

import java.util.Collection;

public class RuntimeCheckInserter {
    public static boolean insertRuntimeChecks(@NonNull PsiJavaFile copy,
            @NonNull Collection<? extends NullValueProblem> nvProblems)
            throws IncorrectOperationException {
        //TODO: support SuppressNullChecks granularity
        boolean changed = false;
        changed |= insertDetectedPossibleNullChecks(nvProblems);

        NonNullMethodCallCheckInserter inserter = new NonNullMethodCallCheckInserter();
        copy.accept(inserter);
        changed |= inserter.insertedAnything();

        ParameterCheckInserterVisitor paraminserter = new ParameterCheckInserterVisitor();
        copy.accept(paraminserter);
        changed |= paraminserter.getModifiedMethods().isEmpty();
        return changed;
    }

    private static boolean insertDetectedPossibleNullChecks(
            @NonNull Collection<? extends NullValueProblem> problems)
            throws IncorrectOperationException {
        boolean changed = false;
        for (NullValueProblem problem : problems) {
            PsiElement el = problem.getElement();
            String oldText = el.getText();
            PsiElementFactory factory = el.getManager().getElementFactory();
            NullProblemType type = problem.getType();
            PsiElement parent = el.getParent();
            PsiExpression newExp = null;
            if (type == NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER
                    || type == NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
                newExp = factory.createExpressionFromText(
                        NullyTools.getUnexpectedNullValueCheckString(oldText), parent);

            } else if (type == NullProblemType.NULL_RETURN_IN_NONNULL_METHOD) {
                newExp = factory.createExpressionFromText(NonNullTools.class.getName()
                        + "." + NullyTools.METHOD_CHECKNONNULLRETURN + "("
                        + oldText + ")", parent);
            }

            if (newExp != null) {
                changed = true;
                el.replace(newExp);
            }
        }
        return changed;
    }
}
