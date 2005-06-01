/* Soot - a J*va Optimization Framework
 * Copyright (C) 2004 Jennifer Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package net.kano.nully.plugin.psiToJimple;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiUtil;

import java.util.*;

public class InnerClassInfoFinder extends PsiRecursiveElementVisitor {
    private List<PsiClass> localClassDeclList;
    private List<PsiAnonymousClass> anonBodyList;
    private List<PsiMember> memberList;

    public List<PsiMember> memberList(){
        return memberList;
    }

    public List<PsiClass> localClassDeclList(){
        return localClassDeclList;
    }

    public List<PsiAnonymousClass> anonBodyList(){
        return anonBodyList;
    }

    public InnerClassInfoFinder(){
        //declFound = null;
        localClassDeclList = new ArrayList<PsiClass>();
        anonBodyList = new ArrayList<PsiAnonymousClass>();
        memberList = new ArrayList<PsiMember>();
        //declaredInstList = new ArrayList();
        //usedInstList = new ArrayList();
    }

    public void visitClass(PsiClass psiClass) {
        super.visitClass(psiClass);

        if (psiClass instanceof PsiAnonymousClass) {
            anonBodyList.add((PsiAnonymousClass) psiClass);
        } else if (PsiUtil.isLocalClass(psiClass)) {
            localClassDeclList.add(psiClass);
        }
    }

    public void visitMethod(PsiMethod psiMethod) {
        super.visitMethod(psiMethod);
        memberList.add(psiMethod);
    }

    public void visitField(PsiField psiField) {
        super.visitField(psiField);
        memberList.add(psiField);
    }

    public void visitClassInitializer(PsiClassInitializer psiClassInitializer) {
        super.visitClassInitializer(psiClassInitializer);
        memberList.add(psiClassInitializer);
    }
}
