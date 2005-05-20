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

package net.kano.nully.plugin.analysis;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiSuperMethodUtil;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.plugin.NullyTools;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

public class IllegalReturnOverrideFinder implements ProblemFinder<IllegalReturnOverrideProblem> {
    @NonNull public Collection<IllegalReturnOverrideProblem> findProblems(
            @NonNull AnalysisContext context) {
        IllegalOverrideVisitor visitor = new IllegalOverrideVisitor();
        context.getFileOrig().accept(visitor);

        List<IllegalReturnOverrideProblem> problems = new ArrayList<IllegalReturnOverrideProblem>();
        for (Map.Entry<PsiMethod,Collection<PsiMethod>> pair
                : visitor.getBadMethods().entrySet()) {
            problems.add(new IllegalReturnOverrideProblem(pair.getKey(), pair.getValue()));
        }
        return problems;
    }

    private class IllegalOverrideVisitor extends PsiRecursiveElementVisitor {
        public Collection<PsiMethod> findIllegalOverrides(PsiMethod method) {
            if (NullyTools.hasNonNullAnnotation(method)) {
                return Collections.emptyList();
            }

            List<PsiMethod> bad = new ArrayList<PsiMethod>();
            for (PsiMethod superm : PsiSuperMethodUtil.findSuperMethods(method)) {
                if (NullyTools.hasNonNullAnnotation(superm)) bad.add(superm);
            }
            return bad;
        }

        private final Map<PsiMethod, Collection<PsiMethod>> badMethods
                = new HashMap<PsiMethod, Collection<PsiMethod>>();

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);

            Collection<PsiMethod> bad = findIllegalOverrides(method);
            if (!bad.isEmpty()) badMethods.put(method, bad);
        }

        public Map<PsiMethod,Collection<PsiMethod>> getBadMethods() {
            return badMethods;
        }
    }

}
