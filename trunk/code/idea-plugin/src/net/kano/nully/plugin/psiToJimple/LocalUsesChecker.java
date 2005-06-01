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
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;

import java.util.ArrayList;
import java.util.List;

public class LocalUsesChecker extends PsiRecursiveElementVisitor{

    private List<IdentityKey<PsiVariable>> locals;
    private List<IdentityKey<PsiVariable>> localDecls;
    private List<PsiNewExpression> news;
    
    public List<IdentityKey<PsiVariable>> getLocals() {
        return locals;
    }

    public List<PsiNewExpression> getNews(){
        return news;
    }

    public List<IdentityKey<PsiVariable>> getLocalDecls(){
        return localDecls;
    }
    
    public LocalUsesChecker(){
        locals = new ArrayList<IdentityKey<PsiVariable>>();
        localDecls = new ArrayList<IdentityKey<PsiVariable>>();
        news = new ArrayList<PsiNewExpression>();
    }

    public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
        super.visitReferenceExpression(psiReferenceExpression);

        PsiElement referenced = psiReferenceExpression.resolve();
        addIfVariable(referenced);
    }

    private void addIfVariable(PsiElement referenced) {
        if (referenced instanceof PsiLocalVariable || referenced instanceof PsiParameter) {
            PsiVariable var = (PsiVariable) referenced;
            locals.add(new IdentityKey<PsiVariable>(var));
        }
    }

    public void visitDeclarationStatement(PsiDeclarationStatement psiDeclarationStatement) {
        super.visitDeclarationStatement(psiDeclarationStatement);

        for (PsiElement element : psiDeclarationStatement.getDeclaredElements()) {
            addIfVariable(element);
        }
    }

    public void visitParameter(PsiParameter psiParameter) {
        super.visitParameter(psiParameter);

        locals.add(new IdentityKey<PsiVariable>(psiParameter));
    }

    public void visitNewExpression(PsiNewExpression psiNewExpression) {
        super.visitNewExpression(psiNewExpression);

        PsiExpression[] dims = psiNewExpression.getArrayDimensions();
        if (dims == null || dims.length == 0) {
            news.add(psiNewExpression);
        }
    }

}
