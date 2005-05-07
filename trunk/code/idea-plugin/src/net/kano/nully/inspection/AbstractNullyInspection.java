package net.kano.nully.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import net.kano.nully.NullyTools;

public abstract class AbstractNullyInspection extends LocalInspectionTool {
    public String getGroupDisplayName() {
        return NullyTools.GROUP_NULL_VALUES;
    }
}
