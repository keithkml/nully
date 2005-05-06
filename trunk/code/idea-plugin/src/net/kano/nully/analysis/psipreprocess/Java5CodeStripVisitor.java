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

package net.kano.nully.analysis.psipreprocess;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import net.kano.nully.NullyTools;
import net.kano.nully.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * When passed to the {@code accept} method of a {@link PsiElement}, converts
 * code in that element and its children which Java 5.0 language features, to
 * equivalent Java 1.4-compatible code.
 */
class Java5CodeStripVisitor extends PsiRecursiveElementVisitor {
    private static final Logger LOGGER
            = Logger.getInstance(Java5CodeStripVisitor.class.getName());

    //TODO: generics
    //TODO: methods in enums or children of enums

    private static final Map<String,String> s_boxingClasses = new HashMap<String, String>(8);
    private static final Map<String,String> s_unboxingMethods = new HashMap<String, String>(8);
    private static final Set<String> s_numberTypes = new HashSet<String>(8);

    static {
        s_boxingClasses.put("int", "Integer");
        s_boxingClasses.put("short", "Short");
        s_boxingClasses.put("boolean", "Boolean");
        s_boxingClasses.put("byte", "Byte");
        s_boxingClasses.put("float", "Float");
        s_boxingClasses.put("double", "Double");
        s_boxingClasses.put("long", "Long");

        s_unboxingMethods.put("int", "intValue");
        s_unboxingMethods.put("short", "shortValue");
        s_unboxingMethods.put("boolean", "booleanValue");
        s_unboxingMethods.put("byte", "byteValue");
        s_unboxingMethods.put("float", "floatValue");
        s_unboxingMethods.put("long", "longValue");
        s_unboxingMethods.put("double", "doubleValue");

        s_numberTypes.add("java.lang.Integer");
        s_numberTypes.add("java.lang.Short");
        s_numberTypes.add("java.lang.Long");
        s_numberTypes.add("java.lang.Double");
        s_numberTypes.add("java.lang.Float");
        s_numberTypes.add("java.lang.Byte");
        s_numberTypes.add("java.lang.Number");
    }

    public void visitModifierList(PsiModifierList list) {
        super.visitModifierList(list);
        removeAnnotations(list);
    }

    public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);

        transformUnboxing(expression);
        transformBoxing(expression);
    }

    public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);

        transformForeachStatement(statement);
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        transformVarargsCall(expression);

    }

    /**
     * Removes all annotations (like {@code @Deprecated}) from the given
     * modifier list.
     *
     * @param list a modifier list
     */
    private static void removeAnnotations(@NonNull PsiModifierList list) {
        for (PsiAnnotation annotation : list.getAnnotations()) {
            try {
                annotation.delete();
            } catch (IncorrectOperationException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Converts a Java 5.0 "foreach" statement to an equivalent for loop.
     *
     * @param statement a foreach statement
     */
    private static void transformForeachStatement(@NonNull PsiForeachStatement statement) {
        final StringBuffer newStatement = new StringBuffer();
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(statement.getManager());
        final PsiExpression iteratedValue = statement.getIteratedValue();
        if(iteratedValue.getType() instanceof PsiArrayType){
            final String index = codeStyleManager.suggestUniqueVariableName("i",
                    statement, true);
            newStatement.append("for(int " + index + " = 0;" + index + '<');
            newStatement.append(iteratedValue.getText());
            newStatement.append(".length;" + index + "++)");
            newStatement.append("{ ");
            newStatement.append(statement.getIterationParameter().getType()
                    .getPresentableText());
            newStatement.append(' ');
            newStatement.append(statement.getIterationParameter()
                    .getName());
            newStatement.append(" = ");
            newStatement.append(iteratedValue.getText());
            newStatement.append('[' + index + "];");
            final PsiStatement body = statement.getBody();
            appendBody(body, newStatement);
            newStatement.append('}');
        } else{

            final String iterator =  codeStyleManager
                    .suggestUniqueVariableName("it", statement, true);
            final String typeText = statement.getIterationParameter()
                    .getType().getPresentableText();
            newStatement.append("for(java.util.Iterator");
            newStatement.append(" " + iterator + " = ");
            newStatement.append(iteratedValue.getText());
            newStatement.append(".iterator();" + iterator + ".hasNext();)");
            newStatement.append('{');
            newStatement.append(typeText);
            newStatement.append(' ');
            newStatement.append(statement.getIterationParameter()
                    .getName());
            newStatement.append(" = (" + typeText + ") ");
            newStatement.append(iterator + ".next();");

            final PsiStatement body = statement.getBody();
            appendBody(body, newStatement);
            newStatement.append('}');
        }
        NullyTools.replaceStatement(statement, newStatement.toString());
    }

    private static void appendBody(final PsiStatement body,
            final StringBuffer newStatement) {
        if(body instanceof PsiBlockStatement){
            final PsiCodeBlock block =
                    ((PsiBlockStatement) body).getCodeBlock();
            final PsiElement[] children =
                    block.getChildren();
            for(int i = 1; i < children.length - 1; i++){
                //skip the braces
                newStatement.append(children[i].getText());
            }
        } else{
            newStatement.append(body.getText());
        }
    }

    /**
     * Replaces {@code expression}, if autoboxed, with an equivalent boxed class
     * instantiation.
     *
     * @param expression an expression
     */
    private static void transformBoxing(@NonNull PsiExpression expression) {
        PsiType expType = expression.getType();
        final PsiType expressionType = expType;
        if(expressionType == null){
            return;
        }
        if(!ClassUtils.isPrimitive(expressionType)){
            return;
        }
        final PsiType expectedType =
                ExpectedTypeUtils.findExpectedType(expression);
        if(expectedType == null){
            return;
        }

        if(ClassUtils.isPrimitive(expectedType)){
            return;
        }
        final String newExpression;
        String expText = expression.getText();
        String expectedText = expectedType.getPresentableText();
        if (expectedType.equals(PsiType.BOOLEAN)) {
            newExpression = "Boolean.valueOf(" + expText + ')';
        } else if (s_boxingClasses.containsValue(expectedText)) {
            final String classToConstruct = expectedText;
            newExpression = "new " + classToConstruct + '(' + expText + ')';
        } else {
            final String classToConstruct = s_boxingClasses.get(expType.getPresentableText());
            newExpression = "new " + classToConstruct + '(' + expText + ')';
        }
        NullyTools.replaceExpression(expression, newExpression);

    }

    /**
     * Transforms {@code expression}, if an autounboxed expression, to an
     * equivalent method call on the boxed class.
     *
     * @param expression an expression
     */
    private static void transformUnboxing(@NonNull PsiExpression expression) {
        final PsiType expressionType = expression.getType();
        if(expressionType == null){
            return;
        }
        if(ClassUtils.isPrimitive(expressionType)){
            return;
        }
        final PsiType expectedType =
                ExpectedTypeUtils.findExpectedType(expression);

        if(expectedType == null){
            return;
        }
        if(!ClassUtils.isPrimitive(expectedType)){
            return;
        }

        if(expressionType.getArrayDimensions() > 0){
            // a horrible hack to get around what happens when you pass an array
            // to a vararg expression
            return;
        }

        final String expectedTypeText = expectedType.getCanonicalText();
        final String typeText = expressionType.getCanonicalText();
        final String expressionText = expression.getText();
        final String boxClassName = s_unboxingMethods.get(expectedTypeText);
        if(TypeUtils.typeEquals("java.lang.Boolean", expressionType)){
            NullyTools.replaceExpression(expression,
                    expressionText + '.' + boxClassName + "()");
        } else if(s_numberTypes.contains(typeText)){
            NullyTools.replaceExpression(expression,
                    expressionText + '.' + boxClassName + "()");
        } else{
            NullyTools.replaceExpression(expression,
                    "((Number)" + expressionText + ")." + boxClassName + "()");
        }
    }

    /**
     * Converts the given method call, if a "varargs" call, to an equivalent
     * call using a newly instantiated array in place of the multiple arguments.
     *
     * @param expression a method call
     */
    private static void transformVarargsCall(@NonNull PsiMethodCallExpression expression) {
        PsiMethod called = expression.resolveMethod();
        if (!called.isVarArgs()) return;

        PsiExpressionList args = expression.getArgumentList();
        PsiExpression[] exps = args.getExpressions();
        PsiParameter[] params = called.getParameterList().getParameters();
        int expsLen = exps.length;
        int paramsLen = params.length;
        PsiParameter varParam = params[paramsLen - 1];
        PsiType varParamType = varParam.getType();
        if (!(varParamType instanceof PsiEllipsisType)) return;

        PsiEllipsisType type = (PsiEllipsisType) varParamType;
        PsiArrayType arrayType = new PsiArrayType(type.getComponentType());
        if (expsLen == paramsLen) {
            PsiExpression varArg = exps[expsLen - 1];
            if (arrayType.isAssignableFrom(varArg.getType())) {
                return;
            }
        }

        StringBuilder expsBuf = new StringBuilder();
        boolean first = true;
        for (int i = paramsLen - 1; i < exps.length - 1; i++) {
            if (first) first = false;
            else expsBuf.append(", ");

            PsiExpression exp = exps[i];
            expsBuf.append(exp.getText());
        }
        String expsStr = expsBuf.toString();

        try {
            args.deleteChildRange(exps[paramsLen-1], exps[expsLen-1]);
            String arrayTypeStr = arrayType.getCanonicalText();
            PsiElementFactory factory = args.getManager().getElementFactory();
            args.add(factory.createExpressionFromText("new "
                    + arrayTypeStr + " { " + expsStr + " }", args));
        } catch (IncorrectOperationException e) {
            LOGGER.error("Tried inserting " + expsStr, e);
        }
    }
}
