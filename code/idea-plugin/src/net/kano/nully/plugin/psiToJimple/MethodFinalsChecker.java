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

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MethodFinalsChecker extends PsiRecursiveElementVisitor {
    private List<IdentityKey<PsiClass>> inners;
    private List<IdentityKey<PsiVariable>> finalLocals;
    private Map<IdentityKey<PsiClass>,List<IdentityKey<PsiVariable>>> typeToLocalsUsed;
    private List<PsiMethodCallExpression> ccallList;
    
    public Map<IdentityKey<PsiClass>,List<IdentityKey<PsiVariable>>> typeToLocalsUsed(){
        return typeToLocalsUsed;
    }
    
    public List<IdentityKey<PsiVariable>> finalLocals(){
        return finalLocals;
    }
    
    public List<IdentityKey<PsiClass>> inners(){
        return inners;
    }
    
    public List<PsiMethodCallExpression> ccallList(){
        return ccallList;
    }
    
    
    public MethodFinalsChecker(){
        finalLocals = new ArrayList<IdentityKey<PsiVariable>>();
        inners = new ArrayList<IdentityKey<PsiClass>>();
        ccallList = new ArrayList<PsiMethodCallExpression>();
        typeToLocalsUsed = new HashMap<IdentityKey<PsiClass>, List<IdentityKey<PsiVariable>>>();
    }


    public void visitClass(PsiClass psiClass) {
        if (PsiUtil.isLocalClass(psiClass)) {
            inners.add(new IdentityKey<PsiClass>(psiClass));
            LocalUsesChecker luc = new LocalUsesChecker();
            psiClass.acceptChildren(luc);
            typeToLocalsUsed.put(new IdentityKey<PsiClass>(psiClass), luc.getLocals());
        } else if (psiClass instanceof PsiAnonymousClass) {
            PsiAnonymousClass anonymousClass = (PsiAnonymousClass) psiClass;
            inners.add(new IdentityKey<PsiClass>(anonymousClass));
            LocalUsesChecker luc = new LocalUsesChecker();
            anonymousClass.accept(luc);
            typeToLocalsUsed.put(new IdentityKey<PsiClass>(anonymousClass), luc.getLocals());
        } else {
            super.visitClass(psiClass);
        }
    }

    public void visitLocalVariable(PsiLocalVariable psiLocalVariable) {
        super.visitLocalVariable(psiLocalVariable);

        addIfFinal(psiLocalVariable);
    }

    public void visitParameter(PsiParameter psiParameter) {
        super.visitParameter(psiParameter);

        addIfFinal(psiParameter);
    }

    private void addIfFinal(PsiVariable psiParameter) {
        if (psiParameter.hasModifierProperty("final")) {
            IdentityKey<PsiVariable> key = new IdentityKey<PsiVariable>(psiParameter);
            if (!finalLocals.contains(key)){
                finalLocals.add(key);
            }
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression psiMethodCallExpression) {
        super.visitMethodCallExpression(psiMethodCallExpression);

        String name = psiMethodCallExpression.getMethodExpression().getReferenceName();
        if (name.equals("super") || name.equals("this")) {

            ccallList.add(psiMethodCallExpression);
        }
    }

}
