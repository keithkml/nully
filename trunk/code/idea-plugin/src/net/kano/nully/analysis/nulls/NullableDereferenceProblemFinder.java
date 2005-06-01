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

package net.kano.nully.plugin.analysis.nulls;

import net.kano.nully.annotations.NonNull;
import net.kano.nully.plugin.NullyTools;
import net.kano.nully.plugin.PossiblyNullReferenceInfo;
import net.kano.nully.plugin.ReferencedElementInfo;
import net.kano.nully.plugin.SootTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import net.kano.nully.plugin.analysis.ProblemFinder;
import net.kano.nully.plugin.analysis.nulls.soot.MayBeNullTag;
import soot.Body;
import soot.SootMethod;
import soot.ValueBox;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NullableDereferenceProblemFinder implements ProblemFinder<NullableDereferenceProblem> {
    public Collection<NullableDereferenceProblem> findProblems(AnalysisContext context) {
        List<NullableDereferenceProblem> problems = new ArrayList<NullableDereferenceProblem>();
        for (SootMethod method : context.getSootMethods()) {
            tagProblemsForMember(context, method.retrieveActiveBody(), problems);
        }
        return problems;
    }

    private synchronized void tagProblemsForMember(AnalysisContext context,
            @NonNull Body body, List<NullableDereferenceProblem> problems) {
        for (Stmt unit : (Collection<Stmt>)body.getUnits()) {
            ValueBox valueBox = SootTools.getDereferencedObject(unit);
            if (valueBox == null) continue;

            MayBeNullTag tag = SootTools.getMayBeNullTag(valueBox);
            if (tag == null) continue;
            NullableTag ntag = SootTools.getNullableTag(valueBox);
            if (ntag == null) continue;

            ReferencedElementInfo refInfo = NullyTools.getReferenceInfo(valueBox);
            PossiblyNullReferenceInfo info
                    = NullyTools.getReferenceInfo(context, unit, refInfo, tag);
            if (info != null) {
                problems.add(new NullableDereferenceProblem(info.getUse(),
                        info.getPossiblyNullReference(), valueBox, info.isDefinitelyNull()));
            }
        }
    }
}
