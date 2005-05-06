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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.BadOverrideInfo;
import net.kano.nully.NonNull;
import net.kano.nully.NullyTools;
import net.kano.nully.NonNullTools;
import static net.kano.nully.NullyTools.METHOD_CHECKNONNULLRETURN;
import net.kano.nully.analysis.CodeAnalyzer;
import net.kano.nully.analysis.AnalysisInfo;
import net.kano.nully.analysis.NullProblemType;
import static net.kano.nully.analysis.NullProblemType.INVALID_NONNULL_OVERRIDE;
import static net.kano.nully.analysis.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.analysis.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.analysis.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import net.kano.nully.analysis.NullValueProblemFinder;
import net.kano.nully.analysis.OtherProblemFinder;
import net.kano.nully.analysis.ProblemFinder;
import net.kano.nully.analysis.PsiNullProblem;
import net.kano.nully.analysis.psipreprocess.PreparerForSoot;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RuntimeCheckInserter implements JavaSourceTransformingCompiler {
    //TODO: insert @NullyInstrumented annotation
    private static final Logger LOGGER = Logger.getInstance(RuntimeCheckInserter.class.getName());

    private FileTypeManager ftm = FileTypeManager.getInstance();
    private FileType javaType = ftm.getFileTypeByExtension("java");

    private FileDocumentManager docmgr = FileDocumentManager.getInstance();
    private PsiDocumentManager psiDocMgr;

    public RuntimeCheckInserter(Project project) {
        psiDocMgr = PsiDocumentManager.getInstance(project);
    }

    public boolean isTransformable(VirtualFile file) {
        return file.getFileType().equals(javaType);
    }

    public boolean transform(final CompileContext context,
            final VirtualFile output, final VirtualFile original) {
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
                                indicator.setText("Validating null value constraints for " + original.getName());

                                try {
                                    value.set(reallyTransform(context,
                                            output, original));
                                } catch (Exception e) {
                                    context.addMessage(ERROR,
                                            "Error while running null checks in "
                                                    + "file " + original.getName(), original.getUrl(), -1, -1);
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
        } catch (InterruptedException e) {
            e.printStackTrace();

        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean reallyTransform(CompileContext context,
            VirtualFile file, VirtualFile original)
            throws IncorrectOperationException, IOException {
        Document doc = docmgr.getDocument(original);
        PsiFile psiFile = psiDocMgr.getPsiFile(doc);
        if (!(psiFile instanceof PsiJavaFile)) return false;
        PsiJavaFile jfile = (PsiJavaFile) psiFile;

//        NonnullChecker checker = new NonnullChecker(false);
//        psiFile.accept(checker);
//
//        if (!checker.shouldProcess()) return false;

        PsiJavaFile copy = NullyTools.getMarkedCopy(jfile);

        AnalysisInfo info = new AnalysisInfo();

        PreparerForSoot preparer = new PreparerForSoot(info);
        preparer.makeMarkedCopy(copy);
        preparer.stripJava5Code(info.getFileCopy());

        CodeAnalyzer analyzer = new CodeAnalyzer();
        analyzer.analyze(info);

        ProblemFinder finder = new NullValueProblemFinder();
        List<PsiNullProblem> problems = new ArrayList<PsiNullProblem>();
        problems.addAll(finder.findProblems(info));
        ProblemFinder otherFinder = new OtherProblemFinder();
        problems.addAll(otherFinder.findProblems(info));

        // this must be done before transforming the code, before the PsiElements mean nothing!
        addCompilerWarnings(context, jfile, problems);

        insertDetectedPossibleNullChecks(problems);

        copy.accept(new NonNullMethodCallCheckInserter());

        copy.accept(new PsiRecursiveElementVisitor() {
            public void visitMethod(PsiMethod method) {
                try {
                    addParameterChecks(method);
                } catch (IncorrectOperationException e) {
                    e.printStackTrace();
                }
            }
        });

        String text = copy.getText();
        System.out.println("New:");
        System.out.println(text);
        writeJavaFile(copy, file);
        return true;
    }

    private void insertDetectedPossibleNullChecks(List<PsiNullProblem> problems)
            throws IncorrectOperationException {
        for (PsiNullProblem problem : problems) {
            PsiElement el = problem.getElement();
            String oldText = el.getText();
            PsiElementFactory factory = el.getManager().getElementFactory();
            NullProblemType type = problem.getType();
            PsiElement parent = el.getParent();
            PsiExpression newExp = null;
            if (type == NULL_ARGUMENT_FOR_NONNULL_PARAMETER
                    || type == NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
                newExp = factory.createExpressionFromText(
                        getUnexpectedNullValueCheckString(oldText), parent);
            } else if (type == NULL_RETURN_IN_NONNULL_METHOD) {
                newExp = factory.createExpressionFromText(NonNullTools.class.getName()
                        + "." + METHOD_CHECKNONNULLRETURN + "("
                        + oldText + ")", parent);
            } else {
                LOGGER.debug("Unknown problem type " + type);
            }

            if (newExp != null) el.replace(newExp);
        }
    }

    private void addCompilerWarnings(CompileContext context, PsiJavaFile jfile,
            List<PsiNullProblem> problems) {
        for (PsiNullProblem problem : problems) {
            addCompilerWarning(context, jfile, problem);
        }
    }

    private String getUnexpectedNullValueCheckString(String oldText) {
        return NonNullTools.class.getName() + "."
                + NullyTools.METHOD_CHECKNONNULLVALUE + "(" + oldText + ")";
    }

    private void addCompilerWarning(CompileContext context,
            PsiJavaFile orig,
            PsiNullProblem problem) {
        PsiElement element = problem.getElement();
        NullProblemType type = problem.getType();
        String desc;
        if (type == NULL_ARGUMENT_FOR_NONNULL_PARAMETER) {
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

        } else if (type == NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
            PsiVariable var = NullyTools.getAssignedVariable(element);
            desc = "Value assigned to '" + var.getName()
                    + "' may be illegally null";

        } else if (type == NullProblemType.NULL_RETURN_IN_NONNULL_METHOD) {
            desc = "Returned value may be ilegally null";
        } else if (type == INVALID_NONNULL_OVERRIDE) {
            BadOverrideInfo badOverrideInfo = NullyTools.getBadOverrideInfo(problem);
            BadOverrideInfo.OverrideType overType = badOverrideInfo.getType();
            String word;
            if (overType == BadOverrideInfo.OverrideType.OVERRIDES) word = "overrides";
            else if (overType == BadOverrideInfo.OverrideType.IMPLEMENTS) word = "implements";
            else word = null;
            PsiMethod overridden = badOverrideInfo.getOverridden();
            desc = "Method " + ((PsiMethod) element).getName() + " illegal "
                    + word + " " + NullyTools.getQualifiedMemberName(overridden)
                    + " without matching @" + NonNull.class.getSimpleName() + " declaration";
        } else {
            return;
        }

        int off = element.getTextOffset();
        String text = orig.getText();
        int line = StringUtil.offsetToLineNumber(text, off) + 1;
        context.addMessage(WARNING, desc, orig.getVirtualFile().getUrl(),
                line, off - StringUtil.lineColToOffset(text, line-1, 0) + 1);
    }

    private int getIndexOfArgument(PsiMethodCallExpression call,
            PsiElement element) {
        int num = -1;
        int i = 1;
        for (PsiElement arg : call.getArgumentList().getExpressions()) {
            if (arg instanceof PsiJavaToken) continue;
            if (PsiTreeUtil.isAncestor(arg, element, false)) {
                num = i;
                break;
            }
            i++;
        }
        return num;
    }

    private void addParameterChecks(PsiMethod method)
            throws IncorrectOperationException {
        PsiElementFactory factory = method.getManager().getElementFactory();
        PsiParameter[] params = method.getParameterList().getParameters();
        PsiCodeBlock body = method.getBody();
        PsiElement addAfter = body.getLBrace();
        for (int pi = 0; pi < params.length; pi++) {
            PsiParameter param = params[pi];
            if (!NullyTools.hasNonNullAnnotation(param)) continue;
            
            PsiElement el = factory.createStatementFromText(
                    makeParamCheckString(param, pi), body);
            body.addAfter(el, addAfter);
            for (int i = 0; i < 1000; i++) {
                PsiElement nextSibling = el.getNextSibling();
                if (nextSibling instanceof PsiWhiteSpace) {
                    nextSibling.delete();
                } else {
                    break;
                }
            }

            addAfter = el;
        }
    }

    private String makeParamCheckString(PsiParameter param, int number) {
        return "if (" + param.getName() + " == null) {"
                + "throw new net.kano.nully.NullParameterException(\""
                + param.getName() + "\", " + (number + 1) + ");"
                + "}";
    }

    private void writeJavaFile(PsiJavaFile copy, VirtualFile file)
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

    public boolean validateConfiguration(CompileScope scope) { return true; }


    private class NonNullMethodCallCheckInserter extends PsiRecursiveElementVisitor {
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            PsiMethod calledMethod = expression.resolveMethod();
            if (NullyTools.hasNonNullAnnotation(calledMethod)
                    && !NullyTools.isNonnullCheckMethod(calledMethod)
                    && !parentIsNonnullCheckMethod(expression)) {
                PsiElementFactory factory = expression.getManager().getElementFactory();
                String newString = getUnexpectedNullValueCheckString(expression.getText());
                try {
                    PsiExpression newExp = factory.createExpressionFromText(newString,
                            expression.getParent());
                    expression.replace(newExp);
                } catch (IncorrectOperationException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean parentIsNonnullCheckMethod(PsiMethodCallExpression expression) {
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression parentCall = (PsiMethodCallExpression) parent;
                return NullyTools.isNonnullCheckMethod(parentCall.resolveMethod());
            }
            return false;
        }
    }
}