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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import net.kano.nully.NullyTools;
import net.kano.nully.NullParameterException;
import net.kano.nully.NonNull;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

class ParameterCheckInserterVisitor extends PsiRecursiveElementVisitor {
    private static final Logger LOGGER
            = Logger.getInstance(ParameterCheckInserterVisitor.class.getName());
    private Set<PsiMethod> modifiedMethods = new HashSet<PsiMethod>();

    public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        try {
            addParameterChecks(method);
            modifiedMethods.add(method);
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
        }
    }

    public Set<PsiMethod> getModifiedMethods() {
        return Collections.unmodifiableSet(modifiedMethods);
    }

    private static String makeParamCheckString(PsiParameter param, int number) {
        return "if (" + param.getName() + " == null) {"
                + "throw new " + NullParameterException.class.getName() + "(\""
                + param.getName() + "\", " + (number + 1) + ");"
                + "}";
    }

    private void addParameterChecks(@NonNull PsiMethod method)
            throws IncorrectOperationException {
        PsiElementFactory factory = method.getManager().getElementFactory();
        PsiParameter[] params = method.getParameterList().getParameters();
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        PsiElement addAfter = body.getLBrace();
        for (int pi = 0; pi < params.length; pi++) {
            PsiParameter param = params[pi];
            if (!NullyTools.hasNonNullAnnotation(param)) continue;

            PsiElement el = factory.createStatementFromText(
                    makeParamCheckString(param, pi), body);
            body.addAfter(el, addAfter);
            for (int i = 0; i < 1000; i++) {
                PsiElement nextSibling = el.getNextSibling();
                if (nextSibling instanceof PsiWhiteSpace) {
                    nextSibling.delete();
                } else {
                    break;
                }
            }

            addAfter = el;
        }
    }
}
