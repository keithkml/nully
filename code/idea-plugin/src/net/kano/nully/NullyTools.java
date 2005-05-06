
package net.kano.nully;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import net.kano.nully.analysis.AnalysisInfo;
import net.kano.nully.analysis.PsiNullProblem;
import net.kano.nully.analysis.soot.MayBeNullTag;
import soot.SootMethod;
import soot.ValueBox;
import soot.jimple.JimpleBody;
import soot.tagkit.Host;
import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class NullyTools {
    public static final Key<PsiElement> KEY_COPY = Key.create("NullyCopy");
    public static final Key<PsiElement> KEY_ORIGINAL = Key.create("NullyOrig");

    public static final String METHOD_CHECKNONNULLRETURN = "checkNonNullReturn";
    public static final String METHOD_CHECKNONNULLVALUE = "checkNonNullValue";
    public static final Object METHOD_CHECKNONNULLPARAM = "checkNonNullParameter";

    private static final Logger LOGGER = Logger.getInstance(NullyTools.class.getName());

    private NullyTools() { }

    public static <E extends UserDataHolder> E getOriginalElement(@NonNull E argel) {
        return (E) argel.getUserData(KEY_ORIGINAL);
    }

    public static <E extends UserDataHolder> E getCopiedElement(@NonNull E el) {
        return (E) el.getUserData(KEY_COPY);
    }

    public static PsiVariable getReferencedVariable(PsiElement el) {
        PsiElement parent = el.getParent();
        if (parent instanceof PsiVariable) {
            return (PsiVariable) parent;
        }
        //TODO: test fixed  usage of PsiReference -> PsiReferenceExpression
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

    public static void markCopiedElements(PsiElement orig, PsiElement copy) {
        copy.putUserData(KEY_ORIGINAL, orig);
        orig.putUserData(KEY_COPY, copy);
        PsiElement[] origchs = orig.getChildren();
        PsiElement[] copychs = copy.getChildren();
        for (int i = 0; i < copychs.length; i++) {
            PsiElement origch = origchs[i];
            PsiElement copych = copychs[i];
            if (!origch.getClass().equals(copych.getClass())) {
                throw new IllegalArgumentException("not a copy");
            }
            markCopiedElements(origch, copych);
        }
    }

    public static void printNullInfo(JimpleBody body) {
        List<ValueBox> useBoxes = body.getUseBoxes();
        for (ValueBox o : useBoxes) {
            List<Tag> tags = o.getTags();
            SourceLnPosTag pos = null;
            boolean needCheck = false;
            for (Tag tag : tags) {
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

    public static PsiJavaFile getMarkedCopy(PsiJavaFile jfile) {
        PsiJavaFile fileCopy = (PsiJavaFile) jfile.copy();

        markCopiedElements(jfile, fileCopy);
        return fileCopy;
    }

    public static PsiMethod getPsiMethod(AnalysisInfo info, SootMethod method) {
        OffsetsTracker tracker = info.getTracker();
        PsiJavaFile fileCopy = info.getFileCopy();
        SourceLnPosTag srcTag = getSourceTag(method);
        if (srcTag == null) return null;
        PsiElement el = tracker.getElementAtPosition(fileCopy, srcTag);
        if (el == null) return null;
        return PsiTreeUtil.getParentOfType(el, PsiMethod.class);
    }

    public static SourceLnPosTag getSourceTag(Host host) {
        return (SourceLnPosTag) host.getTag("SourceLnPosTag");
    }

    public static PsiVariable getAssignedVariable(PsiElement element) {
        PsiVariable var = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
        if (var == null) {
            PsiAssignmentExpression assn = PsiTreeUtil.getParentOfType(element,
                    PsiAssignmentExpression.class);
            PsiReferenceExpression ref = (PsiReferenceExpression) assn.getLExpression();
            var = (PsiVariable) ref.resolve();
        }
        return var;
    }

    public static PsiMethod getCalledMethod(PsiElement element) {
        PsiMethodCallExpression methCallExp = PsiTreeUtil.getParentOfType(element,
                PsiMethodCallExpression.class);
        PsiMethod method = (PsiMethod) methCallExp.getMethodExpression().resolve();
        return method;
    }

    public static boolean hasNonNullAnnotation(PsiModifierListOwner owner) {
        PsiModifierList mods = owner.getModifierList();
        PsiAnnotation anno = mods.findAnnotation(NonNull.class.getName());
        return anno != null;
    }

    public static PsiMethodCallExpression getMethodCallChild(PsiExpression initializer) {
        PsiMethodCallExpression call;
        if (initializer instanceof PsiMethodCallExpression) {
            call = (PsiMethodCallExpression) initializer;
        } else {
            call = PsiTreeUtil.getChildOfType(initializer,
                    PsiMethodCallExpression.class);
        }
        return call;
    }

    public static boolean hasMayBeNullTag(Host host) {
        return host.hasTag("MayBeNull");
    }

    public static int getOffset(OffsetsTracker tracker,
            SourceLnPosTag argSrcTag) {
        return tracker.getOffset(argSrcTag.startLn(), argSrcTag.startPos());
    }

    public static String getRealName(PsiClass cls) {
        PsiClass outer = cls.getContainingClass();
        if (outer == null) {
            return cls.getQualifiedName();
        } else {
            return getRealName(outer) + "$" + cls.getName();
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

    public static String getDefaultValue(PsiType returnType) {
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

    public static boolean isNonnullCheckMethod(PsiMethod method) {
        PsiClass cls = method.getContainingClass();
        String name = method.getName();
        //TODO: request intention to shorten static imported name
        return cls.getQualifiedName().equals(NonNullTools.class.getName())
                && (name.equals(NullyTools.METHOD_CHECKNONNULLRETURN)
                || name.equals(METHOD_CHECKNONNULLVALUE)
                || name.equals(METHOD_CHECKNONNULLPARAM));
    }

    public static String getQualifiedMemberName(PsiMember method) {
        return method.getContainingClass().getName() + "." + method.getName();
    }

    public static BadOverrideInfo getBadOverrideInfo(PsiNullProblem psiProblem) {
        PsiMethod method = (PsiMethod) psiProblem.getElement();
        PsiMethod badOverride = null;
        BadOverrideInfo.OverrideType overrideType = null;

        List<PsiMethod> bad = new ArrayList<PsiMethod>();
        for (PsiMethod superm : PsiSuperMethodUtil.findSuperMethods(method)) {
            if (hasNonNullAnnotation(superm)) bad.add(superm);
        }

        for (PsiMethod badMethod : bad) {
            if (!badMethod.getContainingClass().isInterface()) {
                // we found the real method
                overrideType = BadOverrideInfo.OverrideType.OVERRIDES;
                badOverride = badMethod;
                break;
            }
        }
        if (badOverride == null) {
            // this method is not implemented in any superclasses
            overrideType = BadOverrideInfo.OverrideType.IMPLEMENTS;
            if (bad.size() == 1) {
                badOverride = bad.get(0);
            } else {
                Collections.sort(bad, new MethodNameComparator());
                Set<PsiMethod> badInImplementsList
                        = getBadClassesInImplementsList(method, bad);

                if (!badInImplementsList.isEmpty()) {
                    // some bad superclass is in the implements list
                    if (badInImplementsList.size() == 1) {
                        badOverride = badInImplementsList.iterator().next();
                    }
                }
                if (badOverride == null) {
                    badOverride = null;
                }
            }
        }
        return new BadOverrideInfo(psiProblem, overrideType, badOverride);
    }

    private static Set<PsiMethod> getBadClassesInImplementsList(PsiMethod method,
            List<PsiMethod> bad) {
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

        Set<PsiMethod> badMethodsInImplementsList = new TreeSet<PsiMethod>(new MethodNameComparator());
        for (PsiMethod psiMethod : bad) {
            if (badSuperclasses.contains(psiMethod.getContainingClass())) {
                badMethodsInImplementsList.add(psiMethod);
            }
        }
        return badMethodsInImplementsList;
    }

    public static boolean hasSuppressNullChecksAnnotation(PsiModifierListOwner psiMethod) {
        PsiModifierList mods = psiMethod.getModifierList();
        return mods.findAnnotation(SuppressNullChecks.class.getName()) != null;
    }

    public static boolean shouldCheckNulls(PsiMethod psiMethod) {
        if (psiMethod == null) return false;
        if (hasSuppressNullChecksAnnotation(psiMethod)) return false;
        return shouldCheckNullsForClass(psiMethod.getContainingClass());
    }

    private static boolean shouldCheckNullsForClass(PsiClass cls) {
        if (hasSuppressNullChecksAnnotation(cls)) return false;
        PsiClass outer = cls.getContainingClass();
        if (outer == null) {
            PsiFile containingFile = cls.getContainingFile();
            if (containingFile instanceof PsiJavaFile) {
                PsiJavaFile javaFile = (PsiJavaFile) containingFile;
                javaFile.getPackageStatement().getPackageReference().resolve()
            }
            containingFile
        }
    }

    public static class MethodNameComparator implements Comparator<PsiMethod> {
        public int compare(PsiMethod method, PsiMethod method1) {
            String name1 = getQualifiedMemberName(method);
            String name2 = getQualifiedMemberName(method1);
            return name1.compareToIgnoreCase(name2);
        }
    }
}
