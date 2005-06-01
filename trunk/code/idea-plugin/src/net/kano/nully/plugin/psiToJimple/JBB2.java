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

package net.kano.nully.plugin.psiToJimple;

import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.Type;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.LongConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public abstract class JBB2 extends AbstractJimpleBodyBuilder {
    protected Stack<soot.jimple.Stmt> endControlNoop = new Stack<soot.jimple.Stmt>();     // for break
    protected Stack<soot.jimple.Stmt> condControlNoop = new Stack<soot.jimple.Stmt>();    // continue
    protected Map<PsiVariable,Local> localsMap
            = new IdentityHashMap<PsiVariable, Local>();    // localInst --> soot local
    protected Map<Type,Local> getThisMap = new HashMap<Type, Local>(); // type --> local to ret
    protected Local specialThisLocal;    // === body.getThisLocal();
    protected int paramRefCount = 0;  // counter for param ref stmts
    protected LocalGenerator lg;  // for generated locals not in orig src

    /**
     * Block creation
     */
    protected void createBlock(PsiCodeBlock block){
        if (block == null) return;

        // handle stmts
        for (PsiStatement next : block.getStatements()) createStmt(next);
    }

    /**
     * Catch Formal creation - method parameters
     */
    protected soot.Local createCatchFormal(PsiParameter formal){
        soot.Local formalLocal = createLocal(formal);
        soot.jimple.CaughtExceptionRef exceptRef = soot.jimple.Jimple.v().newCaughtExceptionRef();
        soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newIdentityStmt(formalLocal, exceptRef);
        body.getUnits().add(stmt);

        Util.addPsiTags(stmt, formal);
        Util.addPsiTags(((soot.jimple.IdentityStmt) stmt).getRightOpBox(), formal);

        ArrayList<String> names = new ArrayList<String>();
        names.add(formal.getName());
        stmt.addTag(new soot.tagkit.ParamNamesTag(names));
        return formalLocal;
    }

    /**
     * Formal creation - method parameters
     */
    protected void createFormal(PsiParameter formal, int counter){

        soot.Type sootType = Util.getSootType(formal.getType());
        soot.Local formalLocal = createLocal(formal);
        soot.jimple.ParameterRef paramRef = soot.jimple.Jimple.v().newParameterRef(sootType, counter);
        paramRefCount++;
        soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newIdentityStmt(formalLocal, paramRef);

        body.getUnits().add(stmt);

        Util.addPsiTags(((soot.jimple.IdentityStmt) stmt).getRightOpBox(), formal);
        Util.addPsiTags(stmt, formal);

    }

    /**
     * Literal Creation
     */
    protected soot.Value createLiteral(PsiLiteralExpression lit) {
        PsiType type = lit.getType();
        Object value = lit.getValue();
        if (type.equals(PsiType.INT)) {
            return IntConstant.v(((Number) value).intValue());
        } else if (type.equals(PsiType.LONG)) {
            return LongConstant.v(((Number) value).longValue());
        } else if (value instanceof String) {
            return StringConstant.v((String) value);
        } else if (type.equals(PsiType.NULL)) {
            return soot.jimple.NullConstant.v();
        } else if (type.equals(PsiType.DOUBLE)) {
            return soot.jimple.DoubleConstant.v(((Number) value).doubleValue());
        } else if (type.equals(PsiType.FLOAT)) {
            return soot.jimple.FloatConstant.v(((Number) value).floatValue());
        } else if (type.equals(PsiType.CHAR)) {
            return IntConstant.v(((Number) value).intValue());
        } else if (type.equals(PsiType.BOOLEAN)) {
            boolean litValue = (Boolean) value;
            if (litValue) {
                return soot.jimple.IntConstant.v(1);
            } else {
                return soot.jimple.IntConstant.v(0);
            }
        }
        else {
            throw new RuntimeException("Unknown Literal - Unhandled: "+lit.getClass());
        }
    }

    /**
     * Local Creation
     */

// this should be used for polyglot locals and formals
    protected soot.Local createLocal(PsiVariable localInst) {

        soot.Type sootType = Util.getSootType(localInst.getType());
        String name = localInst.getName();
        soot.Local sootLocal = createLocal(name, sootType);

        localsMap.put(localInst, sootLocal);
        return sootLocal;
    }

    // this should be used for generated locals only
    protected soot.Local createLocal(String name, soot.Type sootType) {
        soot.Local sootLocal = soot.jimple.Jimple.v().newLocal(name, sootType);
        body.getLocals().add(sootLocal);
        return sootLocal;
    }

    /**
     * Local Retreival
     */
    protected soot.Local getLocal(PsiReferenceExpression local) {

        return getLocal((PsiVariable) local.resolve());
    }

    /**
     * Local Retreival
     */
    protected soot.Local getLocal(PsiVariable li) {

        if (localsMap.containsKey(li)) {
            soot.Local sootLocal = localsMap.get(li);
            return sootLocal;
        }
        else if (body.getMethod().getDeclaringClass().declaresField("val$"+li.getName(),
                Util.getSootType(li.getType()))){
            soot.Local fieldLocal = generateLocal(li.getType());
            soot.SootFieldRef field = soot.Scene.v().makeFieldRef(
                    body.getMethod().getDeclaringClass(), "val$"+li.getName(),
                    Util.getSootType(li.getType()), false);
            soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v()
                    .newInstanceFieldRef(specialThisLocal, field);
            soot.jimple.AssignStmt assign = soot.jimple.Jimple.v()
                    .newAssignStmt(fieldLocal, fieldRef);
            body.getUnits().add(assign);
            return fieldLocal;
        }
        /*else {
                throw new RuntimeException("Trying unsuccessfully to get local: "+li.name());
            }*/
        else {
            //else create access meth in outer for val$fieldname
            // get the this$0 field to find the type of an outer class - has
            // to have one because local/anon inner can't declare static
            // memebers so for deepnesting not in static context for these
            // cases

            soot.SootClass currentClass = body.getMethod().getDeclaringClass();
            boolean fieldFound = false;

            while (!fieldFound){
                if (!currentClass.declaresFieldByName("this$0")){
                    throw new RuntimeException("Trying to get field val$"
                            +li.getName()+" from some outer class but can't "
                            + "access the outer class of: "+currentClass.getName()
                            +"!"+" current class contains fields: "
                            +currentClass.getFields());
                }
                Type type = currentClass.getFieldByName("this$0").getType();
                soot.SootClass outerClass = ((soot.RefType)type).getSootClass();
                // look for field of type li.type and name val$li.name in outer
                // class
                if (outerClass.declaresField("val$"+li.getName(), Util.getSootType(li.getType()))){
                    fieldFound = true;
                }
                currentClass = outerClass;
                // repeat until found in some outer class
            }
            // create and add accessor to that outer class (indic as current)
            soot.SootMethod methToInvoke = makeLiFieldAccessMethod(currentClass, li);

            // invoke and return
            // generate a local that corresponds to the invoke of that meth
            ArrayList<Local> methParams = new ArrayList<Local>();
            methParams.add(getThis(currentClass.getType()));

            Local res = Util.getPrivateAccessFieldInvoke(methToInvoke.makeRef(),
                    methParams, body, lg);
            return res;
        }
    }

    protected soot.SootMethod makeLiFieldAccessMethod(soot.SootClass classToInvoke,
            PsiVariable li){
        String name = "access$"+initialResolver
                .getNextPrivateAccessCounter()+"00";
        ArrayList<RefType> paramTypes = new ArrayList<RefType>();
        paramTypes.add(classToInvoke.getType());

        soot.SootMethod meth = new soot.SootMethod(name, paramTypes,
                Util.getSootType(li.getType()), soot.Modifier.STATIC);

        classToInvoke.addMethod(meth);
        PrivateFieldAccMethodSource src = new PrivateFieldAccMethodSource(
        Util.getSootType(li.getType()),
        "val$"+li.getName(),
        false,
                classToInvoke
        );
        meth.setActiveBody(src.getBody(meth, null));
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    protected abstract void createStmt(PsiStatement stmt);

    protected boolean needSootIf(soot.Value sootCond){
        if (sootCond instanceof soot.jimple.IntConstant){
            if (((soot.jimple.IntConstant)sootCond).value == 1){
                return false;
            }
        }
        return true;
    }

    /**
     * If Stmts Creation - only add line-number tags to if (the other
     * stmts needing tags are created elsewhere
     */
    protected void createIf(PsiIfStatement ifExpr){

        // handle cond
        PsiExpression condition = ifExpr.getCondition();
        soot.Value sootCond = base().createExpr(condition);
        boolean needIf = needSootIf(sootCond);
        if (!(sootCond instanceof soot.jimple.ConditionExpr)) {
            sootCond = soot.jimple.Jimple.v().newEqExpr(sootCond,
                    soot.jimple.IntConstant.v(0));
        }
        else {
            sootCond = reverseCondition((soot.jimple.ConditionExpr)sootCond);
            sootCond = handleDFLCond((soot.jimple.ConditionExpr)sootCond);
        }

        // add if
        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();

        if (needIf) {
            soot.jimple.IfStmt ifStmt = soot.jimple.Jimple.v().newIfStmt(sootCond, noop1);
            body.getUnits().add(ifStmt);
            // add line and pos tags
            Util.addPsiTags(ifStmt.getConditionBox(), condition);
            Util.addPsiTags(ifStmt, ifExpr);
        }

        // add consequence
        PsiStatement consequence = ifExpr.getThenBranch();
        createStmt(consequence);

        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
        soot.jimple.Stmt goto1 = soot.jimple.Jimple.v().newGotoStmt(noop2);
        body.getUnits().add(goto1);

        body.getUnits().add(noop1);

        // handle alternative
        PsiStatement alternative = ifExpr.getElseBranch();
        if (alternative != null){
            createStmt(alternative);
        }

        body.getUnits().add(noop2);

    }

    /**
     * While Stmts Creation
     */
    protected void createWhile(PsiWhileStatement whileStmt){

        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();

        // these are for break and continue
        endControlNoop.push(soot.jimple.Jimple.v().newNopStmt());
        condControlNoop.push(soot.jimple.Jimple.v().newNopStmt());

        // handle body

        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
        soot.jimple.Stmt goto1 = soot.jimple.Jimple.v().newGotoStmt(noop2);
        body.getUnits().add(goto1);
        body.getUnits().add(noop1);
        createStmt(whileStmt.getBody());

        body.getUnits().add(noop2);

        // handle cond
        body.getUnits().add((condControlNoop.pop()));

        PsiExpression condition = whileStmt.getCondition();
        soot.Value sootCond = base().createExpr(condition);
        boolean needIf = needSootIf(sootCond);
        if (!(sootCond instanceof soot.jimple.ConditionExpr)) {
            sootCond = soot.jimple.Jimple.v().newNeExpr(sootCond, soot.jimple.IntConstant.v(0));
        }
        else {
            sootCond = handleDFLCond((soot.jimple.ConditionExpr)sootCond);
        }

        if (needIf){
            soot.jimple.IfStmt ifStmt = soot.jimple.Jimple.v().newIfStmt(sootCond, noop1);

            body.getUnits().add(ifStmt);
            Util.addPsiTags(ifStmt.getConditionBox(), condition);
            Util.addPsiTags(ifStmt, condition);
        }
        else {
            soot.jimple.GotoStmt gotoIf = soot.jimple.Jimple.v().newGotoStmt(noop1);
            body.getUnits().add(gotoIf);
        }

        body.getUnits().add((endControlNoop.pop()));

    }

    /**
     * DoWhile Stmts Creation
     */
    protected void createDo(PsiDoWhileStatement doStmt){

        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(noop1);

        // these are for break and continue
        endControlNoop.push(soot.jimple.Jimple.v().newNopStmt());
        condControlNoop.push(soot.jimple.Jimple.v().newNopStmt());

        // handle body
        createStmt(doStmt.getBody());

        // handle cond
        body.getUnits().add((condControlNoop.pop()));

        PsiExpression condition = doStmt.getCondition();
        soot.Value sootCond = base().createExpr(condition);
        boolean needIf = needSootIf(sootCond);
        if (!(sootCond instanceof soot.jimple.ConditionExpr)) {
            sootCond = soot.jimple.Jimple.v().newNeExpr(sootCond, soot.jimple.IntConstant.v(0));
        }
        else {
            sootCond = handleDFLCond((soot.jimple.ConditionExpr)sootCond);
        }
        if (needIf){
            soot.jimple.IfStmt ifStmt = soot.jimple.Jimple.v().newIfStmt(sootCond, noop1);
            body.getUnits().add(ifStmt);
            Util.addPsiTags(ifStmt, condition);
        }
        else {
            soot.jimple.GotoStmt gotoIf = soot.jimple.Jimple.v().newGotoStmt(noop1);
            body.getUnits().add(gotoIf);
        }
        body.getUnits().add((endControlNoop.pop()));
    }

    /**
     * For Loop Stmts Creation
     */
    protected void createForLoop(PsiForStatement forStmt){
        // these ()are for break and continue
        endControlNoop.push(soot.jimple.Jimple.v().newNopStmt());
        condControlNoop.push(soot.jimple.Jimple.v().newNopStmt());

        // handle for inits
        createStmt(forStmt.getInitialization());
        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();

        // handle body
        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
        soot.jimple.Stmt goto1 = soot.jimple.Jimple.v().newGotoStmt(noop2);
        body.getUnits().add(goto1);
        body.getUnits().add(noop1);
        createStmt(forStmt.getBody());

        // handle continue
        body.getUnits().add((condControlNoop.pop()));

        // handle iters
        createStmt(forStmt.getUpdate());
        body.getUnits().add(noop2);

        // handle cond

        PsiExpression condition = forStmt.getCondition();
        if (condition == null) {
            soot.jimple.Stmt goto2 = soot.jimple.Jimple.v().newGotoStmt(noop1);
            body.getUnits().add(goto2);

        } else {
            soot.Value sootCond = base().createExpr(condition);
            boolean needIf = needSootIf(sootCond);
            if (!(sootCond instanceof soot.jimple.ConditionExpr)) {
                sootCond = soot.jimple.Jimple.v().newNeExpr(sootCond, soot.jimple.IntConstant.v(0));
            }
            else {
                sootCond = handleDFLCond((soot.jimple.ConditionExpr)sootCond);
            }
            if (needIf){
                soot.jimple.IfStmt ifStmt = soot.jimple.Jimple.v().newIfStmt(sootCond, noop1);

                // add cond
                body.getUnits().add(ifStmt);

                // add line and pos tags
                Util.addPsiTags(ifStmt.getConditionBox(), condition);
            }
            else {
                soot.jimple.GotoStmt gotoIf = soot.jimple.Jimple.v().newGotoStmt(noop1);
                body.getUnits().add(gotoIf);
            }

        }

        body.getUnits().add((endControlNoop.pop()));

    }

    /**
     * Local Decl Creation
     */
    protected void createLocalDecl(PsiVariable localDecl) {
        soot.Value lhs = createLocal(localDecl);
        PsiExpression expr = localDecl.getInitializer();
        if (expr != null) {
            //System.out.println("expr: "+expr+" get type: "+expr.getClass());
            soot.Value rhs;
            if (expr instanceof PsiArrayInitializerExpression){
                //System.out.println("creating array from localdecl: "+localInst.type());
                rhs = getArrayInitLocal((PsiArrayInitializerExpression)expr, localDecl.getType());
            }
            else {
                rhs = base().createExpr(expr);
            }
            if (rhs instanceof soot.jimple.ConditionExpr) {
                rhs = handleCondBinExpr((soot.jimple.ConditionExpr)rhs);
            }
            //System.out.println("rhs: "+rhs);
            soot.jimple.AssignStmt stmt = soot.jimple.Jimple.v().newAssignStmt(lhs, rhs);
            body.getUnits().add(stmt);
            Util.addPsiTags(stmt, localDecl);
            // this is a special case for position tags
            Util.addPsiTags(stmt.getLeftOpBox(), localDecl.getNameIdentifier());
            if (expr != null){
                Util.addPsiTags(stmt.getRightOpBox(), expr);
            }
        }
    }

    /**
     * Switch Stmts Creation
     */
    protected void createSwitch(PsiSwitchStatement switchStmt) {

        PsiExpression value = switchStmt.getExpression();
        soot.Value sootValue = base().createExpr(value);
        soot.jimple.Stmt defaultTarget = null;

        PsiElement[] children = switchStmt.getChildren();
        PsiSwitchLabelStatement[] caseArray = new PsiSwitchLabelStatement[children.length];
        soot.jimple.Stmt [] targetsArray = new soot.jimple.Stmt[children.length];

        List<soot.jimple.Stmt> targets = new ArrayList<soot.jimple.Stmt>();
        Map<PsiElement,soot.jimple.Stmt> targetsMap
                = new HashMap<PsiElement, soot.jimple.Stmt>();
        int counter = 0;
        for (PsiElement next : children) {
            if (next instanceof PsiSwitchLabelStatement) {
                soot.jimple.Stmt noop = soot.jimple.Jimple.v().newNopStmt();
                PsiSwitchLabelStatement label = ((PsiSwitchLabelStatement) next);
                if (label.isDefaultCase()) {
                    defaultTarget = noop;
                } else {
                    targets.add(noop);
                    caseArray[counter] = label;
                    targetsArray[counter] = noop;
                    counter++;
                    targetsMap.put(next, noop);
                }
            }
        }

        PsiConstantEvaluationHelper constHelper = switchStmt.getManager()
                .getConstantEvaluationHelper();
        for (int i = 0; i < counter; i++) {
            Number ival = (Number) constHelper.computeConstantExpression(
                    caseArray[i].getCaseValue());
            for (int j = i+1; j < counter; j++) {
                Number jval = (Number) constHelper.computeConstantExpression(
                        caseArray[j].getCaseValue());
                if (jval.intValue() < ival.intValue()) {
                    PsiSwitchLabelStatement tempCase = caseArray[i];
                    soot.jimple.Stmt tempTarget = targetsArray[i];
                    caseArray[i] = caseArray[j];
                    targetsArray[i] = targetsArray[j];
                    caseArray[j] = tempCase;
                    targetsArray[j] = tempTarget;
                }
            }
        }

        List<soot.jimple.Stmt> sortedTargets = new ArrayList<soot.jimple.Stmt>();

        for (int i = 0; i < counter; i++) {
            sortedTargets.add(targetsArray[i]);
        }

        // deal with default
        boolean hasDefaultTarget = true;
        if (defaultTarget == null) {
            soot.jimple.Stmt noop = soot.jimple.Jimple.v().newNopStmt();
            defaultTarget = noop;
            hasDefaultTarget = false;
        }

        // lookup or tableswitch
        soot.jimple.Stmt sootSwitchStmt;
        if (isLookupSwitch(switchStmt)) {

            List<soot.jimple.IntConstant> values = new ArrayList<soot.jimple.IntConstant>();
            for (int i = 0; i < counter; i++) {
                if (!caseArray[i].isDefaultCase()) {
                    PsiExpression caseValExp = caseArray[i].getCaseValue();
                    Number constExp = ((Number) constHelper.computeConstantExpression(caseValExp));
                    values.add(soot.jimple.IntConstant.v(constExp.intValue()));
                }
            }


            soot.jimple.LookupSwitchStmt lookupStmt = soot.jimple.Jimple.v()
                    .newLookupSwitchStmt(sootValue, values, sortedTargets, defaultTarget);

            Util.addPsiTags(lookupStmt.getKeyBox(), value);
            sootSwitchStmt = lookupStmt;

        } else {
            long lowVal = 0;
            long highVal = 0;
            boolean unknown = true;

            for (PsiElement next : children) {
                if (next instanceof PsiSwitchLabelStatement) {
                    PsiSwitchLabelStatement label = ((PsiSwitchLabelStatement) next);
                    if (!label.isDefaultCase()) {
                        long temp = ((Number) constHelper.computeConstantExpression(
                                label.getCaseValue())).longValue();
                        if (unknown) {
                            highVal = temp;
                            lowVal = temp;
                            unknown = false;
                        }
                        if (temp > highVal) {
                            highVal = temp;
                        }
                        if (temp < lowVal) {
                            lowVal = temp;
                        }
                    }
                }

            }

            soot.jimple.TableSwitchStmt tableStmt = soot.jimple.Jimple.v()
                    .newTableSwitchStmt(sootValue, (int)lowVal, (int)highVal,
                    sortedTargets, defaultTarget);

            Util.addPsiTags(tableStmt.getKeyBox(), value);
            sootSwitchStmt = tableStmt;
        }

        body.getUnits().add(sootSwitchStmt);

        Util.addPsiTags(sootSwitchStmt, switchStmt);
        endControlNoop.push(soot.jimple.Jimple.v().newNopStmt());

        for (PsiElement next : children) {
            if (next instanceof PsiSwitchLabelStatement) {
                PsiSwitchLabelStatement label = (PsiSwitchLabelStatement) next;
                Stmt target;
                if (label.isDefaultCase()) {
                    target = defaultTarget;
                } else {
                    target = targetsMap.get(next);
                }
                body.getUnits().add(target);
            }
            else {
                PsiCodeBlock blockStmt = (PsiCodeBlock)next;
                createBlock(blockStmt);
            }
        }

        if (!hasDefaultTarget) {
            body.getUnits().add(defaultTarget);
        }
        body.getUnits().add((endControlNoop.pop()));
    }

    /**
     * Determine if switch should be lookup or table - this doesn't
     * always get the same result as javac
     * lookup: non-table
     * table: sequential (no gaps)
     */
    protected boolean isLookupSwitch(PsiSwitchStatement switchStmt){

        int lowest = 0;
        int highest = 0;
        int counter = 0;
        PsiConstantEvaluationHelper constHelper = switchStmt.getManager()
                .getConstantEvaluationHelper();
        for (PsiElement next : switchStmt.getChildren()) {
            if (next instanceof PsiSwitchLabelStatement) {
                PsiSwitchLabelStatement caseStmt = (PsiSwitchLabelStatement) next;
                if (caseStmt.isDefaultCase()) continue;
                int caseValue = ((Number) constHelper.computeConstantExpression(
                        caseStmt.getCaseValue())).intValue();
                if (caseValue <= lowest || counter == 0) {
                    lowest = caseValue;
                }
                if (caseValue >= highest || counter == 0) {
                    highest = caseValue;
                }
                counter++;
            }
        }

        if ((counter-1) == (highest - lowest)) return false;
        return true;
    }

    /**
     * To get the local for the special .class literal
     */
    protected Local getSpecialClassLitLocal(PsiClassObjectAccessExpression lit) {

        PsiType type = lit.getOperand().getType();
        if (type instanceof PsiPrimitiveType){
            PsiPrimitiveType primType = (PsiPrimitiveType)type;
            Local retLocal = lg.generateLocal(RefType.v("java.lang.Class"));
            soot.SootFieldRef primField = null;
            if (primType.equals(PsiType.BOOLEAN)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Boolean"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.BYTE)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Byte"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.CHAR)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Character"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.DOUBLE)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Double"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.FLOAT)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Float"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.INT)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Integer"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.LONG)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Long"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.SHORT)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Short"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            } else if (primType.equals(PsiType.VOID)) {
                primField = soot.Scene.v().makeFieldRef(soot.Scene.v()
                        .getSootClass("java.lang.Void"), "TYPE",
                        RefType.v("java.lang.Class"), true);
            }
            soot.jimple.StaticFieldRef fieldRef = soot.jimple.Jimple.v().newStaticFieldRef(primField);
            soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v().newAssignStmt(retLocal, fieldRef);
            body.getUnits().add(assignStmt);
            return retLocal;
        }
        else {
            // this class
            soot.SootClass thisClass = body.getMethod().getDeclaringClass();
            String fieldName = Util.getFieldNameForClassLit(type);
            Type fieldType = RefType.v("java.lang.Class");
            Local fieldLocal = lg.generateLocal(RefType.v("java.lang.Class"));
            soot.SootFieldRef sootField = null;
            if (thisClass.isInterface()){
                Map<SootClass,SootClass> specialAnonMap = initialResolver.specialAnonMap();
                if ((specialAnonMap != null) && (specialAnonMap.containsKey(thisClass))){
                    soot.SootClass specialClass = (soot.SootClass)specialAnonMap.get(thisClass);
                    sootField = soot.Scene.v().makeFieldRef(specialClass, fieldName, fieldType, true);
                }
                else {
                    throw new IllegalStateException("Class is interface so it must "
                            + "have an anon class to handle class lits but its "
                            + "anon class cannot be found.");
                }
            }
            else {
                sootField = soot.Scene.v().makeFieldRef(thisClass, fieldName, fieldType, true);
            }
            soot.jimple.StaticFieldRef fieldRef = soot.jimple.Jimple.v().newStaticFieldRef(sootField);
            soot.jimple.Stmt fieldAssign = soot.jimple.Jimple.v().newAssignStmt(fieldLocal,  fieldRef);
            body.getUnits().add(fieldAssign);

            soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();
            soot.jimple.Expr neExpr = soot.jimple.Jimple.v().newNeExpr(fieldLocal,
                    soot.jimple.NullConstant.v());
            soot.jimple.Stmt ifStmt = soot.jimple.Jimple.v().newIfStmt(neExpr, noop1);
            body.getUnits().add(ifStmt);

            ArrayList<RefType> paramTypes = new ArrayList<RefType>();
            paramTypes.add(RefType.v("java.lang.String"));
            soot.SootMethodRef invokeMeth = null;
            if (thisClass.isInterface()){
                Map specialAnonMap = initialResolver.specialAnonMap();
                if ((specialAnonMap != null) && (specialAnonMap.containsKey(thisClass))){
                    soot.SootClass specialClass = (soot.SootClass)specialAnonMap.get(thisClass);
                    invokeMeth  = soot.Scene.v().makeMethodRef(specialClass, "class$",
                            paramTypes, RefType.v("java.lang.Class"), true);
                }
                else {
                    throw new RuntimeException("Class is interface so it must "
                            + "have an anon class to handle class lits but its "
                            + "anon class cannot be found.");
                }
            }
            else {
                invokeMeth = soot.Scene.v().makeMethodRef(thisClass, "class$",
                        paramTypes, RefType.v("java.lang.Class"), true);
            }
            List<soot.jimple.StringConstant> params = new ArrayList<soot.jimple.StringConstant>();
            params.add(soot.jimple.StringConstant.v(Util.getParamNameForClassLit(type)));
            soot.jimple.Expr classInvoke = soot.jimple.Jimple.v().newStaticInvokeExpr(
                    invokeMeth, params);
            Local methLocal = lg.generateLocal(RefType.v("java.lang.Class"));
            soot.jimple.Stmt invokeAssign = soot.jimple.Jimple.v()
                    .newAssignStmt(methLocal, classInvoke);
            body.getUnits().add(invokeAssign);

            soot.jimple.Stmt assignField = soot.jimple.Jimple.v()
                    .newAssignStmt(fieldRef, methLocal);
            body.getUnits().add(assignField);

            soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
            soot.jimple.Stmt goto1 = soot.jimple.Jimple.v().newGotoStmt(noop2);
            body.getUnits().add(goto1);

            body.getUnits().add(noop1);
            fieldAssign = soot.jimple.Jimple.v().newAssignStmt(methLocal,  fieldRef);
            body.getUnits().add(fieldAssign);
            body.getUnits().add(noop2);

            return methLocal;
        }
    }

    /**
     * in bytecode and Jimple the conditions in conditional binary
     * expressions are often reversed
     */
    protected soot.Value reverseCondition(soot.jimple.ConditionExpr cond) {

        soot.jimple.ConditionExpr newExpr;
        if (cond instanceof soot.jimple.EqExpr) {
            newExpr = soot.jimple.Jimple.v().newNeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.NeExpr) {
            newExpr = soot.jimple.Jimple.v().newEqExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.GtExpr) {
            newExpr = soot.jimple.Jimple.v().newLeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.GeExpr) {
            newExpr = soot.jimple.Jimple.v().newLtExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.LtExpr) {
            newExpr = soot.jimple.Jimple.v().newGeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof soot.jimple.LeExpr) {
            newExpr = soot.jimple.Jimple.v().newGtExpr(cond.getOp1(), cond.getOp2());
        }
        else {
            throw new RuntimeException("Unknown Condition Expr");
        }


        newExpr.getOp1Box().addAllTagsOf(cond.getOp1Box());
        newExpr.getOp2Box().addAllTagsOf(cond.getOp2Box());
        return newExpr;
    }

    /**
     * Special conditions for doubles and floats and longs
     */
    protected soot.Value handleDFLCond(soot.jimple.ConditionExpr cond){
        Local result = lg.generateLocal(soot.ByteType.v());
        soot.jimple.Expr cmExpr = null;
        if (isDouble(cond.getOp1()) || isDouble(cond.getOp2())
                || isFloat(cond.getOp1()) || isFloat(cond.getOp2())) {
            // use cmpg and cmpl
            if ((cond instanceof soot.jimple.GeExpr) || (cond instanceof soot.jimple.GtExpr)) {
                // use cmpg
                cmExpr = soot.jimple.Jimple.v().newCmpgExpr(cond.getOp1(), cond.getOp2());
            }
            else {
                // use cmpl
                cmExpr = soot.jimple.Jimple.v().newCmplExpr(cond.getOp1(), cond.getOp2());
            }
        }
        else if (isLong(cond.getOp1()) || isLong(cond.getOp2())) {
            // use cmp
            cmExpr = soot.jimple.Jimple.v().newCmpExpr(cond.getOp1(), cond.getOp2());
        }
        else {
            return cond;
        }
        soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(result, cmExpr);
        body.getUnits().add(assign);

        if (cond instanceof soot.jimple.EqExpr){
            cond = soot.jimple.Jimple.v().newEqExpr(result, soot.jimple.IntConstant.v(0));
        }
        else if (cond instanceof soot.jimple.GeExpr){
            cond = soot.jimple.Jimple.v().newGeExpr(result, soot.jimple.IntConstant.v(0));
        }
        else if (cond instanceof soot.jimple.GtExpr){
            cond = soot.jimple.Jimple.v().newGtExpr(result, soot.jimple.IntConstant.v(0));
        }
        else if (cond instanceof soot.jimple.LeExpr){
            cond = soot.jimple.Jimple.v().newLeExpr(result, soot.jimple.IntConstant.v(0));
        }
        else if (cond instanceof soot.jimple.LtExpr){
            cond = soot.jimple.Jimple.v().newLtExpr(result, soot.jimple.IntConstant.v(0));
        }
        else if (cond instanceof soot.jimple.NeExpr){
            cond = soot.jimple.Jimple.v().newNeExpr(result, soot.jimple.IntConstant.v(0));
        }
        else {
            throw new RuntimeException("Unknown Comparison Expr");
        }

        return cond;
    }

    protected boolean isDouble(soot.Value val) {
        if (val.getType() instanceof soot.DoubleType) return true;
        return false;
    }

    protected boolean isFloat(soot.Value val) {
        if (val.getType() instanceof soot.FloatType) return true;
        return false;
    }

    protected boolean isLong(soot.Value val) {
        if (val.getType() instanceof soot.LongType) return true;
        return false;
    }

    protected Local handleCondBinExpr(soot.jimple.ConditionExpr condExpr) {

        Local boolLocal = lg.generateLocal(soot.BooleanType.v());

        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();

        soot.Value newVal;

        newVal = reverseCondition(condExpr);
        newVal = handleDFLCond((soot.jimple.ConditionExpr)newVal);

        soot.jimple.Stmt ifStmt = soot.jimple.Jimple.v().newIfStmt(newVal, noop1);
        body.getUnits().add(ifStmt);

        body.getUnits().add(soot.jimple.Jimple.v().newAssignStmt(boolLocal,
                soot.jimple.IntConstant.v(1)));

        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.Stmt goto1 = soot.jimple.Jimple.v().newGotoStmt(noop2);

        body.getUnits().add(goto1);

        body.getUnits().add(noop1);

        body.getUnits().add(soot.jimple.Jimple.v().newAssignStmt(boolLocal,
                soot.jimple.IntConstant.v(0)));

        body.getUnits().add(noop2);

        return boolLocal;


    }

    protected Local getThis(Type sootType){

        return Util.getThis(sootType, body, getThisMap, lg);
    }

    /**
     * create ArrayInit given init and the array local
     */
    protected Local getArrayInitLocal(PsiArrayInitializerExpression arrInit, PsiType lhsType) {

        //System.out.println("lhs type: "+lhsType);

        Local local = generateLocal(lhsType);

        PsiExpression[] elements = arrInit.getInitializers();
        soot.jimple.NewArrayExpr arrExpr = soot.jimple.Jimple.v()
                .newNewArrayExpr(((soot.ArrayType) local.getType()).getElementType(),
                soot.jimple.IntConstant.v(elements.length));

        soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(local,
                arrExpr);

        body.getUnits().add(assign);
        Util.addPsiTags(assign, arrInit);


        int index = 0;
        //TODO: test array initializer within array initializer
        for (PsiExpression elemExpr : elements) {
            soot.Value elem;
            if (elemExpr instanceof PsiArrayInitializerExpression) {
                PsiArrayInitializerExpression initExpr = (PsiArrayInitializerExpression) elemExpr;
                if (initExpr.getType().equals(PsiType.NULL)) {
                    if (lhsType instanceof PsiArrayType) {
                        elem = getArrayInitLocal(initExpr,
                                ((PsiArrayType) lhsType).getComponentType());
                    } else {
                        elem = getArrayInitLocal(initExpr, lhsType);

                    }
                } else {
                    elem = getArrayInitLocal(initExpr,
                            ((PsiArrayType) lhsType).getComponentType());
                }
            } else {
                elem = base().createExpr(elemExpr);
            }
            soot.jimple.ArrayRef arrRef = soot.jimple.Jimple.v()
                    .newArrayRef(local, soot.jimple.IntConstant.v(index));

            soot.jimple.AssignStmt elemAssign = soot.jimple.Jimple.v()
                    .newAssignStmt(arrRef, elem);
            body.getUnits().add(elemAssign);
            Util.addPsiTags(elemAssign, elemExpr);
            Util.addPsiTags(elemAssign.getRightOpBox(), elemExpr);

            index++;
        }

        return local;
    }

    /**
     * Extra Local Variables Generation
     */
    protected Local generateLocal(PsiType psiType) {
        Type type = Util.getSootType(psiType);
        return lg.generateLocal(type);
    }

    protected abstract soot.SootMethodRef getMethodFromClass(SootClass sootClass,
            String name, List<Type> paramTypes, Type returnType,
            boolean isStatic);

    protected abstract void addUsedLocalParams(List<Value> sootParams,
            List<Type> sootParamTypes, PsiClass keyType);

}
