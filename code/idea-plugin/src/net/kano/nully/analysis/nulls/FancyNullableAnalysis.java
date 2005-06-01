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

import soot.ArrayType;
import soot.EquivalentValue;
import soot.Local;
import soot.NullType;
import soot.RefType;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.EqExpr;
import soot.jimple.IfStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.NeExpr;
import soot.jimple.NullConstant;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ArrayFlowUniverse;
import soot.toolkits.scalar.ArrayPackedSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.FlowUniverse;
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class FancyNullableAnalysis  extends ForwardBranchedFlowAnalysis
{
    /*
        COMPILATION OPTIONS
    */

    // we don't want the analysis to be conservative?
    // i.e. we don't want it to only care for locals
    private  boolean isNotConservative = false;
    
    // do we want the analysis to handle if statements?
    private  boolean isBranched = true;
    
    // do we want the analysis to care that f and g 
    // could be the same reference?
    private  boolean careForAliases = false;
    
    // do we want the analysis to care that a method 
    // call could have side effects?
    private  boolean careForMethodCalls = true;

    // **** END OF COMPILATION OPTIONS *****

/*
    {
        if (true) {
            G.v().out.println();
            G.v().out.println();
            G.v().out.println("BranchedRefVarsAnalysis:");
            G.v().out.println("     isNotConservative = "+isNotConservative);
            G.v().out.println("            isBranched = "+isBranched);
            G.v().out.println("        careForAliases = "+careForAliases);
            G.v().out.println("    careForMethodCalls = "+careForMethodCalls);
        }
    } // end 
*/

    // constants for the analysis
    public final static int kBottom = 0;
    public final static int kNull = 1;
    public final static int kNonNull = 2;
    public final static int kTop = 99;


    // bottom and top sets
    protected FlowSet emptySet;
    protected FlowSet fullSet;


    // gen and preserve sets (for each statement)
    protected Map<Unit,FlowSet> unitToGenerateSet;
    protected Map<Unit,FlowSet> unitToPreserveSet;

    
    // sets of variables that need a null pointer check (for each statement)
    protected Map<Unit,HashSet<Value>> unitToAnalyzedChecksSet;
    protected Map<Unit,HashSet<Value>> unitToArrayRefChecksSet;
    protected Map<Unit,HashSet<Value>> unitToInstanceFieldRefChecksSet;
    protected Map<Unit,HashSet<Value>> unitToInstanceInvokeExprChecksSet;
    protected Map<Unit,HashSet<Value>> unitToLengthExprChecksSet;


    // keep track of the different kinds of reference types this analysis is working on
    protected List<EquivalentValue> refTypeLocals;
    protected List<EquivalentValue> refTypeInstFields;
    protected List<EquivalentValue> refTypeInstFieldBases;
    protected List<EquivalentValue> refTypeStaticFields;
    protected List<EquivalentValue> refTypeValues; // sum of all the above


    // used in flowThrough.
    protected FlowSet tempFlowSet = null;

    // fast conversion from Value -> EquivalentValue
    //  because used in  methods
    private  HashMap<Value,EquivalentValue> valueToEquivValue = new HashMap<Value, EquivalentValue>(2293, 0.7f);

    public  EquivalentValue getEquivalentValue(Value v)
    {
        if (valueToEquivValue.containsKey(v)) {
            return valueToEquivValue.get(v);
        } else {
            EquivalentValue ev = new EquivalentValue(v);
            valueToEquivValue.put(v, ev);
            return ev;
        }
    } // end getEquivalentValue
    
    // constant (r, v) pairs
    //  because used in  methods
    private  HashMap<EquivalentValue,NullableStatus> kRefBotttomPairs = new HashMap<EquivalentValue, NullableStatus>(2293, 0.7f);
    private  HashMap<EquivalentValue,NullableStatus> kRefNonNullPairs = new HashMap<EquivalentValue, NullableStatus>(2293, 0.7f);
    private  HashMap<EquivalentValue,NullableStatus> kRefNullPairs = new HashMap<EquivalentValue, NullableStatus>(2293, 0.7f);
    private  HashMap<EquivalentValue,NullableStatus> kRefTopPairs = new HashMap<EquivalentValue, NullableStatus>(2293, 0.7f);

    // make that (r, v) pairs are constants
    // i.e. the same r and v values always generate the same (r, v) object    
    public  NullableStatus getKRefIntPair(EquivalentValue r, int v)
    {
        HashMap<EquivalentValue,NullableStatus> pairsMap = null;

        if (v == kNonNull) {
            pairsMap = kRefNonNullPairs;
        } else if (v == kNull) {
            pairsMap = kRefNullPairs;
        } else if (v == kTop) {
            pairsMap = kRefTopPairs;
        } else if (v == kBottom) {
            pairsMap = kRefBotttomPairs;
        } else {
            throw new RuntimeException("invalid constant (" + v + ")");
        }

        if (pairsMap.containsKey(r)) {
            return pairsMap.get(r);
        } else {
            NullableStatus pair = new NullableStatus(r, v);
            pairsMap.put(r, pair);
            return pair;
        }
    } // end getKRefIntPair

    /*
        Utility methods.

        They are used all over the place. Most of them are declared 
        "private  final" so they can be inlined with javac -O.

     */


    // isAlwaysNull returns true if the reference r is known to be always null
    private  final boolean isAlwaysNull(Value r)
    {
        return ((r instanceof NullConstant) || 
                (r.getType() instanceof NullType));
    } // end isAlwaysNull


    // isAlwaysTop returns true if the reference r is known to be always top for this analysis
    // i.e. its value is undecidable by this analysis
    private  final boolean isAlwaysTop(Value r)
    {
        if (isNotConservative) {
            return false;
        } else {
            return r instanceof InstanceFieldRef || r instanceof StaticFieldRef;
        }
    } // end isAlwaysTop

    protected boolean isAlwaysNonNull(Value ro) {
        return false;
    }



    // isAnalyzedRef returns true if the reference r is to be analyzed by this analysis
    // i.e. its value is not always known (or undecidable)
    private  final boolean isAnalyzedRef(Value r)
    {
        if (isAlwaysNull(r) || isAlwaysTop(r)) {
            return false;
        } else if (r instanceof Local ||
                r instanceof InstanceFieldRef ||
                r instanceof StaticFieldRef) {
            Type rType = r.getType();

            return (rType instanceof RefType || rType instanceof ArrayType);
        } else {
            return false;
        }
    } // end isAnalyzedRef

    // refInfo is a helper method to tranform our two bit representation back to the four constants
    // For a given reference and a flow set, tell us if r is bottom, top, null or non-null
    // Note: this method will fail if r is not in the flow set
    protected  final int refInfo(EquivalentValue r, FlowSet fs)
    {
        boolean isNull = fs.contains(getKRefIntPair(r, kNull));
        boolean isNonNull = fs.contains(getKRefIntPair(r, kNonNull));

        if (isNull && isNonNull) {
            return kTop;
        } else if (isNull) {
            return kNull;
        } else if (isNonNull) {
            return kNonNull;
        } else {
            return kBottom;
        }
    } // end refInfo

    protected  final int refInfo(Value r, FlowSet fs)
    {
        return refInfo(getEquivalentValue(r), fs);
    } // end refInfo
    
    // Like refInfo, but the reference doesn't have to be in the flow set
    // note: it still need to be a reference, i.e. ArrayType or RefType
    public int anyRefInfo(Value r, FlowSet f)
    {
        if (isAlwaysNull(r)) {
            return kNull;
        } else if (isAlwaysTop(r)) {
            return kTop;
        } else if (isAlwaysNonNull(r)) {
            return kNonNull;
        } else {
            return refInfo(r, f);
        }
    } // end anyRefInfo
    

    /*
       methods: uAddTopToFlowSet
                uAddInfoToFlowSet
                uListAddTopToFlowSet

       Adding a pair (r, v) to a set is always a two steps process:
       a) remove all pairs (r, *)
       b) add the pair (r, v)
       
       The methods above handle that.

       Most of them come in two flavors: to act on one set or two act on separate
       generate and preserve sets.

    */

    // method to add (r, kTop) to the gen set (and remove it from the pre set)
    private  final void uAddTopToFlowSet(EquivalentValue r, FlowSet genFS, FlowSet preFS)
    {
        NullableStatus nullPair = getKRefIntPair(r, kNull);
        NullableStatus nullNonPair = getKRefIntPair(r, kNonNull);
        
        if (genFS != preFS) {
            preFS.remove(nullPair, preFS);
            preFS.remove(nullNonPair, preFS);
        }
        
        genFS.add(nullPair, genFS);
        genFS.add(nullNonPair, genFS);
    } // end uAddTopToFlowSet
    
    private  final void uAddTopToFlowSet(Value r, FlowSet genFS, FlowSet preFS)
    {
        uAddTopToFlowSet(getEquivalentValue(r), genFS, preFS);
    } // end uAddTopToFlowSet

    // method to add (r, kTop) to a set
    private  final void uAddTopToFlowSet(Value r, FlowSet fs)
    {
        uAddTopToFlowSet(getEquivalentValue(r), fs, fs);
    } // end uAddTopToFlowSet
    
    // method to add (r, kTop) to a set
    private  final void uAddTopToFlowSet(EquivalentValue r, FlowSet fs)
    {
        uAddTopToFlowSet(r, fs, fs);
    } // end uAddTopToFlowSet

    // method to add (r, kNonNull) or (r, kNull) to the gen set (and remove it from the pre set)
    private  final void uAddInfoToFlowSet(EquivalentValue r, int v, FlowSet genFS, FlowSet preFS)
    {
        int kill;
        if (v == kNull) {
            kill = kNonNull;
        } else if (v == kNonNull) {
            kill = kNull;
        } else {
            throw new RuntimeException("invalid info");
        }
        
        if (genFS != preFS) {
            preFS.remove(getKRefIntPair(r, kill), preFS);
        }
        
        genFS.remove(getKRefIntPair(r, kill), genFS);
        genFS.add(getKRefIntPair(r, v), genFS);
    } // end uAddInfoToFlowSet

    private  final void uAddInfoToFlowSet(Value r, int v, FlowSet genF, FlowSet preF)
    {
        uAddInfoToFlowSet(getEquivalentValue(r), v, genF, preF);
    } // end uAddInfoToFlowSet

    // method to add (r, kNonNull) or (r, kNull) to a set
    private  final void uAddInfoToFlowSet(Value r, int v, FlowSet fs)
    {
        uAddInfoToFlowSet(getEquivalentValue(r), v, fs, fs);
    } // end uAddInfoToFlowSet

    // method to add (r, kNonNull) or (r, kNull) to a set
    private  final void uAddInfoToFlowSet(EquivalentValue r, int v, FlowSet fs)
    {
        uAddInfoToFlowSet(r, v, fs, fs);
    } // end uAddInfoToFlowSet


    // method to apply uAddTopToFlowSet to a whole list of references
    private  final void uListAddTopToFlowSet(List<EquivalentValue> refs, FlowSet genFS, FlowSet preFS)
    {
        Iterator<EquivalentValue> it = refs.iterator();

        while (it.hasNext()) {
            uAddTopToFlowSet(it.next(), genFS, preFS);
        }
    } // end uListAddTopToFlowSet


    /********** end of utility methods *********/


    // here come the method that start it all, the constructor
    // initialize the object and run the analysis
    public FancyNullableAnalysis (UnitGraph g)
    {
        super(g);

        // initialize all the refType lists
        initRefTypeLists();
        
        // initialize emptySet, fullSet and tempFlowSet
        initUniverseSets();
        
        // initialize unitTo...Sets
        // perform  preservation and generation
        initUnitSets();
        
        doAnalysis();
    } // end constructor


    // method to initialize refTypeLocals, refTypeInstFields, refTypeInstFieldBases
    // refTypeStaticFields, and refTypeValues
    // those lists contains fields that can/need to be analyzed
    private void initRefTypeLists()
    {
        refTypeLocals = new ArrayList<EquivalentValue>();
        refTypeInstFields = new ArrayList<EquivalentValue>();
        refTypeInstFieldBases = new ArrayList<EquivalentValue>();
        refTypeStaticFields = new ArrayList<EquivalentValue>();
        refTypeValues = new ArrayList<EquivalentValue>();

        // build list of locals
        Iterator it = ((UnitGraph)graph).getBody().getLocals().iterator();
        
        while (it.hasNext()) {
            Local l = (Local) (it.next());
            
            if (l.getType() instanceof RefType ||
                l.getType() instanceof ArrayType) {
                refTypeLocals.add(getEquivalentValue(l));
            }
        }
        
        if (isNotConservative) {
            // build list of fields
            // if the analysis is not conservative (if it is then we will only work on locals)
            
            Iterator unitIt = graph.iterator();
            
            while(unitIt.hasNext()) {
                
                Unit s = (Unit) unitIt.next();
                
                Iterator boxIt;
                boxIt = s.getUseBoxes().iterator();
                while(boxIt.hasNext()) initRefTypeLists( (ValueBox) boxIt.next() );
                boxIt = s.getDefBoxes().iterator();
                while(boxIt.hasNext()) initRefTypeLists( (ValueBox) boxIt.next() );
                
            }
            
        } // end build list of fields

        refTypeValues.addAll(refTypeLocals);
        refTypeValues.addAll(refTypeInstFields);
        refTypeValues.addAll(refTypeStaticFields);
        
        // G.v().out.println("Analyzed references: " + refTypeValues);
    } // end initRefTypeLists 
    
    private void initRefTypeLists( ValueBox box ) {
        Value val = box.getValue();
        Type opType = null;
        
        if (val instanceof InstanceFieldRef) {
            
            InstanceFieldRef ir = (InstanceFieldRef) val;
            
            opType = ir.getType(); 
            if (opType instanceof RefType ||
                opType instanceof ArrayType) {
                
                EquivalentValue eir = getEquivalentValue(ir); 
                
                if (!refTypeInstFields.contains(eir)) {
                    refTypeInstFields.add(eir);

                    EquivalentValue eirbase = getEquivalentValue(ir.getBase());
                    if (!refTypeInstFieldBases.contains(eirbase)) {
                        refTypeInstFieldBases.add(eirbase);
                    }
                }

            }
        } else if (val instanceof StaticFieldRef) {
            
            StaticFieldRef sr = (StaticFieldRef) val;
            opType = sr.getType();
            
            if (opType instanceof RefType ||
                opType instanceof ArrayType) {
                
                EquivalentValue esr = getEquivalentValue(sr);

                if (!refTypeStaticFields.contains(esr)) {
                    refTypeStaticFields.add(esr);
                }
            }
        }
    }
    // method to initialize the emptySet, fullSet and tempFlowSet
    // from the refTypeValues
    private void initUniverseSets()
    {
        FlowUniverse localUniverse;
        
        Object[] refTypeValuesArray = refTypeValues.toArray();
        int len = refTypeValuesArray.length;
        Object[] universeArray = new Object[2*len];
        int i;
        
        // kRefIntPairs = new HashMap(len*2 + 1, 0.7f);
        // ideally we would like to be able to do the above to avoid that Map growth
        // but that would screw concurent execution of this analysis
        // and making that field non- would require changing our  utility methods to non-
        
        for (i = 0; i < len; i++) {
            int j = i*2;
            EquivalentValue  r = (EquivalentValue) refTypeValuesArray[i];
            universeArray[j] = getKRefIntPair(r, kNull);
            universeArray[j+1] = getKRefIntPair(r,  kNonNull);
        }
        
        localUniverse = new ArrayFlowUniverse(universeArray);
        
        emptySet = new ArrayPackedSet(localUniverse);   
        fullSet = (FlowSet) emptySet.clone();
        ((ArrayPackedSet) emptySet).complement(fullSet);
        
        tempFlowSet = (FlowSet) newInitialFlow();
    } // end initUniverseSets
    


    private void initUnitSets()
    {
        int cap = graph.size() * 2 + 1;
        float load = 0.7f;
        
        unitToGenerateSet = new HashMap<Unit, FlowSet>(cap, load);
        unitToPreserveSet = new HashMap<Unit, FlowSet>(cap, load);
        
        unitToAnalyzedChecksSet = new HashMap<Unit, HashSet<Value>>(cap, load);
        unitToArrayRefChecksSet = new HashMap<Unit, HashSet<Value>>(cap, load);
        unitToInstanceFieldRefChecksSet = new HashMap<Unit, HashSet<Value>>(cap, load);
        unitToInstanceInvokeExprChecksSet = new HashMap<Unit, HashSet<Value>>(cap, load);
        unitToLengthExprChecksSet = new HashMap<Unit, HashSet<Value>>(cap, load);
        
        
        Iterator unitIt = graph.iterator();
        
        while(unitIt.hasNext()) {
            
            Unit s = (Unit) unitIt.next();
            
            FlowSet genSet = (FlowSet) emptySet.clone();
            FlowSet preSet = (FlowSet) fullSet.clone();
            
            HashSet<Value> analyzedChecksSet = new HashSet<Value>(5,  load);
            HashSet<Value> arrayRefChecksSet = new HashSet<Value>(5,  load);
            HashSet<Value> instanceFieldRefChecksSet = new HashSet<Value>(5,  load);
            HashSet<Value> instanceInvokeExprChecksSet = new HashSet<Value>(5,  load);
            HashSet<Value> lengthExprChecksSet = new HashSet<Value>(5,  load);
            
            
            // *** KILL PHASE ***
            
            // naivity here.  kill all the fields after an invoke, i.e. promote them to top
            if (careForMethodCalls && ((Stmt)s).containsInvokeExpr()) {
                uListAddTopToFlowSet(refTypeInstFields, genSet, preSet);
                uListAddTopToFlowSet(refTypeStaticFields, genSet, preSet);
            }
            
            if (careForAliases && (s instanceof AssignStmt)) {
                AssignStmt as = (AssignStmt) s;
                Value lhs = as.getLeftOp();
                
                if (refTypeInstFieldBases.contains(lhs)) {
                    // we have a write to a local 'f' and 
                    // there is a reference to f.x.
                    // The LHS will certainly be a local.
                    // here, we kill "f.*".

                    for (EquivalentValue eifr : refTypeInstFields) {
                        InstanceFieldRef ifr = (InstanceFieldRef) eifr.getValue();

                        if (ifr.getBase() == lhs) {
                            uAddTopToFlowSet(eifr, genSet, preSet);
                        }
                    }
                }
                
                if (lhs instanceof InstanceFieldRef) {
                    
                    // we have a write to 'f.x', 
                    // so we'd better kill 'g.x' for all g.
                    
                    String lhsName = 
                        ((InstanceFieldRef) lhs).getField().getName();

                    for (EquivalentValue eifr : refTypeInstFields) {
                        InstanceFieldRef ifr = (InstanceFieldRef) eifr.getValue();

                        String name = ifr.getField().getName();

                        if (name.equals(lhsName)) {
                            uAddTopToFlowSet(eifr, genSet, preSet);
                        }
                    }
                }
            } // end if (s instanceof AssignStmt)
            
            // kill rhs of defs
            {
                Iterator boxIt = s.getDefBoxes().iterator();

                while(boxIt.hasNext()) {

                    ValueBox box = (ValueBox) boxIt.next();
                    Value boxValue = box.getValue();

                    if (isAnalyzedRef(boxValue)) {
                        uAddTopToFlowSet(boxValue, genSet, preSet);
                    }
                }
            } // done killing rhs of defs
            
            // GENERATION PHASE
            
            if (s instanceof DefinitionStmt) {
                DefinitionStmt as = (DefinitionStmt) s;
                Value ro = as.getRightOp();
                Value lo = as.getLeftOp();
                
                // take out the cast from "x = (type) y;"
                if (ro instanceof CastExpr) {
                    ro = ((CastExpr) ro).getOp();
                }
                
                if (isAnalyzedRef(lo)) {
                    if (isAlwaysNonNull(ro)) {
                        uAddInfoToFlowSet(lo, kNonNull, genSet, preSet);
                    } else if (isAlwaysNull(ro)) {
                        uAddInfoToFlowSet(lo, kNull, genSet, preSet);
                    } else if (isAlwaysTop(ro)) {
                        uAddTopToFlowSet(lo, genSet, preSet);
                    }
                }
            } // end DefinitionStmt gen case

            unitToGenerateSet.put(s, genSet);
            unitToPreserveSet.put(s, preSet);
            
            unitToAnalyzedChecksSet.put(s, analyzedChecksSet);
            unitToArrayRefChecksSet.put(s, arrayRefChecksSet);
            unitToInstanceFieldRefChecksSet.put(s, instanceFieldRefChecksSet);
            unitToInstanceInvokeExprChecksSet.put(s, instanceInvokeExprChecksSet);
            unitToLengthExprChecksSet.put(s, lengthExprChecksSet);
        }
    } // initUnitSets
    
    protected void flowThrough(Object inValue, Unit stmt, List outFallValue, List outBranchValues)
    {
        FlowSet in = (FlowSet) inValue;
        FlowSet out = tempFlowSet;
        FlowSet pre = unitToPreserveSet.get(stmt);
        FlowSet gen = unitToGenerateSet.get(stmt);

        // Perform perservation
        in.intersection(pre, out);
        
        // Perform generation
        out.union(gen, out);
        
        // Manually add any x = y; when x and y are both analyzed references
        // these are not  sets.
        if (stmt instanceof AssignStmt) {
            AssignStmt as = (AssignStmt) stmt;
            Value rightOp = as.getRightOp();
            Value leftOp = as.getLeftOp();
            
            // take out the cast from "x = (type) y;"
            for (int i = 0; i < 10000 && rightOp instanceof CastExpr; i++) {
                rightOp = ((CastExpr) rightOp).getOp();
            }
            
            if (isAnalyzedRef(leftOp) && isAnalyzedRef(rightOp)) {
                int roInfo = refInfo(rightOp, in);

                if (roInfo == kTop) {
                    uAddTopToFlowSet(leftOp, out);
                } else if (roInfo != kBottom) {
                    uAddInfoToFlowSet(leftOp, roInfo, out);
                }
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

        if (isBranched && (stmt instanceof IfStmt)) {
            Value cond = ((IfStmt) stmt).getCondition();
            Value op1 = ((BinopExpr) cond).getOp1();
            Value op2 = ((BinopExpr) cond).getOp2();

            // make sure at least one of the op is a reference being analyzed
            // and that none is a reference that is always Top
            if ((!(isAlwaysTop(op1) || isAlwaysTop(op2))) && (isAnalyzedRef(op1) || isAnalyzedRef(op2))) {
                Value toGen = null;
                int toGenInfo = kBottom;

                int op1Info = anyRefInfo(op1, in);
                int op2Info = anyRefInfo(op2, in);
                boolean op1isKnown = (op1Info == kNull || op1Info == kNonNull);
                boolean op2isKnown = (op2Info == kNull || op2Info == kNonNull);
                
                if (op1isKnown) {
                    if (!op2isKnown) {
                        toGen = op2;
                        toGenInfo = op1Info;
                    }
                } else if (op2isKnown) {
                    toGen =  op1;
                    toGenInfo = op2Info;
                }
                
                // only generate info for analyzed references that are top or bottom
                if ((toGen != null) && isAnalyzedRef(toGen)) {
                    int fInfo = kBottom;
                    int bInfo = kBottom;
                    
                    if (cond instanceof EqExpr) {
                        // branching mean op1 == op2
                        bInfo = toGenInfo;
                        if (toGenInfo == kNull) {
                            // falling through mean toGen != null
                            fInfo = kNonNull;
                        }
                        
                    } else if (cond instanceof NeExpr) {
                        // if we don't branch that mean op1 == op2
                        fInfo = toGenInfo;
                        if (toGenInfo == kNull) {
                            // branching through mean toGen != null
                            bInfo = kNonNull;
                        }
                    } else {
                        throw new RuntimeException("invalid condition");
                    }

                    if (fInfo != kBottom) {
                        Iterator it = outFallValue.iterator();

                        while(it.hasNext()) {
                            FlowSet fs = (FlowSet) (it.next());

                            copy(out, fs);
                            uAddInfoToFlowSet(toGen, fInfo, fs);
                        }
                    }

                    if (bInfo !=  kBottom) {
                        Iterator it = outBranchValues.iterator();

                        while (it.hasNext()) {
                            FlowSet fs = (FlowSet) (it.next());

                            copy(out, fs);
                            uAddInfoToFlowSet(toGen, bInfo, fs);
                        }
                    } 
                }
            }
        }
    } // end flowThrough


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
        Iterator<EquivalentValue> it = refTypeValues.iterator();
        while (it.hasNext()) {
            EquivalentValue r = it.next();
            int refInfoIn1 = refInfo(r, inSet1Copy);
            int refInfoIn2 = refInfo(r, inSet2Copy);
            if (refInfoIn1 != refInfoIn2) {
                // only process if they are not equal, otherwise the intersection has done its job
                if ((refInfoIn1 == kTop) || (refInfoIn2 == kTop)) {
                    // ok, r is top in one of the sets but not the other, make it top in the outSet
                    uAddTopToFlowSet(r, outSet);
                } else if (refInfoIn1 == kBottom) {
                    // r is bottom in set1 but not set2, promote to the value in set2
                    uAddInfoToFlowSet(r, refInfoIn2, outSet);
                } else if (refInfoIn2 == kBottom) {
                    // r is bottom in set2 but not set1, promote to the value in set1
                    uAddInfoToFlowSet(r, refInfoIn1, outSet);
                } else {
                    // r is known in both set, but it's a different value in each set, make it top
                    uAddTopToFlowSet(r, outSet);
                }
            }
        }
    } // end merge
    

    protected void copy(Object source, Object dest)
    {
        FlowSet sourceSet = (FlowSet) source,
            destSet = (FlowSet) dest;
            
        sourceSet.copy(destSet);
    } // end copy


    protected Object newInitialFlow()
    {
        return emptySet.clone();
    } // end newInitialFlow


    protected Object entryInitialFlow()
    {
        return fullSet.clone();
    }

    // try to workaround exception limitation of ForwardBranchedFlowAnalysis
    // this will make for a very conservative analysys when exception handling
    // statements are in the code :-(
    public boolean treatTrapHandlersAsEntries()
    {
        return true;
    }

} // end class BranchedRefVarsAnalysis

