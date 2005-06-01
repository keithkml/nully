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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiUtil;
import soot.RefType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Modifier;
import soot.tagkit.Host;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassResolver {
    private final InitialResolver initialResolver;

    private List<PsiField> staticFieldInits;
    private List<PsiField> fieldInits;
    private List<PsiCodeBlock> initializerBlocks;
    private List<PsiCodeBlock> staticInitializerBlocks;

    private final SootClass sootClass;
    private final List<Type> references = new ArrayList<Type>();

    ClassResolver(InitialResolver initialResolver, SootClass sootClass) {
        this.initialResolver = initialResolver;
        this.sootClass = sootClass;
    }

    /**
     *  adds source file tag to each sootclass
     */
    protected void addSourceFileTag(soot.SootClass sc){
        soot.tagkit.SourceFileTag tag;
        if (sc.hasTag("SourceFileTag")) {
            tag = (soot.tagkit.SourceFileTag)sc.getTag("SourceFileTag");
        }
        else {
            tag = new soot.tagkit.SourceFileTag();
            sc.addTag(tag);
        }
        
        String name = Util.getSourceFileOfClass(sc);


        if (initialResolver.classToSourceMap() != null){
            if (initialResolver.classToSourceMap().containsKey(name)){
                name = initialResolver.classToSourceMap().get(name);
            }
        }

        // the pkg is not included in the tag for some unknown reason
        // I think in this case windows uses the same slash - may cause 
        // windows problems though
        int slashIndex = name.lastIndexOf("/");
        if (slashIndex != -1){
            name = name.substring(slashIndex+1);
        }
        tag.setSourceFile(name); 
        //sc.addTag(new soot.tagkit.SourceFileTag(name));
    }
    
    /**
     * Class Declaration Creation
     */
    private void createClassDecl(PsiClass cDecl){
        assert Util.getSootType(initialResolver, cDecl).getSootClass().equals(sootClass);
    
        //add outer class tag if neccessary (if class is not top-level)
        PsiClass outer = Util.getOuterClass(cDecl);
        if (outer != null){
            SootClass outerClass = Util.getSootType(initialResolver, outer).getSootClass();
            
            if (initialResolver.getInnerClassInfoMap() == null){
                initialResolver.setInnerClassInfoMap(new HashMap<SootClass, InnerClassInfo>());
            }
            initialResolver.getInnerClassInfoMap().put(sootClass,
                    new InnerClassInfo(outerClass, cDecl.getName(), InnerClassInfo.NESTED));
            sootClass.setOuterClass(outerClass);
        }
    
        // modifiers
        //TODO: check effective modifiers on class
//        addModifiers(flags, cDecl);
        
        // super class
        PsiClass psiSuper = cDecl.getSuperClass();
        if (psiSuper == null) {
            soot.SootClass superClass = soot.Scene.v().getSootClass ("java.lang.Object"); 
            sootClass.setSuperclass(superClass);
        }
        else {
            sootClass.setSuperclass(Util.getSootType(initialResolver, psiSuper).getSootClass());
            PsiClass superOuter = Util.getOuterClass(psiSuper);
            if (superOuter != null){
                Util.addInnerClassTag(sootClass, sootClass.getName(),
                        Util.getSootType(initialResolver, superOuter).toString(),
                        psiSuper.getName(), Util.getModifier(psiSuper));
            }
        
        }
    
    
        // implements
        for (PsiClassType type : cDecl.getImplementsListTypes()) {
            sootClass.addInterface(((soot.RefType)Util.getSootType(initialResolver,
                    type)).getSootClass());
        }
        
        findReferences(cDecl);
        createClassBody(cDecl);
    
        // handle initialization of fields 
        // static fields init in clinit
        // other fields init in init
        handleFieldInits();
        
        if (staticFieldInits != null || staticInitializerBlocks != null) {
            soot.SootMethod clinitMethod;
            if (!sootClass.declaresMethod("<clinit>", new ArrayList(), soot.VoidType.v())) {
                clinitMethod = new soot.SootMethod("<clinit>", new ArrayList(),
                        soot.VoidType.v(), soot.Modifier.STATIC, new ArrayList());
                
                sootClass.addMethod(clinitMethod);
                PsiMethodSource mSource = new PsiMethodSource(initialResolver);
                mSource.setJBB(initialResolver.getJBBFactory().createJimpleBodyBuilder());
                clinitMethod.setSource(mSource);
            }
            else {
                clinitMethod = sootClass.getMethod("<clinit>", new ArrayList(), soot.VoidType.v());
            
            }
            PsiMethodSource methSrc = ((PsiMethodSource) clinitMethod.getSource());
            methSrc.setStaticFieldInits(staticFieldInits);
            methSrc.setStaticInitializerBlocks(staticInitializerBlocks);
    
        }
    
    
        // add final locals to local inner classes inits
        if (PsiUtil.isLocalClass(cDecl)) {
            AnonLocalClassInfo info = initialResolver.finalLocalInfo().get(
                    new IdentityKey<PsiClass>(cDecl));
                List<SootField> finalsList = addFinalLocals(cDecl,
                        info.finalLocalsAvail(), info);
            for (SootMethod meth : ((Collection<SootMethod>) sootClass.getMethods())) {
                if (meth.getName().equals("<init>")) {
                    ((PsiMethodSource) meth.getSource()).setFinalsList(finalsList);
                }
            }
            if (!info.inStaticMethod()){
                PsiClass outerType = Util.getOuterClass(cDecl);
                addOuterClassThisRefToInit(outerType);
                addOuterClassThisRefField(outerType);
            }
        } else if (outer != null && !cDecl.hasModifierProperty("static")) {
            // add outer class ref to constructors of inner classes
            // and out class field ref (only for non-static inner classes
            addOuterClassThisRefToInit(outer);
            addOuterClassThisRefField(outer);
        }
        
        addPsiTags(sootClass, cDecl);
    }

    private static void addPsiTags(Host host, PsiElement el) {
        Util.addPsiTags(host, el);
    }

    private void findReferences(PsiClass node) {
        TypeListBuilder typeListBuilder = new TypeListBuilder();
        
        node.acceptChildren(typeListBuilder);

        for (PsiClassType type : typeListBuilder.getList()) {
            references.add(Util.getSootType(initialResolver, type));
        }
    }

    /**
     * Class Body Creation
     */
    private void createClassBody(PsiClass classBody){
        // reinit static lists
        staticFieldInits = null;
        fieldInits = null;
        initializerBlocks = null;
        staticInitializerBlocks = null;
    
        
        // handle members
        for (PsiMethod method : classBody.getMethods()) {
            if (!method.isConstructor()) createMethodDecl(method);
        }
        for (PsiField field : classBody.getFields()) {
            createFieldDecl(field);
        }
        for (PsiMethod constructor : classBody.getConstructors()) {
            createConstructorDecl(constructor);
        }
        for (PsiClass cls : classBody.getInnerClasses()) {
            Util.addInnerClassTag(sootClass, Util.getSootType(initialResolver,
                    cls).toString(),
                    sootClass.getName(), cls.getName(),
                    Util.getModifier(cls));
        }
        for (PsiClassInitializer initializer : classBody.getInitializers()) {
            createInitializer(initializer);
        }
        handleInnerClassTags(classBody);
        handleClassLiterals(classBody);
        handleAssert(classBody);
    }

    private void addOuterClassThisRefField(PsiClass outerType){
        soot.Type outerSootType = Util.getSootType(initialResolver, outerType);
        soot.SootField field = new soot.SootField("this$0", outerSootType,
                soot.Modifier.PRIVATE | soot.Modifier.FINAL);
        sootClass.addField(field);
        field.addTag(new soot.tagkit.SyntheticTag());
    }

    private void addOuterClassThisRefToInit(PsiClass outerType){
        soot.Type outerSootType = Util.getSootType(initialResolver, outerType);
        for (SootMethod meth : ((Collection<SootMethod>) sootClass.getMethods())) {
            if (meth.getName().equals("<init>")) {
                List<Type> newParams = new ArrayList<Type>();
                newParams.add(outerSootType);
                newParams.addAll(meth.getParameterTypes());
                meth.setParameterTypes(newParams);
                meth.addTag(new soot.tagkit.EnclosingTag());
                if (initialResolver.getHasOuterRefInInit() == null) {
                    initialResolver
                            .setHasOuterRefInInit(new ArrayList<RefType>());
                }
                initialResolver.getHasOuterRefInInit()
                        .add(meth.getDeclaringClass().getType());
            }
        }
    }
    private void addFinals(PsiVariable li, List<SootField> finalFields){
        // add as param for init
        for (SootMethod meth : ((Collection<SootMethod>) sootClass.getMethods())) {
            if (meth.getName().equals("<init>")) {
                List<Type> newParams = new ArrayList<Type>();
                newParams.addAll(meth.getParameterTypes());
                newParams.add(Util.getSootType(initialResolver, li.getType()));
                meth.setParameterTypes(newParams);
            }
        }
                
        // add field
        soot.SootField sf = new soot.SootField("val$"+li.getName(),
                Util.getSootType(initialResolver, li.getType()),
                soot.Modifier.FINAL | soot.Modifier.PRIVATE);
        sootClass.addField(sf);
        finalFields.add(sf);
        sf.addTag(new soot.tagkit.SyntheticTag());       
    }
    private List<SootField> addFinalLocals(PsiClass cBody,
            List<IdentityKey<PsiVariable>> finalLocalsAvail,
            AnonLocalClassInfo info){
        List<SootField> finalFields = new ArrayList<SootField>();
        
        LocalUsesChecker luc = new LocalUsesChecker();
        cBody.accept(luc);
        /*Iterator localsNeededIt = luc.getLocals().iterator();*/
        List<IdentityKey<PsiVariable>> localsUsed = new ArrayList<IdentityKey<PsiVariable>>();

        for (IdentityKey<PsiVariable> aFinalLocalsAvail : finalLocalsAvail) {
            PsiVariable li = (aFinalLocalsAvail).object();
            IdentityKey<PsiVariable> key = new IdentityKey<PsiVariable>(li);
            if (!luc.getLocalDecls().contains(key)) {
                localsUsed.add(key);
                addFinals(li, finalFields);
            }
        }

        // this part is broken it adds all final locals available for the new
        // not just the ones used (which is a problem)
        Map<IdentityKey<PsiClass>,AnonLocalClassInfo> finalLocalInfo = initialResolver
                .finalLocalInfo();
        for (PsiNewExpression tempNew : luc.getNews()) {
            PsiClass tempNewType = ((PsiClassType) tempNew.getType()).resolve();
            IdentityKey<PsiClass> tempNewTypeKey = new IdentityKey<PsiClass>(tempNewType);
            AnonLocalClassInfo lInfo = finalLocalInfo.get(tempNewTypeKey);
            if (lInfo == null) continue;

            for (IdentityKey<PsiVariable> identityKey : lInfo.finalLocalsAvail()) {
                PsiVariable li2 = identityKey.object();
                if (!sootClass.declaresField("val$" + li2.getName(),
                        Util.getSootType(initialResolver, li2.getType()))) {
                    IdentityKey<PsiVariable> li2key = new IdentityKey<PsiVariable>(li2);
                    if (!luc.getLocalDecls().contains(li2key)) {
                        addFinals(li2, finalFields);
                        localsUsed.add(li2key);
                    }
                }
            }
        }
        // also need to add them if any super class all the way up needs one
        // because the super() will be made in init and it will require
        // possibly eventually to send in the finals

        PsiClass superType =  cBody.getSuperClass();
        while (!Util.getSootType(initialResolver, superType).equals(soot.Scene.v()
                .getSootClass("java.lang.Object").getType())) {
            IdentityKey<PsiClass> superKey = new IdentityKey<PsiClass>(superType);
            if (finalLocalInfo.containsKey(superKey)) {
                AnonLocalClassInfo lInfo = finalLocalInfo.get(superKey);
                for (IdentityKey<PsiVariable> identityKey : lInfo.finalLocalsAvail()) {
                    PsiVariable li2 =  identityKey.object();
                    if (!sootClass.declaresField("val$" + li2.getName(),
                            Util.getSootType(initialResolver, li2.getType()))) {
                        IdentityKey<PsiVariable> li2key = new IdentityKey<PsiVariable>(li2);
                        if (!luc.getLocalDecls().contains(li2key)) {
                            addFinals(li2, finalFields);
                            localsUsed.add(li2key);
                        }
                    }
                }
            }
            superType = superType.getSuperClass();
        }
        info.finalLocalsUsed(localsUsed);
        finalLocalInfo.put(new IdentityKey<PsiClass>(cBody), info);
        return finalFields;
    }
//    private void createAnonClassDecl(polyglot.ast.New aNew) {
//
//        SootClass outerClass = ((soot.RefType)Util.getSootType(aNew.anonType().outer())).getSootClass();
//        if (initialResolver.getInnerClassInfoMap() == null){
//            initialResolver.setInnerClassInfoMap(new HashMap<SootClass, InnerClassInfo>());
//        }
//        initialResolver.getInnerClassInfoMap().put(sootClass, new InnerClassInfo(outerClass, "0", InnerClassInfo.ANON));
//        sootClass.setOuterClass(outerClass);
//
//        soot.SootClass typeClass = ((soot.RefType)Util.getSootType(aNew.objectType().type())).getSootClass();
//
//        // set superclass
//        if (((polyglot.types.ClassType)aNew.objectType().type()).flags().isInterface()){
//            sootClass.addInterface(typeClass);
//            sootClass.setSuperclass(soot.Scene.v().getSootClass("java.lang.Object"));
//        }
//        else {
//            sootClass.setSuperclass(typeClass);
//            if (((polyglot.types.ClassType)aNew.objectType().type()).isNested()){
//                polyglot.types.ClassType superType = (polyglot.types.ClassType)aNew.objectType().type();
//                // add inner clas tag
//                Util.addInnerClassTag(sootClass, typeClass.getName(), ((soot.RefType)Util.getSootType(superType.outer())).toString(), superType.name(), Util.getModifier(superType.flags()));
//
//            }
//        }
//
//        // needs to be done for local also
//        ArrayList<Type> params = new ArrayList<Type>();
//
//        soot.SootMethod method;
//        // if interface there are no extra params
//        if (((polyglot.types.ClassType)aNew.objectType().type()).flags().isInterface()){
//            method = new soot.SootMethod("<init>", params, soot.VoidType.v());
//        }
//        else {
//            Iterator aIt = aNew.arguments().iterator();
//            while (aIt.hasNext()){
//                polyglot.types.Type pType = ((polyglot.ast.Expr)aIt.next()).type();
//                params.add(Util.getSootType(pType));
//            }
//            method = new soot.SootMethod("<init>", params, soot.VoidType.v());
//        }
//
//        AnonClassInitMethodSource src = new AnonClassInitMethodSource();
//        method.setSource(src);
//        sootClass.addMethod(method);
//
//        AnonLocalClassInfo info = initialResolver.finalLocalInfo().get(new IdentityKey<ParsedClassType>(aNew.anonType()));
//
//        if (aNew.qualifier() != null) {
//            // add qualifier ref - do this first to get right order
//            addQualifierRefToInit(aNew.qualifier().type());
//            src.hasQualifier(true);
//        }
//        if (info != null && !info.inStaticMethod()){
//            if (!initialResolver.isAnonInCCall(aNew.anonType())){
//                addOuterClassThisRefToInit(aNew.anonType().outer());
//                addOuterClassThisRefField(aNew.anonType().outer());
//                src.thisOuterType(Util.getSootType(aNew.anonType().outer()));
//                src.hasOuterRef(true);
//            }
//        }
//        src.polyglotType((polyglot.types.ClassType)aNew.anonType().superType());
//        src.anonType((polyglot.types.ClassType)aNew.anonType());
//        src.inStaticMethod(info.inStaticMethod());
//        if (info != null){
//            src.setFinalsList(addFinalLocals(aNew.body(), info.finalLocalsAvail(), info));
//        }
//        src.outerClassType(Util.getSootType(aNew.anonType().outer()));
//        if (((polyglot.types.ClassType)aNew.objectType().type()).isNested()){
//            src.superOuterType(Util.getSootType(((polyglot.types.ClassType)aNew.objectType().type()).outer()));
//            src.isSubType(Util.isSubType(aNew.anonType().outer(), ((polyglot.types.ClassType)aNew.objectType().type()).outer()));
//        }
//
//        Util.addLnPosTags(sootClass, aNew.position().line(), aNew.body().position().endLine(), aNew.position().column(), aNew.body().position().endColumn());
//    }

    /**
     * adds modifiers
     */
//    private void addModifiers(polyglot.types.Flags flags, polyglot.ast.ClassDecl cDecl){
//        int modifiers = 0;
//        if (cDecl.type().isNested()){
//            if (flags.isPublic() || flags.isProtected() || flags.isPrivate()){
//                modifiers = soot.Modifier.PUBLIC;
//            }
//            if (flags.isInterface()){
//                modifiers = modifiers | soot.Modifier.INTERFACE;
//            }
//            if (flags.isAbstract()){
//                modifiers = modifiers | soot.Modifier.ABSTRACT;
//            }
//            // if inner classes are declared in an interface they need to be
//            // given public access but I have no idea why
//            // if inner classes are declared in an interface the are
//            // implicitly static and public (jls9.5)
//            if (cDecl.type().outer().flags().isInterface()){
//                modifiers = modifiers | soot.Modifier.PUBLIC;
//            }
//        }
//        else {
//            modifiers = Util.getModifier(flags);
//        }
//        sootClass.setModifiers(modifiers);
//    }

    private soot.SootClass getSpecialInterfaceAnonClass(soot.SootClass addToClass){
        // check to see if there is already a special anon class for this
        // interface
        if ((initialResolver.specialAnonMap() != null) && (initialResolver.specialAnonMap().containsKey(addToClass))){
            return initialResolver.specialAnonMap().get(addToClass);
        }
        else {
            String specialClassName = addToClass.getName()+"$"+initialResolver.getNextAnonNum();
            // add class to scene and other maps and lists as needed
            soot.SootClass specialClass = new soot.SootClass(specialClassName);
            soot.Scene.v().addClass(specialClass);
            specialClass.setApplicationClass();
            specialClass.addTag(new soot.tagkit.SyntheticTag());
            specialClass.setSuperclass(soot.Scene.v().getSootClass("java.lang.Object"));
            Util.addInnerClassTag(addToClass, specialClass.getName(), addToClass.getName(), null, soot.Modifier.STATIC);
            Util.addInnerClassTag(specialClass, specialClass.getName(), addToClass.getName(), null, soot.Modifier.STATIC);
            initialResolver.addNameToAST(specialClassName);
            references.add(RefType.v(specialClassName));
            if (initialResolver.specialAnonMap() == null){
                initialResolver.setSpecialAnonMap(new HashMap<SootClass, SootClass>());
            }
            initialResolver.specialAnonMap().put(addToClass, specialClass);
            return specialClass;
        }
    }
        
    /**
     * Handling for assert stmts - extra fields and methods are needed
     * in the Jimple 
     */
    private void handleAssert(PsiClass cBody){
        
        // find any asserts in class body but not in inner class bodies
        AssertStmtChecker asc = new AssertStmtChecker();
        cBody.acceptChildren(asc);
        if (!asc.isHasAssert()) return;
        
        // two extra fields

        // $assertionsDisabled field is added to the actual class where the
        // assert is found (even if its an inner class - interfaces cannot
        // have asserts stmts directly contained within them)
        String fieldName = "$assertionsDisabled";
        soot.Type fieldType = soot.BooleanType.v();
        if (!sootClass.declaresField(fieldName, fieldType)){
            soot.SootField assertionsDisabledField = new soot.SootField(fieldName,
                    fieldType, soot.Modifier.STATIC | soot.Modifier.FINAL);
            sootClass.addField(assertionsDisabledField);
            assertionsDisabledField.addTag(new soot.tagkit.SyntheticTag());
        }

        // class$ field is added to the outer most class if sootClass 
        // containing the assert is inner - if the outer most class is 
        // an interface - add instead to special interface anon class
        soot.SootClass addToClass = sootClass;
        Map<SootClass, InnerClassInfo> innerClassInfoMap = initialResolver.getInnerClassInfoMap();
        while ((innerClassInfoMap != null)
                && (innerClassInfoMap.containsKey(addToClass))){
            addToClass = innerClassInfoMap.get(addToClass).getOuterClass();
        }
        
        // this field is named after the outer class even if the outer
        // class is an interface and will be actually added to the
        // special interface anon class
        fieldName = "class$"+soot.util.StringTools.replaceAll(addToClass.getName(), ".", "$");
        List<String> interfacesList = initialResolver.getInterfacesList();
        if ((interfacesList != null) && (interfacesList.contains(addToClass.getName()))) {
            addToClass = getSpecialInterfaceAnonClass(addToClass);
        }
        
        fieldType = soot.RefType.v("java.lang.Class"); 
        
        if (!addToClass.declaresField(fieldName, fieldType)){
            soot.SootField classField =new soot.SootField(fieldName, fieldType,
                    soot.Modifier.STATIC);
            addToClass.addField(classField);
            classField.addTag(new soot.tagkit.SyntheticTag());
        }
        
        // two extra methods
        
        // class$ method is added to the outer most class if sootClass 
        // containing the assert is inner - if the outer most class is 
        // an interface - add instead to special interface anon class
        String methodName = "class$";
        soot.Type methodRetType = soot.RefType.v("java.lang.Class");
        ArrayList<RefType> paramTypes = new ArrayList<RefType>();
        paramTypes.add(soot.RefType.v("java.lang.String"));
        
        // make meth
        soot.SootMethod sootMethod = new soot.SootMethod(methodName, paramTypes,
                methodRetType, soot.Modifier.STATIC);
        AssertClassMethodSource assertMSrc = new AssertClassMethodSource();
        sootMethod.setSource(assertMSrc);
        
        if (!addToClass.declaresMethod(methodName, paramTypes, methodRetType)){
            addToClass.addMethod(sootMethod);
            sootMethod.addTag(new soot.tagkit.SyntheticTag());
        }

        // clinit method is added to actual class where assert is found
        // if the class already has a clinit method its method source is
        // informed of an assert
        methodName = "<clinit>";
        methodRetType = soot.VoidType.v();
        paramTypes = new ArrayList<RefType>();
        
        // make meth
        sootMethod = new soot.SootMethod(methodName, paramTypes, methodRetType, soot.Modifier.STATIC);
        PsiMethodSource mSrc = new PsiMethodSource(initialResolver);
        mSrc.setJBB(initialResolver.getJBBFactory().createJimpleBodyBuilder());
        mSrc.hasAssert(true);
        sootMethod.setSource(mSrc);
        
        if (!sootClass.declaresMethod(methodName, paramTypes, methodRetType)){
            sootClass.addMethod(sootMethod);
        }
        else {
            SootMethod method = sootClass.getMethod(methodName, paramTypes, methodRetType);
            ((PsiMethodSource)method.getSource()).hasAssert(true);
        }
    }
    /**
     * Constructor Declaration Creation
     */
    private void createConstructorDecl(PsiMethod constructor){
        String name = "<init>";
    
        List<Type> parameters = createParameters(constructor);
    
        List<SootClass> exceptions = createExceptions(constructor);
    
        soot.SootMethod sootMethod = createSootConstructor(name, constructor, parameters, exceptions);
    
        finishProcedure(constructor, sootMethod);
    }
    /**
     * Method Declaration Creation
     */
    private void createMethodDecl(PsiMethod method) {
    
        String name = createName(method);
            
        // parameters
        List<Type> parameters = createParameters(method);
                  
        // exceptions
        List<SootClass> exceptions = createExceptions(method);
    
        soot.SootMethod sootMethod = createSootMethod(name, method, parameters, exceptions);
    
        finishProcedure(method, sootMethod);
    }
    /**
     * looks after pos tags for methods and constructors
     */
    private void finishProcedure(PsiMethod procedure, soot.SootMethod sootMethod){
        
        addProcedureToClass(sootMethod);

        PsiCodeBlock body = procedure.getBody();
        if (body != null) {
            addPsiTags(sootMethod, body);
        } else {
            addPsiTags(sootMethod, procedure);
        }

    
        PsiMethodSource mSrc = new PsiMethodSource(initialResolver, procedure);
        mSrc.setJBB(initialResolver.getJBBFactory().createJimpleBodyBuilder());
        
        sootMethod.setSource(mSrc);
        
    }

    private void handleFieldInits(){
        if ((fieldInits != null) || (initializerBlocks != null)) {
            for (SootMethod next : ((Collection<SootMethod>) sootClass.getMethods())) {
                if (next.getName().equals("<init>")) {

                    PsiMethodSource src = (PsiMethodSource) next.getSource();
                    src.setInitializerBlocks(initializerBlocks);
                    src.setFieldInits(fieldInits);
                }
            }
        }
        
    }
    private void handleClassLiterals(PsiClass cBody){
    
        // check for class lits whose type is not primitive
        ClassLiteralChecker classLitChecker = new ClassLiteralChecker();
        cBody.acceptChildren(classLitChecker);
        List<PsiType> classLitList = classLitChecker.getList();
        
        if (!classLitList.isEmpty()){

            soot.SootClass addToClass = sootClass;
            if (addToClass.isInterface()) {
                addToClass = getSpecialInterfaceAnonClass(addToClass);
            }
            
            // add class$ meth
            String methodName = "class$";
            soot.Type methodRetType = soot.RefType.v("java.lang.Class");
            ArrayList<RefType> paramTypes = new ArrayList<RefType>();
            paramTypes.add(soot.RefType.v("java.lang.String"));
            soot.SootMethod sootMethod = new soot.SootMethod(methodName,
                    paramTypes, methodRetType, soot.Modifier.STATIC);
            ClassLiteralMethodSource mSrc = new ClassLiteralMethodSource();
            sootMethod.setSource(mSrc);
            
            if (!addToClass.declaresMethod(methodName, paramTypes, methodRetType)){
                addToClass.addMethod(sootMethod);
                sootMethod.addTag(new soot.tagkit.SyntheticTag());
            }
            

            // add fields for all non prim class lits
            for (PsiType type : classLitList) {
                // field
                String fieldName = Util.getFieldNameForClassLit(initialResolver,
                        type);
                soot.Type fieldType = soot.RefType.v("java.lang.Class");

                soot.SootField sootField = new soot.SootField(fieldName,
                        fieldType, soot.Modifier.STATIC);
                if (!addToClass.declaresField(fieldName, fieldType)) {
                    addToClass.addField(sootField);
                    sootField.addTag(new soot.tagkit.SyntheticTag());
                }
            }
        } 
    }
    /**
     * returns the name of the class without the package part
     */
//    private String getSimpleClassName(){
//        String name = sootClass.getName();
//        if (sootClass.getPackageName() != null){
//            name = name.substring(name.lastIndexOf(".")+1, name.length());
//        }
//        return name;
//    }
    /**
     * Source Creation 
     */
    protected void createSource(PsiJavaFile source){
        PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
            public void visitClass(PsiClass psiClass) {
                super.visitClass(psiClass);

                if (Util.getSootType(initialResolver, psiClass).getSootClass().equals(sootClass)) {
                    createClassDecl(psiClass);
                }
            }
        };
        source.accept(visitor);

//        for (PsiClass cls : source.getClasses()) {
//            createClassDecl(cls);
//        }
    }

    private void handleInnerClassTags(PsiClass classBody){
        // if this class is an inner class add self
        Map<SootClass, InnerClassInfo> innerClassInfoMap = initialResolver.getInnerClassInfoMap();
        if (innerClassInfoMap != null
                && innerClassInfoMap.containsKey(sootClass)) {
            //hasTag("OuterClassTag")){

            InnerClassInfo tag = innerClassInfoMap.get(sootClass);
            String outerName ;
            String simpleName;
            if (tag.getInnerType() == InnerClassInfo.ANON) {
                outerName = null;
                simpleName = null;
            } else {
                outerName = tag.getOuterClass().getName();
                simpleName = tag.getSimpleName();
            }
            int access ;
            if (soot.Modifier.isInterface(tag.getOuterClass().getModifiers())) {
                access = soot.Modifier.STATIC | soot.Modifier.PUBLIC;
            } else {
                access = sootClass.getModifiers();
            }
            Util.addInnerClassTag(sootClass, sootClass.getName(),
                    outerName, simpleName, access);
            // if this class is an inner class and enclosing class is also
            // an inner class add enclsing class
/*
            SootClass outerClass = tag.getOuterClass();
            while (innerClassInfoMap
                    .containsKey(outerClass)) {
                InnerClassInfo tag2 = innerClassInfoMap.get(outerClass);
                Util.addInnerClassTag(sootClass, outerClass.getName(),
                        tag2.getInnerType() == InnerClassInfo.ANON ? null
                                : tag2.getOuterClass().getName(),
                        tag2.getInnerType() == InnerClassInfo.ANON ? null
                                : tag2.getSimpleName(),
                        tag2.getInnerType() == InnerClassInfo.ANON
                                && soot.Modifier
                                .isInterface(tag2.getOuterClass()
                                .getModifiers()) ? soot.Modifier.STATIC
                                | soot.Modifier.PUBLIC
                                : outerClass.getModifiers());
                outerClass = tag2.getOuterClass();
            }*/
            SootClass outerClass = tag.getOuterClass();
            while (innerClassInfoMap.containsKey(outerClass)) {
                InnerClassInfo tag2 = innerClassInfoMap.get(outerClass);
                String outerName2 ;
                String simpleName2 ;
                if (tag2.getInnerType() == InnerClassInfo.ANON) {
                    outerName2 = null;
                    simpleName2 = null;
                } else {
                    outerName2 = tag2.getOuterClass().getName();
                    simpleName2 = tag2.getSimpleName();
                }

                int access2;
                if (tag2.getInnerType() == InnerClassInfo.ANON
                        && Modifier.isInterface(tag2.getOuterClass()
                        .getModifiers())) {
                    access2 = Modifier.STATIC | Modifier.PUBLIC;
                } else {
                    access2 = outerClass.getModifiers();
                }
                Util.addInnerClassTag(sootClass, outerClass.getName(),
                        outerName2, simpleName2, access2);
                outerClass = tag2.getOuterClass();
            }
        }
    
    }
//    private void addQualifierRefToInit(polyglot.types.Type type){
//        soot.Type sootType = Util.getSootType(type);
//        Iterator it = sootClass.getMethods().iterator();
//        while (it.hasNext()){
//            soot.SootMethod meth = (soot.SootMethod)it.next();
//            if (meth.getName().equals("<init>")){
//                List newParams = new ArrayList();
//                newParams.add(sootType);
//                newParams.addAll(meth.getParameterTypes());
//                meth.setParameterTypes(newParams);
//                meth.addTag(new soot.tagkit.QualifyingTag());
//            }
//        }
//    }
    private void addProcedureToClass(soot.SootMethod method) {
        sootClass.addMethod(method);
    }
    
    private void addConstValTag(PsiField field, soot.SootField sootField){
        PsiConstantEvaluationHelper constHelper = field.getManager()
                .getConstantEvaluationHelper();
        Object constValue = constHelper.computeConstantExpression(field.getInitializer());
        if (constValue instanceof Integer){
            sootField.addTag(new soot.tagkit.IntegerConstantValueTag((Integer) constValue));
        }
        else if (constValue instanceof Character){
            sootField.addTag(new soot.tagkit.IntegerConstantValueTag((Character) constValue));
        }
        else if (constValue instanceof Short){
            sootField.addTag(new soot.tagkit.IntegerConstantValueTag((Short) constValue));
        }
        else if (constValue instanceof Byte){
            sootField.addTag(new soot.tagkit.IntegerConstantValueTag((Byte) constValue));
        }
        else if (constValue instanceof Boolean){
            boolean b = (Boolean) constValue;
            sootField.addTag(new soot.tagkit.IntegerConstantValueTag(b ? 1: 0));
        }
        else if (constValue instanceof Long){
            sootField.addTag(new soot.tagkit.LongConstantValueTag((Long) constValue));
        }
        else if (constValue instanceof Double){
            sootField.addTag(new soot.tagkit.DoubleConstantValueTag((Double) constValue));
        }
        else if (constValue instanceof Float){
            sootField.addTag(new soot.tagkit.FloatConstantValueTag((Float) constValue));
        }
        else if (constValue instanceof String){
            sootField.addTag(new soot.tagkit.StringConstantValueTag((String)constValue));
        }
        else {
            throw new IllegalArgumentException("Expecting static final "
                    + "field to have a constant value! For field: "+field+" of "
                    + "type: "+constValue.getClass());
        }
    }
    
    /**
     * Field Declaration Creation
     */
    private void createFieldDecl(PsiField field){
   
        //System.out.println("field decl: "+field);
        int modifiers = Util.getModifier(field);
        String name = field.getName();
        soot.Type sootType = Util.getSootType(initialResolver, field.getType());
        soot.SootField sootField = new soot.SootField(name, sootType, modifiers);
        sootClass.addField(sootField);
    
        
        if (field.hasModifierProperty("static")) {
            if (field.getInitializer() != null) {
                PsiType fieldType = field.getType();
                if (field.hasModifierProperty("final")
                        && (fieldType instanceof PsiPrimitiveType
                        || (fieldType.equalsToText("java.lang.String")))
                        && PsiUtil.isCompileTimeConstant(field)){
                    //System.out.println("adding constantValtag: to field: "+sootField);
                    addConstValTag(field, sootField);
                }
                else {
                    if (staticFieldInits == null) {
                        staticFieldInits = new ArrayList<PsiField>();
                    }
                    staticFieldInits.add(field);
                }
            }
        }
        else {
            if (field.getInitializer() != null) {
                if (fieldInits == null) {
                    fieldInits = new ArrayList<PsiField>();
                }
                fieldInits.add(field);
            }
        }
    
    
        addPsiTags(sootField, field);
    }


    
    /**
     * Procedure Declaration Helper Methods
     * creates procedure name
     */
    private String createName(PsiMethod procedure) {
        return procedure.getName();
    }

    /**
     * creates soot params from polyglot formals
     */
    private List<Type> createParameters(PsiMethod procedure) {
        ArrayList<Type> parameters = new ArrayList<Type>();
        for (PsiParameter parameter : procedure.getParameterList()
                .getParameters()) {
            parameters.add(Util.getSootType(initialResolver, parameter.getType()));
        }
        return parameters;
    }
    
    /**
     * creates soot exceptions from polyglot throws
     */
    private List<SootClass> createExceptions(PsiMethod procedure) {
        ArrayList<SootClass> exceptions = new ArrayList<SootClass>();
        for (PsiClassType type : procedure.getThrowsList()
                .getReferencedTypes()) {
            exceptions.add(((soot.RefType)Util.getSootType(initialResolver, type)).getSootClass());
        }
        return exceptions; 
    }
    
    
    private soot.SootMethod createSootMethod(String name, PsiMethod method ,
            List<Type> parameters, List<SootClass> exceptions){
        
        int modifier = Util.getModifier(method);
        soot.Type sootReturnType = Util.getSootType(initialResolver, method.getReturnType());

        return new soot.SootMethod(name, parameters,
                sootReturnType, modifier, exceptions);
    }
    
    /**
     * Initializer Creation
     */
    private void createInitializer(PsiClassInitializer initializer) {
        if (initializer.hasModifierProperty("static")) {
            if (staticInitializerBlocks == null) {
                staticInitializerBlocks = new ArrayList<PsiCodeBlock>();
            }
            staticInitializerBlocks.add(initializer.getBody());
        }
        else {
            if (initializerBlocks == null) {
                initializerBlocks = new ArrayList<PsiCodeBlock>();
            }
            initializerBlocks.add(initializer.getBody());
        }
    }
    
    private soot.SootMethod createSootConstructor(String name, PsiMethod psiMethod,
            List<Type> parameters, List<SootClass> exceptions) {
        
        int modifier = Util.getModifier(psiMethod);

        soot.SootMethod method = new soot.SootMethod(name, parameters,
                soot.VoidType.v(), modifier, exceptions);

        return method;
    }

    public List<Type> getReferences() {
        return references;
    }
}

