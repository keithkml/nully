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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import static com.intellij.psi.util.PsiSuperMethodUtil.findSuperMethods;
import static net.kano.nully.analysis.NullProblemType.INVALID_NONNULL_OVERRIDE;
import net.kano.nully.analysis.AnalysisInfo;
import net.kano.nully.NullyTools;

import java.util.ArrayList;
import java.util.List;

public class OtherProblemFinder implements ProblemFinder {
    public List<PsiNullProblem> findProblems(AnalysisInfo info) {
        List<PsiNullProblem> problems = new ArrayList<PsiNullProblem>();
        for (PsiClass cls : info.getFileOrig().getClasses()) {
            checkClass(cls, problems);
        }
        return problems;
    }

    private void checkClass(PsiClass cls, List<PsiNullProblem> problems) {
        ClassMethods:
        for (PsiMethod method : cls.getMethods()) {
            if (NullyTools.hasNonNullAnnotation(method)) continue;
            if (method.isConstructor()) continue;

            for (PsiMethod superm : findSuperMethods(method, true)) {
                if (NullyTools.hasNonNullAnnotation(superm)) {
                    problems.add(new PsiNullProblem(INVALID_NONNULL_OVERRIDE, method));
                    continue ClassMethods;
                }
            }
        }

        for (PsiClass inner : cls.getInnerClasses()) checkClass(inner, problems);
    }
}
