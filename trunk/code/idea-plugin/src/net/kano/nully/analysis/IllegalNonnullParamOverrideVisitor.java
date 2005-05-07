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

import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.openapi.diagnostic.Logger;
import net.kano.nully.NullyTools;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class IllegalNonnullParamOverrideVisitor extends PsiRecursiveElementVisitor {
    private static final Logger LOGGER
            = Logger.getInstance(IllegalNonnullParamOverrideVisitor.class.getName());

    public void visitMethod(PsiMethod method) {
        LOGGER.error("not implemented");
    }

    public static boolean methodIllegallyOverridesParameter(PsiMethod method) {
        return !(getViolatedSuperMethods(method).isEmpty());
    }

    public static List<ViolatedParameter> getViolatedSuperMethods(PsiMethod method) {
        PsiMethod[] supers = PsiSuperMethodUtil.findSuperMethods(method);
        if (supers.length == 0) return Collections.emptyList();
        PsiParameter[] methodParams = method.getParameterList().getParameters();
        if (methodParams.length == 0) return Collections.emptyList();

        boolean[] nonnull = new boolean[methodParams.length];
        boolean anyNonnullParams = false;
        for (int i = 0; i < methodParams.length; i++) {
            nonnull[i] = NullyTools.hasNonNullAnnotation(methodParams[i]);
            anyNonnullParams |= nonnull[i];
        }
        if (!anyNonnullParams) return Collections.emptyList();

        List<ViolatedParameter> badParams = new ArrayList<ViolatedParameter>();
        for (PsiMethod superMethod : supers) {
            PsiParameter[] superParams = superMethod.getParameterList().getParameters();
            for (int i = 0; i < superParams.length; i++) {
                PsiParameter superParam = superParams[i];
                if (nonnull[i] && !NullyTools.hasNonNullAnnotation(superParam)) {
                    badParams.add(new ViolatedParameter(methodParams[i],
                            superMethod, superParam));
                    break;
                }
            }
        }
        return badParams;
    }

}