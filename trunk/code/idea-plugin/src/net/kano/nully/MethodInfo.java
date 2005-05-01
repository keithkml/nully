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

package net.kano.nully;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import soot.SootClass;
import soot.SootMethod;

import java.util.List;
import java.util.ArrayList;

public class MethodInfo {
    private PsiClass parentClassCopy;
    private PsiMethod methodCopy;
    private PsiJavaFile fileCopy;
    private List<SootMethod> sootMethods = new ArrayList<SootMethod>();
    private List<SootClass> sootClasses;
    private InspectionManager inspectionManager;
    private OffsetsTracker tracker;
    private PsiMethod methodOrig;

    public OffsetsTracker getTracker() {
        return tracker;
    }

    public void setTracker(OffsetsTracker tracker) {
        this.tracker = tracker;
    }

    public InspectionManager getInspectionManager() {
        return inspectionManager;
    }

    public void setInspectionManager(InspectionManager inspectionManager) {
        this.inspectionManager = inspectionManager;
    }


    public PsiJavaFile getFileCopy() {
        return fileCopy;
    }

    public void setFileCopy(PsiJavaFile fileCopy) {
        this.fileCopy = fileCopy;
    }

    public List<SootMethod> getSootMethods() {
        return sootMethods;
    }

    public void setSootClasses(List<SootClass> sootClasses) {
        this.sootClasses = sootClasses;
        for (SootClass cls : sootClasses) {
            sootMethods.addAll((List<SootMethod>) cls.getMethods());
        }
    }
}
