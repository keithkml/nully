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

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

public class PsiPreparer {
    private MethodInfo info;

    public PsiPreparer(MethodInfo info) {
        this.info = info;
    }

    public void prepare(PsiJavaFile jfile, PsiMethod method) {
        makeMarkedCopy(jfile);
        strip((PsiMethod) NullyTools.getCopiedElement(method));
        stripJava5Code(info.getFileCopy());
    }

    public PsiJavaFile makeMarkedCopy(PsiJavaFile jfile) {
        PsiJavaFile fileCopy = NullyTools.getMarkedCopy(jfile);
        info.setFileCopy(fileCopy);
        return fileCopy;
    }

    private void strip(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        stripOtherClasses(info.getFileCopy(), containingClass);
        stripOtherMethods(containingClass, method);
    }

    private void stripOtherMethods(PsiClass copy, PsiMethod method) {
        for (PsiMethod otherMethod : copy.getMethods()) {
            if (otherMethod != method) {
//                System.out.println("Deleting method " + otherMethod.getName());
//                try {
//                    otherMethod.delete();
//                } catch (IncorrectOperationException e) {
//                    e.printStackTrace();
//                }
                PsiCodeBlock body = otherMethod.getBody();
                PsiStatement[] statements = body.getStatements();
                for (PsiStatement statement : statements) {
                    try {
                        statement.delete();
                    } catch (IncorrectOperationException e) {
                        e.printStackTrace();
                    }
                }
                PsiType returnType = otherMethod.getReturnType();
                String val;
                if (returnType.equals(PsiType.BOOLEAN)) {
                    val = "false";
                } else if (returnType.isAssignableFrom(PsiType.BYTE)) {
                    val = "(byte) 0";
                } else if (returnType.equals(PsiType.VOID)) {
                    val = "";
                } else {
                    val = "null";
                }
                try {
                    PsiElementFactory factory = body.getManager()
                            .getElementFactory();
                    body.addAfter(factory.createStatementFromText("return "
                            + val + ";", body),
                            body.getLBrace());
                } catch (IncorrectOperationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void stripOtherClasses(PsiJavaFile copy, PsiClass parentClassCopy) {
        for (PsiClass cls : copy.getClasses()) {
            if (cls != parentClassCopy) {
                System.out.println("deleting class " + cls.getName());
                try {
                    cls.delete();
                } catch (IncorrectOperationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void stripJava5Code(PsiElement el) {
        if (el instanceof PsiModifierListOwner) {
            PsiModifierListOwner modifierListOwner
                    = (PsiModifierListOwner) el;
            PsiModifierList modList = modifierListOwner.getModifierList();
            for (PsiAnnotation annotation : modList.getAnnotations()) {
//                PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
//                System.out.println("Deleting @" + ref.getQualifiedName());
                try {
                    annotation.delete();
                } catch (IncorrectOperationException e) {
                    e.printStackTrace();
                }
            }
        }
        for (PsiElement child : el.getChildren()) {
            stripJava5Code(child);
        }
    }

    public void removeCopy(PsiElement el) {
        PsiElement copy = NullyTools.getCopiedElement(el);
        if (copy != null) {
            el.putUserData(NullyTools.nullyCopyKey, null);

            try {
                copy.delete();
            } catch (IncorrectOperationException ignored) { }
        }

        for (PsiElement child : el.getChildren()) removeCopy(child);
    }
}
