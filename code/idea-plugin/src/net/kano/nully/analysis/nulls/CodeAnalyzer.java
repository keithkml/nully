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

package net.kano.nully.plugin.analysis.nulls;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.util.PsiUtil;
import net.kano.nully.annotations.NonNullTools;
import net.kano.nully.annotations.NullParameterException;
import net.kano.nully.annotations.NullReturnException;
import net.kano.nully.annotations.UnexpectedNullValueException;
import net.kano.nully.plugin.PsiTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import net.kano.nully.plugin.analysis.nulls.soot.JimpleMethodPreprocessor;
import net.kano.nully.plugin.analysis.nulls.soot.NullPointerTagger;
import net.kano.nully.plugin.psiToJimple.InitialResolver;
import net.kano.nully.plugin.psiToJimple.Util;
import soot.Body;
import soot.ClassProvider;
import soot.ClassSource;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootResolver;
import soot.SourceLocator;
import soot.Type;
import soot.options.Options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CodeAnalyzer {
    //TOLATER: show definitely nulls even if not Nullable
    //TOLATER: allow specification of nonnull param schemas for classes (Collection.add, etc)
    private AnalysisContext context;
    private PsiClassProvider provider;

    public void analyze(AnalysisContext context) {
        this.context = context;

        prepareSootClasses();

        loadSootClasses();

        storeSootObjects();

        prepareCodeForAnalysis();

        JimpleMethodPreprocessor preprocessor = new JimpleMethodPreprocessor(context);
        preprocessor.preprocessCode();

        addAnalysisTags();
    }

    public void resetSoot() {
        // we don't use Soot's InitialResolver anymore
//        InitialResolver ir = InitialResolver.v();
//        ir.clearCallList();
//        ir.clearAsts();
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

        // the PsiMethodSource does not strip assignments anymore
//        for (SootMethod method : context.getSootMethods()) {
//            convertSootStrippedAssignment(method);
//        }
    }

    /*
    private void convertSootStrippedAssignment(SootMethod method) {
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
                    epos = posTag.endPos();
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
            PsiElement el = context.getElementAtPosition(context.getFileCopy(),
                    new SourceLnPosTag(sline, eline, spos, epos));
            if (el == null) continue;
            PsiVariable var = PsiTools.getReferencedVariable(el);
            if (var == null) continue;

            JInvokeStmt invokeStmt = (JInvokeStmt) unit;
            InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
            Chain locals = body.getLocals();
            String name = SootTools.getUnusedLocalName(locals);
            Local local = new JimpleLocal(name, invokeExpr.getType());
            locals.add(local);
            JAssignStmt stmt = new JAssignStmt(local, invokeExpr);
            stmt.addAllTagsOf(unit);
            ValueBox origBox = stmt.getInvokeExprBox();
            origBox.addAllTagsOf(invokeStmt.getInvokeExprBox());
            origBox.addAllTagsOf(invokeStmt);
            body.getUnits().swapWith(unit, stmt);
        }
    }*/

    /**
     * Adds the necessary class references to Soot's list of classes, and sets
     * the class provider to a virtual provider for {@code fileCopy}.
     */
    private void prepareSootClasses() {
        final PsiJavaFile fileCopy = context.getFileCopy();

        // tell Soot where to find the code
        provider = new PsiClassProvider(fileCopy);
        SourceLocator.v().setClassProviders(Collections.singletonList(provider));

        // clean out Soot from previous runs
        Scene scene = Scene.v();
        List<SootClass> scs = new ArrayList<SootClass>(scene.getClasses());
        for (SootClass sc : scs) scene.removeClass(sc);
        scene.getPhantomClasses().clear();

        // add the classes we're scanning to Soot's list
        for (String name : provider.getNames()) {
            scene.addBasicClass(name, SootClass.BODIES);
        }

        // add the classes which were stripped out
        for (String stripped : context.getStrippedClassNames()) {
            scene.addBasicClass(stripped);
        }

        //TOLATER: add all resolvable classes
        // add any classes we might reference
        scene.addBasicClass(UnexpectedNullValueException.class.getName());
        scene.addBasicClass(NullParameterException.class.getName());
        scene.addBasicClass(NullReturnException.class.getName());
        scene.addBasicClass(NonNullTools.class.getName());
    }

    private void loadSootClasses() {
        Options options = Options.v();
        options.set_allow_phantom_refs(true);
//        options.set_keep_line_number(true);
//        options.set_keep_offset(true);
        Scene scene = Scene.v();
        scene.setPhantomRefs(true);
        scene.loadBasicClasses();

        List<SootClass> classes = new ArrayList<SootClass>();
        for (String name : provider.getNames()) {
            classes.add(scene.getSootClass(name));
        }
        context.setSootClasses(classes);
    }

    private void addClass(PsiClass cls, List<SootClass> classes) {
//        if (cls.isInterface()) return;
        String name = PsiTools.getJavaNameForClass(cls);
        classes.add(Scene.v().getSootClass(name));
        for (PsiClass psiClass : cls.getInnerClasses()) {
            addClass(psiClass, classes);
        }
    }

    private void addAnalysisTags() {
        NullPointerTagger checker = new NullPointerTagger();
        NullableTagger nullableTagger = new NullableTagger(context);
        Scene.v().setPhantomRefs(true);
        for (SootMethod method : context.getSootMethods()) {
            Body body = method.retrieveActiveBody();
            checker.transform(body);
            nullableTagger.transform(body);
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

    private static class PsiClassProvider implements ClassProvider {
        private InitialResolver resolver = new InitialResolver();
        private List<String> names = new ArrayList<String>();
        private PsiManager manager;
        private PsiJavaFile fileCopy;

        public PsiClassProvider(PsiJavaFile fileCopy) {
            this.fileCopy = fileCopy;
            manager = fileCopy.getManager();
            resolver.setAst(fileCopy);
            resolver.resolveAST();
        }

        private void addClass(PsiClass psiClass) {
            names.add(Util.getJavaClassName(resolver, psiClass));
        }

        public List<String> getNames() {
            updateNames();
            return names;
        }

        public ClassSource find(String className) {
            if (!getNames().contains(className)) {
                return null;
            } else {
                return new PsiClassSource(resolver, className);
            }
        }

        private void updateNames() {
            names.clear();
            fileCopy.accept(new PsiRecursiveElementVisitor() {
                public void visitClass(PsiClass psiClass) {
                    super.visitClass(psiClass);

                    if (PsiUtil.isLocalClass(psiClass)) {
                        String name = Util.getLocalClassName(resolver, psiClass);
                        if (name != null) names.add(name);
                    } else if (psiClass instanceof PsiAnonymousClass) {
                        PsiAnonymousClass acls = (PsiAnonymousClass) psiClass;

                        String name = Util.getAnonymousClassName(resolver, acls);
                        if (name != null) names.add(name);
                    } else {
                        addClass(psiClass);
                    }
                }

                public void visitAnonymousClass(PsiAnonymousClass psiAnonymousClass) {
                    super.visitAnonymousClass(psiAnonymousClass);
                }
            });
        }

        private class PsiClassSource extends ClassSource {
            private final InitialResolver resolver;

            public PsiClassSource(InitialResolver resolver, String className) {
                super(className);
                this.resolver = resolver;
            }

            public List<Type> resolve(SootClass sc) {
                List<Type> types = resolver.resolveFromJavaFile(sc);
                return types;
            }
        }
    }
}
