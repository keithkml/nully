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

package net.kano.nully;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import soot.ArrayType;
import soot.BooleanType;
import soot.ByteType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootMethod;
import soot.SootResolver;
import soot.SourceLocator;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.InitialResolver;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import soot.options.Options;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodAnalyzer {
    private MethodInfo info;

    private Map<PsiType,Type> staticSootTypes = new HashMap<PsiType, Type>();
    {
        staticSootTypes.put(PsiType.BOOLEAN, BooleanType.v());
        staticSootTypes.put(PsiType.BYTE, ByteType.v());
        staticSootTypes.put(PsiType.CHAR, BooleanType.v());
        staticSootTypes.put(PsiType.DOUBLE, DoubleType.v());
        staticSootTypes.put(PsiType.FLOAT, FloatType.v());
        staticSootTypes.put(PsiType.INT, IntType.v());
        staticSootTypes.put(PsiType.LONG, LongType.v());
        staticSootTypes.put(PsiType.SHORT, ShortType.v());
        staticSootTypes.put(PsiType.VOID, VoidType.v());
    }

    public void analyze(MethodInfo info) {
        this.info = info;

//        NonnullChecker checker = new NonnullChecker(false);
//        psiFile.accept(checker);
//
//        if (!checker.shouldProcess()) return false;

        //TODO: don't delete nully import
        PsiJavaFile fileCopy = info.getFileCopy();
        PsiImportList imports = fileCopy.getImportList();
        PsiImportStatementBase nullyImport
                = imports.findSingleClassImportStatement(NullyTools.ANNO_NONNULL);
        if (nullyImport != null) {
            try {
                nullyImport.delete();
            } catch (IncorrectOperationException e) {
                e.printStackTrace();
            }
        }

        prepareSootClasses();

//        System.out.println(fileCopy.getText());

        loadSootClasses();

        try {
            info.setTracker(new OffsetsTracker(new StringReader(fileCopy.getText())));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        storeSootObjects();

        prepareCodeForAnalysis();

        JimpleMethodInstrumenter instrumenter = new JimpleMethodInstrumenter();
        instrumenter.instrumentSootCode(info);

        tagNulls();
    }

    public void resetSoot() {
        InitialResolver ir = InitialResolver.v();
        ir.clearCallList();
        ir.clearAsts();
        Scene scene = Scene.v();
        scene.getClassNumberer().clear();
        scene.getMethodNumberer().clear();
//        System.out.println("method numberer size: "
//         + scene.getMethodNumberer().size() + ", next: " + scene.getMethodNumberer().iterator().hasNext());
        scene.getFieldNumberer().clear();
        scene.getLocalNumberer().clear();
        scene.getSubSigNumberer().clear();
        scene.getTypeNumberer().clear();
//        scene.getContextNumberer().clear();
        scene.getUnitNumberer().clear();

        scene.getPhantomClasses().clear();
        scene.getLibraryClasses().clear();
        scene.getApplicationClasses().clear();
        SourceLocator.v().setClassProviders(Collections.EMPTY_LIST);
        SootResolver.v().clear();
        info = null;

        List<SootClass> scs = new ArrayList<SootClass>(scene.getClasses());
        for (SootClass sc : scs) scene.removeClass(sc);
    }

    private void prepareCodeForAnalysis() {
//        LocalSplitter.v().transform(body);
//        CopyPropagator.v().transform(body);
        for (SootMethod method : info.getSootMethods()) {
            ConstantPropagatorAndFolder.v().transform(method.retrieveActiveBody());
        }
    }

    /**
     * Adds the necessary class references to Soot's list of classes, and sets
     * the class provider to a virtual provider for {@code fileCopy}.
     */
    private void prepareSootClasses() {
        List<String> names = new ArrayList<String>();
        PsiJavaFile fileCopy = info.getFileCopy();
        List<PsiClass> classes = new ArrayList<PsiClass>(
                Arrays.asList(fileCopy.getClasses()));
        for (PsiClass cls : classes) {
            names.add(cls.getQualifiedName());
        }

        // tell Soot where to find the code
        SourceLocator.v().setClassProviders(Collections.singletonList(
                new VirtualJavaFileClassProvider(names, fileCopy)));

        // clean out Soot from previous runs
        Scene scene = Scene.v();
        List<SootClass> scs = new ArrayList<SootClass>(scene.getClasses());
        for (SootClass sc : scs) scene.removeClass(sc);
        scene.getPhantomClasses().clear();

        // add the classes we're scanning to Soot's list
        for (String name : names) {
            scene.addBasicClass(name, SootClass.BODIES);
        }
        scene.addBasicClass(NullyTools.CLASS_UNEXPECTEDNULL);
        scene.addBasicClass(NullyTools.CLASS_NULLPARAM);
        scene.addBasicClass(NullyTools.CLASS_NONNULLTOOLS);
        scene.addBasicClass(NullyTools.CLASS_NULLRETURN);
    }

    private void loadSootClasses() {
        Options options = Options.v();
        options.set_allow_phantom_refs(true);
        options.set_keep_line_number(true);
        options.set_keep_offset(true);
        Scene scene = Scene.v();
        scene.setPhantomRefs(true);
        scene.loadBasicClasses();
        List<SootClass> classes = new ArrayList<SootClass>();
        for (PsiClass cls : info.getFileCopy().getClasses()) {
            classes.add(scene.getSootClass(cls.getQualifiedName()));
        }
        info.setSootClasses(classes);
    }

    private void tagNulls() {
        NullPointerTagger checker = new NullPointerTagger();
        Scene.v().setPhantomRefs(true);
        for (SootMethod method : info.getSootMethods()) {
            checker.transform(method.retrieveActiveBody());
        }
    }

    private void storeSootObjects() {
//        PsiMethod methodCopy = info.getMethodCopy();
//        List<Type> paramTypes = new ArrayList<Type>();
//        for (PsiParameter param : methodCopy.getParameterList()
//                .getParameters()) {
//            PsiType psiType = param.getType();
//            Type out = getSootType(psiType);
//            paramTypes.add(out);
//        }
    }

    private Type getSootType(PsiType psiType) {
        Type out = staticSootTypes.get(psiType);
        if (out == null) {
            if (psiType instanceof PsiArrayType) {
                PsiArrayType type = (PsiArrayType) psiType;
                return ArrayType.v(getSootType(type.getComponentType()),
                        type.getArrayDimensions());
            } else if (psiType instanceof PsiClassType) {
                PsiClassType type = (PsiClassType) psiType;
                PsiClass resolved = type.resolve();
                String fqn;
                if (resolved == null) {
                    fqn = type.getClassName();
                } else {
                    fqn = resolved.getQualifiedName();
                }
                return RefType.v(fqn);
            } else {
                throw new IllegalArgumentException("Cannot convert to Soot type:"
                        + psiType);
            }
        }
        return out;
    }
}
