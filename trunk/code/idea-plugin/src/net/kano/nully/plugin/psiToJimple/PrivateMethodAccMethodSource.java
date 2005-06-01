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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import soot.Type;
import soot.Local;
import soot.Body;
import soot.SootMethod;
import soot.VoidType;
import soot.SootMethodRef;
import soot.Scene;
import soot.RefType;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.InvokeExpr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
public class PrivateMethodAccMethodSource implements soot.MethodSource {
    private InitialResolver resolver;

    public PrivateMethodAccMethodSource(InitialResolver resolver, PsiMethod methInst){
        this.resolver = resolver;
        this.methodInst = methInst;
    }
    private PsiMethod methodInst;

    public void setMethodInst(PsiMethod mi) {
        methodInst = mi;
    }
    
    private boolean isCallParamType(soot.Type sootType) {
        for (PsiParameter param : methodInst.getParameterList()
                .getParameters()) {
            soot.Type compareType = Util.getSootType(resolver, param.getType());
            if (compareType.equals(sootType)) return true;
        }
        return false;
    }
    
    public Body getBody(SootMethod sootMethod, String phaseName){

        Body body = Jimple.v().newBody(sootMethod);
        LocalGenerator lg = new LocalGenerator(body);

        Local base = null;
        List<Local> methParams = new ArrayList<Local>();
        List<Type> methParamsTypes = new ArrayList<Type>();
        // create parameters
        Iterator paramIt = sootMethod.getParameterTypes().iterator();
        int paramCounter = 0;
        while (paramIt.hasNext()) {
            Type sootType = (Type)paramIt.next();
            Local paramLocal = lg.generateLocal(sootType);
            //body.getLocals().add(paramLocal);
            ParameterRef paramRef = Jimple.v().newParameterRef(sootType, paramCounter);
            Stmt stmt = Jimple.v().newIdentityStmt(paramLocal, paramRef);
            body.getUnits().add(stmt);
            if (!isCallParamType(sootType)){
                base = paramLocal;
            }
            else {
                methParams.add(paramLocal);
                methParamsTypes.add(paramLocal.getType());
            }
            paramCounter++;
        }

        // create return type local
        Type type = Util.getSootType(resolver, methodInst.getReturnType());

        Local returnLocal = null;
        if (!(type instanceof VoidType)){
            returnLocal = lg.generateLocal(type);
            //body.getLocals().add(returnLocal);
        }

        // assign local to meth
        Type methodCls = Util.getSootType(resolver, methodInst.getContainingClass());
        boolean isStatic = methodInst.hasModifierProperty("static");
        SootMethodRef meth = Scene.v()
                .makeMethodRef(((RefType)methodCls).getSootClass(),
                methodInst.getName(), methParamsTypes,
                Util.getSootType(resolver, methodInst.getReturnType()), isStatic);

        InvokeExpr invoke = null;
        if (isStatic) {
            invoke = Jimple.v().newStaticInvokeExpr(meth, methParams);
        }
        else {
            invoke = Jimple.v().newSpecialInvokeExpr(base, meth, methParams);
        }

        Stmt stmt = null;
        if (!(type instanceof VoidType)){
            stmt = Jimple.v().newAssignStmt(returnLocal, invoke);
        }
        else{
            stmt = Jimple.v().newInvokeStmt(invoke);
        }
        body.getUnits().add(stmt);

        //return local
        Stmt retStmt = null;
        if (!(type instanceof VoidType)) {
            retStmt = Jimple.v().newReturnStmt(returnLocal);
        }
        else {
            retStmt = Jimple.v().newReturnVoidStmt();
        }
        body.getUnits().add(retStmt);

        return body;

    }
    

}
