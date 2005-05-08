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

package net.kano.nully.analysis.psipreprocess;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;
import net.kano.nully.analysis.AnalysisInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides methods which prepare Java code to be passed to Soot's parser.
 */
public class PreparerForSoot {
    //TODO: strip erroneous lines
    private final AnalysisInfo info;

    public PreparerForSoot(@NonNull AnalysisInfo info) {
        this.info = info;
    }

    public void prepareForElementsAnalysis(@NonNull PsiJavaFile jfile,
            @NonNull Collection<PsiMember> toInspect) {
        makeMarkedCopy(jfile);
        strip(toInspect);
        stripJava5Code(NullyTools.getCopiedElement(jfile));
    }

    /**
     * Makes a copy of the given file, and stores it in the {@code AnalysisInfo}
     * associated with this preparer. Each element of the original tree will
     * have a reference to the corresponding copied elemnt, stored under the
     * user data key {@link NullyTools#KEY_COPY}. Each element of the copy will
     * have a corresponding reference to the original element under the key
     *  {@link NullyTools#KEY_ORIGINAL}.
     * <br /><br />
     * The given file is also set as the "original file" property of the
     * associated {@code AnalysisInfo}.
     *
     * @param jfile the file
     * @return the copy
     */
    public @NonNull PsiJavaFile makeMarkedCopy(@NonNull PsiJavaFile jfile) {
        info.setFileOrig(jfile);
        PsiJavaFile fileCopy = NullyTools.getMarkedCopy(jfile);
        info.setFileCopy(fileCopy);
        return fileCopy;
    }

    private void strip(@NonNull Collection<PsiMember> toInspect) {
        List<PsiMember> toInspectCopies = new ArrayList<PsiMember>();
        for (PsiMember psiMember : toInspect) {
            toInspectCopies.add(NullyTools.getCopiedElement(psiMember));
        }
        PsiJavaFile fileCopy = info.getFileCopy();
        PsiOtherMethodStripper stripper = new PsiOtherMethodStripper(toInspectCopies);
        fileCopy.accept(stripper);
        info.addStrippedClassNames(stripper.getStrippedClassesNames());
    }

    /**
     * Strips Java 5.0 code from the given file according to the specification
     * of {@link Java5CodeStripVisitor};
     *
     * @param el the element to transform
     */
    public void stripJava5Code(@NonNull PsiElement el) {
        el.accept(new Java5CodeStripVisitor());
//        Pair<?,?> result = DegeneratorUtil.degenerate(el);
//        System.out.println("result");
    }

    /**
     * Deletes all reference to {@linkplain #makeMarkedCopy copied elements} in
     * the given PSI element tree.
     *
     * @param el the element whose tree will be stripped of all copies
     */
    public void removeCopy(@NonNull PsiElement el) {
        PsiElement copy = NullyTools.getCopiedElement(el);

        if (copy != null) {
            el.putUserData(NullyTools.KEY_COPY, null);

//            try {
//                if (copy.isValid()) copy.delete();
//            } catch (Exception ignored) {
//                LOGGER.debug(ignored);
//            }
        }

        for (PsiElement child : el.getChildren()) removeCopy(child);
    }

}
