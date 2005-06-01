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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEmptyStatement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.tree.IElementType;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Trap;
import soot.Type;
import soot.Value;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public abstract class JBB3 extends JBB2 {
    List<Trap> exceptionTable;       // list of exceptions
    Stack<Value> monitorStack;     // for synchronized blocks
    Stack<PsiTryStatement> tryStack; // for try stmts in case of returns
    Stack<PsiTryStatement> catchStack; // for catch stmts in case of returns
    Map<String,soot.jimple.Stmt> labelBreakMap; // for break label --> nop to jump to
    Map<String,soot.jimple.Stmt> labelContinueMap; // for continue label --> nop to jump to
    Local outerClassParamLocal;    // outer class this

    

    protected void handleAssert(soot.SootMethod sootMethod){
        PsiMethodSource source = ((PsiMethodSource) sootMethod.getSource());
        if (!source.hasAssert()) return;
        source.addAssertInits(body);
    }

    /**
     * adds any needed field inits
     */
    protected void handleFieldInits(soot.SootMethod sootMethod) {

        PsiMethodSource source = ((PsiMethodSource) sootMethod.getSource());
        List<PsiField> fieldInits = source.getFieldInits();
        if (fieldInits != null) {
            handleFieldInits(fieldInits);
        }
    }

    protected void handleFieldInits(List<PsiField> fieldInits){
        for (PsiField field : fieldInits) {
            String fieldName = field.getName();
            PsiExpression initExpr = field.getInitializer();
            soot.SootClass currentClass = body.getMethod().getDeclaringClass();
            soot.SootFieldRef sootField = soot.Scene.v()
                    .makeFieldRef(currentClass, fieldName,
                    Util.getSootType(field.getType()),
                    field.hasModifierProperty("static"));

            soot.Local base = specialThisLocal;

            soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v()
                    .newInstanceFieldRef(base, sootField);

            soot.Value sootExpr;
            if (initExpr instanceof PsiArrayInitializerExpression) {
                sootExpr = getArrayInitLocal((PsiArrayInitializerExpression) initExpr,
                        field.getType());
            } else {
                //System.out.println("field init expr: "+initExpr);
                sootExpr = base().createExpr(initExpr);
                //System.out.println("soot expr: "+sootExpr);
            }
            if (sootExpr instanceof soot.jimple.ConditionExpr) {
                sootExpr = handleCondBinExpr((soot.jimple.ConditionExpr) sootExpr);
            }

            soot.jimple.AssignStmt assign;
            if (sootExpr instanceof soot.Local) {
                assign = soot.jimple.Jimple.v().newAssignStmt(fieldRef,
                        (soot.Local) sootExpr);
            } else if (sootExpr instanceof soot.jimple.Constant) {
                assign = soot.jimple.Jimple.v().newAssignStmt(fieldRef,
                        (soot.jimple.Constant) sootExpr);
            } else {
                throw new RuntimeException("fields must assign to local or constant only");
            }
            body.getUnits().add(assign);
            Util.addPsiTags(assign, initExpr);
            Util.addPsiTags(assign.getRightOpBox(), initExpr);
        }
    }

    /**
     * adds this field for the outer class
     */
    protected void handleOuterClassThisInit(soot.SootMethod sootMethod) {
        // static inner classes are different
        if (body.getMethod().getDeclaringClass().declaresFieldByName("this$0")){
            SootField this0Field = body.getMethod().getDeclaringClass()
                    .getFieldByName("this$0");
            soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v()
                    .newInstanceFieldRef(specialThisLocal, this0Field.makeRef());
            soot.jimple.AssignStmt stmt = soot.jimple.Jimple.v()
                    .newAssignStmt(fieldRef, outerClassParamLocal);
            body.getUnits().add(stmt);
        }
    }

    /**
     * adds any needed static field inits
     */
    protected void handleStaticFieldInits(soot.SootMethod sootMethod) {

        PsiMethodSource source = ((PsiMethodSource) sootMethod.getSource());
        List<PsiField> staticFieldInits
                = source.getStaticFieldInits();
        if (staticFieldInits != null) {
            for (PsiField field : staticFieldInits) {
                String fieldName = field.getName();
                PsiExpression initExpr = field.getInitializer();
                soot.SootClass currentClass = body.getMethod()
                        .getDeclaringClass();
                soot.SootFieldRef sootField = soot.Scene.v()
                        .makeFieldRef(currentClass, fieldName,
                        Util.getSootType(field.getType()),
                        field.getModifierList().hasModifierProperty("static"));
                soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v()
                        .newStaticFieldRef(sootField);

                //System.out.println("initExpr: "+initExpr);
                soot.Value sootExpr;
                if (initExpr instanceof PsiArrayInitializerExpression) {
                    sootExpr = getArrayInitLocal((PsiArrayInitializerExpression) initExpr,
                            field.getType());
                } else {
                    //System.out.println("field init expr: "+initExpr);
                    sootExpr = base().createExpr(initExpr);
                    //System.out.println("soot expr: "+sootExpr);

                    if (sootExpr instanceof soot.jimple.ConditionExpr) {
                        sootExpr = handleCondBinExpr((soot.jimple.ConditionExpr) sootExpr);
                    }
                }

                soot.jimple.Stmt assign = soot.jimple.Jimple.v()
                        .newAssignStmt(fieldRef, sootExpr);

                body.getUnits().add(assign);
                Util.addPsiTags(assign, initExpr);

            }
        }
    }

    /**
     * init blocks get created within init methods in Jimple
     */
    protected void handleInitializerBlocks(soot.SootMethod sootMethod) {
        PsiMethodSource source = ((PsiMethodSource) sootMethod.getSource());
        List<PsiCodeBlock> initializerBlocks
                = source.getInitializerBlocks();

        if (initializerBlocks != null) {
            handleStaticBlocks(initializerBlocks);
        }
    }

    protected void handleStaticBlocks(List<PsiCodeBlock> initializerBlocks){
        for (PsiCodeBlock initializerBlock : initializerBlocks) {
            createBlock(initializerBlock);
        }
    }

    /**
     * static init blocks get created in clinit methods in Jimple
     */
    protected void handleStaticInitializerBlocks(soot.SootMethod sootMethod) {
        PsiMethodSource source = ((PsiMethodSource) sootMethod.getSource());
        List<PsiCodeBlock> staticInitializerBlocks
                = source.getStaticInitializerBlocks();

        if (staticInitializerBlocks != null) {
            for (PsiCodeBlock staticInitializerBlock : staticInitializerBlocks) {
                createBlock(staticInitializerBlock);
            }
        }
    }

    /**
     * create body and make it be active
     */
    protected void createBody(soot.SootMethod sootMethod) {
        body = soot.jimple.Jimple.v().newBody(sootMethod);
        sootMethod.setActiveBody(body);

    }

    /**
     * Stmt creation
     */
    protected void createStmt(PsiStatement stmt) {
        if (stmt instanceof PsiExpressionStatement) {
            PsiExpression expr = ((PsiExpressionStatement) stmt).getExpression();
            boolean constCall = false;
            PsiMethodCallExpression call = null;
            if (expr instanceof PsiMethodCallExpression) {
                call = (PsiMethodCallExpression) expr;
                String qname = call.getMethodExpression().getQualifiedName();
                if (qname.equals("this") || qname.equals("super")) {
                    constCall = true;
                } else {
                    PsiMethod method = call.resolveMethod();
                    if (method != null && method.isConstructor()) {
                        constCall = true;
                    }
                }
            }
            if (constCall) {
                assert call != null;
                createConstructorCall(call);
            } else {
                base().createExpr(expr);
            }
        }
        else if (stmt instanceof PsiIfStatement) {
           createIf((PsiIfStatement)stmt);
        }
        else if (stmt instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement decl = (PsiDeclarationStatement) stmt;
            PsiElement[] declared = decl.getDeclaredElements();
            if (declared.length == 1 && declared[0] instanceof PsiClass) {
                createLocalClassDecl(decl);
            } else {
                for (PsiElement declaredElement : declared) {
                    createLocalDecl((PsiVariable) declaredElement);
                }
            }
        }
        else if (stmt instanceof PsiBlockStatement) {
            createBlock(((PsiBlockStatement)stmt).getCodeBlock());
        }
        else if (stmt instanceof PsiWhileStatement) {
            createWhile((PsiWhileStatement)stmt);
        }
        else if (stmt instanceof PsiDoWhileStatement) {
            createDo((PsiDoWhileStatement)stmt);
        }
        else if (stmt instanceof PsiForStatement) {
            createForLoop((PsiForStatement)stmt);
        }
        else if (stmt instanceof PsiSwitchStatement) {
            createSwitch((PsiSwitchStatement)stmt);
        }
        else if (stmt instanceof PsiReturnStatement) {
            createReturn((PsiReturnStatement)stmt);
        }
        else if (stmt instanceof PsiContinueStatement) {
            createContinue((PsiContinueStatement)stmt);
        }
        else if (stmt instanceof PsiBreakStatement) {
            createBreak((PsiBreakStatement)stmt);
        }
        else if (stmt instanceof PsiEmptyStatement) {
            // do nothing empty stmt
        }
        else if (stmt instanceof PsiThrowStatement) {
            createThrow((PsiThrowStatement)stmt);
        }
        else if (stmt instanceof PsiTryStatement) {
            createTry((PsiTryStatement)stmt);
        }
        else if (stmt instanceof PsiLabeledStatement) {
            createLabeled((PsiLabeledStatement)stmt);
        }
        else if (stmt instanceof PsiSynchronizedStatement) {
            createSynchronized((PsiSynchronizedStatement)stmt);
        }
        else if (stmt instanceof PsiAssertStatement) {
            createAssert((PsiAssertStatement)stmt);
        }
        else {
            throw new IllegalArgumentException("Unhandled Stmt: "+stmt.getClass());
        }
    }

    private void createContinue(PsiContinueStatement branchStmt) {
        body.getUnits().add(soot.jimple.Jimple.v().newNopStmt());
        PsiIdentifier label = branchStmt.getLabelIdentifier();
        if (label == null) {
            soot.jimple.Stmt gotoCondNoop = condControlNoop.pop();
            soot.jimple.Stmt gotoCond = soot.jimple.Jimple.v()
                    .newGotoStmt(gotoCondNoop);
            condControlNoop.push(gotoCondNoop);
            body.getUnits().add(gotoCond);
            Util.addPsiTags(gotoCond, branchStmt);
        }
        else {
            soot.jimple.Stmt gotoLabel = soot.jimple.Jimple.v()
                    .newGotoStmt(labelContinueMap.get(label.getText()));
            body.getUnits().add(gotoLabel);
            Util.addPsiTags(gotoLabel, branchStmt);
        }
    }

    private void createBreak(PsiBreakStatement branchStmt) {
        body.getUnits().add(soot.jimple.Jimple.v().newNopStmt());
        PsiIdentifier label = branchStmt.getLabelIdentifier();
        if (label == null) {
            soot.jimple.Stmt gotoEndNoop = endControlNoop.pop();
            soot.jimple.Stmt gotoEnd = soot.jimple.Jimple.v().newGotoStmt(gotoEndNoop);
            endControlNoop.push(gotoEndNoop);
            body.getUnits().add(gotoEnd);
            Util.addPsiTags(gotoEnd, branchStmt);
        }
        else {
            soot.jimple.Stmt gotoLabel = soot.jimple.Jimple.v()
                    .newGotoStmt(labelBreakMap.get(label.getText()));
            body.getUnits().add(gotoLabel);
            Util.addPsiTags(gotoLabel, branchStmt);
        }
    }

    /**
     * Labeled Stmt Creation
     */
    private void createLabeled(PsiLabeledStatement labeledStmt){
        String label = labeledStmt.getLabelIdentifier().getText();
        PsiStatement stmt = labeledStmt.getStatement();

        soot.jimple.Stmt noop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(noop);

        if (labelBreakMap == null) {
            labelBreakMap = new HashMap<String, Stmt>();
        }

        if (labelContinueMap == null) {
            labelContinueMap = new HashMap<String, Stmt>();
        }

        labelContinueMap.put(label, noop);
        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
        labelBreakMap.put(label, noop2);

        createStmt(stmt);

        body.getUnits().add(noop2);

        // the idea here is to make a map of labels to the first
        // jimple stmt of the stmt (a noop) to be created - so
        // there is something to look up for breaks and continues
        // with labels
    }

    /**
     * Assert Stmt Creation
     */
    private void createAssert(PsiAssertStatement assertStmt) {

        // check if assertions are disabled
        soot.Local testLocal = lg.generateLocal(soot.BooleanType.v());
        soot.SootFieldRef assertField = soot.Scene.v().makeFieldRef(
                body.getMethod().getDeclaringClass(), "$assertionsDisabled",
                soot.BooleanType.v(), true);
        soot.jimple.FieldRef assertFieldRef = soot.jimple.Jimple.v()
                .newStaticFieldRef(assertField);
        soot.jimple.AssignStmt fieldAssign = soot.jimple.Jimple.v()
                .newAssignStmt(testLocal, assertFieldRef);
        body.getUnits().add(fieldAssign);

        soot.jimple.NopStmt nop1 = soot.jimple.Jimple.v().newNopStmt();
        soot.jimple.ConditionExpr cond1 = soot.jimple.Jimple.v()
                .newNeExpr(testLocal, soot.jimple.IntConstant.v(0));
        soot.jimple.IfStmt testIf = soot.jimple.Jimple.v().newIfStmt(cond1, nop1);
        body.getUnits().add(testIf);

        // actual cond test
        PsiExpression cond = assertStmt.getAssertCondition();
        if (cond instanceof PsiLiteralExpression
                && cond.getType() == PsiType.BOOLEAN
                && !((Boolean) ((PsiLiteralExpression) cond).getValue())){
            // don't makeif
        }
        else {
            soot.Value sootCond = base().createExpr(cond);
            boolean needIf = needSootIf(sootCond);
            if (!(sootCond instanceof soot.jimple.ConditionExpr)) {
                sootCond = soot.jimple.Jimple.v().newEqExpr(sootCond,
                        soot.jimple.IntConstant.v(1));
            }
            else {
                sootCond = handleDFLCond((soot.jimple.ConditionExpr)sootCond);
            }

            if (needIf){
                // add if
                soot.jimple.IfStmt ifStmt = soot.jimple.Jimple.v().newIfStmt(sootCond, nop1);
                body.getUnits().add(ifStmt);

                Util.addPsiTags(ifStmt.getConditionBox(), cond);
                Util.addPsiTags(ifStmt, assertStmt);
            }
        }

        // assertion failure code
        soot.Local failureLocal = lg.generateLocal(soot.RefType.v("java.lang.AssertionError"));
        soot.jimple.NewExpr newExpr = soot.jimple.Jimple.v().newNewExpr(
                soot.RefType.v("java.lang.AssertionError"));
        soot.jimple.AssignStmt newAssign = soot.jimple.Jimple.v()
                .newAssignStmt(failureLocal, newExpr);
        body.getUnits().add(newAssign);

        soot.SootMethodRef methToInvoke;
        ArrayList<Type> paramTypes = new ArrayList<Type>();
        ArrayList<Value> params = new ArrayList<Value>();
        PsiExpression errorMsg = assertStmt.getAssertDescription();
        if (errorMsg != null){
            Value errorExpr = base().createExpr(errorMsg);
            if (errorExpr instanceof soot.jimple.ConditionExpr) {
                errorExpr = handleCondBinExpr((soot.jimple.ConditionExpr)errorExpr);
            }
            Type errorType = errorExpr.getType();

            if (errorMsg.getType() == PsiType.CHAR){
                errorType = soot.CharType.v();
            }
            if (errorType instanceof soot.IntType) {
                paramTypes.add(soot.IntType.v());
            }
            else if (errorType instanceof soot.LongType){
                paramTypes.add(soot.LongType.v());
            }
            else if (errorType instanceof soot.FloatType){
                paramTypes.add(soot.FloatType.v());
            }
            else if (errorType instanceof soot.DoubleType){
                paramTypes.add(soot.DoubleType.v());
            }
            else if (errorType instanceof soot.CharType){
                paramTypes.add(soot.CharType.v());
            }
            else if (errorType instanceof soot.BooleanType){
                paramTypes.add(soot.BooleanType.v());
            }
            else if (errorType instanceof soot.ShortType){
                paramTypes.add(soot.IntType.v());
            }
            else if (errorType instanceof soot.ByteType){
                paramTypes.add(soot.IntType.v());
            }
            else {
                paramTypes.add(soot.Scene.v().getSootClass("java.lang.Object").getType());
            }

            params.add(errorExpr);
        }
        SootClass aeClass = soot.Scene.v().getSootClass("java.lang.AssertionError");
        methToInvoke = soot.Scene.v().makeMethodRef( aeClass, "<init>",
                paramTypes, soot.VoidType.v(), false);

        soot.jimple.SpecialInvokeExpr invokeExpr = soot.jimple.Jimple.v()
                .newSpecialInvokeExpr(failureLocal, methToInvoke, params);
        soot.jimple.InvokeStmt invokeStmt = soot.jimple.Jimple.v().newInvokeStmt(invokeExpr);
        body.getUnits().add(invokeStmt);


        if (errorMsg != null){
            Util.addPsiTags(invokeExpr.getArgBox(0), errorMsg);
        }

        soot.jimple.ThrowStmt throwStmt = soot.jimple.Jimple.v().newThrowStmt(failureLocal);
        body.getUnits().add(throwStmt);

        // end
        body.getUnits().add(nop1);

    }

    /**
     * Synchronized Stmt Creation
     */
    private void createSynchronized(PsiSynchronizedStatement synchStmt) {
        PsiExpression lockExpr = synchStmt.getLockExpression();
        Value sootExpr = base().createExpr((PsiExpression) lockExpr);

        soot.jimple.EnterMonitorStmt enterMon = soot.jimple.Jimple.v().newEnterMonitorStmt(sootExpr);
        body.getUnits().add(enterMon);

        if (monitorStack == null){
            monitorStack = new Stack<Value>();
        }
        monitorStack.push(sootExpr);

        Util.addPsiTags(enterMon.getOpBox(), lockExpr);
        Util.addPsiTags(enterMon, synchStmt);

        soot.jimple.Stmt startNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(startNoop);

        createBlock(synchStmt.getBody());

        soot.jimple.ExitMonitorStmt exitMon = soot.jimple.Jimple.v().newExitMonitorStmt(sootExpr);
        body.getUnits().add(exitMon);

        monitorStack.pop();
        Util.addPsiTags(exitMon.getOpBox(), lockExpr);
        Util.addPsiTags(exitMon, synchStmt);

        soot.jimple.Stmt endSynchNoop = soot.jimple.Jimple.v().newNopStmt();
        soot.jimple.Stmt gotoEnd = soot.jimple.Jimple.v().newGotoStmt(endSynchNoop);

        soot.jimple.Stmt endNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(endNoop);

        body.getUnits().add(gotoEnd);

        soot.jimple.Stmt catchAllBeforeNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(catchAllBeforeNoop);

        // catch all
        soot.Local formalLocal = lg.generateLocal(soot.RefType.v("java.lang.Throwable"));

        soot.jimple.CaughtExceptionRef exceptRef = soot.jimple.Jimple.v().newCaughtExceptionRef();
        soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newIdentityStmt(formalLocal, exceptRef);
        body.getUnits().add(stmt);

        // catch
        soot.jimple.Stmt catchBeforeNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(catchBeforeNoop);

        soot.Local local = lg.generateLocal(soot.RefType.v("java.lang.Throwable"));

        soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(local, formalLocal);

        body.getUnits().add(assign);
        soot.jimple.ExitMonitorStmt catchExitMon = soot.jimple.Jimple.v().newExitMonitorStmt(sootExpr);

        body.getUnits().add(catchExitMon);
        Util.addPsiTags(catchExitMon.getOpBox(), lockExpr);

        soot.jimple.Stmt catchAfterNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(catchAfterNoop);

        // throw
        soot.jimple.Stmt throwStmt = soot.jimple.Jimple.v().newThrowStmt(local);
        body.getUnits().add(throwStmt);


        body.getUnits().add(endSynchNoop);

        addToExceptionList(startNoop, endNoop, catchAllBeforeNoop,
                soot.Scene.v().getSootClass("java.lang.Throwable"));
        addToExceptionList(catchBeforeNoop, catchAfterNoop, catchAllBeforeNoop,
                soot.Scene.v().getSootClass("java.lang.Throwable"));

    }

    /**
     * Return Stmts Creation
     */
    private void createReturn(PsiReturnStatement retStmt) {
        PsiExpression expr = retStmt.getReturnValue();
        Value sootLocal = null;
        if (expr != null){
            sootLocal = base().createExpr(expr);
        }

        // handle monitor exits before return if necessary
        if (monitorStack != null){
            Stack<Local> putBack = new Stack<Local>();
            while (!monitorStack.isEmpty()){
                Local exitVal = (Local)monitorStack.pop();
                putBack.push(exitVal);
                soot.jimple.ExitMonitorStmt emStmt = soot.jimple.Jimple.v().newExitMonitorStmt(exitVal);
                body.getUnits().add(emStmt);
            }
            while(!putBack.isEmpty()){
                monitorStack.push(putBack.pop());
            }
        }

        //handle finally blocks before return if inside try block
        if (tryStack != null && !tryStack.isEmpty()){
            PsiTryStatement currentTry = tryStack.pop();
            PsiCodeBlock finallyBlock = currentTry.getFinallyBlock();
            if (finallyBlock != null){
                createBlock(finallyBlock);
                tryStack.push(currentTry);
                // if return stmt contains a return don't create the other return
                ReturnStmtChecker rsc = new ReturnStmtChecker();
                finallyBlock.accept(rsc);
                if (rsc.hasRet()) return;
            }
            else {
                tryStack.push(currentTry);
            }
        }

        //handle finally blocks before return if inside catch block
        if (catchStack != null && !catchStack.isEmpty()){
            PsiTryStatement currentTry = catchStack.pop();
            PsiCodeBlock finallyBlock = currentTry.getFinallyBlock();
            if (finallyBlock != null){
                createBlock(finallyBlock);
                catchStack.push(currentTry);
                // if return stmt contains a return don't create the other return
                ReturnStmtChecker rsc = new ReturnStmtChecker();
                finallyBlock.accept(rsc);
                if (rsc.hasRet()) return;
            }
            else {
                catchStack.push(currentTry);
            }
        }

        // return
        if (expr == null) {
            soot.jimple.Stmt retStmtVoid = soot.jimple.Jimple.v().newReturnVoidStmt();
            body.getUnits().add(retStmtVoid);
            Util.addPsiTags(retStmtVoid, retStmt);
        }
        else {
            //soot.Value sootLocal = createExpr(expr);
            if (sootLocal instanceof soot.jimple.ConditionExpr) {
                sootLocal = handleCondBinExpr((soot.jimple.ConditionExpr)sootLocal);
            }
            soot.jimple.ReturnStmt retStmtLocal = soot.jimple.Jimple.v().newReturnStmt(sootLocal);
            body.getUnits().add(retStmtLocal);
            Util.addPsiTags(retStmtLocal.getOpBox(), expr);
            Util.addPsiTags(retStmtLocal, retStmt);
        }
    }

    /**
     * Throw Stmt Creation
     */
    private void createThrow(PsiThrowStatement throwStmt){
        PsiExpression thrown = throwStmt.getException();
        Value toThrow = base().createExpr(thrown);
        soot.jimple.ThrowStmt throwSt = soot.jimple.Jimple.v().newThrowStmt(toThrow);
        body.getUnits().add(throwSt);
        Util.addPsiTags(throwSt, throwStmt);
        Util.addPsiTags(throwSt.getOpBox(), thrown);
    }

    /**
     * Try Stmt Creation
     */
    private void createTry(PsiTryStatement tryStmt) {

        PsiCodeBlock finallyBlock = tryStmt.getFinallyBlock();

        if (finallyBlock == null) {
            createTryCatch(tryStmt);
        }
        else {
            createTryCatchFinally(tryStmt);
        }
    }

    /**
     * handles try/catch (try/catch/finally is separate for simplicity)
     */
    private void createTryCatch(PsiTryStatement tryStmt){

        // try
        PsiCodeBlock tryBlock = tryStmt.getTryBlock();

        // this nop is for the fromStmt of try
        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(noop1);

        if (tryStack == null){
            tryStack = new Stack<PsiTryStatement>();
        }
        tryStack.push(tryStmt);
        createBlock(tryBlock);
        tryStack.pop();

        // this nop is for the toStmt of try
        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(noop2);

        // create end nop for after entire try/catch
        soot.jimple.Stmt endNoop = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.Stmt tryEndGoto = soot.jimple.Jimple.v().newGotoStmt(endNoop);
        body.getUnits().add(tryEndGoto);

        for (PsiCatchSection catchBlock : tryStmt.getCatchSections()) {
            soot.jimple.Stmt noop3 = soot.jimple.Jimple.v().newNopStmt();
            body.getUnits().add(noop3);

            // create catch stmts

            // create catch ref
            createCatchFormal(catchBlock.getParameter());

            if (catchStack == null) {
                catchStack = new Stack<PsiTryStatement>();
            }
            catchStack.push(tryStmt);
            createBlock(catchBlock.getCatchBlock());
            catchStack.pop();

            soot.jimple.Stmt catchEndGoto = soot.jimple.Jimple.v()
                    .newGotoStmt(endNoop);
            body.getUnits().add(catchEndGoto);


            Type sootType = Util.getSootType(catchBlock.getCatchType());

            addToExceptionList(noop1, noop2, noop3,
                    soot.Scene.v().getSootClass(sootType.toString()));

        }

        body.getUnits().add(endNoop);
    }

    /**
     * handles try/catch/finally (try/catch is separate for simplicity)
     */
    private void createTryCatchFinally(PsiTryStatement tryStmt){

        HashMap<Stmt,Stmt> gotoMap = new HashMap<Stmt, Stmt>();

        // try
        PsiCodeBlock tryBlock = tryStmt.getTryBlock();

        // this nop is for the fromStmt of try
        soot.jimple.Stmt noop1 = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(noop1);

        if (tryStack == null){
            tryStack = new Stack<PsiTryStatement>();
        }
        tryStack.push(tryStmt);
        createBlock(tryBlock);
        tryStack.pop();

        // this nop is for the toStmt of try
        soot.jimple.Stmt noop2 = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(noop2);

        // create end nop for after entire try/catch
        soot.jimple.Stmt endNoop = soot.jimple.Jimple.v().newNopStmt();

        // to finally
        soot.jimple.Stmt tryGotoFinallyNoop = soot.jimple.Jimple.v().newNopStmt();

        body.getUnits().add(tryGotoFinallyNoop);
        soot.jimple.Stmt tryFinallyNoop = soot.jimple.Jimple.v().newNopStmt();

        soot.jimple.Stmt tryGotoFinally = soot.jimple.Jimple.v().newGotoStmt(tryFinallyNoop);
        body.getUnits().add(tryGotoFinally);

        // goto end stmts
        soot.jimple.Stmt beforeEndGotoNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(beforeEndGotoNoop);
        soot.jimple.Stmt tryEndGoto = soot.jimple.Jimple.v().newGotoStmt(endNoop);
        body.getUnits().add(tryEndGoto);

        gotoMap.put(tryFinallyNoop, beforeEndGotoNoop);



        // catch section
        soot.jimple.Stmt catchAllBeforeNoop = soot.jimple.Jimple.v().newNopStmt();
        for (PsiCatchSection catchBlock : tryStmt.getCatchSections()) {
            soot.jimple.Stmt noop3 = soot.jimple.Jimple.v().newNopStmt();
            body.getUnits().add(noop3);

            // create catch ref
            soot.jimple.Stmt catchRefNoop = soot.jimple.Jimple.v().newNopStmt();
            body.getUnits().add(catchRefNoop);

            createCatchFormal(catchBlock.getParameter());

            soot.jimple.Stmt catchStmtsNoop = soot.jimple.Jimple.v()
                    .newNopStmt();
            body.getUnits().add(catchStmtsNoop);

            if (catchStack == null) {
                catchStack = new Stack<PsiTryStatement>();
            }
            catchStack.push(tryStmt);
            createBlock(catchBlock.getCatchBlock());
            catchStack.pop();

            // to finally
            soot.jimple.Stmt catchGotoFinallyNoop = soot.jimple.Jimple.v()
                    .newNopStmt();
            body.getUnits().add(catchGotoFinallyNoop);
            soot.jimple.Stmt catchFinallyNoop = soot.jimple.Jimple.v()
                    .newNopStmt();

            soot.jimple.Stmt catchGotoFinally = soot.jimple.Jimple.v()
                    .newGotoStmt(catchFinallyNoop);
            body.getUnits().add(catchGotoFinally);

            // goto end stmts
            soot.jimple.Stmt beforeCatchEndGotoNoop = soot.jimple.Jimple.v()
                    .newNopStmt();
            body.getUnits().add(beforeCatchEndGotoNoop);
            soot.jimple.Stmt catchEndGoto = soot.jimple.Jimple.v()
                    .newGotoStmt(endNoop);
            body.getUnits().add(catchEndGoto);


            gotoMap.put(catchFinallyNoop, beforeCatchEndGotoNoop);

            Type sootType = Util.getSootType(catchBlock.getCatchType());

            addToExceptionList(noop1, noop2, noop3,
                    soot.Scene.v().getSootClass(sootType.toString()));
            addToExceptionList(catchStmtsNoop, beforeCatchEndGotoNoop,
                    catchAllBeforeNoop,
                    soot.Scene.v().getSootClass("java.lang.Throwable"));
        }

        // catch all ref
        Local formalLocal = lg.generateLocal(soot.RefType.v("java.lang.Throwable"));

        body.getUnits().add(catchAllBeforeNoop);
        soot.jimple.CaughtExceptionRef exceptRef = soot.jimple.Jimple.v().newCaughtExceptionRef();
        soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newIdentityStmt(formalLocal, exceptRef);
        body.getUnits().add(stmt);

        // catch all assign
        soot.jimple.Stmt beforeCatchAllAssignNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(beforeCatchAllAssignNoop);
        Local catchAllAssignLocal = lg.generateLocal(soot.RefType.v("java.lang.Throwable"));
        soot.jimple.Stmt catchAllAssign = soot.jimple.Jimple.v()
                .newAssignStmt(catchAllAssignLocal, formalLocal);

        body.getUnits().add(catchAllAssign);

        // catch all finally
        soot.jimple.Stmt catchAllFinallyNoop = soot.jimple.Jimple.v().newNopStmt();
        soot.jimple.Stmt catchAllGotoFinally = soot.jimple.Jimple.v().newGotoStmt(catchAllFinallyNoop);
        body.getUnits().add(catchAllGotoFinally);

        // catch all throw
        soot.jimple.Stmt catchAllBeforeThrowNoop = soot.jimple.Jimple.v().newNopStmt();
        body.getUnits().add(catchAllBeforeThrowNoop);
        soot.jimple.Stmt throwStmt = soot.jimple.Jimple.v().newThrowStmt(catchAllAssignLocal);
        throwStmt.addTag(new soot.tagkit.ThrowCreatedByCompilerTag());
        body.getUnits().add(throwStmt);

        gotoMap.put(catchAllFinallyNoop, catchAllBeforeThrowNoop);

        // catch all goto end
        soot.jimple.Stmt catchAllGotoEnd = soot.jimple.Jimple.v().newGotoStmt(endNoop);
        body.getUnits().add(catchAllGotoEnd);

        addToExceptionList(beforeCatchAllAssignNoop, catchAllBeforeThrowNoop,
                catchAllBeforeNoop, soot.Scene.v().getSootClass("java.lang.Throwable"));

        // create finally's
        for (Stmt noopStmt : gotoMap.keySet()) {
            body.getUnits().add(noopStmt);

            createBlock(tryStmt.getFinallyBlock());
            soot.jimple.Stmt backToStmt = gotoMap.get(noopStmt);
            soot.jimple.Stmt backToGoto = soot.jimple.Jimple.v()
                    .newGotoStmt(backToStmt);
            body.getUnits().add(backToGoto);
        }
        body.getUnits().add(endNoop);

        addToExceptionList(noop1, beforeEndGotoNoop, catchAllBeforeNoop,
                soot.Scene.v().getSootClass("java.lang.Throwable"));
    }

    /**
     * add exceptions to a list that gets added at end of method
     */
    private void addToExceptionList(soot.jimple.Stmt from, soot.jimple.Stmt to,
            soot.jimple.Stmt with, SootClass exceptionClass) {
        if (exceptionTable == null) {
            exceptionTable = new ArrayList<Trap>();
        }
        Trap trap = soot.jimple.Jimple.v().newTrap(exceptionClass, from, to, with);
        exceptionTable.add(trap);
    }

    public soot.jimple.Constant createConstant(PsiExpression expr,
            Object constValue){
        return getConstant(constValue, expr.getType());
    }

    protected soot.SootMethod addSetAccessMeth(SootClass conClass,
            PsiField field, Value param){
        IdentityKey<PsiField> fieldKey = new IdentityKey<PsiField>(field);
        Map<IdentityKey<PsiField>, SootMethod> privateFieldSetAccessMap = initialResolver
                .getPrivateFieldSetAccessMap();
        if ((privateFieldSetAccessMap != null) && (privateFieldSetAccessMap.containsKey(fieldKey))){
            return privateFieldSetAccessMap.get(fieldKey);
        }
        String name = "access$"+initialResolver.getNextPrivateAccessCounter()+"00";
        ArrayList<Type> paramTypes = new ArrayList<Type>();
        boolean isStatic = field.getModifierList().hasModifierProperty("static");
        if (!isStatic){
            // add this param type
            paramTypes.add(conClass.getType());
            //paramTypes.add(Util.getSootType(field.target().type()));
        }
        paramTypes.add(param.getType());
        soot.SootMethod meth = new soot.SootMethod(name, paramTypes, param.getType(), soot.Modifier.STATIC);
        PrivateFieldSetMethodSource pfsms = new PrivateFieldSetMethodSource(
            Util.getSootType(field.getType()),
            field.getName(),
            isStatic
            );


        conClass.addMethod(meth);
        meth.setActiveBody(pfsms.getBody(meth, null));

        initialResolver.addToPrivateFieldSetAccessMap(field, meth);
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    protected soot.SootMethod addGetFieldAccessMeth(SootClass conClass,
            PsiField field){
        if ((initialResolver.getPrivateFieldGetAccessMap() != null)
                && (initialResolver.getPrivateFieldGetAccessMap()
                .containsKey(new IdentityKey<PsiField>(field)))){
            return initialResolver.getPrivateFieldGetAccessMap()
                    .get(new IdentityKey<PsiField>(field));
        }
        String name = "access$"+initialResolver
                .getNextPrivateAccessCounter()+"00";
        ArrayList<RefType> paramTypes = new ArrayList<RefType>();
        boolean isStatic = field.getModifierList().hasModifierProperty("static");
        if (!isStatic){
            // add this param type
            paramTypes.add(conClass.getType());//(soot.Local)getBaseLocal(field.target()));
            //paramTypes.add(Util.getSootType(field.target().type()));
        }

        soot.SootMethod meth = new soot.SootMethod(name, paramTypes,
                Util.getSootType(field.getType()),  soot.Modifier.STATIC);
        PrivateFieldAccMethodSource pfams = new PrivateFieldAccMethodSource(
            Util.getSootType(field.getType()),
            field.getName(),
            isStatic,
            conClass
        );


        conClass.addMethod(meth);
        meth.setActiveBody(pfams.getBody(meth, null));

        initialResolver.addToPrivateFieldGetAccessMap(field, meth);
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    protected soot.SootMethod addGetMethodAccessMeth(SootClass conClass,
            PsiMethodCallExpression call){
        Map<IdentityKey<PsiMethod>, SootMethod> privateMethodGetAccessMap = initialResolver
                .getPrivateMethodGetAccessMap();
        IdentityKey<PsiMethod> methKey = new IdentityKey<PsiMethod>(call.resolveMethod());
        if ((privateMethodGetAccessMap != null)
                && (privateMethodGetAccessMap.containsKey(methKey))){
            return privateMethodGetAccessMap.get(methKey);
        }
        String name = "access$"+initialResolver
                .getNextPrivateAccessCounter()+"00";
        List<Type> paramTypes = new ArrayList<Type>();
        PsiMethod method = call.resolveMethod();
        if (!method.hasModifierProperty("static")){
            // add this param type
            //paramTypes.add(Util.getSootType(call.methodInstance().container()));
            paramTypes.add(conClass.getType());
        }
        List<Type> sootParamsTypes = getSootParamsTypes(call);
        paramTypes.addAll(sootParamsTypes);
        soot.SootMethod meth = new soot.SootMethod(name, paramTypes,
                Util.getSootType(method.getReturnType()),  soot.Modifier.STATIC);
        PrivateMethodAccMethodSource pmams = new PrivateMethodAccMethodSource(
            method
        );


        conClass.addMethod(meth);
        meth.setActiveBody(pmams.getBody(meth, null));

        initialResolver.addToPrivateMethodGetAccessMap(call, meth);
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    protected Local getStringConcatAssignRightLocal(PsiAssignmentExpression assign){
        Local sb = (Local)createStringBuffer(assign);
        generateAppends(assign.getLExpression(), sb);
        generateAppends(assign.getRExpression(), sb);
        Local rLocal = createToString(sb, assign);
        return rLocal;
    }

    protected Local getComplexAssignRightLocal(PsiAssignmentExpression assign, Local leftLocal){
        Value right = base().createExpr(assign.getRExpression());
        if (right instanceof soot.jimple.ConditionExpr) {
            right = handleCondBinExpr((soot.jimple.ConditionExpr)right);
        }

        soot.jimple.BinopExpr binop = null;
        IElementType op = assign.getOperationSign().getTokenType();
        if (op == JavaTokenType.PLUSEQ) {
            binop = soot.jimple.Jimple.v().newAddExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.MINUSEQ){
            binop = soot.jimple.Jimple.v().newSubExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.ASTERISKEQ) {
            binop = soot.jimple.Jimple.v().newMulExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.DIVEQ) {
            binop = soot.jimple.Jimple.v().newDivExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.PERCEQ) {
            binop = soot.jimple.Jimple.v().newRemExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.LTLTEQ) {
            binop = soot.jimple.Jimple.v().newShlExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.GTGTEQ) {
            binop = soot.jimple.Jimple.v().newShrExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.GTGTGTEQ) {
            binop = soot.jimple.Jimple.v().newUshrExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.ANDEQ) {
            binop = soot.jimple.Jimple.v().newAndExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.OREQ) {
            binop = soot.jimple.Jimple.v().newOrExpr(leftLocal, right);
        }
        else if (op == JavaTokenType.XOREQ) {
            binop = soot.jimple.Jimple.v().newXorExpr(leftLocal, right);
        }

        Local retLocal = lg.generateLocal(leftLocal.getType());
        soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v().newAssignStmt(retLocal, binop);
        body.getUnits().add(assignStmt);

        Util.addPsiTags(binop.getOp1Box(), assign.getLExpression());
        Util.addPsiTags(binop.getOp2Box(), assign.getRExpression());

        return retLocal;
    }

    protected Value getSimpleAssignLocal(PsiAssignmentExpression assign){
        soot.jimple.AssignStmt stmt;
        Value left = base().createLHS(assign.getLExpression());

        Value right = base().getSimpleAssignRightLocal(assign);
        stmt = soot.jimple.Jimple.v().newAssignStmt(left, right);
        body.getUnits().add(stmt);
        Util.addPsiTags(stmt, assign);
        Util.addPsiTags(stmt.getRightOpBox(), assign.getRExpression());
        Util.addPsiTags(stmt.getLeftOpBox(), assign.getLExpression());
        if (left instanceof Local){
            return left;
        }
        else {
            return right;
        }

    }

    protected soot.jimple.Constant getConstant(Object constVal, PsiType type){
        //System.out.println("getConstant: "+constVal);
        if (constVal instanceof String){
            return soot.jimple.StringConstant.v((String)constVal);
        }
        else if (constVal instanceof Boolean){
            boolean val = (Boolean) constVal;
            return soot.jimple.IntConstant.v(val ? 1 : 0);
        }
        else if (type.equals(PsiType.CHAR)){
            char val;

            if (constVal instanceof Integer){
                val = (char) ((Integer) constVal).intValue();
            }
            else {
                val = ((Character) constVal);
            }
            //System.out.println("val: "+val);
            return soot.jimple.IntConstant.v(val);
        }
        else {
            Number num = (Number)constVal;
            //System.out.println("num: "+num);
            num = createConstantCast(type, num);
            //System.out.println("num: "+num);
            if (num instanceof Long) {
                //System.out.println(((Long)num).longValue());
                return soot.jimple.LongConstant.v((Long) num);
            }
            else if (num instanceof Double) {
                return soot.jimple.DoubleConstant.v((Double) num);
            }
            else if (num instanceof Float) {
                return soot.jimple.FloatConstant.v((Float) num);
            }
            else if (num instanceof Byte) {
                return soot.jimple.IntConstant.v((Byte) num);
            }
            else if (num instanceof Short) {
                return soot.jimple.IntConstant.v((Short) num);
            }
            else {
                return soot.jimple.IntConstant.v((Integer) num);
            }
        }
    }

    @SuppressWarnings("UnnecessaryBoxing")
    protected Number createConstantCast(PsiType fieldType, Number constant) {
        if (constant instanceof Integer){
            Integer intConstant = (Integer) constant;
            if (fieldType.equals(PsiType.DOUBLE)){
                return new Double((double) intConstant);
            }
            else if (fieldType.equals(PsiType.FLOAT)){
                return new Float((float) intConstant);
            }
            else if (fieldType.equals(PsiType.LONG)){
                return new Long((long) intConstant);
            }
        }
        return constant;
    }

    protected Local createStringBuffer(PsiExpression expr){
        // create and add one string buffer
        Local local = lg.generateLocal(RefType.v("java.lang.StringBuffer"));
        soot.jimple.NewExpr newExpr = soot.jimple.Jimple.v()
                .newNewExpr(RefType.v("java.lang.StringBuffer"));
        soot.jimple.Stmt assign = soot.jimple.Jimple.v().newAssignStmt(local, newExpr);

        body.getUnits().add(assign);
        Util.addPsiTags(assign, expr);

        SootClass classToInvoke1 = soot.Scene.v().getSootClass("java.lang.StringBuffer");
        soot.SootMethodRef methodToInvoke1 = soot.Scene.v().makeMethodRef(
                classToInvoke1, "<init>", new ArrayList(), soot.VoidType.v(), false);

        soot.jimple.SpecialInvokeExpr invoke = soot.jimple.Jimple.v().newSpecialInvokeExpr(
                local, methodToInvoke1);

        soot.jimple.Stmt invokeStmt = soot.jimple.Jimple.v().newInvokeStmt(invoke);
        body.getUnits().add(invokeStmt);
        Util.addPsiTags(invokeStmt, expr);

        return local;
    }

    protected Local createToString(Local sb, PsiExpression expr){
        // invoke toString on local (type StringBuffer)
        Local newString = lg.generateLocal(RefType.v("java.lang.String"));
        SootClass classToInvoke2 = soot.Scene.v().getSootClass("java.lang.StringBuffer");
        soot.SootMethodRef methodToInvoke2 = soot.Scene.v().makeMethodRef(
                classToInvoke2, "toString", new ArrayList(), RefType.v("java.lang.String"), false);

        soot.jimple.VirtualInvokeExpr toStringInvoke = soot.jimple.Jimple.v()
                .newVirtualInvokeExpr(sb, methodToInvoke2);

        soot.jimple.Stmt lastAssign = soot.jimple.Jimple.v().newAssignStmt(newString, toStringInvoke);

        body.getUnits().add(lastAssign);
        Util.addPsiTags(lastAssign, expr);

        return newString;
    }

    private boolean isStringConcat(PsiExpression expr){
        if (expr instanceof PsiBinaryExpression) {
            PsiBinaryExpression bin = (PsiBinaryExpression)expr;
            if (bin.getOperationSign().getTokenType() == JavaTokenType.EQ){
                if (bin.getType().equalsToText("java.lang.String")) return true;
                return false;
            }
            return false;
        }
        else if (expr instanceof PsiAssignmentExpression) {
            PsiAssignmentExpression assign = (PsiAssignmentExpression)expr;
            if (assign.getOperationSign().getTokenType() == JavaTokenType.PLUSEQ){
                if (assign.getType().equalsToText("java.lang.String")) return true;
                return false;
            }
            return false;
        }
        return false;
    }

    /**
     * Generates one part of a concatenation String
     */
    protected void generateAppends(PsiExpression expr, Local sb) {

        //System.out.println("generate appends for expr: "+expr);
        if (isStringConcat(expr)){
            if (expr instanceof PsiBinaryExpression){
                PsiBinaryExpression bexpr = ((PsiBinaryExpression) expr);
                generateAppends(bexpr.getLOperand(), sb);
                generateAppends(bexpr.getROperand(), sb);
            }
            else {
                PsiAssignmentExpression aexpr = ((PsiAssignmentExpression) expr);
                generateAppends(aexpr.getLExpression(), sb);
                generateAppends(aexpr.getRExpression(), sb);
            }
        }
        else {
            Value toApp = base().createExpr(expr);
            //System.out.println("toApp: "+toApp+" type: "+toApp.getType());
            Type appendType = null;
            if (toApp instanceof soot.jimple.StringConstant) {
                appendType = RefType.v("java.lang.String");
            }
            else if (toApp instanceof soot.jimple.NullConstant){
                appendType = RefType.v("java.lang.Object");
            }
            else if (toApp instanceof soot.jimple.Constant) {
                appendType = toApp.getType();
            }
            else if (toApp instanceof Local) {
                if (((Local)toApp).getType() instanceof soot.PrimType) {
                    appendType = ((Local)toApp).getType();
                }
                else if (((Local)toApp).getType() instanceof RefType) {
                    if (((Local)toApp).getType().toString().equals("java.lang.String")){
                        appendType = RefType.v("java.lang.String");
                    }
                    else if (((Local)toApp).getType().toString().equals("java.lang.StringBuffer")){
                        appendType = RefType.v("java.lang.StringBuffer");
                    }
                    else{
                        appendType = RefType.v("java.lang.Object");
                    }
                }
                else {
                    // this is for arrays
                    appendType = RefType.v("java.lang.Object");
                }
            }
            else if (toApp instanceof soot.jimple.ConditionExpr) {
                toApp = handleCondBinExpr((soot.jimple.ConditionExpr)toApp);
                appendType = soot.BooleanType.v();
            }

            // handle shorts
            if (appendType instanceof soot.ShortType || appendType instanceof soot.ByteType) {
                Local intLocal = lg.generateLocal(soot.IntType.v());
                soot.jimple.Expr cast = soot.jimple.Jimple.v().newCastExpr(
                        toApp, soot.IntType.v());
                soot.jimple.Stmt castAssign = soot.jimple.Jimple.v().newAssignStmt(
                        intLocal, cast);
                body.getUnits().add(castAssign);
                toApp = intLocal;
                appendType = soot.IntType.v();
            }

            ArrayList<Type> paramsTypes = new ArrayList<Type>();
            paramsTypes.add(appendType);
            ArrayList<Value> params = new ArrayList<Value>();
            params.add(toApp);

            SootClass classToInvoke = soot.Scene.v().getSootClass("java.lang.StringBuffer");
            soot.SootMethodRef methodToInvoke = soot.Scene.v().makeMethodRef(
                    classToInvoke, "append", paramsTypes,
                    RefType.v("java.lang.StringBuffer"), false);

            soot.jimple.VirtualInvokeExpr appendInvoke
                    = soot.jimple.Jimple.v().newVirtualInvokeExpr(sb, methodToInvoke, params);

            Util.addPsiTags(appendInvoke.getArgBox(0), expr);
            soot.jimple.Stmt appendStmt = soot.jimple.Jimple.v().newInvokeStmt(appendInvoke);

            body.getUnits().add(appendStmt);

            Util.addPsiTags(appendStmt, expr);
        }
    }

    /**
     * Returns list of param types
     */
    protected List<Type> getSootParamsTypes(PsiCallExpression call) {
        List<Type> sootParamsTypes = new ArrayList<Type>();
        for (PsiParameter next : call.resolveMethod()
                .getParameterList().getParameters()) {
            sootParamsTypes.add(Util.getSootType(next.getType()));
        }
        return sootParamsTypes;
    }

    protected abstract void createConstructorCall(PsiMethodCallExpression cCall);

    /**
     * Local Class Decl - Local Inner Class
     */
    private void createLocalClassDecl(PsiDeclarationStatement cDecl) {
        PsiClass cls = (PsiClass) cDecl.getDeclaredElements()[0];
        String name = Util.getSootType(cls).toString();
        SootClass declaringClass = body.getMethod().getDeclaringClass();
        if (!initialResolver.hasClassInnerTag(declaringClass, name)){
            Util.addInnerClassTag(declaringClass, name, null, cls.getName(),
                    Util.getModifier((PsiModifierListOwner) cDecl));
        }
    }
}
