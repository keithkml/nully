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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;

import java.util.HashSet;
import java.util.Set;

public class TypeListBuilder extends PsiRecursiveElementVisitor {
    private Set<PsiClassType> list;

    public Set<PsiClassType> getList() {
        return list;
    }

    public TypeListBuilder(){
        list = new HashSet<PsiClassType>();
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
        super.visitReferenceElement(psiJavaCodeReferenceElement);

        addReferencedElementType(psiJavaCodeReferenceElement.resolve());
    }

    public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
        super.visitReferenceExpression(psiReferenceExpression);
        PsiElement referenced = psiReferenceExpression.resolve();
        addReferencedElementType(referenced);
    }

    private void addReferencedElementType(PsiElement referenced) {
        if (referenced instanceof PsiClass) {
            addClassType((PsiClass) referenced);
        } else if (referenced instanceof PsiMember) {
            addClassType(((PsiMember) referenced).getContainingClass());
        }
    }

    public void visitExpression(PsiExpression psiExpression) {
        super.visitExpression(psiExpression);

        PsiType type = psiExpression.getType();
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            list.add(classType);
        }
    }

    public void visitClass(PsiClass psiClass) {
        addClassType(psiClass);
    }

    private void addClassType(PsiClass psiClass) {
        list.add(psiClass.getManager().getElementFactory().createType(psiClass));
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) addClassType(superClass);
        for (PsiClassType type : psiClass.getImplementsListTypes()) {
            PsiClass cls = type.resolve();
            if (cls != null) addClassType(cls);
        }
        PsiClass container = psiClass.getContainingClass();
        if (container != null) addClassType(container);            
    }
}
