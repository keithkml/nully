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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import soot.Local;
import soot.MethodSource;
import soot.SootField;
import soot.SootMethod;
import soot.Trap;
import soot.Type;
import soot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JimpleBodyBuilder extends JBB4 {

    public JimpleBodyBuilder(InitialResolver initialResolver){
        this.initialResolver = initialResolver;
        //ext(null);
        //base(this);
    }


    /**
     * Jimple Body Creation
     */
    public soot.jimple.JimpleBody createJimpleBody(PsiCodeBlock block,
            List<PsiParameter> formals, soot.SootMethod sootMethod){

        createBody(sootMethod);

        lg = new LocalGenerator(body);
        // create this formal except for static methods
        if (!soot.Modifier.isStatic(sootMethod.getModifiers())) {

            soot.RefType type = sootMethod.getDeclaringClass().getType();
            specialThisLocal = soot.jimple.Jimple.v().newLocal("this", type);
            body.getLocals().add(specialThisLocal);

            soot.jimple.ThisRef thisRef = soot.jimple.Jimple.v().newThisRef(type);

            soot.jimple.Stmt thisStmt = soot.jimple.Jimple.v()
                    .newIdentityStmt(specialThisLocal, thisRef);
            body.getUnits().add(thisStmt);

            // this is causing problems - no this in java code -> no tags
            //Util.addLineTag(thisStmt, block);
        }

        int formalsCounter = 0;

        //create outer class this param ref for inner classes except for static
        // inner classes - this is not needed
        int outerIndex = sootMethod.getDeclaringClass().getName().lastIndexOf("$");
        int classMod = sootMethod.getDeclaringClass().getModifiers();

        if ((outerIndex != -1) && (sootMethod.getName().equals("<init>"))
                && sootMethod.getDeclaringClass().declaresFieldByName("this$0")){

            // we know its an inner non static class can get outer class
            // from field ref of the this$0 field
            SootField this0field = sootMethod.getDeclaringClass().getFieldByName("this$0");
            soot.SootClass outerClass = ((soot.RefType)this0field.getType()).getSootClass();
            soot.Local outerLocal = lg.generateLocal(outerClass.getType());

            soot.jimple.ParameterRef paramRef = soot.jimple.Jimple.v()
                    .newParameterRef(outerClass.getType(), formalsCounter);
            paramRefCount++;
            soot.jimple.Stmt stmt = soot.jimple.Jimple.v().newIdentityStmt(outerLocal, paramRef);
            stmt.addTag(new soot.tagkit.EnclosingTag());
            body.getUnits().add(stmt);

            MethodSource source = sootMethod.getSource();
            ((PsiMethodSource)source).setOuterClassThisInit(outerLocal);
            outerClassParamLocal = outerLocal;
            formalsCounter++;
        }

        // handle formals
        if (formals != null) {
            ArrayList<String> formalNames = new ArrayList<String>();
            for (PsiParameter formal : formals) {
                createFormal(formal, formalsCounter);
                formalNames.add(formal.getName());
                formalsCounter++;
            }
            body.getMethod().addTag(new soot.tagkit.ParamNamesTag(formalNames));
        }

        // handle final local params
        PsiMethodSource source = getMethodSource();
        List<SootField> finalsList = source.getFinalsList();
        if (finalsList != null){
            for (SootField sf : finalsList) {
                soot.jimple.ParameterRef paramRef = soot.jimple.Jimple.v()
                        .newParameterRef(sf.getType(), formalsCounter);
                paramRefCount++;
                soot.jimple.Stmt stmt = soot.jimple.Jimple.v()
                        .newIdentityStmt(lg.generateLocal(sf.getType()),
                        paramRef);
                body.getUnits().add(stmt);
                formalsCounter++;
            }
        }

        PsiMethod method = getMethodForCodeBlock(block);
        if (method != null) {
            Util.addPsiTags(sootMethod, method);
        }

        String methodName = sootMethod.getName();
        if (methodName.equals("<clinit>")){
            // if method is <clinit> handle static field inits
            handleAssert(sootMethod);
            handleStaticFieldInits(sootMethod);
            handleStaticInitializerBlocks(sootMethod);

        } else if (methodName.equals("<init>")) {
            if (method != null) {
                SuperOrThisCallFinder finder = new SuperOrThisCallFinder();
                block.accept(finder);
                if (!finder.isCallsSuper()) {
                    handleImpliedConstructorCall(method);
                }
            }
        }
        createBlock(block);

        // determine if body has a return stmt
        boolean hasReturn = false;
		if (block != null) {
            for (PsiStatement next : block.getStatements()) {
			    if (next instanceof PsiReturnStatement){
                    hasReturn = true;
                }
            }
        }

        soot.Type retType = body.getMethod().getReturnType();
        // only do this if noexplicit return
	    if ((!hasReturn) && (retType instanceof soot.VoidType)) {
            soot.jimple.Stmt retStmt = soot.jimple.Jimple.v().newReturnVoidStmt();
            body.getUnits().add(retStmt);
        }

        // add exceptions from exceptionTable
        if (exceptionTable != null) {
            for (Trap aExceptionTable : exceptionTable) {
                body.getTraps().add(aExceptionTable);
            }
        }
        return body;

    }

    private PsiMethod getMethodForCodeBlock(PsiCodeBlock block) {
        PsiMethod methodt = PsiTreeUtil.getParentOfType(block, PsiMethod.class);
        if (methodt != null && block == methodt.getBody()) {
            return methodt;
        }
        return null;
    }


    protected soot.Local handlePrivateFieldUnarySet(PsiExpression unary){
        PsiReferenceExpression expr = (PsiReferenceExpression) Util.getUnaryOperand(unary);

        PsiExpression qualifier = expr.getQualifierExpression();
        soot.Value base;
        if (qualifier == null) {
            base = specialThisLocal;
        } else {
            base = base().getBaseLocal(qualifier);
        }
        PsiField field = (PsiField) expr.resolve();
        soot.Value fieldGetLocal = getPrivateAccessFieldLocal(field, base);

        soot.Local tmp = generateLocal(expr.getType());
        soot.jimple.AssignStmt stmt1 = soot.jimple.Jimple.v().newAssignStmt(tmp, fieldGetLocal);
        body.getUnits().add(stmt1);
        Util.addPsiTags(stmt1, unary);

        soot.Value incVal = base().getConstant(Util.getSootType(expr.getType()), 1);

        soot.jimple.BinopExpr binExpr;
        IElementType op = Util.getUnaryOperationSign(unary).getTokenType();
        if (op == JavaTokenType.PLUSPLUS) {
            binExpr = soot.jimple.Jimple.v().newAddExpr(tmp, incVal);
        }
        else {
            binExpr = soot.jimple.Jimple.v().newSubExpr(tmp, incVal);
        }

        soot.Local tmp2 = generateLocal(expr.getType());
        soot.jimple.AssignStmt assign = soot.jimple.Jimple.v().newAssignStmt(tmp2, binExpr);
        body.getUnits().add(assign);

        if (unary instanceof PsiPrefixExpression){
            return base().handlePrivateFieldSet(expr, tmp2, base);
        }
        else {
            base().handlePrivateFieldSet(expr, tmp2, base);
            return tmp;
        }
    }

    protected Local handlePrivateFieldAssignSet(PsiAssignmentExpression assign){
        PsiReferenceExpression lvalue = (PsiReferenceExpression) assign.getLExpression();
        PsiField fLeft = (PsiField)lvalue.resolve();
        //soot.Value right = createExpr(assign.right());

        // if assign is not = but +=, -=, *=, /=, >>=, >>>-, <<=, %=,
        // |= &= or ^= then compute it all into a local first
        //if (assign.operator() != polyglot.ast.Assign.ASSIGN){
        // in this cas can cast to local (never a string const here
        // as it has to be a lhs
        soot.Value right;
        PsiExpression lqualifier = lvalue.getQualifierExpression();
        soot.Value fieldBase;
        if (lqualifier == null) {
            if (fLeft.hasModifierProperty("static")) {
                fieldBase = null;
            } else {
                fieldBase = specialThisLocal;
            }
        } else {
            fieldBase = base().getBaseLocal(lqualifier);
        }
        IElementType op = assign.getOperationSign().getTokenType();
        if (op == JavaTokenType.EQ){
            right = base().getSimpleAssignRightLocal(assign);
        }
        else if ((op == JavaTokenType.PLUSEQ)
                && assign.getType().equalsToText("java.lang.String")){
            right = getStringConcatAssignRightLocal(assign);
        }
        else {
            // here the lhs is a private field and needs to use get call
            soot.Local leftLocal = getPrivateAccessFieldLocal(fLeft, fieldBase);
            //soot.Local leftLocal = (soot.Local)base().createExpr(fLeft);
            right = base().getAssignRightLocal(assign, leftLocal);
        }

        return handlePrivateFieldSet(lvalue, right, fieldBase);
    }

    protected soot.Local handlePrivateFieldSet(PsiReferenceExpression expr,
            soot.Value right, soot.Value base){
        // in normal j2j its always a field (and checked before call)
        // only has an expr for param for extensibility
        PsiField field = (PsiField) expr.resolve();
        PsiExpression qualifier = expr.getQualifierExpression();
        PsiClass destCls;
        if (qualifier == null) {
            destCls = field.getContainingClass();
        } else {
            destCls = ((PsiClassType) qualifier.getType()).resolve();
        }
        soot.SootClass containClass = Util.getSootType(
                destCls).getSootClass();
        soot.SootMethod methToUse = addSetAccessMeth(containClass, field, right);
        List<Value> params = new ArrayList<Value>();
        if (!field.getModifierList().hasModifierProperty("static")){
            // this is the this ref if needed
            //params.add(getThis(Util.getSootType(fLeft.target().type())));
            params.add(base);
        }
        params.add(right);
        soot.jimple.InvokeExpr invoke = soot.jimple.Jimple.v().newStaticInvokeExpr(
                methToUse.makeRef(), params);
        soot.Local retLocal = lg.generateLocal(right.getType());
        soot.jimple.AssignStmt assignStmt = soot.jimple.Jimple.v().newAssignStmt(retLocal, invoke);
        body.getUnits().add(assignStmt);

        return retLocal;
    }


    protected soot.Value getAssignRightLocal(PsiAssignmentExpression assign, soot.Local leftLocal){
        IElementType op = assign.getOperationSign().getTokenType();
        if (op == JavaTokenType.EQ){
            return base().getSimpleAssignRightLocal(assign);
        }
        else if (op == JavaTokenType.PLUSEQ
                && assign.getType().equalsToText("java.lang.String")){
            return getStringConcatAssignRightLocal(assign);
        }
        else {
            return getComplexAssignRightLocal(assign, leftLocal);
        }
    }

    protected Value getSimpleAssignRightLocal(PsiAssignmentExpression assign){
        soot.Value right = base().createExpr(assign.getRExpression());
        if (right instanceof soot.jimple.ConditionExpr) {
            right = handleCondBinExpr((soot.jimple.ConditionExpr)right);
        }
        return right;
    }


    /**
     * Field Expression Creation - LHS
     */
    private soot.Value getFieldLocalLeft(PsiReferenceExpression field){
        PsiExpression qualifier = field.getQualifierExpression();
        if (field.getReferenceName().equals("length") && qualifier != null
                && qualifier.getType() instanceof PsiArrayType){
            return getSpecialArrayLengthLocal(field);
        }
        else {
            return getFieldRef(field);
        }
    }

    /**
     *  needs a private access method if field is private and in
     *  some other class
     */
    /*protected boolean needsPrivateAccessor(polyglot.ast.Field field){
        if (field.fieldInstance().flags().isPrivate()){
            if (!Util.getSootType(field.fieldInstance().container()).equals(body.getMethod().getDeclaringClass().getType())){
                return true;
            }
        }
        return false;
    }*/

    /**
     *  creates a field ref
     */
    protected soot.jimple.FieldRef getFieldRef(PsiReferenceExpression ref) {
        PsiField field = (PsiField) ref.resolve();
        PsiExpression qualifier = ref.getQualifierExpression();
        PsiClass qualifierCls;
        boolean isStatic = field.hasModifierProperty("static");
        if (qualifier == null || qualifier.getType() == null) {
            qualifierCls = field.getContainingClass();
        } else {
            qualifierCls = ((PsiClassType) qualifier.getType()).resolve();
        }
        soot.SootClass receiverClass = Util.getSootType(qualifierCls).getSootClass();
        soot.SootFieldRef receiverField = soot.Scene.v().makeFieldRef(receiverClass,
                field.getName(), Util.getSootType(field.getType()), isStatic);

        soot.jimple.FieldRef fieldRef;
        if (isStatic) {
            fieldRef = soot.jimple.Jimple.v().newStaticFieldRef(receiverField);
        } else {
            soot.Local base;
            if (qualifier == null) {
                base = (soot.Local)base().getBaseLocal(qualifier);
            } else {
                base = specialThisLocal;
            }
            fieldRef = soot.jimple.Jimple.v().newInstanceFieldRef(base, receiverField);
        }

        if (qualifier instanceof PsiReferenceExpression){
            PsiElement qreferee = ((PsiReferenceExpression) qualifier).resolve();
            if (qreferee instanceof PsiLocalVariable
                    || qreferee instanceof PsiParameter
                    && fieldRef instanceof soot.jimple.InstanceFieldRef){
                Util.addPsiTags(((soot.jimple.InstanceFieldRef)fieldRef)
                        .getBaseBox(), qualifier);
            }
        }
        return fieldRef;
    }


    /**
     * Returns a needed constant given a type and val
     */
    protected soot.jimple.Constant getConstant(soot.Type type, int val) {

        if (type instanceof soot.DoubleType) {
            return soot.jimple.DoubleConstant.v(val);
        }
        else if (type instanceof soot.FloatType) {
            return soot.jimple.FloatConstant.v(val);
        }
        else if (type instanceof soot.LongType) {
            return soot.jimple.LongConstant.v(val);
        }
        else {
            return soot.jimple.IntConstant.v(val);
        }
    }

    /**
     * Gets the Soot Method form the given Soot Class
     */
    protected soot.SootMethodRef getMethodFromClass(soot.SootClass sootClass,
            String name, List<Type> paramTypes, soot.Type returnType,
            boolean isStatic) {
        soot.SootMethodRef ref = soot.Scene.v().makeMethodRef(sootClass, name,
                paramTypes, returnType, isStatic);
        return ref;
    }

    /**
     * Adds extra params
     */
    protected void addUsedLocalParams(List<Value> sootParams,
            List<Type> sootParamTypes, PsiClass keyType){
        Map<IdentityKey<PsiClass>,AnonLocalClassInfo> finalLocalInfo
                = initialResolver.finalLocalInfo();
        if (finalLocalInfo != null){
            IdentityKey<PsiClass> key = new net.kano.nully.plugin.psiToJimple.IdentityKey<PsiClass>(keyType);
            if (finalLocalInfo.containsKey(key)){
                AnonLocalClassInfo alci = (AnonLocalClassInfo)finalLocalInfo.get(key);

                List<IdentityKey<PsiVariable>> finalLocals = alci.finalLocalsUsed();
                if (finalLocals != null){
                    for (IdentityKey<PsiVariable> next : finalLocals) {
                        PsiVariable li =   next.object();
                        sootParamTypes.add(Util.getSootType(li.getType()));
                        sootParams.add(getLocal(li));
                    }
                }
            }
        }
    }

    protected boolean needsOuterClassRef(PsiClass typeToInvoke){
        // anon and local
        AnonLocalClassInfo info = initialResolver
                .finalLocalInfo().get(new IdentityKey<PsiClass>(typeToInvoke));

        if (initialResolver.isAnonInCCall(typeToInvoke)) return false;

        if ((info != null) && (!info.inStaticMethod())){
            return true;
        }

        // other nested
        else if (typeToInvoke.getContainingClass() != null
                && !typeToInvoke.getModifierList().hasModifierProperty("static")
                && !PsiUtil.isLocalOrAnonymousClass(typeToInvoke)){
            return true;
        }

        return false;
    }

    /**
     * adds outer class params
     */
    protected void handleOuterClassParams(List<Value> sootParams,
            soot.Value qVal,  List<Type> sootParamsTypes,
            PsiClass typeToInvoke){
        boolean addRef = needsOuterClassRef(typeToInvoke);
        //(needsRef != null) && (needsRef.contains(Util.getSootType(typeToInvoke)));
        PsiClass outerCls = typeToInvoke.getContainingClass();
        if (addRef){
            // if adding an outer type ref always add exact type
            soot.SootClass outerClass = Util.getSootType(
                    outerCls).getSootClass();
            sootParamsTypes.add(outerClass.getType());
        }

        boolean isAnon = typeToInvoke instanceof PsiAnonymousClass;
        if (addRef && !isAnon && (qVal != null)){
            // for nested and local if qualifier use that for param
            sootParams.add(qVal);
        }
        else if (addRef && !isAnon){
            soot.SootClass outerClass = Util.getSootType(
                    outerCls).getSootClass();
            sootParams.add(getThis(outerClass.getType()));
        }
        else if (addRef && isAnon){
            soot.SootClass outerClass = Util.getSootType(
                    outerCls).getSootClass();
            sootParams.add(getThis(outerClass.getType()));
        }

        // handle anon qualifiers
        if (isAnon && (qVal != null)){
            sootParamsTypes.add(qVal.getType());
            sootParams.add(qVal);
        }
    }

    /**
     * Constructor Call Creation
     */
    protected void createConstructorCall(PsiMethodCallExpression call) {


        PsiMethod cInst = call.resolveMethod();

        PsiClass cls = cInst.getContainingClass();

        soot.SootClass classToInvoke;

        PsiReferenceExpression methodExp = call.getMethodExpression();
        String name = methodExp.getReferenceName();
        PsiExpression qualifier = methodExp.getQualifierExpression();

        if (name.equals("super")) {
            PsiElementFactory factory = call.getManager().getElementFactory();
            classToInvoke = ((soot.RefType)Util.getSootType(factory.createType(cls))).getSootClass();
        }
        else if (name.equals("this")) {
            classToInvoke = body.getMethod().getDeclaringClass();
        }
        else {
            throw new IllegalArgumentException("Unknown kind of Constructor Call " + call);
        }
        soot.Local qVal = null;
        if (qualifier != null){
            qVal = (soot.Local)base().createExpr(qualifier);
        }

        List<Value> sootParams = new ArrayList<Value>();
        List<Type> sootParamsTypes = new ArrayList<Type>();
        handleOuterClassParams(sootParams, qVal, sootParamsTypes, cls);
        int prefixParamCount = sootParamsTypes.size();
        sootParams.addAll(getSootParams(call));
        sootParamsTypes.addAll(getSootParamsTypes(call));
        addUsedLocalParams(sootParams, sootParamsTypes, cls);

        soot.SootMethodRef methodToInvoke = getMethodFromClass(classToInvoke,
                "<init>", sootParamsTypes, soot.VoidType.v(), false);

        soot.jimple.SpecialInvokeExpr specialInvokeExpr
                = soot.jimple.Jimple.v().newSpecialInvokeExpr(specialThisLocal,
                methodToInvoke, sootParams);

        soot.jimple.Stmt invokeStmt = soot.jimple.Jimple.v().newInvokeStmt(specialInvokeExpr);

        body.getUnits().add(invokeStmt);
        Util.addPsiTags(invokeStmt, call);

        int parami = prefixParamCount;
        for (PsiExpression expr : call.getArgumentList().getExpressions()) {
            Util.addPsiTags(specialInvokeExpr.getArgBox(parami), expr);
            parami++;
        }

        // if method is <init> handle field inits
        if (body.getMethod().getName().equals("<init>") && isSuperCall(call)){
            handleOuterClassThisInit(body.getMethod());
            handleFinalLocalInits();
            handleFieldInits(body.getMethod());
            handleInitializerBlocks(body.getMethod());
        }
    }

    protected void handleImpliedConstructorCall(PsiMethod constructor) {
        PsiClass cls = constructor.getContainingClass().getSuperClass();

        soot.SootClass classToInvoke = Util.getSootType(cls).getSootClass();

        List<Value> sootParams = new ArrayList<Value>();
        List<Type> sootParamsTypes = new ArrayList<Type>();
        handleOuterClassParams(sootParams, null, sootParamsTypes, cls);
        addUsedLocalParams(sootParams, sootParamsTypes, cls);

        soot.SootMethodRef methodToInvoke = getMethodFromClass(classToInvoke,
                "<init>", sootParamsTypes, soot.VoidType.v(), false);

        soot.jimple.SpecialInvokeExpr specialInvokeExpr
                = soot.jimple.Jimple.v().newSpecialInvokeExpr(specialThisLocal,
                methodToInvoke, sootParams);

        soot.jimple.Stmt invokeStmt = soot.jimple.Jimple.v().newInvokeStmt(
                specialInvokeExpr);

        body.getUnits().add(invokeStmt);
        Util.addPsiTags(invokeStmt, constructor);

        SootMethod sootMethod = body.getMethod();
        assert sootMethod.getName().equals("<init>");

        handleOuterClassThisInit(sootMethod);
        handleFinalLocalInits();
        handleFieldInits(sootMethod);
        handleInitializerBlocks(sootMethod);
    }

    private static boolean isSuperCall(PsiMethodCallExpression cCall) {
        return (cCall.getMethodExpression().getReferenceName().equals("super"));
    }

    private void handleFinalLocalInits(){
        PsiMethodSource source = getMethodSource();
        List<SootField> finalsList = source.getFinalsList();
        if (finalsList == null) return;
        int paramCount = paramRefCount -  finalsList.size();
        for (SootField sf : finalsList) {
            soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v()
                    .newInstanceFieldRef(specialThisLocal, sf.makeRef());
            soot.jimple.AssignStmt stmt = soot.jimple.Jimple.v()
                    .newAssignStmt(fieldRef,
                    body.getParameterLocal(paramCount));
            body.getUnits().add(stmt);
            paramCount++;
        }
    }

    private PsiMethodSource getMethodSource() {
        PsiMethodSource source = ((PsiMethodSource) body.getMethod()
                .getSource());
        return source;
    }

    protected soot.SootMethodRef getSootMethodRef(PsiMethodCallExpression call){
        soot.Type sootRecType = null;
        soot.SootClass receiverTypeClass;
        PsiMethod method = call.resolveMethod();
        PsiClass methodCls = method.getContainingClass();
        if (Util.getSootType(methodCls)
                .equals(soot.RefType.v("java.lang.Object"))){
            sootRecType = soot.RefType.v("java.lang.Object");
            receiverTypeClass = soot.Scene.v().getSootClass("java.lang.Object");
        }
        else {
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier != null) {
                PsiType qualifierType = qualifier.getType();
                if (qualifierType != null) {
                    sootRecType = Util.getSootType(qualifierType);
                }
            }
            if (sootRecType == null) {
                sootRecType = Util.getSootType(methodCls);
            }
            if (sootRecType instanceof soot.RefType){
                 receiverTypeClass = ((soot.RefType)sootRecType).getSootClass();
            }
            else if (sootRecType instanceof soot.ArrayType){
                receiverTypeClass = soot.Scene.v().getSootClass("java.lang.Object");
            }
            else {
                throw new IllegalStateException("call target problem: "+call
                        + " - sootRecType=" + sootRecType);
            }
        }

        soot.Type sootRetType = Util.getSootType(method.getReturnType());
        List<Type> sootParamsTypes = getSootParamsTypes(call);

        soot.SootMethodRef callMethod = soot.Scene.v().makeMethodRef(
                receiverTypeClass, method.getName(), sootParamsTypes,
                sootRetType, method.hasModifierProperty("static"));
        return callMethod;
    }


    protected soot.Value getBaseLocal(PsiExpression receiver) {

//        if (receiver instanceof polyglot.ast.TypeNode) {
//            return generateLocal(((polyglot.ast.TypeNode)receiver).type());
//        }
//        else {
            soot.Value val = base().createExpr(receiver);
            if (val instanceof soot.jimple.Constant) {
                soot.Local retLocal = lg.generateLocal(val.getType());
                soot.jimple.AssignStmt stmt = soot.jimple.Jimple.v().newAssignStmt(
                        retLocal, val);
                body.getUnits().add(stmt);
                return retLocal;
            }
            return val;
//        }
    }


    /**
     * create LHS expressions
     */
    protected soot.Value createLHS(PsiExpression expr) {
        if (expr instanceof PsiReferenceExpression) {
            PsiReferenceExpression rexpr = (PsiReferenceExpression) expr;
            PsiElement referenced = rexpr.resolve();
            if (referenced instanceof PsiField) {
                return getFieldLocalLeft(rexpr);
            } else {
                return getLocal(rexpr);
            }
        }
        else if (expr instanceof PsiArrayAccessExpression) {
            return getArrayRefLocalLeft((PsiArrayAccessExpression)expr);
        }
        else {
            throw new RuntimeException("Unhandled LHS: " + expr.getText());
        }
    }


    /**
     * Utility methods
     */
    /*private boolean isLitOrLocal(polyglot.ast.Expr exp) {
        if (exp instanceof polyglot.ast.Lit) return true;
        if (exp instanceof polyglot.ast.Local) return true;
        else return false;
    }*/

    protected soot.Local generateLocal(soot.Type sootType){
        return lg.generateLocal(sootType);
    }

    public InitialResolver getInitialResolver() {
        return initialResolver;
    }

    private static class SuperOrThisCallFinder extends PsiRecursiveElementVisitor {
        private boolean callsSuper = false;

        public void visitClass(PsiClass psiClass) {
            // don't descend into classes
        }

        public void visitMethodCallExpression(PsiMethodCallExpression psiMethodCallExpression) {
            super.visitMethodCallExpression(psiMethodCallExpression);

            String name = psiMethodCallExpression.getMethodExpression().getReferenceName();
            if (name.equals("super") || name.equals("this")) {
                callsSuper = true;
            }
        }

        public boolean isCallsSuper() {
            return callsSuper;
        }
    }
}
