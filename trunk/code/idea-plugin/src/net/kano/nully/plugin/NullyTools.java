package net.kano.nully.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.annotations.NonNullTools;
import net.kano.nully.annotations.NullCheckLevel;
import net.kano.nully.annotations.Nullable;
import net.kano.nully.annotations.SuppressNullChecks;
import net.kano.nully.plugin.analysis.AnalysisContext;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.tagkit.SourceLnPosTag;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class NullyTools {
    private static final Logger LOGGER = Logger.getInstance(NullyTools.class.getName());

    public static final String METHOD_CHECKNONNULLRETURN = "checkNonNullReturn";
    public static final String METHOD_CHECKNONNULLVALUE = "checkNonNullValue";
    public static final String METHOD_CHECKNONNULLPARAM = "checkNonNullParameter";

    public static final String GROUP_NULL_VALUES = "Null values";

    private NullyTools() { }

    public static PsiMember getPsiMemberCopy(AnalysisContext context, SootMethod method) {
        OffsetsTracker tracker = context.getTracker();
        PsiJavaFile fileCopy = context.getFileCopy();
        SourceLnPosTag srcTag = SootTools.getSourceTag(method);
        if (srcTag == null) return null;
        PsiElement el = tracker.getElementAtPosition(fileCopy, srcTag);
        if (el == null) return null;
        return PsiTreeUtil.getParentOfType(el, PsiMember.class);
    }

    public static @NonNull String getUnexpectedNullValueCheckString(@NonNull String oldText) {
        return NonNullTools.class.getName() + "."
                + METHOD_CHECKNONNULLVALUE + "(" + oldText + ")";
    }

    public static boolean isNonnullCheckMethod(@NonNull PsiMethod method) {
        PsiClass cls = method.getContainingClass();
        String name = method.getName();
        String clsName = cls.getQualifiedName();
        if (clsName == null) return false;
        return clsName.equals(NonNullTools.class.getName())
                && (name.equals(METHOD_CHECKNONNULLRETURN)
                || name.equals(METHOD_CHECKNONNULLVALUE)
                || name.equals(METHOD_CHECKNONNULLPARAM));
    }

    public static boolean hasValidNullableAnnotation(PsiModifierListOwner var) {
        return isNullAnnotationApplicable(var) && hasNullableAnnotation(var);
    }

    private static boolean hasNullableAnnotation(PsiModifierListOwner var) {
        return getNullableAnnotation(var) != null;
    }

    public static PsiAnnotation getNullableAnnotation(@NonNull PsiModifierListOwner owner) {
        return getAnnnotation(owner, Nullable.class);
    }

    public static boolean hasNonNullAnnotation(@NonNull PsiModifierListOwner owner) {
        return getNonnullAnnotation(owner) != null;
    }

    public static PsiAnnotation getNonnullAnnotation(@NonNull PsiModifierListOwner owner) {
        return getAnnnotation(owner, NonNull.class);
    }

    public static boolean hasValidNonNullAnnotation(PsiModifierListOwner param) {
        return isNullAnnotationApplicable(param) && hasNonNullAnnotation(param);
    }

    public static Set<PsiAnnotation> getNullyAnnotations(@NonNull PsiModifierListOwner owner) {
        PsiAnnotation nonnull = getNonnullAnnotation(owner);
        PsiAnnotation nullable = getNullableAnnotation(owner);
        if (nonnull == null && nullable == null) return Collections.emptySet();
        if (nonnull == null) return Collections.singleton(nullable);
        if (nullable == null) return Collections.singleton(nonnull);
        return new HashSet<PsiAnnotation>(Arrays.asList(nonnull, nullable));
    }

    private static boolean isNullAnnotationApplicable(PsiModifierListOwner param) {
        PsiType type;
        if (param instanceof PsiVariable) {
            PsiVariable variable = (PsiVariable) param;
            type = variable.getType();
        } else if (param instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) param;
            type = method.getReturnType();
        } else {
            type = null;
        }
        return type != null && !(type instanceof PsiPrimitiveType);
    }

    public static boolean hasSuppressNullChecksAnnotation(@NonNull PsiModifierListOwner member) {
        return getAnnnotation(member, SuppressNullChecks.class) != null;
    }

    private static PsiAnnotation getAnnnotation(PsiModifierListOwner owner,
            Class<?> cls) {
        return owner.getModifierList().findAnnotation(cls.getName());
    }

    public static boolean shouldCheckNulls(@NonNull PsiMember member) {
        if (hasSuppressNullChecksAnnotation(member)) return false;
        PsiClass outer = member.getContainingClass();
        if (outer == null) {
            return true;
        } else {
            return shouldCheckNulls(outer);
        }
    }

    public static boolean shouldCheckNulls(PsiElement expression,
            NullCheckLevel level) {
        return shouldCheckNulls(expression, Collections.singleton(level));
    }

    /**
     * Must match all specified levels
     */
    public static boolean shouldCheckNulls(PsiElement el,
            Collection<NullCheckLevel> levels) {
        PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(el,
                PsiModifierListOwner.class, false);
        for (; owner != null; owner = PsiTreeUtil.getParentOfType(owner,
                PsiModifierListOwner.class, true)) {
            PsiModifierList mods = owner.getModifierList();
            PsiAnnotation anno = mods.findAnnotation(SuppressNullChecks.class.getName());
            if (anno == null) continue;
            PsiAnnotationParameterList params = anno.getParameterList();
            PsiNameValuePair[] attrs = params.getAttributes();
            if (attrs.length == 0) {
                // if there are no parameters, ALL is the default
                return false;
            }
            for (PsiNameValuePair pair : attrs) {
                String name = pair.getName();
                if (name == null || name.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
                    PsiAnnotationMemberValue value = pair.getValue();
                    SuppressNullChecksVisitor visitor = new SuppressNullChecksVisitor(levels);
                    value.accept(visitor);
                    if (visitor.isSuppress()) return false;
                }
            }
        }
        return true;
    }

    public static @Nullable PossiblyNullReferenceInfo getPossiblyNullNullableReference(
            AnalysisContext context, Unit unit, ValueBox valueBox) {
        if (!SootTools.hasMayBeNullTag(valueBox)) return null;

        ReferencedElementInfo refInfo = getReferenceInfo(context, valueBox);
        if (refInfo == null) return null;

        return getPossiiblyNullReference(context, unit, refInfo);

    }

    public static PossiblyNullReferenceInfo getPossiiblyNullReference(AnalysisContext context,
            Unit unit, ReferencedElementInfo refInfo) {
        PsiModifierListOwner referenced = refInfo.getReferenced();
        PsiModifierListOwner origReferenced = context.getOriginalElement(referenced);
        PsiExpression origBad = context.getOriginalElement(refInfo.getRefExpression());
        if (origReferenced == null || origBad == null) return null;

        if (!hasValidNullableAnnotation(origReferenced)) return null;

        SourceLnPosTag unitSrcTag = SootTools.getSourceTag(unit);
        PsiElement highlight = context.getTracker()
                .getElementAtPosition(context.getFileCopy(),
                unitSrcTag);
        if (highlight == null) highlight = referenced;

        PsiElement origHighlight = context.getOriginalElement(highlight);

        return new PossiblyNullReferenceInfo(origBad, origHighlight);
    }

    public static @Nullable ReferencedElementInfo getReferenceInfo(
            AnalysisContext context, ValueBox valueBox) {
        SourceLnPosTag srcTag = SootTools.getSourceTag(valueBox);
        if (srcTag == null) return null;

        PsiElement el = context.getTracker()
                .getElementAtPosition(context.getFileCopy(), srcTag);
        ReferencedElementInfo refInfo = getReferenceInfo(el);
        if (refInfo == null) {
            LOGGER.debug("Element " + valueBox
                    + " has no var or methcall parent");
        }
        return refInfo;

    }

    public static ReferencedElementInfo getReferenceInfo(PsiElement el) {
        PsiExpression badEl = null;
        PsiModifierListOwner referenced = null;

        PsiReferenceExpression refExp = PsiTreeUtil.getParentOfType(el,
                PsiReferenceExpression.class, false);
        if (refExp != null) {
            PsiElement var = refExp.resolve();
            if (var instanceof PsiVariable) {
                badEl = refExp;
                referenced = (PsiModifierListOwner) var;
            }
        }

        if (referenced == null) {
            PsiMethodCallExpression call = PsiTools.getMethodCallParent(el);
            if (call != null) {
                badEl = call;
                referenced = PsiTools.getCalledMethod(el);
            }
        }

        if (referenced == null) {
            return null;
        } else {
            return new ReferencedElementInfo(badEl, referenced);
        }
    }


    private static class SuppressNullChecksVisitor extends PsiRecursiveElementVisitor {
        private Collection<String> requestedNames = new HashSet<String>(5);
        private Collection<String> foundNames = new HashSet<String>(5);
        private boolean all = false;

        public SuppressNullChecksVisitor(Collection<NullCheckLevel> levels) {
            for (NullCheckLevel level : levels) {
                requestedNames.add(level.name());
            }
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);

            if (all) return;

            PsiElement resolved = reference.resolve();
            if (!(resolved instanceof PsiField)) return;

            PsiField field = (PsiField) resolved;
            String className = field.getContainingClass().getQualifiedName();
            if (className == null) return;
            String fieldName = field.getName();
            if (className.equals(NullCheckLevel.class.getName())) {
                if (fieldName.equals(NullCheckLevel.ALL.name())) {
                    all = true;
                    return;
                }
                if (requestedNames.contains(fieldName)) {
                    foundNames.add(fieldName);
                }
            }
        }

        public boolean isSuppress() {
            return all || foundNames.containsAll(requestedNames);
        }
    }

}
