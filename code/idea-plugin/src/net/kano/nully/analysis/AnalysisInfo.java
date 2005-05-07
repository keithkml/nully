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
import net.kano.nully.OffsetsTracker;
import soot.SootClass;
import soot.SootMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AnalysisInfo {
    private PsiJavaFile fileOrig;
    private PsiJavaFile fileCopy;
    private List<SootMethod> sootMethods = new ArrayList<SootMethod>();
    private List<SootClass> sootClasses;
    private OffsetsTracker tracker;
    private List<String> strippedClassNames = new ArrayList<String>();

    public OffsetsTracker getTracker() {
        return tracker;
    }

    public void setTracker(OffsetsTracker tracker) {
        this.tracker = tracker;
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

    public List<SootClass> getSootClasses() {
        return sootClasses;
    }

    public void setSootClasses(List<SootClass> sootClasses) {
        this.sootClasses = sootClasses;
        for (SootClass cls : sootClasses) {
            for (SootMethod method : (List<SootMethod>) cls.getMethods()) {
                if (method.isConcrete()) sootMethods.add(method);
            }
        }
    }

    public PsiJavaFile getFileOrig() {
        return fileOrig;
    }

    public void setFileOrig(PsiJavaFile fileOrig) {
        this.fileOrig = fileOrig;
    }

    public void addStrippedClassNames(Collection<String> names) {
        strippedClassNames.addAll(names);
    }

    public List<String> getStrippedClassNames() {
        return strippedClassNames;
    }
}
