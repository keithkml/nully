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

import net.kano.nully.plugin.NullyTools;
import net.kano.nully.plugin.PossiblyNullReferenceInfo;
import net.kano.nully.plugin.ReferencedElementInfo;
import net.kano.nully.plugin.SootTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import net.kano.nully.plugin.analysis.ProblemFinder;
import net.kano.nully.plugin.analysis.nulls.soot.MayBeNullTag;
import soot.SootMethod;
import soot.ValueBox;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//TODO: deal with suppression in other finders
public class DefinitelyNullDereferenceProblemFinder implements ProblemFinder<DefinitelyNullDereferenceProblem> {
    public Collection<DefinitelyNullDereferenceProblem> findProblems(AnalysisContext context) {
        List<DefinitelyNullDereferenceProblem> problems = new ArrayList<DefinitelyNullDereferenceProblem>();
        for (SootMethod method : context.getSootMethods()) {
            for (Stmt stmt : (Collection<Stmt>)method.retrieveActiveBody().getUnits()) {
                ValueBox box = SootTools.getDereferencedObject(stmt);
                if (box == null) continue;

                MayBeNullTag nullTag = SootTools.getMayBeNullTag(box);
                if (nullTag == null) continue;
                boolean definitelyNull = nullTag.isDefinitelyNull();
                if (definitelyNull/* || SootTools.hasNullableTag(box)*/) {
                    ReferencedElementInfo refInfo = NullyTools.getReferenceInfo(box);
                    PossiblyNullReferenceInfo info
                            = NullyTools.getReferenceInfo(context, stmt, refInfo, nullTag);
                    problems.add(new DefinitelyNullDereferenceProblem(info.getUse(),
                            info.getPossiblyNullReference(), box, definitelyNull));
                }
            }
        }
        return problems;
    }
}
