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

package net.kano.nully.analysis.nulls;

import net.kano.nully.NonNull;
import net.kano.nully.SootTools;
import net.kano.nully.analysis.AnalysisContext;
import net.kano.nully.analysis.ProblemFinder;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.toolkits.annotation.nullcheck.BranchedRefVarsAnalysis;

import java.util.Collection;

public class NullableProblemFinder implements ProblemFinder<NullableProblem> {
    //TODO: implement @Nullable stuff
    public Collection<NullableProblem> findProblems(AnalysisContext context) {
        for (SootMethod method : context.getSootMethods()) {
            tagProblemsForMember(method.retrieveActiveBody());
        }
    }

    private synchronized void tagProblemsForMember(@NonNull Body body) {
        for (Unit unit : (Collection<Unit>)body.getUnits()) {
            for (ValueBox valueBox : (Collection<ValueBox>) unit.getUseBoxes()) {
                if (!SootTools.hasMayBeNullTag(valueBox)) continue;

                BranchedRefVarsAnalysis
            }
        }
    }

}
