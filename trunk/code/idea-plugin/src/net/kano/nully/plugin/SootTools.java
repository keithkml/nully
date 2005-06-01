package net.kano.nully.plugin;

import com.intellij.psi.PsiElement;
import net.kano.nully.annotations.NonNull;
import net.kano.nully.annotations.Nullable;
import net.kano.nully.plugin.psiToJimple.PsiTag;
import net.kano.nully.plugin.analysis.nulls.soot.MayBeNullTag;
import soot.Local;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.LengthExpr;
import soot.jimple.MonitorStmt;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.tagkit.Host;
import soot.tagkit.SourceLnPosTag;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SootTools {
    //TOLATER: highlight definitely null value in any context
    private static Lock sootLock = new ReentrantLock();

    public static @Nullable PsiElement getPsiElement(@NonNull Host host) {
        PsiTag tag = ((PsiTag) host.getTag("PsiTag"));
        if (tag == null) return null;
        else return tag.getElement();
    }

    public static MayBeNullTag getMayBeNullTag(@NonNull Host host) {
        return (MayBeNullTag) host.getTag("MayBeNull");
    }

    public static boolean hasMayBeNullTag(@NonNull Host host) {
        return host.hasTag("MayBeNull");
    }

    public static int getOffset(@NonNull OffsetsTracker tracker,
            @NonNull SourceLnPosTag argSrcTag) {
        return tracker.getOffset(argSrcTag.startLn(), argSrcTag.startPos());
    }

    /**
     * Returns a name for a new local which does not conflict with any other
     * local in {@code locals}.
     *
     * @param locals a list of locals
     * @return a name for a new local whose name is unique to {@code locals}
     */
    public static @NonNull String getUnusedLocalName(@NonNull Collection<Local> locals) {
        for (int r = 0; r < 1000000; r++) {
            String tryName = "$r" + r;
            boolean good = true;
            for (Local local : locals) {
                if (local.getName().equals(tryName)) {
                    good = false;
                    break;
                }
            }
            if (good) return tryName;
        }
        throw new IllegalStateException("could not find name. locals: " + locals);
    }

    public static ValueBox getReferencedObject(Stmt s) {
        ValueBox obj = null;

        if (s.containsArrayRef()) {
            ArrayRef aref = s.getArrayRef();
            obj = aref.getBaseBox();
        } else {
            // Throw
            if (s instanceof ThrowStmt) {
                obj = ((ThrowStmt) s).getOpBox();
            } else if (s instanceof MonitorStmt) {
                obj = ((MonitorStmt) s).getOpBox();
            } else {
                Iterator boxIt;
                boxIt = s.getDefBoxes().iterator();
                while (boxIt.hasNext()) {
                    ValueBox vBox = (ValueBox) boxIt.next();
                    Value v = vBox.getValue();

                    // putfield, and getfield
                    if (v instanceof InstanceFieldRef) {
                        obj = ((InstanceFieldRef) v).getBaseBox();
                        break;
                    } else
                    // invokevirtual, invokespecial, invokeinterface
                    if (v instanceof InstanceInvokeExpr) {
                        obj = ((InstanceInvokeExpr) v).getBaseBox();
                        break;
                    } else
                    // arraylength
                    if (v instanceof LengthExpr) {
                        obj = ((LengthExpr) v).getOpBox();
                        break;
                    }
                }
                boxIt = s.getUseBoxes().iterator();
                while (boxIt.hasNext()) {
                    ValueBox vBox = (ValueBox) boxIt.next();
                    Value v = vBox.getValue();

                    // putfield, and getfield
                    if (v instanceof InstanceFieldRef) {
                        obj = ((InstanceFieldRef) v).getBaseBox();
                        break;
                    } else
                    // invokevirtual, invokespecial, invokeinterface
                    if (v instanceof InstanceInvokeExpr) {
                        obj = ((InstanceInvokeExpr) v).getBaseBox();
                        break;
                    } else
                    // arraylength
                    if (v instanceof LengthExpr) {
                        obj = ((LengthExpr) v).getOpBox();
                        break;
                    }
                }
            }
        }
        return obj;
    }

    public static void lockSootGlobally() {
        getSootLock().lock();
    }

    public static void unlockSootGlobally() {
        try {
            getSootLock().unlock();
        } catch (IllegalMonitorStateException ignored) {
        }
    }

    public static @NonNull Lock getSootLock() { return sootLock; }

}
