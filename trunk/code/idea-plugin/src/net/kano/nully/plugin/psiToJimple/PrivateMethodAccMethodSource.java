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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
public class PrivateMethodAccMethodSource implements soot.MethodSource {

    public PrivateMethodAccMethodSource(PsiMethod methInst){
        this.methodInst = methInst;
    }
    private PsiMethod methodInst;

    public void setMethodInst(PsiMethod mi) {
        methodInst = mi;
    }
    
    private boolean isCallParamType(soot.Type sootType) {
        for (PsiParameter param : methodInst.getParameterList()
                .getParameters()) {
            soot.Type compareType = Util.getSootType(param.getType());
            if (compareType.equals(sootType)) return true;
        }
        return false;
    }
    
    public soot.Body getBody(soot.SootMethod sootMethod, String phaseName){

        soot.Body body = soot.jimple.Jimple.v().newBody(sootMethod);
        LocalGenerator lg = new LocalGenerator(body);

        soot.Local base = null;
        List<Local> methParams = new ArrayList<Local>();
        List<Type> methParamsTypes = new ArrayList<Type>();
        // create parameters
        Iterator paramIt = sootMethod.getParameterTypes().iterator();
        int paramCounter = 0;
        while (paramIt.hasNext()) {
            soot.Type sootType = (soot.Type)paramIt.next();
            soot.Local paramLocal = lg.generateLocal(sootType);
            //body.getLocals().add(paramLocal);
            soot.jimple.ParameterRef paramRef = soot.jimple.Jimple.v().newParameterRef(sootType, paramCounter);
            soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newIdentityStmt(paramLocal, paramRef);
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
        soot.Type type = Util.getSootType(methodInst.getReturnType());

        soot.Local returnLocal = null;
        if (!(type instanceof soot.VoidType)){
            returnLocal = lg.generateLocal(type);
            //body.getLocals().add(returnLocal);
        }

        // assign local to meth
        Type methodCls = Util.getSootType(methodInst.getContainingClass());
        boolean isStatic = methodInst.hasModifierProperty("static");
        soot.SootMethodRef meth = soot.Scene.v()
                .makeMethodRef(((soot.RefType)methodCls).getSootClass(),
                methodInst.getName(), methParamsTypes,
                Util.getSootType(methodInst.getReturnType()), isStatic);

        soot.jimple.InvokeExpr invoke = null;
        if (isStatic) {
            invoke = soot.jimple.Jimple.v().newStaticInvokeExpr(meth, methParams);
        }
        else {
            invoke = soot.jimple.Jimple.v().newSpecialInvokeExpr(base, meth, methParams);
        }

        soot.jimple.Stmt stmt = null;
        if (!(type instanceof soot.VoidType)){
            stmt = soot.jimple.Jimple.v().newAssignStmt(returnLocal, invoke);
        }
        else{
            stmt = soot.jimple.Jimple.v().newInvokeStmt(invoke);
        }
        body.getUnits().add(stmt);

        //return local
        soot.jimple.Stmt retStmt = null;
        if (!(type instanceof soot.VoidType)) {
            retStmt = soot.jimple.Jimple.v().newReturnStmt(returnLocal);
        }
        else {
            retStmt = soot.jimple.Jimple.v().newReturnVoidStmt();
        }
        body.getUnits().add(retStmt);

        return body;

    }
    

}
