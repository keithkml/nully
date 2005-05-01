
package net.kano.nully;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import soot.ValueBox;
import soot.SootMethod;
import soot.jimple.JimpleBody;
import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Tag;
import soot.tagkit.Host;

import java.util.List;

public final class NullyTools {
    public static final String ANNO_NONNULL = NonNull.class.getName();;

    public static final String CLASS_UNEXPECTEDNULL
            = UnexpectedNullValueException.class.getName();
    public static final String CLASS_NULLPARAM = NullParameterException.class.getName();
    public static final String CLASS_NULLRETURN = NullReturnException.class.getName();
    public static final String CLASS_NONNULLTOOLS = NonNullTools.class.getName();

    public static final Key<PsiElement> nullyCopyKey = Key.create("NullyCopy");
    public static final Key<PsiElement> nullyOrigKey = Key.create("NullyOrig");

    private NullyTools() { }

    public static <E extends UserDataHolder> E getOriginalElement(E argel) {
        return (E) argel.getUserData(nullyOrigKey);
    }

    public static <E extends UserDataHolder> E getCopiedElement(E el) {
        return (E) el.getUserData(nullyCopyKey);
    }

    public static PsiVariable getReferencedVariable(PsiElement el) {
        PsiElement parent = el.getParent();
        if (parent instanceof PsiVariable) {
            return (PsiVariable) parent;
        }
        PsiReference ref = (PsiReference) PsiTreeUtil.getParentOfType(el,
                        (Class) PsiReference.class, false);
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
        copy.putUserData(nullyOrigKey, orig);
        orig.putUserData(nullyCopyKey, copy);
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

    public static PsiMethod getPsiMethod(MethodInfo info, SootMethod method) {
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
}
