/*
 *  Copyright (c) 2004, Keith Lea
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

package net.kano.nully.compilation;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import static com.intellij.openapi.compiler.CompilerMessageCategory.ERROR;
import static com.intellij.openapi.compiler.CompilerMessageCategory.WARNING;
import com.intellij.openapi.compiler.JavaSourceTransformingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.ImportantSuperMethodInfo;
import net.kano.nully.NonNull;
import net.kano.nully.NullyInstrumented;
import net.kano.nully.NullyTools;
import net.kano.nully.OverrideType;
import net.kano.nully.NullCheckLevel;
import static net.kano.nully.OverrideType.IMPLEMENTS;
import static net.kano.nully.OverrideType.OVERRIDES;
import net.kano.nully.analysis.AnalysisContext;
import net.kano.nully.analysis.IllegalAnnotationProblem;
import net.kano.nully.analysis.IllegalParamOverrideFinder;
import net.kano.nully.analysis.IllegalParamOverrideProblem;
import net.kano.nully.analysis.IllegalReturnOverrideFinder;
import net.kano.nully.analysis.IllegalReturnOverrideProblem;
import net.kano.nully.analysis.NullyInstrumentedFinder;
import net.kano.nully.analysis.NullyInstrumentedProblem;
import net.kano.nully.analysis.NullyProblem;
import net.kano.nully.analysis.PrimitiveAnnotationFinder;
import net.kano.nully.analysis.ProblemFinder;
import net.kano.nully.analysis.PrimitiveAnnotationProblem;
import net.kano.nully.analysis.nulls.CodeAnalyzer;
import net.kano.nully.analysis.nulls.NullProblemType;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.analysis.nulls.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import net.kano.nully.analysis.nulls.NullValueProblem;
import net.kano.nully.analysis.nulls.NullValueProblemFinder;
import net.kano.nully.analysis.nulls.psipreprocess.PreparerForSoot;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NullyCompilerStep implements JavaSourceTransformingCompiler {
    private static final Logger LOGGER
            = Logger.getInstance(NullyCompilerStep.class.getName());

    private FileTypeManager ftm = FileTypeManager.getInstance();
    private FileType javaType = ftm.getFileTypeByExtension("java");

    private FileDocumentManager docmgr = FileDocumentManager.getInstance();
    private PsiDocumentManager psiDocMgr;
    private static final List<Class<? extends ProblemFinder<? extends NullyProblem<? extends PsiElement>>>> AUXILLARY_FINDER_CLASSES
            = Arrays.<Class<? extends ProblemFinder<? extends NullyProblem<? extends PsiElement>>>>asList(
            NullyInstrumentedFinder.class,
            PrimitiveAnnotationFinder.class,
            IllegalParamOverrideFinder.class,
            IllegalReturnOverrideFinder.class);

    public NullyCompilerStep(@NonNull Project project) {
        psiDocMgr = PsiDocumentManager.getInstance(project);
    }

    public boolean isTransformable(@NonNull VirtualFile file) {
        return file.getFileType().equals(javaType);
    }

    public boolean transform(final @NonNull CompileContext context,
            final @NonNull VirtualFile output, final @NonNull VirtualFile original) {
        try {
            final AtomicBoolean value = new AtomicBoolean();
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    Application app = ApplicationManager.getApplication();
                    app.runWriteAction(new Runnable() {
                        public void run() {
                            ProgressIndicator indicator = context.getProgressIndicator();
                            try {
                                indicator.pushState();
                                indicator.setText("Validating null value "
                                        + "constraints for " + original.getName());

                                try {
                                    value.set(reallyTransform(context,
                                            output, original));
                                } catch (Exception e) {
                                    context.addMessage(ERROR,
                                            "Error while running null checks in "
                                                    + "file " + original.getName(),
                                            original.getUrl(), -1, -1);
                                    LOGGER.error(e);
                                }
                            } finally {
                                indicator.popState();
                            }
                        }
                    });
                }
            });

            return value.get();
        } catch (Exception e) {
            LOGGER.error(e);
        }

        return false;
    }

    private boolean reallyTransform(@NonNull CompileContext context,
            @NonNull VirtualFile file, @NonNull VirtualFile original)
            throws IncorrectOperationException, IOException {
        Document doc = docmgr.getDocument(original);
        PsiFile psiFile = psiDocMgr.getPsiFile(doc);
        if (!(psiFile instanceof PsiJavaFile)) return false;
        PsiJavaFile jfile = (PsiJavaFile) psiFile;

//        NonnullChecker checker = new NonnullChecker(false);
//        psiFile.accept(checker);
//
//        if (!checker.shouldProcess()) return false;

        AnalysisContext ctx = new AnalysisContext();
        ctx.addCheckLevel(NullCheckLevel.COMPILER);
        ctx.addCheckLevel(NullCheckLevel.RUNTIME);

        PsiJavaFile copy = NullyTools.getMarkedCopy(jfile,
                ctx.getOriginalKey(), ctx.getCopyKey());

        PreparerForSoot preparer = new PreparerForSoot(ctx);
        preparer.makeMarkedCopy(copy);
        preparer.stripJava5Code(ctx.getFileCopy());

        CodeAnalyzer analyzer = new CodeAnalyzer();
        analyzer.analyze(ctx);

        List<NullyProblem<? extends PsiElement>> problems
                = new ArrayList<NullyProblem<? extends PsiElement>>(findKnownProblems(ctx));
        NullValueProblemFinder finder = new NullValueProblemFinder();
        Collection<NullValueProblem> nvProblems = finder.findProblems(ctx);
        problems.addAll(nvProblems);

        // this must be done before transforming the code, before the
        // PsiElements mean nothing because they were deleted
        addCompilerWarnings(context, jfile, problems);

        boolean changed = RuntimeCheckInserter.insertRuntimeChecks(copy, nvProblems);

        if (changed) {
            // add NullyInstrumented annotation
            for (PsiClass cls : copy.getClasses()) {
                PsiAnnotation oldAnno = cls.getModifierList()
                        .findAnnotation(NullyInstrumented.class.getName());
                if (oldAnno != null) continue;
                PsiElementFactory factory = cls.getManager().getElementFactory();
                PsiAnnotation anno = factory.createAnnotationFromText("@"
                        + NullyInstrumented.class.getName(), cls);
                cls.getModifierList().add(anno);
            }

            // make copy
            String text = copy.getText();
            System.out.println("New:");
            System.out.println(text);
            writeJavaFile(copy, file);
            return true;
        } else {
            return false;
        }
    }

    private static List<NullyProblem<? extends PsiElement>> findKnownProblems(
            @NonNull AnalysisContext ctx) {
        List<NullyProblem<? extends PsiElement>> problems
                = new ArrayList<NullyProblem<? extends PsiElement>>();
        for (Class<? extends ProblemFinder<? extends NullyProblem<? extends PsiElement>>> aClass : AUXILLARY_FINDER_CLASSES) {
            ProblemFinder<? extends NullyProblem<? extends PsiElement>> finder;
            try {
                finder = aClass.newInstance();
            } catch (Exception e) {
                LOGGER.error("Couldn't instantiate " + aClass.getSimpleName(), e);
                continue;
            }
            problems.addAll(finder.findProblems(ctx));
        }
        return problems;
    }

    private static void addCompilerWarnings(@NonNull CompileContext context,
            @NonNull PsiJavaFile jfile, @NonNull List<NullyProblem<? extends PsiElement>> problems) {
        for (NullyProblem<? extends PsiElement> problem : problems) {
            PsiElement element = problem.getElement();
            if (NullyTools.shouldCheckNulls(element, NullCheckLevel.COMPILER)) {
                addCompilerWarning(context, jfile, problem);
            }
        }
    }

    private static void addCompilerWarning(@NonNull CompileContext context,
            @NonNull PsiJavaFile orig, @NonNull NullyProblem<? extends PsiElement> problem) {
        String desc;
        NullyProblem hack = problem;
        if (hack instanceof NullValueProblem) {
            NullValueProblem nvProblem = (NullValueProblem) hack;
            desc = getWarningForNullProblem(nvProblem);

        } else if (hack instanceof IllegalReturnOverrideProblem) {
            IllegalReturnOverrideProblem overrideProblem
                    = (IllegalReturnOverrideProblem) hack;

            PsiMethod method = overrideProblem.getElement();
            Collection<PsiMethod> badSupers = overrideProblem.getBadSupers();
            ImportantSuperMethodInfo important
                    = NullyTools.getImportantSuperMethod(method, badSupers);
            OverrideType overType = important.getType();
            String word = getWordForType(overType);

            PsiMethod overridden = important.getOverridden();
            desc = "Method " + method.getName() + " illegal "
                    + word + " " + NullyTools.getQualifiedMemberName(overridden)
                    + " without matching @" + NonNull.class.getSimpleName()
                    + " declaration";

        } else if (hack instanceof IllegalParamOverrideProblem) {
            IllegalParamOverrideProblem pop = (IllegalParamOverrideProblem) hack;
            PsiAnnotation anno = pop.getElement();
            PsiParameter param = PsiTreeUtil.getParentOfType(anno, PsiParameter.class);
            PsiMethod method = PsiTreeUtil.getParentOfType(param, PsiMethod.class);
            String superClassName = NullyTools.getImportantSuperMethod(method,
                    pop.getSuperMethods()).getOverridden().getContainingClass().getName();
            desc = "Parameter " + param.getName() + " of method "
                    + method.getName() + " cannot be declared @"
                    + NonNull.class.getSimpleName() + "; corresponding super "
                    + "method in " + superClassName + " parameter is not marked @"
                    + NonNull.class.getSimpleName();

        } else if (hack instanceof PrimitiveAnnotationProblem) {
            IllegalAnnotationProblem inp = (IllegalAnnotationProblem) hack;
            PsiAnnotation anno = inp.getElement();
            PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(anno,
                    PsiModifierListOwner.class, false);

            String annoName = getAnnotationName(anno);
            if (owner instanceof PsiVariable) {
                PsiVariable psiVariable = (PsiVariable) owner;

                String typeStr = NullyTools.getVariableTypeString(psiVariable);

                PsiType type = psiVariable.getType();
                desc = "@" + annoName + " cannot be used with "
                        + type.getPresentableText() + " " + typeStr + " "
                        + psiVariable.getName();

            } else if (owner instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) owner;
                desc = "@" + annoName + " cannot be used for method "
                        + method.getName() + "'s "
                        + method.getReturnType().getPresentableText()
                        + " return value";

            } else {
                throw new IllegalStateException("element was " + owner);
            }

        } else if (hack instanceof NullyInstrumentedProblem) {
            desc = "@" + NullyInstrumented.class.getSimpleName() + " may only "
                    + "be inserted by Nully";
        } else {
            return;
        }

        int off = problem.getElement().getTextOffset();
        String text = orig.getText();
        String url = orig.getVirtualFile().getUrl();
        int line = StringUtil.offsetToLineNumber(text, off) + 1;
        int col = off - StringUtil.lineColToOffset(text, line - 1, 0) + 1;
        context.addMessage(WARNING, desc, url, line, col);
    }

    private static String getAnnotationName(PsiAnnotation anno) {
        String annoName = anno.getNameReferenceElement().getReferenceName();
        return annoName;
    }

    private static String getWordForType(OverrideType overType) {
        String word;
        if (overType == OVERRIDES) {
            word = "overrides";
        } else if (overType == IMPLEMENTS) {
            word = "implements";
        } else {
            throw new IllegalStateException("overtype == " + overType);
        }
        return word;
    }

    private static String getWarningForNullProblem(NullValueProblem nvProblem) {
        NullProblemType type = nvProblem.getType();
        String desc;
        if (type == NULL_ARGUMENT_FOR_NONNULL_PARAMETER) {
            desc = getWarningForNullArgument(nvProblem.getElement());

        } else if (type == NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
            desc = getWarningForNullAssignment(nvProblem.getElement());

        } else if (type == NULL_RETURN_IN_NONNULL_METHOD) {
            desc = "Returned value may be ilegally null";
        } else {
            desc = null;
        }
        return desc;
    }

    private static String getWarningForNullAssignment(PsiElement element) {
        String desc;
        PsiVariable var = NullyTools.getAssignedVariable(element);
        desc = "Value assigned to '" + var.getName()
                + "' may be illegally null";
        return desc;
    }

    private static String getWarningForNullArgument(PsiElement element) {
        String desc;
        PsiMethod method = NullyTools.getCalledMethod(element);
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element,
                PsiMethodCallExpression.class);

        int num = getIndexOfArgument(call, element);
        String numStr;
        if (num == -1) {
            numStr = "";
        } else {
            numStr = num + " ";
        }

        desc = "Argument " + numStr + "passed to method '" + method.getName()
                + "' may be illegally null";
        return desc;
    }

    private static int getIndexOfArgument(@NonNull PsiMethodCallExpression call,
            @NonNull PsiElement desiredArg) {
        int num = -1;
        int i = 1;
        PsiExpressionList args = call.getArgumentList();
        if (args == null) return -1;
        for (PsiElement arg : args.getExpressions()) {
            if (arg instanceof PsiJavaToken) continue;
            if (PsiTreeUtil.isAncestor(arg, desiredArg, false)) {
                num = i;
                break;
            }
            i++;
        }
        return num;
    }

    private void writeJavaFile(@NonNull PsiJavaFile copy, @NonNull VirtualFile file)
            throws IOException {
        psiDocMgr.commitDocument(psiDocMgr.getDocument(copy));
        Writer writer = null;
        try {
            writer = file.getWriter(this);
            writer.write(copy.getText());
            writer.close();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException okay) { }
            }
        }
    }

    public String getDescription() {
        return "Analyzes nullness for @" + NonNull.class.getSimpleName()
                + " declarations in Java code";
    }

    public boolean validateConfiguration(CompileScope scope) {
        return true;
    }
}