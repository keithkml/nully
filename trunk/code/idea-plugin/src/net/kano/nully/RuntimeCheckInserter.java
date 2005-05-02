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

package net.kano.nully;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import static com.intellij.openapi.compiler.CompilerMessageCategory.WARNING;
import com.intellij.openapi.compiler.JavaSourceTransformingCompiler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import static net.kano.nully.NullProblemType.NULL_ARGUMENT_FOR_NONNULL_PARAMETER;
import static net.kano.nully.NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE;
import static net.kano.nully.NullProblemType.NULL_RETURN_IN_NONNULL_METHOD;
import static net.kano.nully.NullyTools.CLASS_NONNULLTOOLS;
import soot.ValueBox;
import soot.jimple.JimpleBody;
import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Tag;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.Writer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RuntimeCheckInserter implements JavaSourceTransformingCompiler {

    private FileTypeManager ftm = FileTypeManager.getInstance();
    private FileType javaType = ftm.getFileTypeByExtension("java");

    private FileDocumentManager docmgr = FileDocumentManager.getInstance();
    private Project project;
    private PsiManager psiMgr;
    private PsiDocumentManager psiDocMgr;

    public RuntimeCheckInserter(Project project) {
        this.project = project;
        psiMgr = PsiManager.getInstance(project);
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
                            //TODO: progress indicator
                            ProgressIndicator indicator = context.getProgressIndicator();
                            try {
                                indicator.pushState();
                                indicator.setText2("Validating null value constraints");

                                try {
                                    value.set(reallyTransform(context,
                                            output, original));
                                } catch (IncorrectOperationException e) {
                                    //TODO: print errors
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
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

        OffsetsTracker origTracker = new OffsetsTracker(new StringReader(jfile.getText()), true);
        PsiJavaFile copy = NullyTools.getMarkedCopy(jfile);
        MethodInfo info = new MethodInfo();
        PsiPreparer preparer = new PsiPreparer(info);
        preparer.makeMarkedCopy(copy);
        preparer.stripJava5Code(info.getFileCopy());
        MethodAnalyzer analyzer = new MethodAnalyzer();
        analyzer.analyze(info);
        ProblemFinder finder = new NullValueProblemFinder();
        List<PsiNullProblem> problems = finder.findProblems(info);
        for (PsiNullProblem problem : problems) {
            PsiElement el = problem.getElement();
            String oldText = el.getText();
            PsiElementFactory factory = el.getManager().getElementFactory();
            NullProblemType type = problem.getType();
            PsiElement parent = el.getParent();
            PsiExpression newExp = null;
            if (type == NULL_ARGUMENT_FOR_NONNULL_PARAMETER
                    || type == NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
                newExp = factory.createExpressionFromText(CLASS_NONNULLTOOLS
                        + ".checkNonNullValue(" + oldText + ")", parent);
            } else if (type == NULL_RETURN_IN_NONNULL_METHOD) {
                newExp = factory.createExpressionFromText(CLASS_NONNULLTOOLS
                        + ".checkNonNullReturn(" + oldText + ")", parent);
            } else {
                System.err.println("Unknown problem type " + type);
            }

            addCompilerWarning(context, jfile, origTracker, problem);

            if (newExp != null) el.replace(newExp);
        }

        for (PsiClass cls : copy.getClasses()) {
            for (PsiMethod method : cls.getMethods()) {
                addParameterChecks(method);
            }
        }

        String text = copy.getText();
        System.out.println("New:");
        System.out.println(text);
        writeJavaFile(copy, file);
        return true;
    }

    private void addCompilerWarning(CompileContext context,
            PsiJavaFile orig, OffsetsTracker tracker,
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
            if (num == -1) numStr = "";
            else numStr = num + " ";
            desc = "Argument " + numStr + "passed to method '" + method.getName()
                    + "' may be illegally null";

        } else if (type == NullProblemType.NULL_ASSIGNMENT_TO_NONNULL_VARIABLE) {
            PsiVariable var = NullyTools.getAssignedVariable(element);
            desc = "Value assigned to '" + var.getName()
                    + "' may be illegally null";

        } else if (type == NullProblemType.NULL_RETURN_IN_NONNULL_METHOD) {
            desc = "Returned value may be ilegally null";
        } else {
            return;
        }

        int off = getOffsetInFile(orig, NullyTools.getOriginalElement(element));
        context.addMessage(WARNING, desc, orig.getVirtualFile().getUrl(),
                tracker.getLine(off) + 2, tracker.getColumn(off));
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

    private int getOffsetInFile(PsiElement parent, PsiElement child) {
        PsiElement el = child;
        int off = 0;
        while (el != parent && el != null) {
            off += el.getStartOffsetInParent();
            el = el.getParent();
        }
        return off;
    }

    private void printNullInfo(JimpleBody body) {
        for (ValueBox o : (List<ValueBox>) body.getUseBoxes()) {
            SourceLnPosTag pos = null;
            boolean needCheck = false;
            for (Tag tag : (List<Tag>) o.getTags()) {
                if (tag instanceof MayBeNullTag) {
                    needCheck = true;
                } else if (tag instanceof SourceLnPosTag) {
                    pos = (SourceLnPosTag) tag;
                }
            }
            if (needCheck && pos != null) {
                System.out.println("Need null check for " + o + " at "
                        + pos.startLn() + " chs. " + pos.startPos()
                        + "-" + pos.endPos());
            }
        }
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

    private void writeJavaFile(PsiJavaFile copy, VirtualFile file) {
        psiDocMgr.commitDocument(psiDocMgr.getDocument(copy));
        Writer writer = null;
        try {
            writer = file.getWriter(this);
            writer.write(copy.getText());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException okay) { }
            }
        }
    }

    public String getDescription() {
        return "Inserts runtime checks for @NonNull declarations";
    }

    public boolean validateConfiguration(CompileScope scope) { return true; }

}