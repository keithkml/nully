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
// *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElement;
import static net.kano.nully.inspection.ProblemFinderAndHighlighter.findNullProblems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NullProblemInspector extends AbstractNullyInspection {
    //TODO: whole-file checks?
    
    public String getDisplayName() {
        return "Possibly null value in @NonNull context";
    }

    public String getShortName() {
        return "NullyNullProblemCheck";
    }

    public ProblemDescriptor[] checkClass(PsiClass aClass,
            InspectionManager manager, boolean isOnTheFly) {
        PsiJavaFile jfile = getParentJavaFile(aClass);
        if (jfile == null) return null;

        List<PsiMember> okayEls = new ArrayList<PsiMember>();
        Collections.addAll(okayEls, aClass.getInitializers());

        return findNullProblems(jfile, okayEls, manager);
    }

    public ProblemDescriptor[] checkField(PsiField field,
            InspectionManager manager, boolean isOnTheFly) {
        PsiJavaFile jfile = getParentJavaFile(field);
        if (jfile == null) return null;

        return findNullProblems(jfile,
                Arrays.<PsiMember>asList(field), manager);
    }

    public ProblemDescriptor[] checkMethod(PsiMethod method,
            InspectionManager manager, boolean isOnTheFly) {
        PsiJavaFile jfile = getParentJavaFile(method);
        if (jfile == null) return null;

        return findNullProblems(jfile,
                Arrays.<PsiMember>asList(method), manager);
    }

    private static PsiJavaFile getParentJavaFile(PsiElement el) {
        PsiFile container = el.getContainingFile();
        PsiJavaFile jfile;
        if (!(container instanceof PsiJavaFile)) jfile = null;
        else jfile = (PsiJavaFile) container;
        return jfile;
    }
}
