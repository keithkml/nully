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

package net.kano.nully.analysis.nulls;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.NonNullTools;
import net.kano.nully.NullParameterException;
import net.kano.nully.NullReturnException;
import net.kano.nully.NullyTools;
import net.kano.nully.OffsetsTracker;
import net.kano.nully.UnexpectedNullValueException;
import net.kano.nully.NonNull;
import net.kano.nully.Nullable;
import net.kano.nully.analysis.nulls.soot.JimpleMethodPreprocessor;
import net.kano.nully.analysis.nulls.soot.NullPointerTagger;
import net.kano.nully.analysis.nulls.soot.PsiJavaFileClassProvider;
import net.kano.nully.analysis.AnalysisContext;
import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootMethod;
import soot.SootResolver;
import soot.SourceLocator;
import soot.Type;
import soot.Unit;
import soot.VoidType;
import soot.ValueBox;
import soot.util.Chain;
import soot.javaToJimple.InitialResolver;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import soot.jimple.InvokeExpr;
import soot.options.Options;
import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Tag;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeAnalyzer {
    private AnalysisContext context;

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

    public void analyze(AnalysisContext context) {
        this.context = context;

//        NonnullChecker checker = new NonnullChecker(false);
//        psiFile.accept(checker);
//
//        if (!checker.shouldProcess()) return false;

        PsiJavaFile fileCopy = context.getFileCopy();
        //TODO: optimize imports is necessary?
        try {
            // avoid unnecessarily referencing classes which can't be resolved
            CodeStyleManager.getInstance(fileCopy.getManager()).optimizeImports(fileCopy);
        } catch (IncorrectOperationException e) {
            throw new IllegalStateException(e);
        }

        prepareSootClasses();

//        System.out.println(fileCopy.getText());

        loadSootClasses();

        try {
            context.setTracker(new OffsetsTracker(new StringReader(fileCopy.getText())));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        storeSootObjects();

        prepareCodeForAnalysis();

        JimpleMethodPreprocessor preprocessor = new JimpleMethodPreprocessor(context);
        preprocessor.preprocessCode();

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
        context = null;

        List<SootClass> scs = new ArrayList<SootClass>(scene.getClasses());
        for (SootClass sc : scs) scene.removeClass(sc);
    }

    private void prepareCodeForAnalysis() {
//        LocalSplitter.v().transform(body);
//        CopyPropagator.v().transform(body);
        for (SootMethod method : context.getSootMethods()) {
            Body body = method.retrieveActiveBody();
            ConstantPropagatorAndFolder.v().transform(body);
            List<Unit> units = new ArrayList<Unit>(body.getUnits());
            for (Unit unit : units) {
                if (!(unit instanceof JInvokeStmt)) continue;

                boolean any = false;
                int sline = 0;
                int spos = 0;
                int eline = 0;
                int epos = 0;
                for (Tag tag : (List<Tag>) unit.getTags()) {
                    if (!(tag instanceof SourceLnPosTag)) continue;

                    SourceLnPosTag posTag = (SourceLnPosTag) tag;
                    if (!any) {
                        sline = posTag.startLn();
                        spos = posTag.startPos();
                        eline = posTag.endLn();
                        eline = posTag.endPos();
                        any = true;
                    } else {
                        if (posTag.startLn() < sline) {
                            sline = posTag.startLn();
                            spos = posTag.startPos();
                        } else if (posTag.startPos() < spos) {
                            spos = posTag.startPos();
                        }
                        if (posTag.endLn() > eline) {
                            eline = posTag.endLn();
                            epos = posTag.endPos();
                        } else if (posTag.endPos() > epos) {
                            epos = posTag.endPos();
                        }
                    }
                }
                if (!any) continue;

                OffsetsTracker tracker = context.getTracker();
                PsiElement el = tracker.getElementAtPosition(context.getFileCopy(),
                        new SourceLnPosTag(sline, eline, spos, epos));
                if (el == null) continue;
                PsiVariable var = NullyTools.getReferencedVariable(el);
                if (var == null) continue;

                JInvokeStmt invokeStmt = (JInvokeStmt) unit;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                Chain locals = body.getLocals();
                String name = NullyTools.getUnusedLocalName(locals);
                Local local = new JimpleLocal(name, invokeExpr.getType());
                locals.add(local);
                JAssignStmt stmt = new JAssignStmt(local, invokeExpr);
                stmt.addAllTagsOf(unit);
                ValueBox origBox = stmt.getInvokeExprBox();
                origBox.addAllTagsOf(invokeStmt.getInvokeExprBox());
                origBox.addAllTagsOf(invokeStmt);
                body.getUnits().swapWith(unit, stmt);
            }
        }
    }

    /**
     * Adds the necessary class references to Soot's list of classes, and sets
     * the class provider to a virtual provider for {@code fileCopy}.
     */
    private void prepareSootClasses() {
        List<String> names = new ArrayList<String>();
        PsiJavaFile fileCopy = context.getFileCopy();
        PsiClass[] psiClasses = fileCopy.getClasses();
        List<PsiClass> classes = new ArrayList<PsiClass>(
                Arrays.asList(psiClasses));
        for (PsiClass psiClass : psiClasses) {
            Collections.addAll(classes, psiClass.getAllInnerClasses());
        }
        for (PsiClass cls : classes) {
            names.add(NullyTools.getJavaNameForClass(cls));
        }

        // tell Soot where to find the code
        SourceLocator.v().setClassProviders(Collections.singletonList(
                new PsiJavaFileClassProvider(names, fileCopy)));

        // clean out Soot from previous runs
        Scene scene = Scene.v();
        List<SootClass> scs = new ArrayList<SootClass>(scene.getClasses());
        for (SootClass sc : scs) scene.removeClass(sc);
        scene.getPhantomClasses().clear();

        // add the classes we're scanning to Soot's list
        for (String name : names) {
            scene.addBasicClass(name, SootClass.BODIES);
        }

        // add the classes which were stripped out
        for (String stripped : context.getStrippedClassNames()) {
            scene.addBasicClass(stripped);
        }

        //TODO: add all resolvable classes
        // add any classes we might reference
        scene.addBasicClass(UnexpectedNullValueException.class.getName());
        scene.addBasicClass(NullParameterException.class.getName());
        scene.addBasicClass(NonNullTools.class.getName());
        scene.addBasicClass(NonNull.class.getName());
        scene.addBasicClass(Nullable.class.getName());
        scene.addBasicClass(NullReturnException.class.getName());
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
        for (PsiClass cls : context.getFileCopy().getClasses()) {
            addClass(cls, classes);
        }
        context.setSootClasses(classes);
    }

    private void addClass(PsiClass cls, List<SootClass> classes) {
        if (cls.isInterface()) return;
        String name = NullyTools.getJavaNameForClass(cls);
        classes.add(Scene.v().getSootClass(name));
        for (PsiClass psiClass : cls.getInnerClasses()) {
            addClass(psiClass, classes);
        }
    }

    private void tagNulls() {
        NullPointerTagger checker = new NullPointerTagger();
        Scene.v().setPhantomRefs(true);
        for (SootMethod method : context.getSootMethods()) {
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
