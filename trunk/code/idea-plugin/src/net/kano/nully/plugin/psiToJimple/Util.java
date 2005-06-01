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
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import soot.Local;
import soot.RefType;
import soot.Type;
import soot.ValueBox;
import soot.tagkit.Host;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Util {
    //TODO: move some Util methods to initialresolver
    //TODO: class initializers don't work


    public static void addInnerClassTag(soot.SootClass sc, String innerName,
            String outerName, String simpleName, int access){
        // maybe need file sep here - may break windows

        innerName = soot.util.StringTools.replaceAll(innerName, ".", "/");
        if (outerName != null){
            outerName = soot.util.StringTools.replaceAll(outerName, ".", "/");
        }
        sc.addTag(new soot.tagkit.InnerClassTag(innerName, outerName,
                simpleName, access));

    }

    public static String getParamNameForClassLit(InitialResolver initialResolver,
            PsiType type){
        String name = "";
        if (type instanceof PsiArrayType){
            int dims = type.getArrayDimensions();
            PsiType arrType = ((PsiArrayType)type).getComponentType();
            while (arrType instanceof PsiArrayType) {
              arrType = ((PsiArrayType)arrType).getComponentType();
            }
            String fieldName = "";
            if (arrType.equals(PsiType.BOOLEAN)){
                fieldName = "Z";
            }
            else if (arrType.equals(PsiType.BYTE)){
                fieldName = "B";
            }
            else if (arrType.equals(PsiType.CHAR)){
                fieldName = "C";
            }
            else if (arrType.equals(PsiType.DOUBLE)){
                fieldName = "D";
            }
            else if (arrType.equals(PsiType.FLOAT)){
                fieldName = "F";
            }
            else if (arrType.equals(PsiType.INT)){
                fieldName = "I";
            }
            else if (arrType.equals(PsiType.LONG)){
                fieldName = "J";
            }
            else if (arrType.equals(PsiType.SHORT)){
                fieldName = "S";
            }
            else {
                String typeSt = getSootType(initialResolver, arrType).toString();
                fieldName = "L"+typeSt;
            }

            for (int i = 0; i < dims; i++){
                name += "[";
            }
            name += fieldName;
            if (!(arrType instanceof PsiPrimitiveType)){
                name += ";";
            }
        }
        else {
            name = getSootType(initialResolver, type).toString();
        }
        return name;
    }

    public static String getFieldNameForClassLit(InitialResolver initialResolver,
            PsiType type){
        String fieldName = "";
        if (type instanceof PsiArrayType){
            PsiArrayType atype = ((PsiArrayType) type);
            int dims = atype.getArrayDimensions();
            PsiType arrType = atype.getComponentType();
            while (arrType instanceof PsiArrayType) {
              arrType = ((PsiArrayType)arrType).getComponentType();
            }
            fieldName = "array$";
            for (int i = 0; i < (dims - 1); i++){
                fieldName += "$";
            }
            if (arrType.equals(PsiType.BOOLEAN)){
                fieldName += "Z";
            }
            else if (arrType.equals(PsiType.BYTE)){
                fieldName += "B";
            }
            else if (arrType.equals(PsiType.CHAR)){
                fieldName += "C";
            }
            else if (arrType.equals(PsiType.DOUBLE)){
                fieldName += "D";
            }
            else if (arrType.equals(PsiType.FLOAT)){
                fieldName += "F";
            }
            else if (arrType.equals(PsiType.INT)){
                fieldName += "I";
            }
            else if (arrType.equals(PsiType.LONG)){
                fieldName += "J";
            }
            else if (arrType.equals(PsiType.SHORT)){
                fieldName += "S";
            }
            else {
                String typeSt = getSootType(initialResolver, arrType).toString();
                typeSt = soot.util.StringTools.replaceAll(typeSt, ".", "$");

                fieldName = fieldName+"L"+typeSt;
            }
       }
       else {
            fieldName = "class$";
            String typeSt = getSootType(initialResolver, type).toString();
            typeSt = soot.util.StringTools.replaceAll(typeSt, ".", "$");
            fieldName = fieldName+typeSt;
       }

       return fieldName;
    }

    public static String getSourceFileOfClass(soot.SootClass sootClass){
        String name = sootClass.getName();
        int index = name.indexOf("$");

        // inner classes are found in the very outer class
        if (index != -1){
            name = name.substring(0, index);
        }
        return name;
    }


    public static soot.Local getThis(InitialResolver initialResolver,
            soot.Type sootType, soot.Body body,
            Map<soot.Type, soot.Local> getThisMap, LocalGenerator lg){

        if (initialResolver.hierarchy() == null){
            initialResolver.hierarchy(new soot.FastHierarchy());
        }

        soot.FastHierarchy fh = initialResolver.hierarchy();

        //System.out.println("getting this for type: "+sootType);
        // if this for type already created return it from map
        //if (getThisMap.containsKey(sootType)){
        //    return (soot.Local)getThisMap.get(sootType);
        //}
        soot.Local specialThisLocal = body.getThisLocal();
        // if need this just return it
        if (specialThisLocal.getType().equals(sootType)) {

            getThisMap.put(sootType, specialThisLocal);
            return specialThisLocal;
        }

        // check to see if this method has a local of the correct type (it will
        // if its an initializer - then ust use it)
        // here we need an exact type I think
        if (bodyHasLocal(initialResolver, body, sootType)){
            soot.Local l = getLocalOfType(initialResolver, body, sootType);
            getThisMap.put(sootType, l);
            return l;
        }

        // otherwise get this$0 for one level up
        soot.SootClass classToInvoke = ((soot.RefType)specialThisLocal.getType()).getSootClass();
        soot.SootField outerThisField = classToInvoke.getFieldByName("this$0");
        soot.Local t1 = lg.generateLocal(outerThisField.getType());

        soot.jimple.FieldRef fieldRef = soot.jimple.Jimple.v().newInstanceFieldRef(
                specialThisLocal, outerThisField.makeRef());
        soot.jimple.AssignStmt fieldAssignStmt = soot.jimple.Jimple.v().newAssignStmt(
                t1, fieldRef);
        body.getUnits().add(fieldAssignStmt);

        if (fh.canStoreType(t1.getType(), sootType)){
            getThisMap.put(sootType, t1);
            return t1;
        }

        // check to see if this method has a local of the correct type (it will
        // if its an initializer - then ust use it)
        // here we need an exact type I think
        /*if (bodyHasLocal(body, sootType)){
            soot.Local l = getLocalOfType(body, sootType);
            getThisMap.put(sootType, l);
            return l;
        }*/

        // otherwise make a new access method
        soot.Local t2 = t1;

        return getThisGivenOuter(initialResolver, sootType, getThisMap, body, lg, t2);
    }

    private static soot.Local getLocalOfType(InitialResolver initialResolver,
            soot.Body body, soot.Type type) {
        soot.FastHierarchy fh = initialResolver.hierarchy();
        Iterator stmtsIt = body.getUnits().iterator();
        soot.Local correctLocal = null;
        while (stmtsIt.hasNext()){
            soot.jimple.Stmt s = (soot.jimple.Stmt)stmtsIt.next();
            if (s instanceof soot.jimple.IdentityStmt
                    && (s.hasTag("EnclosingTag") || s.hasTag("QualifyingTag"))){
                for (ValueBox vb : ((Collection<soot.ValueBox>) s.getDefBoxes())) {
                    if ((vb.getValue() instanceof soot.Local)
                            && (fh.canStoreType(type,
                            vb.getValue().getType()))) {
                        //(vb.getValue().getType().equals(type))){
                        correctLocal = (soot.Local) vb.getValue();
                    }
                }
            }
        }
        return correctLocal;
    }

    private static boolean bodyHasLocal(InitialResolver initialResolver,
            soot.Body body, soot.Type type) {
        soot.FastHierarchy fh = initialResolver.hierarchy();
        Iterator stmtsIt = body.getUnits().iterator();
        soot.Local correctLocal = null;
        while (stmtsIt.hasNext()){
            soot.jimple.Stmt s = (soot.jimple.Stmt)stmtsIt.next();
            if (s instanceof soot.jimple.IdentityStmt
                    && (s.hasTag("EnclosingTag") || s.hasTag("QualifyingTag"))){
                for (ValueBox vb : ((Collection<ValueBox>) s.getDefBoxes())) {
                    if ((vb.getValue() instanceof soot.Local)
                            && (fh.canStoreType(type,
                            vb.getValue().getType()))) {
                        //(vb.getValue().getType().equals(type))){
                        return true;
                    }
                }
            }
        }
        return false;
        /*soot.FastHierarchy fh = InitialResolver.v().hierarchy();
       Iterator it = body.getDefBoxes().iterator();
       while (it.hasNext()){
           soot.ValueBox vb = (soot.ValueBox)it.next();
           if ((vb.getValue() instanceof soot.Local) && (fh.canStoreType(type, vb.getValue().getType()))){//(vb.getValue().getType().equals(type))){
               return true;
           }
       }
       return false;*/
    }


    public static soot.Local getThisGivenOuter(InitialResolver initialResolver,
            soot.Type sootType,
            Map<Type, Local> getThisMap, soot.Body body, LocalGenerator lg,
            soot.Local t2){

        if (initialResolver.hierarchy() == null){
            initialResolver.hierarchy(new soot.FastHierarchy());
        }

        soot.FastHierarchy fh = initialResolver.hierarchy();

        while (!fh.canStoreType(t2.getType(),sootType)){
            soot.SootClass classToInvoke = ((soot.RefType)t2.getType()).getSootClass();
            // make an access method and add it to that class for accessing 
            // its private this$0 field
            soot.SootMethod methToInvoke = makeOuterThisAccessMethod(classToInvoke);

            // generate a local that corresponds to the invoke of that meth
            soot.Local t3 = lg.generateLocal(methToInvoke.getReturnType());
            ArrayList<Local> methParams = new ArrayList<Local>();
            methParams.add(t2);
            soot.Local res = getPrivateAccessFieldInvoke(methToInvoke.makeRef(),
                    methParams, body, lg);
            soot.jimple.AssignStmt assign = soot.jimple.Jimple.v().newAssignStmt(t3, res);
            body.getUnits().add(assign);
            t2 = t3;
        }

        getThisMap.put(sootType, t2);

        return t2;
    }


    private static soot.SootMethod makeOuterThisAccessMethod(soot.SootClass classToInvoke){
        String name = "access$"+soot.javaToJimple.InitialResolver.v()
                .getNextPrivateAccessCounter()+"00";
        ArrayList<RefType> paramTypes = new ArrayList<RefType>();
        paramTypes.add(classToInvoke.getType());

        soot.SootMethod meth = new soot.SootMethod(name, paramTypes,
                classToInvoke.getFieldByName("this$0").getType(), soot.Modifier.STATIC);

        classToInvoke.addMethod(meth);
        PrivateFieldAccMethodSource src = new PrivateFieldAccMethodSource(
            classToInvoke.getFieldByName("this$0").getType(),
            "this$0",
            classToInvoke.getFieldByName("this$0").isStatic(),
            classToInvoke
            );
        meth.setActiveBody(src.getBody(meth, null));
        meth.addTag(new soot.tagkit.SyntheticTag());
        return meth;
    }

    public static soot.Local getPrivateAccessFieldInvoke(soot.SootMethodRef toInvoke,
            List params, soot.Body body, LocalGenerator lg){
        soot.jimple.InvokeExpr invoke = soot.jimple.Jimple.v().newStaticInvokeExpr(
                toInvoke, params);

        soot.Local retLocal = lg.generateLocal(toInvoke.returnType());

        soot.jimple.AssignStmt stmt = soot.jimple.Jimple.v().newAssignStmt(
                retLocal, invoke);
        body.getUnits().add(stmt);

        return retLocal;
    }

//    public static boolean isSubType(polyglot.types.ClassType type,
//            polyglot.types.ClassType superType){
//        if (type.equals(superType)) return true;
//        if (type.superType() == null) return false;
//        return isSubType((polyglot.types.ClassType)type.superType(), superType);
//    }

    /**
     * Type handling
     */
    public static soot.Type getSootType(InitialResolver resolver, PsiType type) {
        if (type == null) {
            throw new IllegalArgumentException("Trying to get soot type for null polyglot type");
        }
        soot.Type sootType = null;

        if (type.equals(PsiType.INT)) {
            sootType = soot.IntType.v();
        } else if (type instanceof PsiArrayType) {
            soot.Type baseType = getSootType(resolver, type.getDeepComponentType());
            int dims = type.getArrayDimensions();

            // do something here if baseType is still an array
            sootType = soot.ArrayType.v(baseType, dims);
        } else if (type.equals(PsiType.BOOLEAN)) {
            sootType = soot.BooleanType.v();
        } else if (type.equals(PsiType.BYTE)) {
            sootType = soot.ByteType.v();
        } else if (type.equals(PsiType.CHAR)) {
            sootType = soot.CharType.v();
        } else if (type.equals(PsiType.DOUBLE)) {
            sootType = soot.DoubleType.v();
        } else if (type.equals(PsiType.FLOAT)) {
            sootType = soot.FloatType.v();
        } else if (type.equals(PsiType.LONG)) {
            sootType = soot.LongType.v();
        } else if (type.equals(PsiType.SHORT)) {
            sootType = soot.ShortType.v();
        } else if (type.equals(PsiType.NULL)) {
            sootType = soot.NullType.v();
        } else if (type.equals(PsiType.VOID)) {
            sootType = soot.VoidType.v();
        } else if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass cls = ((PsiClassType) TypeConversionUtil.erasure(classType)).resolve();
            if (cls == null) {
                throw new IllegalArgumentException("Couldn't resolve class: " + type);
            }
            sootType = getSootType(resolver, cls);

        } else {
            throw new IllegalArgumentException("Unknown Type " + type);
        }
        return sootType;
    }

    public static RefType getSootType(InitialResolver resolver, PsiClass cls) {
        PsiClass outer = getOuterClass(cls);
        String className = null;
        String qname = getJavaClassName(resolver, cls);
        if (outer == null) {
            className = qname;

        } else {
            Map<IdentityKey<PsiAnonymousClass>,String> anonTypeMap = resolver.getAnonTypeMap();
            Map<IdentityKey<PsiClass>,String> localTypeMap = resolver.getLocalTypeMap();
            if (PsiUtil.isLocalOrAnonymousClass(cls)) {
                IdentityKey<PsiClass> key = new IdentityKey<PsiClass>(cls);
                if (PsiUtil.isLocalClass(cls)) {
                    if ((localTypeMap != null) && localTypeMap
                            .containsKey(key)) {
                        className = localTypeMap.get(key);
                    }
                } else {
                    assert cls instanceof PsiAnonymousClass;
                    if (anonTypeMap != null && anonTypeMap
                            .containsKey(key)) {
                        className = anonTypeMap.get(key);
                    }
                }
            }
            if (className == null) {
                className = qname;
            }
        }

        return RefType.v(className);
    }

    /**
     * Modifier Creation
     */
    public static int getModifier(PsiModifierListOwner owner) {

        int modifier = 0;

        if (owner.hasModifierProperty("public")){
            modifier = modifier | soot.Modifier.PUBLIC;
        }
        if (owner.hasModifierProperty("private")){
            modifier = modifier | soot.Modifier.PRIVATE;
        }
        if (owner.hasModifierProperty("protected")){
            modifier = modifier | soot.Modifier.PROTECTED;
        }
        if (owner.hasModifierProperty("final")){
            modifier = modifier | soot.Modifier.FINAL;
        }
        if (owner.hasModifierProperty("static")){
            modifier = modifier | soot.Modifier.STATIC;
        }
        if (owner.hasModifierProperty("native")){
            modifier = modifier | soot.Modifier.NATIVE;
        }
        if (owner.hasModifierProperty("abstract")){
            modifier = modifier | soot.Modifier.ABSTRACT;
        }
        if (owner.hasModifierProperty("volatile")){
            modifier = modifier | soot.Modifier.VOLATILE;
        }
        if (owner.hasModifierProperty("transient")){
            modifier = modifier | soot.Modifier.TRANSIENT;
        }
        if (owner.hasModifierProperty("synchronized")){
            modifier = modifier | soot.Modifier.SYNCHRONIZED;
        }
        if (owner instanceof PsiClass && ((PsiClass) owner).isInterface()){
            modifier = modifier | soot.Modifier.INTERFACE;
        }
        if (owner.hasModifierProperty("strictfp")) {
            modifier = modifier | soot.Modifier.STRICTFP;
        }
        return modifier;
    }

    public static boolean isLocalReference(PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression)) return false;
        PsiReferenceExpression ref = (PsiReferenceExpression) expression;
        PsiElement referenced = ref.resolve();
        if (referenced == null) return false;
        return referenced instanceof PsiLocalVariable
                || referenced instanceof PsiParameter;
    }

    public static boolean isFieldReference(PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression)) return false;
        PsiReferenceExpression ref = (PsiReferenceExpression) expression;
        PsiElement referenced = ref.resolve();
        if (referenced == null) return false;
        return referenced instanceof PsiField;

    }

    public static Object evaluateConstantValue(PsiExpression node) {
        PsiConstantEvaluationHelper constHelper = node.getManager()
                .getConstantEvaluationHelper();
        return constHelper.computeConstantExpression(node);
    }

    public static PsiJavaToken getUnaryOperationSign(PsiExpression unary) {
        PsiJavaToken op;
        if (unary instanceof PsiPrefixExpression) {
            PsiPrefixExpression prefixExpression = (PsiPrefixExpression) unary;
            op = prefixExpression.getOperationSign();
        } else {
            PsiPostfixExpression postfix = (PsiPostfixExpression) unary;
            op = postfix.getOperationSign();
        }
        return op;
    }

    public static PsiExpression getUnaryOperand(PsiExpression unary) {
        PsiExpression expr;
        if (unary instanceof PsiPrefixExpression) {
            PsiPrefixExpression prefixExpression = (PsiPrefixExpression) unary;
            expr = prefixExpression.getOperand();
        } else {
            PsiPostfixExpression postfix = (PsiPostfixExpression) unary;
            expr = postfix.getOperand();
        }
        return expr;
    }

    public static void addPsiTags(Host host, final PsiElement element) {
        host.addTag(new PsiTag(element));
    }

    public static String getJavaClassName(InitialResolver resolver,
            PsiClass cls) {
        if (PsiUtil.isLocalClass(cls)) {
            String name = getLocalClassName(resolver, cls);
            if (name == null) {
                throw new IllegalArgumentException("No Soot name for local class: "
                        + cls.getName());
            }
            return name;
        }
        if (cls instanceof PsiAnonymousClass) {
            PsiAnonymousClass acls = (PsiAnonymousClass) cls;

            String name = getAnonymousClassName(resolver, acls);
            if (name == null) {
                throw new IllegalArgumentException("No Soot name for anonymous subclass of "
                        + acls.getBaseClassType().resolve().getQualifiedName());
            }
            return name;
        }
        PsiClass outer = getOuterClass(cls);
        if (outer == null) {
            return cls.getQualifiedName();
        } else {
            return getJavaClassName(resolver, outer) + "$" + cls.getName();
        }
    }

    public static String getAnonymousClassName(InitialResolver resolver,
            PsiAnonymousClass acls) {
        return resolver.getAnonClassMap().getVal(acls);
    }

    public static String getLocalClassName(InitialResolver resolver,
            PsiClass cls) {
        if (!PsiUtil.isLocalClass(cls)) {
            throw new IllegalArgumentException("class " + cls.getName() + " is not a local class");
        }
        return resolver.getLocalClassMap().getVal(cls);
    }

    public static PsiClass getOuterClass(PsiClass cls) {
        PsiClass outer = cls.getContainingClass();
        if (outer == null) {
            outer = PsiTreeUtil.getParentOfType(cls, PsiClass.class);
        }
        return outer;
    }
}
