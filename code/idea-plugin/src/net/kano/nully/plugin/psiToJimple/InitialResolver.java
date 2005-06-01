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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.openapi.vfs.VirtualFile;
import soot.FastHierarchy;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.tagkit.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InitialResolver {

    private PsiJavaFile astNode;  // source node
    private BiMap<PsiAnonymousClass,String> anonClassMap;   // maps New to SootClass (name)
    private Map<IdentityKey<PsiAnonymousClass>,String> anonTypeMap;    //maps psi types to soot types
    private BiMap<PsiClass,String> localClassMap;  // maps LocalClassDecl to SootClass (name)
    private Map<IdentityKey<PsiClass>,String> localTypeMap;   // maps psi types to soot types
    private int privateAccessCounter = 0; // global for whole program because
                                          // the methods created are static
    private Map<IdentityKey<PsiClass>,AnonLocalClassInfo> finalLocalInfo; // new or lcd mapped to list of final locals avail in current meth and the whether its static

    private Map<String,PsiJavaFile> sootNameToAST = null;
    private List<RefType> hasOuterRefInInit; // list of sootclass types that need an outer class this param in for init

    private Map<String,String> classToSourceMap;
    private Map<SootClass,SootClass> specialAnonMap;
    private Map<IdentityKey<PsiField>,SootMethod> privateFieldGetAccessMap;
    private Map<IdentityKey<PsiField>,SootMethod> privateFieldSetAccessMap;
    private Map<IdentityKey<PsiMethod>,SootMethod> privateMethodGetAccessMap;
    private List<String> interfacesList;
    private List<PsiMethodCallExpression> cCallList;

    private FastHierarchy hierarchy;

    private AbstractJBBFactory jbbFactory = new JimpleBodyBuilderFactory(this);

    public void setJBBFactory(AbstractJBBFactory jbbFactory){
        this.jbbFactory = jbbFactory;
    }

    public AbstractJBBFactory getJBBFactory(){
        return jbbFactory;
    }

    /**
     * returns true if there is an AST avail for given soot class
     */
    public boolean hasASTForSootName(String name){
       if (sootNameToAST == null) return false;
       if (sootNameToAST.containsKey(name)) return true;
       return false;
    }

    /**
     * sets AST for given soot class if possible
     */
    public void setASTForSootName(String name){
        if (!hasASTForSootName(name)) {
            throw new RuntimeException("Can only set AST for name if it exists. You should probably not be calling this method unless you know what you're doing!");
        }
        setAst(sootNameToAST.get(name));
    }

    /**
     * if you have a special AST set it here then call resolveFormJavaFile
     * on the soot class
     */
    public void setAst(PsiJavaFile ast) {
        astNode = ast;
    }

    private void makeASTMap() {
        ClassDeclFinder finder = new ClassDeclFinder();
        astNode.accept(finder);
        for (PsiClass decl : finder.declsFound()) {
            Type sootType = Util.getSootType(decl);
            if (decl.isInterface()) {
                if (interfacesList == null) {
                    interfacesList = new ArrayList<String>();
                }
                interfacesList.add(sootType.toString());
            }
            addNameToAST(sootType.toString());
        }
    }

    /**
     * add name to AST to map - used mostly for inner and non public
     * top-level classes
     */
    protected void addNameToAST(String name){
        if (sootNameToAST == null){
            sootNameToAST = new HashMap<String, PsiJavaFile>();
        }
        sootNameToAST.put(name, astNode);
    }

    public void resolveAST(){
        buildInnerClassInfo();
        createClassToSourceMap(astNode);
    }

    // resolves all types and deals with .class literals and asserts
    public List<Type> resolveFromJavaFile(soot.SootClass sc) {
        ClassResolver cr = new ClassResolver(this, sc);

        // create class to source map first
        // create source file
        cr.createSource(astNode);

        cr.addSourceFileTag(sc);

        makeASTMap();

        List<Type> refs = cr.getReferences();
        return refs;
    }


    private void createClassToSourceMap(PsiJavaFile src){
        VirtualFile vfile = src.getVirtualFile();
        String srcName;
        if (vfile == null) {
            srcName = src.getName();
        } else {
            srcName = vfile.getUrl();
        }

        for (PsiClass cls : src.getClasses()) {
            addToClassToSourceMap(Util.getSootType(cls).toString(), srcName);
        }
    }

    private void createLocalAndAnonClassNames(List<PsiAnonymousClass> anonBodyList, List<PsiClass> localClassDeclList){
        for (PsiAnonymousClass cls : anonBodyList) {
            createAnonClassName(cls);
        }
        for (PsiClass cls : localClassDeclList) {
            createLocalClassName(cls);
        }
    }

    protected int getNextAnonNum(){
        if (anonTypeMap == null) {
            return 1;
        } else {
            return anonTypeMap.size() + 1;
        }
    }

    private void createAnonClassName(PsiAnonymousClass nextNew){
        // maybe this anon has already been resolved
        if (anonClassMap == null){
            anonClassMap = new BiMap<PsiAnonymousClass, String>();
        }
        if (anonTypeMap == null){
            anonTypeMap = new HashMap<IdentityKey<PsiAnonymousClass>, String>();
        }
        if (!anonClassMap.containsKey(nextNew)){
            int nextAvailNum = 1;
            PsiClass outerToMatch = nextNew.getContainingClass();
            outerToMatch = getOutermostClass(outerToMatch);

            if (!anonTypeMap.isEmpty()){
                for (IdentityKey<PsiAnonymousClass> identityKey : anonTypeMap.keySet()) {
                    PsiClass pType = identityKey.object();
                    PsiClass outerMatch = pType.getContainingClass();
                    outerMatch = getOutermostClass(outerMatch);
                    if (outerMatch.equals(outerToMatch)) {
                        IdentityKey<PsiClass> key = new IdentityKey<PsiClass>(pType);
                        int numFound = getAnonClassNum(anonTypeMap.get(key));
                        if (numFound >= nextAvailNum) {
                            nextAvailNum = numFound + 1;
                        }
                    }
                }
            }

            String realName = outerToMatch.getQualifiedName()+"$"+nextAvailNum;
            anonClassMap.put(nextNew, realName);
            anonTypeMap.put(new IdentityKey<PsiAnonymousClass>(nextNew), realName);
            addNameToAST(realName);

        }
    }

    private PsiClass getOutermostClass(PsiClass outerMatch) {
        while (outerMatch.getContainingClass() != null) {
            outerMatch = outerMatch.getContainingClass();
        }
        return outerMatch;
    }

    private void createLocalClassName(PsiClass lcd){
        // maybe this localdecl has already been resolved
        if (localClassMap == null){
            localClassMap = new BiMap<PsiClass, String>();
        }
        if (localTypeMap == null){
            localTypeMap = new HashMap<IdentityKey<PsiClass>, String>();
        }

        if (!localClassMap.containsKey(lcd)){
            int nextAvailNum = 1;
            PsiClass outerToMatch = lcd.getContainingClass();
            outerToMatch = getOutermostClass(outerToMatch);

            if (!localTypeMap.isEmpty()){
                for (IdentityKey<PsiClass> identityKey : localTypeMap.keySet()) {
                    PsiClass pType = (identityKey).object();
                    PsiClass outerMatch = pType.getContainingClass();
                    outerMatch = getOutermostClass(outerMatch);
                    if (outerMatch.equals(outerToMatch)) {
                        String realName = localTypeMap.get(new IdentityKey<PsiClass>(pType));
                        int numFound = getLocalClassNum(realName, lcd.getName());
                        if (numFound >= nextAvailNum) {
                            nextAvailNum = numFound + 1;
                        }
                    }
                }
            }

            String realName = outerToMatch.getQualifiedName()+"$"+nextAvailNum+lcd.getName();
            localClassMap.put(lcd, realName);
            localTypeMap.put(new IdentityKey<PsiClass>(lcd), realName);
            addNameToAST(realName);
        }
    }

    private static final int NO_MATCH = 0;

    private int getLocalClassNum(String realName, String simpleName){
        // a local inner class is named outer$NsimpleName where outer
        // is the very outer most class
        int dIndex = realName.indexOf("$");
        int nIndex = realName.indexOf(simpleName, dIndex);
        if (nIndex == -1) return NO_MATCH;
        if (dIndex == -1) {
            throw new RuntimeException("Matching an incorrectly named local inner class: "+realName);
        }
        String numString = realName.substring(dIndex+1, nIndex);
        for (int i = 0; i < numString.length(); i++){
            if (!Character.isDigit(numString.charAt(i))) return NO_MATCH;
        }
        return new Integer(numString);
    }

    private int getAnonClassNum(String realName){
        // a anon inner class is named outer$N where outer
        // is the very outer most class
        int dIndex = realName.indexOf("$");
        if (dIndex == -1) {
            throw new RuntimeException("Matching an incorrectly named anon inner class: "+realName);
        }
        return new Integer(realName.substring(dIndex + 1));
    }


    /**
     * ClassToSourceMap is for classes whos names don't match the source file
     * name - ex: multiple top level classes in a single file
     */
    private void addToClassToSourceMap(String className, String sourceName) {

        if (classToSourceMap == null){
            classToSourceMap = new HashMap<String, String>();
        }
        classToSourceMap.put(className, sourceName);
    }


    public boolean hasClassInnerTag(soot.SootClass sc, String innerName){
        for (Tag t : ((Collection<Tag>) sc.getTags())) {
            if (t instanceof soot.tagkit.InnerClassTag) {
                soot.tagkit.InnerClassTag tag = (soot.tagkit.InnerClassTag) t;
                if (tag.getInnerClass().equals(innerName)) return true;
            }
        }
        return false;
    }

    private void buildInnerClassInfo(){
        InnerClassInfoFinder icif = new InnerClassInfoFinder();
        astNode.accept(icif);
        createLocalAndAnonClassNames(icif.anonBodyList(), icif.localClassDeclList());
        buildFinalLocalMap(icif.memberList());
    }

    private void buildFinalLocalMap(List<PsiMember> memberList){
        for (PsiMember member : memberList) {
            handleFinalLocals(member);
        }
    }

    public List<PsiMethodCallExpression> getCallList() {
        return cCallList;
    }

    public void clearCallList() {
        List<? extends PsiMethodCallExpression> calls = cCallList;
        if (calls != null) calls.clear();
    }

    public void clearAsts() {
        astNode = null;
        if (sootNameToAST != null) sootNameToAST.clear();
    }

    private void handleFinalLocals(PsiMember member){
        MethodFinalsChecker mfc = new MethodFinalsChecker();
        member.accept(mfc);
        //System.out.println("member: "+member);
        //System.out.println("mcf final locals avail: "+mfc.finalLocals());
        //System.out.println("mcf locals used: "+mfc.typeToLocalsUsed());
        //System.out.println("mfc inners: "+mfc.inners());
        if (cCallList == null){
            cCallList = new ArrayList<PsiMethodCallExpression>();
        }
        cCallList.addAll(mfc.ccallList());
        //System.out.println("cCallList: "+cCallList);
        AnonLocalClassInfo alci = new AnonLocalClassInfo();
        boolean isStatic = member.hasModifierProperty("static");
        if (member instanceof PsiMethod){
            // not sure if this will break deep nesting
            alci.finalLocalsAvail(mfc.finalLocals());
            if (isStatic){
                alci.inStaticMethod(true);
            }
        }
        else if (member instanceof PsiField){
            alci.finalLocalsAvail(new ArrayList<IdentityKey<PsiVariable>>());
            if (isStatic){
                alci.inStaticMethod(true);
            }
        }
        else if (member instanceof PsiClassInitializer){
            //TODO: test anonymous/local classes in initializers
            // for now don't make final locals avail in init blocks
            // need to test this
            alci.finalLocalsAvail(mfc.finalLocals());
            if (isStatic){
                alci.inStaticMethod(true);
            }
        }
        if (finalLocalInfo == null){
            finalLocalInfo = new HashMap<IdentityKey<PsiClass>, AnonLocalClassInfo>();
        }
        for (IdentityKey<PsiClass> identityKey : mfc.inners()) {
            PsiClass cType = (identityKey).object();
            // do the comparison about locals avail and locals used here
            Map<IdentityKey<PsiClass>, List<IdentityKey<PsiVariable>>> typeToLocalUsed
                    = mfc.typeToLocalsUsed();
            List<IdentityKey<PsiVariable>> localsUsed = new ArrayList<IdentityKey<PsiVariable>>();
            IdentityKey<PsiClass> cTypeKey = new IdentityKey<PsiClass>(cType);
            if (typeToLocalUsed.containsKey(cTypeKey)) {
                List<IdentityKey<PsiVariable>> localsNeeded = typeToLocalUsed.get(cTypeKey);
                for (IdentityKey<PsiVariable> aLocalsNeeded : localsNeeded) {
                    PsiVariable li = (aLocalsNeeded).object();
                    IdentityKey<PsiVariable> varKey = new IdentityKey<PsiVariable>(li);
                    if (alci.finalLocalsAvail()
                            .contains(varKey)) {
                        localsUsed.add(varKey);
                    }
                }
            }


            AnonLocalClassInfo info = new AnonLocalClassInfo();
            info.inStaticMethod(alci.inStaticMethod());
            info.finalLocalsAvail(localsUsed);
            if (!finalLocalInfo.containsKey(cTypeKey)) {
                finalLocalInfo.put(new IdentityKey<PsiClass>(cType), info);
            }
        }
    }

    public boolean isAnonInCCall(PsiClass anonType){
        //System.out.println("checking type: "+anonType);
        for (PsiMethodCallExpression cCall : cCallList) {
            //System.out.println("cCall params: "+cCall.arguments());
            for (PsiExpression arg : cCall.getArgumentList()
                    .getExpressions()) {
                if (arg instanceof PsiNewExpression) {
                    PsiAnonymousClass anonCls = ((PsiNewExpression) arg).getAnonymousClass();
                    if (anonCls != null) {
                        if (anonCls.equals(anonType)) return true;
                    }
                }
            }
        }
        return false;
    }

    public BiMap<PsiAnonymousClass,String> getAnonClassMap(){
        return anonClassMap;
    }

    public BiMap<PsiClass,String> getLocalClassMap(){
        return localClassMap;
    }

    public Map<IdentityKey<PsiAnonymousClass>,String> getAnonTypeMap(){
        return anonTypeMap;
    }

    public Map<IdentityKey<PsiClass>,String> getLocalTypeMap(){
        return localTypeMap;
    }

    public Map<IdentityKey<PsiClass>,AnonLocalClassInfo> finalLocalInfo(){
        return finalLocalInfo;
    }

    public int getNextPrivateAccessCounter(){
        int res = privateAccessCounter;
        privateAccessCounter++;
        return res;
    }

    public List<RefType> getHasOuterRefInInit(){
        return hasOuterRefInInit;
    }

    public void setHasOuterRefInInit(List<RefType> list){
        hasOuterRefInInit = list;
    }

    public Map<SootClass,SootClass> specialAnonMap(){
        return specialAnonMap;
    }

    public void setSpecialAnonMap(Map<SootClass,SootClass> map){
        specialAnonMap = map;
    }

    public void hierarchy(soot.FastHierarchy fh){
        hierarchy = fh;
    }
    
    public soot.FastHierarchy hierarchy(){
        return hierarchy;
    }

    private Map<SootClass,InnerClassInfo> innerClassInfoMap;
   
    public Map<SootClass,InnerClassInfo> getInnerClassInfoMap(){
        return innerClassInfoMap;
    }
 
    public void setInnerClassInfoMap(HashMap<SootClass,InnerClassInfo> map){
        innerClassInfoMap = map;
    }
 
    protected Map<String,String> classToSourceMap(){
        return classToSourceMap;
    }

    public void addToPrivateFieldGetAccessMap(PsiField field, soot.SootMethod meth){
        if (privateFieldGetAccessMap == null){
            privateFieldGetAccessMap = new HashMap<IdentityKey<PsiField>, SootMethod>();
        }
        privateFieldGetAccessMap.put(new IdentityKey<PsiField>(field), meth);
    }
    
    public Map<IdentityKey<PsiField>,SootMethod> getPrivateFieldGetAccessMap(){
        return privateFieldGetAccessMap;
    }
    
    public void addToPrivateFieldSetAccessMap(PsiField field, soot.SootMethod meth){
        if (privateFieldSetAccessMap == null){
            privateFieldSetAccessMap = new HashMap<IdentityKey<PsiField>, SootMethod>();
        }
        privateFieldSetAccessMap.put(new IdentityKey<PsiField>(field), meth);
    }
    
    public Map<IdentityKey<PsiField>,SootMethod> getPrivateFieldSetAccessMap(){
        return privateFieldSetAccessMap;
    }
    
    public void addToPrivateMethodGetAccessMap(PsiMethodCallExpression call, soot.SootMethod meth){
        if (privateMethodGetAccessMap == null){
            privateMethodGetAccessMap = new HashMap<IdentityKey<PsiMethod>, SootMethod>();
        }
        privateMethodGetAccessMap.put(new IdentityKey<PsiMethod>(call.resolveMethod()), meth);
    }
    
    public Map<IdentityKey<PsiMethod>,SootMethod> getPrivateMethodGetAccessMap(){
        return privateMethodGetAccessMap;
    }

    public List<String> getInterfacesList() {
        return interfacesList;
    } 
}

