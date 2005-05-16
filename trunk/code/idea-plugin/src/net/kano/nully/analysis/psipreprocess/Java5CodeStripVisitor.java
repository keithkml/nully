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

package net.kano.nully.analysis.nulls.psipreprocess;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import net.kano.nully.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

    //TODO: strip generics
    //TODO: convert enums to classes

    private static final Map<String,String> s_boxingClasses = new HashMap<String, String>(10);
    private static final Map<String,String> s_unboxingMethods = new HashMap<String, String>(10);
    private static final Set<String> s_numberTypes = new HashSet<String>(10);

    static {
        s_boxingClasses.put("int", "Integer");
        s_boxingClasses.put("short", "Short");
        s_boxingClasses.put("boolean", "Boolean");
        s_boxingClasses.put("byte", "Byte");
        s_boxingClasses.put("float", "Float");
        s_boxingClasses.put("double", "Double");
        s_boxingClasses.put("long", "Long");
        s_boxingClasses.put("char", "Character");

        s_unboxingMethods.put("int", "intValue");
        s_unboxingMethods.put("short", "shortValue");
        s_unboxingMethods.put("boolean", "booleanValue");
        s_unboxingMethods.put("byte", "byteValue");
        s_unboxingMethods.put("float", "floatValue");
        s_unboxingMethods.put("long", "longValue");
        s_unboxingMethods.put("double", "doubleValue");
        // char maps to intValue because int can be cast down to char and
        // there's no charValue method
        s_unboxingMethods.put("char", "intValue");

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

        try {
            transformForeachStatement(statement);
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
        }
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
     * @param foreachStmt a foreach statement
     */
    private static void transformForeachStatement(@NonNull PsiForeachStatement foreachStmt)
            throws IncorrectOperationException {
        CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(foreachStmt.getManager());
        PsiExpression iteratedValue = foreachStmt.getIteratedValue();
        PsiElement parent = foreachStmt.getParent();
        PsiElementFactory factory = foreachStmt.getManager().getElementFactory();
        PsiParameter iterationParam = foreachStmt.getIterationParameter();
        PsiForStatement forStmt;
        PsiCodeBlock forBody;
        String indexVarName = codeStyleManager.suggestUniqueVariableName("i",
                foreachStmt, true);
        String forStatementText;
        String initializerStr;
        PsiType iteratedValueType = iteratedValue.getType();
        String holderName = codeStyleManager.suggestUniqueVariableName("h",
                foreachStmt, true);
        if (iteratedValueType instanceof PsiArrayType) {
            forStatementText = "for(int " + indexVarName + " = 0;"
                    + indexVarName + '<' + holderName + ".length;"
                    + indexVarName + "++) { }";
            initializerStr = iteratedValue.getText() + "[" + indexVarName + "]";

        } else {
            forStatementText = "for(java.util.Iterator " + indexVarName
                    + " = " + holderName + ".iterator();"
                    + indexVarName + ".hasNext();) { }";
            initializerStr = "(" + iterationParam
                    .getType().getPresentableText() + ") " + indexVarName + ".next()";
        }
        forStmt = (PsiForStatement) factory.createStatementFromText(forStatementText.toString(),
                parent);
        PsiExpression initializer = factory.createExpressionFromText(initializerStr,
                foreachStmt.getParent());
        PsiDeclarationStatement varDecl = createVariableDeclaration(iterationParam,
                initializer);
        PsiBlockStatement forBodyBlock = (PsiBlockStatement) forStmt.getBody();
        forBody = forBodyBlock.getCodeBlock();
        forBody.addAfter(varDecl, forBody.getLBrace());
        forBody.addBefore(foreachStmt.getBody(), forBody.getRBrace());
        PsiExpression nullStmt = factory.createExpressionFromText("null",
                foreachStmt);
        PsiDeclarationStatement iteratedHolder
                = factory.createVariableDeclarationStatement(holderName,
                iteratedValueType, nullStmt);
        PsiVariable holderVar = (PsiVariable) iteratedHolder.getDeclaredElements()[0];
        holderVar.getInitializer().replace(iteratedValue.copy());
        foreachStmt.getParent().addBefore(iteratedHolder, foreachStmt);
        foreachStmt.replace(forStmt);
    }

    private static PsiDeclarationStatement createVariableDeclaration(
            @NonNull PsiParameter iterationParam, @NonNull PsiElement initializer)
            throws IncorrectOperationException {
        PsiElementFactory factory = iterationParam.getManager().getElementFactory();
        PsiDeclarationStatement varDecl
                = factory.createVariableDeclarationStatement("x",
                PsiType.BOOLEAN, null);
        PsiVariable var = (PsiVariable) varDecl.getDeclaredElements()[0];
        PsiElement equalsSign = getEqualsSign(var);
        deleteUpTo(var, equalsSign);
        for (PsiElement child : iterationParam.getChildren()) {
            var.addBefore(child.copy(), equalsSign);
        }
        var.getInitializer().replace(initializer);
        return varDecl;
    }

    private static void deleteUpTo(PsiVariable parent, PsiElement upto)
            throws IncorrectOperationException {
        for (PsiElement element : parent.getChildren()) {
            if (element == upto) {
                break;
            } else {
                element.delete();
            }
        }
    }

    private static PsiElement getEqualsSign(@NonNull PsiVariable var) {
        PsiElement el = var.getInitializer();
        while (el != null && !isEqualsSign(el)) el = el.getPrevSibling();
        return el;
    }

    private static boolean isEqualsSign(@NonNull PsiElement el) {
        if (el instanceof PsiJavaToken) {
            PsiJavaToken tok = (PsiJavaToken) el;
            if (tok.getTokenType() == JavaTokenType.EQ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces {@code expression}, if autoboxed, with an equivalent boxed class
     * instantiation.
     *
     * @param expression an expression
     */
    private static void transformBoxing(@NonNull PsiExpression expression) {
        PsiType expressionType = expression.getType();
        if (expressionType == null) {
            return;
        }
        if (!ClassUtils.isPrimitive(expressionType)) {
            return;
        }
        PsiType expectedType =
                ExpectedTypeUtils.findExpectedType(expression);
        if (expectedType == null) {
            return;
        }

        if (ClassUtils.isPrimitive(expectedType)) {
            return;
        }
        String expectedText = expectedType.getPresentableText();
        PsiElementFactory factory = expression.getManager().getElementFactory();
        try {
            PsiCallExpression call;
            if (expectedType.equals(PsiType.BOOLEAN)) {
                call = (PsiCallExpression) factory.createExpressionFromText(
                    "Boolean.valueOf(" + null + ')', expression);
            } else {
                String classToConstruct;
                if (s_boxingClasses.containsValue(expectedText)) {
                    classToConstruct = expectedText;
                } else {
                    classToConstruct = s_boxingClasses.get(expressionType.getPresentableText());
                }
                call = (PsiCallExpression) factory.createExpressionFromText(
                    "new " + classToConstruct + '(' + "null" + ')', expression);
            }
            PsiExpressionList args = call.getArgumentList();
            LOGGER.assertTrue(args != null);
            args.getExpressions()[0].replace(expression.copy());
            expression.replace(call);

        } catch (IncorrectOperationException e) {
            e.printStackTrace();
        }
    }


    /**
     * Transforms {@code expression}, if an autounboxed expression, to an
     * equivalent method call on the boxed class.
     *
     * @param expression an expression
     */
    private static void transformUnboxing(@NonNull PsiExpression expression) {
        PsiType expressionType = expression.getType();
        if(expressionType == null){
            return;
        }
        if(ClassUtils.isPrimitive(expressionType)){
            return;
        }
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression);

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

        String expectedTypeText = expectedType.getPresentableText();
        PsiElementFactory factory = expression.getManager().getElementFactory();
//        PsiClassType characterType = factory.createTypeByFQClassName("java.lang.Character",
//                expression.getResolveScope());
        String unboxMethod = s_unboxingMethods.get(expectedTypeText);
        try {
            PsiTypeCastExpression cast
                    = (PsiTypeCastExpression) factory.createExpressionFromText(
                    "(" + expectedTypeText + ") (null)." + unboxMethod + "()", expression);
            PsiMethodCallExpression call = (PsiMethodCallExpression) cast.getOperand();
            PsiParenthesizedExpression parenth
                    = (PsiParenthesizedExpression) call.getMethodExpression()
                    .getQualifierExpression();
            parenth.getExpression().replace(expression.copy());
            expression.replace(cast);
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
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
        if (called == null || !called.isVarArgs()) return;

        PsiExpressionList args = expression.getArgumentList();
        if (args == null) return;
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

        // create array instantiation code
        StringBuilder expsBuf = new StringBuilder();
        boolean first = true;
        List<PsiExpression> good = new ArrayList<PsiExpression>();
        for (int i = paramsLen - 1; i <= exps.length - 1; i++) {
            if (first) {
                first = false;
            } else {
                expsBuf.append(",");
            }

            expsBuf.append("null");
            good.add((PsiExpression) exps[i].copy());
        }
        String expsStr = expsBuf.toString();

        try {
            args.deleteChildRange(exps[paramsLen-1], exps[expsLen-1]);
            String arrayTypeStr = arrayType.getPresentableText();
            PsiElementFactory factory = args.getManager().getElementFactory();
            PsiNewExpression newArrayExpr
                    = (PsiNewExpression) factory.createExpressionFromText("new "
                    + arrayTypeStr + " { " + expsStr + " }", args);
            PsiExpression[] initializers = newArrayExpr.getArrayInitializer()
                    .getInitializers();
            LOGGER.assertTrue(good.size() == initializers.length);
            Iterator<PsiExpression> goodIt = good.iterator();
            for (PsiExpression initializer : initializers) {
                PsiExpression next = goodIt.next();
                initializer.replace(next);
            }
            args.add(newArrayExpr);
        } catch (IncorrectOperationException e) {
            LOGGER.error("Tried inserting " + expsStr, e);
        }
    }
}
