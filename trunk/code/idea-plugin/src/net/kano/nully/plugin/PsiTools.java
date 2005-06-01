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

package net.kano.nully.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.annotations.NonNull;
import soot.Type;
import soot.BooleanType;
import soot.ByteType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.ShortType;
import soot.VoidType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class PsiTools {
    private static final Logger LOGGER = Logger.getInstance(PsiTools.class.getName());
    private static Map<PsiType,Type> staticSootTypes = new HashMap<PsiType, Type>();
    static {
        PsiTools.staticSootTypes.put(PsiType.BOOLEAN, BooleanType.v());
        PsiTools.staticSootTypes.put(PsiType.BYTE, ByteType.v());
        PsiTools.staticSootTypes.put(PsiType.CHAR, BooleanType.v());
        PsiTools.staticSootTypes.put(PsiType.DOUBLE, DoubleType.v());
        PsiTools.staticSootTypes.put(PsiType.FLOAT, FloatType.v());
        PsiTools.staticSootTypes.put(PsiType.INT, IntType.v());
        PsiTools.staticSootTypes.put(PsiType.LONG, LongType.v());
        PsiTools.staticSootTypes.put(PsiType.SHORT, ShortType.v());
        PsiTools.staticSootTypes.put(PsiType.VOID, VoidType.v());
    }


    private PsiTools() { }

    public static PsiVariable getReferencedVariable(PsiElement el) {
        if (el instanceof PsiVariable) return (PsiVariable) el;
        PsiElement parent = el.getParent();
        if (parent instanceof PsiVariable) {
            return (PsiVariable) parent;
        }
        PsiReferenceExpression ref = PsiTreeUtil.getParentOfType(el,
                        PsiReferenceExpression.class, false);
        PsiVariable variable = null;
        if (ref != null) {
            PsiElement referenced = ref.resolve();
            if (referenced instanceof PsiVariable) {
                variable = (PsiVariable) referenced;
            }
        }
        return variable;
    }

    public static void markCopiedElements(PsiElement orig, PsiElement copy,
            Key<PsiElement> originalKey, Key<PsiElement> copyKey) {
        copy.putCopyableUserData(originalKey, orig);
        orig.putCopyableUserData(copyKey, copy);
        PsiElement[] origchs = orig.getChildren();
        PsiElement[] copychs = copy.getChildren();
        for (int i = 0; i < copychs.length; i++) {
            PsiElement origch = origchs[i];
            PsiElement copych = copychs[i];
            if (!origch.getClass().equals(copych.getClass())) {
                throw new IllegalArgumentException("not a copy");
            }
            markCopiedElements(origch, copych, originalKey, copyKey);
        }
    }

    public static PsiJavaFile getMarkedCopy(PsiJavaFile jfile,
            Key<PsiElement> originalKey, Key<PsiElement> copyKey) {
        PsiJavaFile fileCopy = (PsiJavaFile) jfile.copy();
        markCopiedElements(jfile, fileCopy, originalKey, copyKey);
        return fileCopy;
    }

    public static PsiVariable getAssignedVariable(PsiElement element) {
        PsiVariable var = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
        if (var == null) {
            PsiAssignmentExpression assn = PsiTreeUtil.getParentOfType(element,
                    PsiAssignmentExpression.class);
            if (assn != null) {
                PsiReferenceExpression ref = (PsiReferenceExpression) assn.getLExpression();
                var = (PsiVariable) ref.resolve();
            }
        }
        return var;
    }

    public static PsiMethod getCalledMethod(@NonNull PsiElement element) {
        PsiMethodCallExpression methCallExp = getMethodCallParent(element);
        if (methCallExp == null) return null;
        return methCallExp.resolveMethod();
    }

    public static PsiMethodCallExpression getMethodCallParent(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element,
                PsiMethodCallExpression.class, false);
    }

    public static PsiMethodCallExpression getMethodCallChild(@NonNull PsiExpression initializer) {
        PsiMethodCallExpression call;
        if (initializer instanceof PsiMethodCallExpression) {
            call = (PsiMethodCallExpression) initializer;
        } else {
            call = PsiTreeUtil.getChildOfType(initializer,
                    PsiMethodCallExpression.class);
        }
        return call;
    }

    public static String getJavaNameForClass(@NonNull PsiClass cls) {
        if (PsiUtil.isLocalOrAnonymousClass(cls)) {
            throw new IllegalArgumentException("local or anonymous: " + cls);
        }
        PsiClass outer = cls.getContainingClass();
        if (outer == null) {
            return cls.getQualifiedName();
        } else {
            return getJavaNameForClass(outer) + "$" + cls.getName();
        }
    }

    public static void replaceStatement(PsiStatement statement, String newStatement) {
        try {
            final PsiManager psiManager = statement.getManager();
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiStatement newExp = factory.createStatementFromText(newStatement, null);
            final PsiElement replacementExp = statement.replace(newExp);
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(replacementExp);
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
        }
    }

    public static void replaceExpression(PsiExpression expression, String newExpression) {
        try {
            final PsiManager psiManager = expression.getManager();
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiExpression newExp = factory.createExpressionFromText(newExpression, null);
            final PsiElement replacementExp = expression.replace(newExp);
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(replacementExp);
        } catch (IncorrectOperationException e) {
            LOGGER.error(e);
        }
    }

    public static String getDefaultValue(@NonNull PsiType returnType) {
        String val;
        if (returnType.equals(PsiType.BOOLEAN)) {
            val = "false";
        } else if (returnType.equals(PsiType.VOID)) {
            val = "";
        } else if (returnType.isAssignableFrom(PsiType.BYTE)) {
            val = "(byte) 0";
        } else {
            val = "null";
        }
        return val;
    }

    public static @NonNull String getQualifiedMemberName(@NonNull PsiMember method) {
        return method.getContainingClass().getName() + "." + method.getName();
    }

    public static ImportantSuperMethodInfo getBadOverrideInfo(@NonNull PsiMethod method) {
        List<PsiMethod> bad = new ArrayList<PsiMethod>();
        for (PsiMethod superm : PsiSuperMethodUtil.findSuperMethods(method)) {
            if (NullyTools.hasNonNullAnnotation(superm)) bad.add(superm);
        }

        return getImportantSuperMethod(method, bad);
    }

    public static @NonNull ImportantSuperMethodInfo getImportantSuperMethod(@NonNull PsiMethod method,
            @NonNull Collection<PsiMethod> supers) {
        if (supers.isEmpty()) throw new IllegalArgumentException("supers is empty");
        PsiMethod importantSuper = null;
        OverrideType overrideType = null;

        for (PsiMethod superm : supers) {
            if (!superm.getContainingClass().isInterface()) {
                // we found the real method
                overrideType = OverrideType.OVERRIDES;
                importantSuper = superm;
                break;
            }
        }
        if (importantSuper == null) {
            // this method is not implemented in any superclasses
            overrideType = OverrideType.IMPLEMENTS;
            if (supers.size() == 1) {
                importantSuper = supers.iterator().next();

            } else {
                List<PsiMethod> superList = new ArrayList<PsiMethod>(supers);
                Collections.sort(superList, new MethodNameComparator());
                Set<PsiMethod> badInImplementsList
                        = getBadClassesInImplementsList(method, supers);

                if (!badInImplementsList.isEmpty()) {
                    // some bad superclass is in the implements list
                    if (badInImplementsList.size() == 1) {
                        importantSuper = badInImplementsList.iterator().next();
                    }
                }
            }
        }
        return new ImportantSuperMethodInfo(overrideType, importantSuper);
    }

    private static Set<PsiMethod> getBadClassesInImplementsList(PsiMethod method,
            Collection<PsiMethod> bad) {
        Set<PsiClass> badSuperclasses = new HashSet<PsiClass>();
        for (PsiMethod psiMethod : bad) {
            badSuperclasses.add(psiMethod.getContainingClass());
        }
        Set<PsiClass> inImplementsList = new HashSet<PsiClass>();

        for (PsiClassType itype : method.getContainingClass()
                .getImplementsListTypes()) {
            inImplementsList.add(itype.resolve());
        }

        badSuperclasses.retainAll(inImplementsList);

        Set<PsiMethod> badMethodsInImplementsList = new TreeSet<PsiMethod>(
                new MethodNameComparator());
        for (PsiMethod psiMethod : bad) {
            if (badSuperclasses.contains(psiMethod.getContainingClass())) {
                badMethodsInImplementsList.add(psiMethod);
            }
        }
        return badMethodsInImplementsList;
    }

    public static String getVariableTypeString(PsiVariable psiVariable) {
        String typeStr;
        if (psiVariable instanceof PsiParameter) {
            typeStr = "parameter";
        } else if (psiVariable instanceof PsiLocalVariable) {
            typeStr = "variable";
        } else {
            typeStr = "variable";
            LOGGER.error("PsiVariable was " + psiVariable.getClass().getName());
        }
        return typeStr;
    }
//
//    public static Type getSootType(PsiType psiType) {
//        Type out = staticSootTypes.get(psiType);
//        if (out == null) {
//            if (psiType instanceof PsiArrayType) {
//                PsiArrayType type = (PsiArrayType) psiType;
//                return ArrayType.v(getSootType(type.getComponentType()),
//                        type.getArrayDimensions());
//            } else if (psiType instanceof PsiClassType) {
//                PsiClassType type = (PsiClassType) psiType;
//                PsiClass resolved = type.resolve();
//                String fqn;
//                if (resolved == null) {
//                    fqn = type.getClassName();
//                } else {
//                    fqn = resolved.getQualifiedName();
//                }
//                return RefType.v(fqn);
//            } else {
//                throw new IllegalArgumentException("Cannot convert to Soot type:"
//                        + psiType);
//            }
//        }
//        return out;
//    }

    public static class MethodNameComparator implements Comparator<PsiMethod> {
        public int compare(PsiMethod method, PsiMethod method1) {
            String name1 = getQualifiedMemberName(method);
            String name2 = getQualifiedMemberName(method1);
            return name1.compareToIgnoreCase(name2);
        }
    }
}
