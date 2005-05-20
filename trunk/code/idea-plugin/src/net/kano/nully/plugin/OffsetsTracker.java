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

package net.kano.nully.plugin;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import soot.tagkit.SourceLnPosTag;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class OffsetsTracker {
    private List<Integer> lineOffsets;

    public OffsetsTracker(Reader reader) throws IOException {
        this(reader, false);
    }

    public OffsetsTracker(Reader reader, boolean ignoreCarriageReturns)
            throws IOException {
        loadOffsets(reader, ignoreCarriageReturns);
    }

    private void loadOffsets(Reader reader, boolean ignoreCarriageReturns) throws IOException {
        lineOffsets = new ArrayList<Integer>();
        lineOffsets.add(0);

        int last = -1;
        for (int offset = 0;; offset++) {
            int val = reader.read();
            if (val == -1) break;

            if (ignoreCarriageReturns && val == '\r') {
                offset--;
                continue;
            }

            char ch = (char) val;
            if (ch == '\n') {
                lineOffsets.add(offset + 1);
            } else if (last == '\r') {
                lineOffsets.add(offset);
            }
            last = ch;
        }
    }

    public int getOffset(int line, int col) {
        int lnIndex = line - 1;
        int colIndex = col;
        return lineOffsets.get(lnIndex) + colIndex;
    }

    public PsiElement getElementAtPosition(PsiJavaFile fileCopy,
            SourceLnPosTag useTag) {
        int foff = getOffset(useTag.startLn(), useTag.startPos());
        PsiElement first = fileCopy.findElementAt(foff);
        int eoff = getOffset(useTag.endLn(), useTag.endPos()) - 1;
        PsiElement last = fileCopy.findElementAt(eoff);
        PsiElement commonParent = PsiTreeUtil.findCommonParent(first, last);

        if (commonParent != null) return commonParent;
        else return first;
    }

    public int getLine(int off) {
        int line = 0;
        for (int loff : lineOffsets) {
            if (loff > off) return line - 1;
            line++;
        }
        return line;
    }

    public int getColumn(int off) {
        int lastLoff = 0;
        for (int loff : lineOffsets) {
            if (loff > off) {
                return off - lastLoff;
            }
            lastLoff = loff;
        }
        return off - lastLoff;
    }
}
