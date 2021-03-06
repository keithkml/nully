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

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiElement;
import net.kano.nully.annotations.NonNull;

/**
 * Created by IntelliJ IDEA. User: keithkml Date: May 19, 2005 Time: 3:50:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class PossiblyNullReferenceInfo {
    private PsiExpression possiblyNullReference;
    private PsiElement use;
    private boolean definitelyNull;

    public PossiblyNullReferenceInfo(@NonNull PsiExpression possiblyNullReference,
            @NonNull PsiElement use, boolean definitelyNull) {
        this.possiblyNullReference = possiblyNullReference;
        this.use = use;
        this.definitelyNull = definitelyNull;
    }

    public PsiExpression getPossiblyNullReference() { return possiblyNullReference; }

    public PsiElement getUse() { return use; }

    public boolean isDefinitelyNull() { return definitelyNull; }
}
