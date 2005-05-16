package net.kano.nully;

import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Host;
import soot.Local;

import java.util.Collection;

public class SootTools {
    public static SourceLnPosTag getSourceTag(Host host) {
        return (SourceLnPosTag) host.getTag("SourceLnPosTag");
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
}
