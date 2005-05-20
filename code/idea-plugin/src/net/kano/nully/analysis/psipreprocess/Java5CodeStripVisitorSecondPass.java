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

package net.kano.nully.plugin.analysis.nulls.psipreprocess;

import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiArrayType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;

public class Java5CodeStripVisitorSecondPass extends PsiRecursiveElementVisitor {
    private static final Logger LOGGER
            = Logger.getInstance(Java5CodeStripVisitorSecondPass.class.getName());

    public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        if (!method.isVarArgs()) return;

        PsiParameter[] params = method.getParameterList().getParameters();
        PsiParameter varargParam = params[params.length - 1];
        PsiTypeElement origTypeEl = varargParam.getTypeElement();
        PsiEllipsisType origType = (PsiEllipsisType) origTypeEl.getType();
        PsiElementFactory factory = method.getManager().getElementFactory();
        PsiArrayType newType = new PsiArrayType(origType.getComponentType());
        PsiTypeElement newTypeEl = factory.createTypeElement(newType);
        try {
            origTypeEl.replace(newTypeEl);
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
        }
    }
}
