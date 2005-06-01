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

import com.intellij.psi.PsiElement;
import net.kano.nully.plugin.NullyTools;
import net.kano.nully.plugin.PossiblyNullReferenceInfo;
import net.kano.nully.plugin.SootTools;
import net.kano.nully.plugin.analysis.AnalysisContext;
import soot.ArrayType;
import soot.EquivalentValue;
import soot.Local;
import soot.RefType;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ArrayFlowUniverse;
import soot.toolkits.scalar.ArrayPackedSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.FlowUniverse;
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NullableAnalysis extends ForwardBranchedFlowAnalysis {
    public static final int K_NULLABLE = 3;
    public static final int K_EMPTY = 2;

    private List<EquivalentValue> refTypeLocals = new ArrayList<EquivalentValue>();
    private FlowSet emptySet;
    private FlowSet fullSet;
    private FlowSet tempFlowSet;
    private AnalysisContext context;

    public NullableAnalysis(AnalysisContext context, UnitGraph graph) {
        super(graph);
        this.context = context;

        initRefTypeLists();
        initUniverseSets();

        doAnalysis();
    }

    private void initRefTypeLists()
    {
        refTypeLocals = new ArrayList<EquivalentValue>();

        // build list of locals

        for (Local l : (Collection<Local>) ((UnitGraph) graph).getBody().getLocals()) {

            if (l.getType() instanceof RefType ||
                    l.getType() instanceof ArrayType) {
                refTypeLocals.add(getEquivalentValue(l));
            }
        }

        // G.v().out.println("Analyzed references: " + refTypeValues);
    } // end initRefTypeLists

    private void initUniverseSets()
    {
        FlowUniverse localUniverse;

        List<NullableStatus> universe = new ArrayList<NullableStatus>(refTypeLocals.size());
        for (EquivalentValue value : refTypeLocals) {
            universe.add(getKRefIntPair(value, K_EMPTY));
        }

        localUniverse = new ArrayFlowUniverse(universe.toArray(
                new NullableStatus[universe.size()]));

        emptySet = new ArrayPackedSet(localUniverse);
        fullSet = (FlowSet) emptySet.clone();
        ((ArrayPackedSet) emptySet).complement(fullSet);

        tempFlowSet = (FlowSet) newInitialFlow();
    } // end initUniverseSets

    private Map<EquivalentValue,NullableStatus> emptyRefs = new HashMap<EquivalentValue, NullableStatus>();
    private Map<EquivalentValue,NullableStatus> nullableRefs = new HashMap<EquivalentValue, NullableStatus>();

    private NullableStatus getKRefIntPair(EquivalentValue value, int type) {
        Map<EquivalentValue,NullableStatus> map;
        if (type == K_EMPTY) {
            map = emptyRefs;
        } else if (type == K_NULLABLE) {
            map = nullableRefs;
        } else {
            throw new IllegalArgumentException("invalid type " + type);
        }

        NullableStatus pair = map.get(value);
        if (pair == null) {
            pair = new NullableStatus(value, type);
            map.put(value,pair);
        }
        return pair;
    }

    private  HashMap<Value,EquivalentValue> valueToEquivValue
            = new HashMap<Value, EquivalentValue>(2293, 0.7f);

    public  EquivalentValue getEquivalentValue(Value v)
    {
        EquivalentValue val = valueToEquivValue.get(v);
        if (val == null) {
            val = new EquivalentValue(v);
            valueToEquivValue.put(v, val);
        }
        return val;
    } // end getEquivalentValue


    public final int refInfo(Value r, FlowSet fs)
    {
        return refInfo(getEquivalentValue(r), fs);
    } // end refInfo

    protected void flowThrough(Object inValue, Unit stmt, List outFallValue, List outBranchValues)
    {
        FlowSet in = (FlowSet) inValue;
        FlowSet out = tempFlowSet;

        {
            // kill rhs of defs
            {
                for (ValueBox box : ((Collection<ValueBox>) stmt.getDefBoxes())) {
                    Value boxValue = box.getValue();

                    Type type = boxValue.getType();
                    if (boxValue instanceof Local
                            && type instanceof RefType
                            || type instanceof ArrayType) {
                        EquivalentValue eq = getEquivalentValue(boxValue);
                        NullableStatus nullablePair = getKRefIntPair(eq, K_NULLABLE);
                        NullableStatus emptyPair = getKRefIntPair(eq, K_EMPTY);

                        if (in != out) {
                            if (in.contains(nullablePair)) in.remove(nullablePair, in);
                            in.add(emptyPair, in);
                        }
                        out.add(emptyPair, out);
                    }
                }
            } // done killing rhs of defs
        }

        // Manually add any x = y; when x and y are both analyzed references
        // these are not  sets.
        if (stmt instanceof AssignStmt) {
            AssignStmt as = (AssignStmt) stmt;
            Value rightOp = as.getRightOp();
            ValueBox rightOpBox = as.getRightOpBox();
            Value leftOp = as.getLeftOp();

            // take out the cast from "x = (type) y;"
            for (int i = 0; i < 10000 && rightOp instanceof CastExpr; i++) {
                rightOp = ((CastExpr) rightOp).getOp();
            }

            if (leftOp instanceof Local) {
                int roInfo = refInfo(rightOp, in);
                int newFlag;
                if (roInfo == K_NULLABLE) {
                    newFlag = K_NULLABLE;
                } else {
                    newFlag = K_EMPTY;
                    PsiElement el = SootTools.getPsiElement(rightOpBox);

                    if (el != null) {
                        PossiblyNullReferenceInfo info
                                = NullyTools.getPossiblyNullNullableReference(context,
                                as, rightOpBox);
                        if (info != null) {
                            newFlag = K_NULLABLE;
                        }
                    }
                }
                EquivalentValue eqv = getEquivalentValue(leftOp);
                if (newFlag == K_EMPTY) {
                    NullableStatus pair = getKRefIntPair(eqv, K_NULLABLE);
                    if (out.contains(pair)) out.remove(pair);
                }
                out.add(getKRefIntPair(eqv, newFlag));
            }
        }

        // Copy the out value to all branch boxes.
        for (FlowSet fs : ((Iterable<FlowSet>) outBranchValues)) {
            copy(out, fs);
        }

        // Copy the out value to the fallthrough box (don't need iterator)
        for (FlowSet fs : ((Iterable<FlowSet>) outFallValue)) {
            copy(out, fs);
        }
    } // end flowThrough

    protected Object newInitialFlow() {
        return emptySet.clone();
    }

    protected Object entryInitialFlow() {
        return fullSet.clone();
    }

    protected void merge(Object in1, Object in2, Object out)
    {
        FlowSet inSet1 = (FlowSet) in1;
        FlowSet inSet2 = (FlowSet) in2;
        FlowSet inSet1Copy = (FlowSet) inSet1.clone();
        FlowSet inSet2Copy = (FlowSet) inSet2.clone();
        // we do that in case out is in1 or in2

        FlowSet outSet = (FlowSet) out;

        inSet1.intersection(inSet2, outSet);
        // first step, set out to the intersection of in1 & in2
        // but we are not over, the intersection doesn't handle the top & bottom cases
        for (EquivalentValue r : refTypeLocals) {
            int refInfoIn1 = refInfo(r, inSet1Copy);
            int refInfoIn2 = refInfo(r, inSet2Copy);
            if (refInfoIn1 != refInfoIn2) {
                // only process if they are not equal, otherwise the intersection has done its job
                if ((refInfoIn1 == K_NULLABLE) || (refInfoIn2 == K_NULLABLE)) {
                    outSet.add(getKRefIntPair(r, K_NULLABLE), outSet);
                } else {
                    outSet.add(getKRefIntPair(r, K_EMPTY), outSet);
                }
            }
        }
    } // end merge


    protected  final int refInfo(EquivalentValue r, FlowSet fs)
    {
        return fs.contains(getKRefIntPair(r, K_NULLABLE)) ? K_NULLABLE : K_EMPTY;
    } // end refInfo


    protected void copy(Object source, Object dest)
    {
        FlowSet sourceSet = (FlowSet) source,
                destSet = (FlowSet) dest;

        sourceSet.copy(destSet);
    } // end copy


    // workaround for something, see BranchedRefVarsAnalysis
    protected boolean treatTrapHandlersAsEntries() {
        return true;
    }
}
