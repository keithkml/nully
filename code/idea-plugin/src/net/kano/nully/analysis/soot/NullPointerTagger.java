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

package net.kano.nully.analysis.nulls.soot;

import soot.Body;
import soot.BodyTransformer;
import soot.RefLikeType;
import soot.Value;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.toolkits.annotation.nullcheck.BranchedRefVarsAnalysis;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.FlowSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Tags possible null references with a {@link MayBeNullTag}.
 */
public class NullPointerTagger extends BodyTransformer {

	protected void internalTransform (Body b, String phaseName, Map options) {
		BranchedRefVarsAnalysis analysis = new BranchedRefVarsAnalysis (
				new ExceptionalUnitGraph(b));

        for (Stmt s : (Collection<Stmt>)b.getUnits()) {
            FlowSet beforeSet = (FlowSet) analysis.getFlowBefore(s);

            for (ValueBox vBox : (List<ValueBox>) s.getUseBoxes()) {
                addColorTags(vBox, beforeSet, analysis);
            }

            FlowSet afterSet = (FlowSet) analysis.getFallFlowAfter(s);

            for (ValueBox vBox : (Collection<ValueBox>)s.getDefBoxes()) {
                addColorTags(vBox, afterSet, analysis);
            }
        }
	}

	private void addColorTags(ValueBox vBox, FlowSet set,
            BranchedRefVarsAnalysis analysis){
		Value val = vBox.getValue();
        if (!(val.getType() instanceof RefLikeType)) return;

        int vInfo = analysis.anyRefInfo(val, set);

        boolean definitelyNull = vInfo == BranchedRefVarsAnalysis.kNull;
        if (definitelyNull
                || vInfo == BranchedRefVarsAnalysis.kTop
                || vInfo == BranchedRefVarsAnalysis.kBottom) {
            vBox.addTag(new MayBeNullTag(definitelyNull));
        }
    }
}