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

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import soot.MethodSource;
import soot.Pack;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.jimple.StringConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PsiMethodSource implements MethodSource {

    private PsiCodeBlock block;
    private List<PsiParameter> formals;
    private List<PsiField> fieldInits;
    private List<PsiField> staticFieldInits;
    private List<PsiCodeBlock> initializerBlocks;
    private List<PsiCodeBlock> staticInitializerBlocks;
    private soot.Local outerClassThisInit;
    private boolean hasAssert = false;
    private List<SootField> finalsList;
    private AbstractJimpleBodyBuilder ajbb;
    private InitialResolver initialResolver;

    public PsiMethodSource(InitialResolver resolver){
        this(resolver, null, null);
    }
    
    public PsiMethodSource(InitialResolver resolver, PsiCodeBlock block, List<PsiParameter> formals){
        initialResolver = resolver;
        this.block = block;
        this.formals = formals;
    }

    public PsiMethodSource(InitialResolver resolver, PsiMethod procedure) {
        this(resolver, procedure.getBody(), Arrays.asList(procedure.getParameterList().getParameters()));
    }

    public soot.Body getBody(soot.SootMethod sm, String phaseName) {
        //JimpleBodyBuilder jbb = new JimpleBodyBuilder();
        soot.jimple.JimpleBody jb = ajbb.createJimpleBody(block, formals, sm);

        Pack pack = new PsiBodyPack();
        pack.apply(jb);
        return jb;
    }

    public void setJBB(AbstractJimpleBodyBuilder ajbb){
        this.ajbb = ajbb;
    }

    public void setFieldInits(List<PsiField> fieldInits){
        this.fieldInits = fieldInits;
    }
    
    public void setStaticFieldInits(List<PsiField> staticFieldInits){
        this.staticFieldInits = staticFieldInits;
    }

    public List<PsiField> getFieldInits() {
        return fieldInits;
    }
    
    public List<PsiField> getStaticFieldInits() {
        return staticFieldInits;
    }

    public void setStaticInitializerBlocks(List<PsiCodeBlock> staticInits) {
        staticInitializerBlocks = staticInits;
    }
    
    public void setInitializerBlocks(List<PsiCodeBlock> inits) {
        initializerBlocks = inits;
    }

    public List<PsiCodeBlock> getStaticInitializerBlocks() {
        return staticInitializerBlocks;
    }
    
    public List<PsiCodeBlock> getInitializerBlocks() {
        return initializerBlocks;
    }
    
    public void setOuterClassThisInit(soot.Local l) {
        outerClassThisInit = l;
    }

    public soot.Local getOuterClassThisInit(){
        return outerClassThisInit;
    }

    public boolean hasAssert(){
        return hasAssert;
    }

    public void hasAssert(boolean val){
        hasAssert = val;
    }

    public void addAssertInits(soot.Body body){
        // if class is inner get desired assertion status from outer most class
        soot.SootClass assertStatusClass = body.getMethod().getDeclaringClass();
        Map innerMap = soot.javaToJimple.InitialResolver.v()
                .getInnerClassInfoMap();
        while ((innerMap != null)
                && (innerMap.containsKey(assertStatusClass))) {
            assertStatusClass = ((InnerClassInfo) innerMap.get(assertStatusClass)).getOuterClass();
        }

        String paramName = assertStatusClass.getName();
        String fieldName = "class$" + soot.util.StringTools
                .replaceAll(assertStatusClass.getName(), ".", "$");

        if (assertStatusClass.isInterface()) {
            assertStatusClass = initialResolver.specialAnonMap()
                    .get(assertStatusClass);
        }

        // field ref
        soot.SootFieldRef field = soot.Scene.v().makeFieldRef(assertStatusClass,
                fieldName, soot.RefType.v("java.lang.Class"), true);

        soot.Local fieldLocal = soot.jimple.Jimple.v().newLocal("$r0",
                soot.RefType.v("java.lang.Class"));

        body.getLocals().add(fieldLocal);

        soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v()
                .newStaticFieldRef(field);

        soot.jimple.AssignStmt fieldAssignStmt = soot.jimple.Jimple.v()
                .newAssignStmt(fieldLocal, fieldRef);

        body.getUnits().add(fieldAssignStmt);

        // if field not null
        soot.jimple.ConditionExpr cond = soot.jimple.Jimple.v()
                .newNeExpr(fieldLocal, soot.jimple.NullConstant.v());

        soot.jimple.NopStmt nop1 = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.IfStmt ifStmt = soot.jimple.Jimple.v().newIfStmt(cond,
                nop1);
        body.getUnits().add(ifStmt);

        // if alternative
        soot.Local invokeLocal = soot.jimple.Jimple.v().newLocal("$r1",
                soot.RefType.v("java.lang.Class"));

        body.getLocals().add(invokeLocal);

        List<RefType> paramTypes = new ArrayList<RefType>();
        paramTypes.add(soot.RefType.v("java.lang.String"));

        soot.SootMethodRef methodToInvoke = soot.Scene.v()
                .makeMethodRef(assertStatusClass, "class$", paramTypes,
                soot.RefType.v("java.lang.Class"), true);

        List<StringConstant> params = new ArrayList<StringConstant>();
        params.add(soot.jimple.StringConstant.v(paramName));
        soot.jimple.StaticInvokeExpr invoke = soot.jimple.Jimple.v()
                .newStaticInvokeExpr(methodToInvoke, params);
        soot.jimple.AssignStmt invokeAssign = soot.jimple.Jimple.v()
                .newAssignStmt(invokeLocal, invoke);

        body.getUnits().add(invokeAssign);

        // field ref assign
        soot.jimple.AssignStmt fieldRefAssign = soot.jimple.Jimple.v()
                .newAssignStmt(fieldRef, invokeLocal);

        body.getUnits().add(fieldRefAssign);

        soot.jimple.NopStmt nop2 = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.GotoStmt goto1 = soot.jimple.Jimple.v().newGotoStmt(nop2);

        body.getUnits().add(goto1);
        // add nop1 - and if consequence
        body.getUnits().add(nop1);

        soot.jimple.AssignStmt fieldRefAssign2 = soot.jimple.Jimple.v()
                .newAssignStmt(invokeLocal, fieldRef);

        body.getUnits().add(fieldRefAssign2);

        body.getUnits().add(nop2);

        // boolean tests
        soot.Local boolLocal1 = soot.jimple.Jimple.v().newLocal("$z0",
                soot.BooleanType.v());
        body.getLocals().add(boolLocal1);
        soot.Local boolLocal2 = soot.jimple.Jimple.v().newLocal("$z1",
                soot.BooleanType.v());
        body.getLocals().add(boolLocal2);

        // virtual invoke
        soot.SootMethodRef vMethodToInvoke = Scene.v()
                .makeMethodRef(soot.Scene.v().getSootClass("java.lang.Class"),
                "desiredAssertionStatus", new ArrayList(), soot.BooleanType.v(),
                false);
        soot.jimple.VirtualInvokeExpr vInvoke = soot.jimple.Jimple.v()
                .newVirtualInvokeExpr(invokeLocal, vMethodToInvoke,
                new ArrayList());


        soot.jimple.AssignStmt testAssign = soot.jimple.Jimple.v()
                .newAssignStmt(boolLocal1, vInvoke);

        body.getUnits().add(testAssign);

        // if
        soot.jimple.ConditionExpr cond2 = soot.jimple.Jimple.v()
                .newNeExpr(boolLocal1, soot.jimple.IntConstant.v(0));

        soot.jimple.NopStmt nop3 = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.IfStmt ifStmt2 = soot.jimple.Jimple.v().newIfStmt(cond2,
                nop3);
        body.getUnits().add(ifStmt2);

        // alternative
        soot.jimple.AssignStmt altAssign = soot.jimple.Jimple.v()
                .newAssignStmt(boolLocal2, soot.jimple.IntConstant.v(1));

        body.getUnits().add(altAssign);

        soot.jimple.NopStmt nop4 = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.GotoStmt goto2 = soot.jimple.Jimple.v().newGotoStmt(nop4);

        body.getUnits().add(goto2);

        body.getUnits().add(nop3);

        soot.jimple.AssignStmt conAssign = soot.jimple.Jimple.v()
                .newAssignStmt(boolLocal2, soot.jimple.IntConstant.v(0));

        body.getUnits().add(conAssign);

        body.getUnits().add(nop4);

        // field assign
        soot.SootFieldRef fieldD = Scene.v().makeFieldRef(body.getMethod()
                .getDeclaringClass(), "$assertionsDisabled",
                soot.BooleanType.v(), true);

        soot.jimple.FieldRef fieldRefD = soot.jimple.Jimple.v()
                .newStaticFieldRef(fieldD);
        soot.jimple.AssignStmt fAssign = soot.jimple.Jimple.v()
                .newAssignStmt(fieldRefD, boolLocal2);
        body.getUnits().add(fAssign);

    }

    public void setFinalsList(List<SootField> list){
        finalsList = list;
    }

    public List<SootField> getFinalsList(){
        return finalsList;
    }

}
