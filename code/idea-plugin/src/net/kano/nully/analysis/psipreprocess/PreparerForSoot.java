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

package net.kano.nully.plugin.analysis.nulls.psipreprocess;

import com.intellij.degenerator.CastingVisitor;
import com.intellij.degenerator.ModifyingVisitor;
import com.intellij.degenerator.TypeParametersRemovingVisitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.plugin.PsiTools;
import net.kano.nully.plugin.analysis.AnalysisContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides methods which prepare Java code to be passed to Soot's parser.
 */
public class PreparerForSoot {
    private static final Logger LOGGER
            = Logger.getInstance(PreparerForSoot.class.getName());

    private final AnalysisContext context;

    public PreparerForSoot(@NonNull AnalysisContext context) {
        this.context = context;
    }

    public void prepareForFileAnalysis(@NonNull PsiJavaFile jfile) {
        makeMarkedCopy(jfile);
        stripErrors(jfile);
        stripJava5Code(context.getCopiedElement(jfile));
    }

    private void stripErrors(@NonNull PsiJavaFile jfile) {
        jfile.accept(new PsiRecursiveElementVisitor() {
            public void visitStatement(PsiStatement statement) {
                super.visitStatement(statement);

                if (PsiTreeUtil.getChildOfType(statement, PsiErrorElement.class) != null) {
                    try {
                        statement.delete();
                    } catch (IncorrectOperationException e) {
                        LOGGER.error(e);
                    }
                }
            }
//            public void visitExpression(PsiExpression expression) {
//                super.visitExpression(expression);
//
//                if (PsiTreeUtil.getChildOfType(expression, PsiErrorElement.class) != null) {
//                    PsiElementFactory factory = expression.getManager().getElementFactory();
//                    String def = NullyTools.getDefaultValue(ExpectedTypeUtils.findExpectedType(expression));
//                    try {
//                        expression.replace(factory.createExpressionFromText(def, expression));
//                    } catch (IncorrectOperationException e) {
//                        LOGGER.error(e);
//                    }
//                }
//            }
//
//            public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
//                super.visitReferenceElement(reference);
//
//                if (reference.resolve() == null) {
//                    PsiExpression exp = PsiTreeUtil.getParentOfType(reference,
//                            PsiExpression.class, false);
//                    PsiElementFactory factory = reference.getManager().getElementFactory();
//                    String def = NullyTools.getDefaultValue(ExpectedTypeUtils.findExpectedType(exp));
//                    try {
//                        reference.replace(factory.createExpressionFromText(def));
//                    } catch (IncorrectOperationException e) {
//                        LOGGER.error(e);
//                    }
//                }
//            }
        });
    }

    public void prepareForElementsAnalysis(@NonNull PsiJavaFile jfile,
            @NonNull Collection<PsiMember> toInspect) {
        makeMarkedCopy(jfile);
        strip(toInspect);
        stripErrors(jfile);
        stripJava5Code(context.getCopiedElement(jfile));
    }

    /**
     * Makes a copy of the given file, and stores it in the {@code AnalysisContext}
     * associated with this preparer. Each element of the original tree will
     * have a reference to the corresponding copied elemnt, stored under the
     * user data key {@link AnalysisContext#getCopyKey()}. Each element of the copy will
     * have a corresponding reference to the original element under the key
     *  {@link AnalysisContext#getOriginalKey()}.
     * <br /><br />
     * The given file is also set as the "original file" property of the
     * associated {@code AnalysisInfo}.
     *
     * @param jfile the file
     * @return the copy
     */
    public @NonNull PsiJavaFile makeMarkedCopy(@NonNull PsiJavaFile jfile) {
        context.setFileOrig(jfile);
        PsiJavaFile fileCopy = PsiTools.getMarkedCopy(jfile,
                context.getOriginalKey(), context.getCopyKey());
        context.setFileCopy(fileCopy);
        return fileCopy;
    }

    private void strip(@NonNull Collection<PsiMember> toInspect) {
        List<PsiMember> toInspectCopies = new ArrayList<PsiMember>();
        for (PsiMember psiMember : toInspect) {
            toInspectCopies.add(context.getCopiedElement(psiMember));
        }
        PsiJavaFile fileCopy = context.getFileCopy();
        PsiOtherMethodStripper stripper = new PsiOtherMethodStripper(toInspectCopies);
        fileCopy.accept(stripper);
        context.addStrippedClassNames(stripper.getStrippedClassesNames());
    }

    /**
     * Strips Java 5.0 code from the given file according to the specification
     * of {@link Java5CodeStripVisitor};
     *
     * @param file the element to transform
     */
    public void stripJava5Code(@NonNull PsiJavaFile file) {
//        for (PsiClass cls : file.getClasses()) {
//            for (PsiMethod method : cls.getMethods()) {
//                DataFlowRunner r = new DataFlowRunner();
//                boolean b = r.analyzeMethod(method.getBody());
//                List<Instruction> instructions = new ArrayList<Instruction>();
//                for (int i = 0;; i++) {
//                    try {
//                        instructions.add(r.getInstruction(i));
//                    } catch (ArrayIndexOutOfBoundsException e) {
//                        break;
//                    }
//                }
//                for (Instruction instruction : instructions) {
//                    if (instruction instanceof PushInstruction) {
//                        PushInstruction pi = (PushInstruction) instruction;
//                        DfaMemoryState st = new DfaMemoryStateImpl(r.getFactory());
//                        st.
//                        pi.apply(r, st);
//                        DfaValue value = st.pop();
//                        System.out.println("value");
//                    }
//                }
//                System.out.println("b");
//            }
//        }

        file.accept(new Java5CodeStripVisitor());
        file.accept(new Java5CodeStripVisitorSecondPass());
//        PsiJavaFile copy = degenerate(file);
//        System.out.println("degenerated");
//        Pair<?,?> result = DegeneratorUtil.degenerate(el);
//        System.out.println("result");
    }

    private PsiJavaFile degenerate(PsiFile file) {
        Project project = file.getProject();
        CastingVisitor castingvisitor = new CastingVisitor(project, new ArrayList());
        TypeParametersRemovingVisitor typeparametersremovingvisitor
                = new TypeParametersRemovingVisitor(project);
        file.accept(castingvisitor);
        file.accept(typeparametersremovingvisitor);
        PsiJavaFile copy = (PsiJavaFile) file.copy();
        ModifyingVisitor modifyingvisitor = new ModifyingVisitor();
        copy.accept(modifyingvisitor);
        return copy;
    }

    /**
     * Deletes all reference to {@linkplain #makeMarkedCopy copied elements} in
     * the given PSI element tree.
     *
     * @param el the element whose tree will be stripped of all copies
     */
    public void removeCopy(@NonNull PsiElement el) {
        PsiElement copy = context.getCopiedElement(el);

        if (copy != null) {
            this.context.clearCopiedElementData(el);

//            try {
//                if (copy.isValid()) copy.delete();
//            } catch (Exception ignored) {
//                LOGGER.debug(ignored);
//            }
        }

        for (PsiElement child : el.getChildren()) removeCopy(child);
    }

}
