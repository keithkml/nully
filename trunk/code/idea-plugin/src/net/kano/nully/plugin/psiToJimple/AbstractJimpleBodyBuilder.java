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

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import soot.Local;
import soot.Value;

import java.util.List;

public abstract class AbstractJimpleBodyBuilder {

    protected InitialResolver initialResolver;
    protected soot.jimple.JimpleBody body;

    public void ext(AbstractJimpleBodyBuilder ext){
        this.ext = ext;
        if (ext.ext != null){
            throw new IllegalStateException("Extensions created in wrong order");
        }
        ext.base = this.base;
    }
    public AbstractJimpleBodyBuilder ext(){
        if (ext == null) return this;
        return ext;
    }
    private AbstractJimpleBodyBuilder ext = null;
    
    public void base(AbstractJimpleBodyBuilder base){
        this.base = base;
    }
    public AbstractJimpleBodyBuilder base(){
        return base;
    }
    private AbstractJimpleBodyBuilder base = this;
    
    public soot.jimple.JimpleBody createJimpleBody(PsiCodeBlock block,
            List<PsiParameter> formals, soot.SootMethod sootMethod){
        return ext().createJimpleBody(block, formals, sootMethod);
    }
    
    protected soot.Value createExpr(PsiExpression expr){
        return ext().createExpr(expr);
    }
    
    protected void createStmt(PsiStatement stmt){
        ext().createStmt(stmt);
    }

    protected boolean needsAccessor(PsiExpression expr){
        return ext().needsAccessor(expr);
    }
    
    protected Local handlePrivateFieldAssignSet(PsiAssignmentExpression assign){
        return ext().handlePrivateFieldAssignSet(assign);
    }
    
    protected soot.Local handlePrivateFieldUnarySet(PsiExpression unary){
        return ext().handlePrivateFieldUnarySet(unary);
    }
    

    protected soot.Value getAssignRightLocal(PsiAssignmentExpression assign,
            soot.Local leftLocal){
        return ext().getAssignRightLocal(assign, leftLocal);
    }
   
    protected Value getSimpleAssignRightLocal(PsiAssignmentExpression assign){
        return ext().getSimpleAssignRightLocal(assign);
    }
   
    protected soot.Local handlePrivateFieldSet(PsiReferenceExpression expr,
            soot.Value right, soot.Value base){
        return ext().handlePrivateFieldSet(expr, right, base);
    }

    protected soot.SootMethodRef getSootMethodRef(PsiMethodCallExpression call){
        return ext().getSootMethodRef(call);
    }

    protected soot.Local generateLocal(soot.Type sootType){
        return ext().generateLocal(sootType);
    }

    protected soot.Local generateLocal(PsiType psiType){
        return ext().generateLocal(psiType);
    }

    protected soot.Local getThis(soot.Type sootType){
        return ext().getThis(sootType);
    }

    protected soot.Value getBaseLocal(PsiExpression receiver){
        return ext().getBaseLocal(receiver);
    }

    protected soot.Value createLHS(PsiExpression expr){
        return ext().createLHS(expr);
    }

    protected soot.jimple.FieldRef getFieldRef(PsiReferenceExpression field){
        return ext().getFieldRef(field);
    }

    protected soot.jimple.Constant  getConstant(soot.Type sootType, int val){
        return ext().getConstant(sootType, val);
    }
}
