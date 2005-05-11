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

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiJavaFile;
import net.kano.nully.NonNull;
import net.kano.nully.NullyInstrumented;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NullyInstrumentedFinder implements ProblemFinder<NullyInstrumentedProblem> {
    @NonNull public Collection<NullyInstrumentedProblem> findProblems(
            @NonNull AnalysisContext context) {
        PsiJavaFile orig = context.getFileOrig();
        NullyInstrumentedVisitor visitor = new NullyInstrumentedVisitor();
        orig.accept(visitor);

        List<NullyInstrumentedProblem> problems = new ArrayList<NullyInstrumentedProblem>();
        for (PsiAnnotation anno : visitor.getBadElements()) {
            problems.add(new NullyInstrumentedProblem(anno));
        }
        return problems;
    }

    private class NullyInstrumentedVisitor extends PsiRecursiveElementVisitor {
        private List<PsiAnnotation> badElements = new ArrayList<PsiAnnotation>();

        public void visitModifierList(PsiModifierList list) {
            super.visitModifierList(list);

            PsiAnnotation anno = list.findAnnotation(NullyInstrumented.class.getName());
            if (anno != null) badElements.add(anno);
        }

        public List<PsiAnnotation> getBadElements() {
            return badElements;
        }
    }

}
