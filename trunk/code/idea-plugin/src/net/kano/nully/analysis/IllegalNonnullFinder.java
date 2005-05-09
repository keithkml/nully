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

package net.kano.nully.analysis;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiPrimitiveType;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public class IllegalNonnullFinder implements ProblemFinder<IllegalNonnullProblem> {
    @NonNull public Collection<IllegalNonnullProblem> findProblems(
            @NonNull AnalysisContext context) {
        PsiJavaFile orig = context.getFileOrig();

        List<IllegalNonnullProblem> problems = new ArrayList<IllegalNonnullProblem>();
        IllegalNonnullVisitor visitor = new IllegalNonnullVisitor();
        orig.accept(visitor);
        for (PsiModifierListOwner owner : visitor.getBadElements()) {
            problems.add(new IllegalNonnullProblem(owner));
        }

        return problems;
    }

    private class IllegalNonnullVisitor extends PsiRecursiveElementVisitor {
        private List<PsiModifierListOwner> badElements
                = new ArrayList<PsiModifierListOwner>();

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);

            if (!method.isConstructor()
                    && checkNonNullPrimitive(method, method.getReturnType())) {
                badElements.add(method);
            }
        }

        public void visitParameter(PsiParameter parameter) {
            super.visitParameter(parameter);

            if (checkNonNullPrimitive(parameter, parameter.getType())) {
                badElements.add(parameter);
            }
        }

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);

            if (checkNonNullPrimitive(variable, variable.getType())) {
                badElements.add(variable);
            }
        }

        private boolean checkNonNullPrimitive(PsiModifierListOwner owner, PsiType type) {
            return type instanceof PsiPrimitiveType && NullyTools.hasNonNullAnnotation(owner);
        }

        public List<PsiModifierListOwner> getBadElements() {
            return badElements;
        }
    }

}
