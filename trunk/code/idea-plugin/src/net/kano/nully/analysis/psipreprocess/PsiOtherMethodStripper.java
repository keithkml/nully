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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import net.kano.nully.NullyTools;
import net.kano.nully.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes all code from the file which contains the given method, except for
 * the given method. This class removes the following from all classes in the
 * file:
 * <ul>
 * <li>Constructors</li>
 * <li>Other methods</li>
 * <li>Field initializers</li>
 * <li>Class / instance initializers</li>
 * </ul>
 * The stripped code is guaranteed to be valid Java code, as all necessary
 * declarations are kept, so all references in the desired method are still
 * valid. Required {@code super} calls in constructors are updated to ensure
 * that the code compiles.
 * <br /><br />
 * Any classes which are not outer classes of the given method's containing
 * class are removed completely.
 * <br /><br />
 * An instance of this class should be passed to a {@code PsiElement}'s
 * {@code visit} method only once. Otherwise the stored list of stripped methods
 * will be inaccurate.
 */
public class PsiOtherMethodStripper extends PsiRecursiveElementVisitor {
    private static final Logger LOGGER
            = Logger.getInstance(PsiOtherMethodStripper.class.getName());

    //TODO: strip constructors
    //TODO: strip inheritance

    /** A list of classes which must not be deleted. */
    private final List<PsiClass> okayClasses;
    /** The method to keep. */
    private final PsiMethod methodCopy;
    /** The names of classes which were stripped. */
    private final List<String> strippedClassesNames = new ArrayList<String>();

    /**
     * Creates a new PSI other-method stripper.
     *
     * @param okayClasses a list of classes which should not be stripped
     * @param methodCopy the method to keep
     */
    public PsiOtherMethodStripper(@NonNull List<PsiClass> okayClasses, @NonNull PsiMethod methodCopy) {
        this.okayClasses = okayClasses;
        this.methodCopy = methodCopy;
    }

    public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);

        if (!okayClasses.contains(aClass)) stripClass(aClass);
    }

    public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        if (method != methodCopy) stripMethod(method);
    }

    public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        try {
            initializer.delete();
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
        }
    }

    public void visitField(PsiField field) {
        super.visitField(field);

        transformField(field);
    }

    private static void transformField(@NonNull PsiField field) {
        // the field shouldn't be final so the compiler doesn't complain
        // about uninitialized values
        try {
            field.getModifierList().setModifierProperty("final", false);
        } catch (IncorrectOperationException e) {
            LOGGER.error(NullyTools.getQualifiedMemberName(field), e);
            return;
        }

        PsiElementFactory factory = field.getManager().getElementFactory();
        String defaultValue = NullyTools.getDefaultValue(field.getType());
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) return;
        try {
            PsiExpression defEl = factory.createExpressionFromText(
                    defaultValue, initializer);
            initializer.replace(defEl);
        } catch (IncorrectOperationException e) {
            LOGGER.error(NullyTools.getQualifiedMemberName(field), e);
        }
    }

    /**
     * Deletes the body of the given method and replaces it with a dummy return
     * statement. The dummy return statement returns the default value for the
     * method's return type.
     *
     * @param method a method
     */
    private void stripMethod(@NonNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        // abstract methods are okay
        if (body == null) return;

        // delete the method's statements
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
            try {
                body.deleteChildRange(statements[0],
                        statements[statements.length - 1]);
            } catch (IncorrectOperationException e) {
                LOGGER.error(NullyTools.getQualifiedMemberName(method), e);
            }
        }

        // insert the dummy return value
        String val = NullyTools.getDefaultValue(method.getReturnType());
        try {
            PsiElementFactory factory = body.getManager()
                    .getElementFactory();
            body.addAfter(factory.createStatementFromText("return "
                    + val + ";", body),
                    body.getLBrace());
        } catch (IncorrectOperationException e) {
            LOGGER.error(NullyTools.getQualifiedMemberName(method), e);
        }
    }

    /**
     * Deletes the given class's declaration and adds its name to the list of
     * stripped classes.
     *
     * @param aClass a class
     */
    private void stripClass(@NonNull PsiClass aClass) {
        String actualName = NullyTools.getRealName(aClass);
        try {
            aClass.delete();
            strippedClassesNames.add(actualName);
        } catch (IncorrectOperationException e) {
            LOGGER.error(actualName, e);
        }
    }

    /**
     * Returns the
     * @return
     */
    public @NonNull List<String> getStrippedClassesNames() {
        return strippedClassesNames;
    }
}
