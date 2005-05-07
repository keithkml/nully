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

import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import net.kano.nully.NullyTools;
import net.kano.nully.NonNull;

class NonNullMethodCallCheckInserter extends PsiRecursiveElementVisitor {
    private static final Logger LOGGER
            = Logger.getInstance(NonNullMethodCallCheckInserter.class.getName());

    private boolean insertedAnything = false;

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        PsiMethod calledMethod = expression.resolveMethod();
        if (NullyTools.hasNonNullAnnotation(calledMethod)
                && !NullyTools.isNonnullCheckMethod(calledMethod)
                && !parentIsNonnullCheckMethod(expression)) {
            PsiElementFactory factory = expression.getManager().getElementFactory();
            String newString = NullyTools.getUnexpectedNullValueCheckString(expression.getText());
            try {
                PsiExpression newExp = factory.createExpressionFromText(newString,
                        expression.getParent());
                expression.replace(newExp);
            } catch (IncorrectOperationException e) {
                LOGGER.error(e);
            }
        }
    }

    private boolean parentIsNonnullCheckMethod(@NonNull PsiMethodCallExpression expression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression parentCall = (PsiMethodCallExpression) parent;
            return NullyTools.isNonnullCheckMethod(parentCall.resolveMethod());
        }
        return false;
    }

    public boolean insertedAnything() { return insertedAnything; }
}
