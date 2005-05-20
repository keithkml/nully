/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-1999 Raja Vallee-Rai
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */


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

package net.kano.nully.plugin.analysis.nulls.soot;
import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.ValueBox;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.jimple.StmtBody;
import soot.options.CPOptions;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.SmartLocalDefs;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CopyPropagator extends BodyTransformer {
    /** Cascaded copy propagator.
    
        If it encounters situations of the form: A: a = ...; B: ... x = a; C:... use (x); 
        where a has only one definition, and x has only one definition (B), then it can 
        propagate immediately without checking between B and C for redefinitions
        of a (namely) A because they cannot occur.  In this case the propagator is global.
        
        Otherwise, if a has multiple definitions then it only checks for redefinitions of
        Propagates constants and copies in extended basic blocks. 
        
        Does not propagate stack locals when the "only-regular-locals" option is true.
    */
    protected void internalTransform(Body b, String phaseName, Map opts) {
        CPOptions options = new CPOptions(opts);
        StmtBody stmtBody = (StmtBody) b;

        Map<Local, Integer> localToDefCount = getLocalToDefCount(stmtBody.getUnits());

        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(stmtBody);

        LocalDefs localDefs = new SmartLocalDefs(graph, new SimpleLiveLocals(graph));

        LocalUses localUses = new SimpleLocalUses(graph, localDefs);

        // Perform a local propagation pass.
        PseudoTopologicalOrderer orderer = new PseudoTopologicalOrderer();
        List<Stmt> listForGraph = orderer.newList(graph);

        for (Stmt stmt : listForGraph) {
            List<ValueBox> boxes = stmt.getUseBoxes();

            for (ValueBox useBox : boxes) {
                Value value = useBox.getValue();
                if (!(value instanceof Local)) continue;

                Local l = (Local) value;

                if (options.only_regular_locals() && l.getName()
                        .startsWith("$")) {
                    continue;
                }

                if (options.only_stack_locals() && !l.getName()
                        .startsWith("$")) {
                    continue;
                }

                List<DefinitionStmt> defsOfUse = localDefs.getDefsOfAt(l, stmt);
                if (defsOfUse.size() != 1) continue;

                DefinitionStmt def = defsOfUse.get(0);

                Value rightOp = def.getRightOp();
                if (!(rightOp instanceof Local)) continue;

                Local m = (Local) rightOp;
                if (l == m) continue;

                Integer defCount = localToDefCount.get(m);

                if (defCount == null || defCount == 0) {
                    throw new IllegalStateException("Variable "
                            + m
                            + " used without definition!");
                }

                if (defCount == 1) {
                    useBox.setValue(m);
//                                    fastCopyPropagationCount++;
                    continue;
                }

                List<Stmt> path = graph.getExtendedBasicBlockPathBetween(def,
                        stmt);

                if (path == null) {
                    // no path in the extended basic block
                    continue;
                }

                Iterator<Stmt> pathIt = path.iterator();

                // Skip first node
                pathIt.next();

                // Make sure that m is not redefined along path
                if (isRedefined(pathIt, stmt, m)) continue;

                useBox.setValue(m);
//                                slowCopyPropagationCount++;
            }
        }
    }

    private Map<Local, Integer> getLocalToDefCount(Collection<Stmt> units) {
        Map<Local,Integer> localToDefCount = new HashMap<Local, Integer>();

        // Count number of definitions for each local.
        for (Stmt s : units) {
            if (s instanceof DefinitionStmt) {
                DefinitionStmt defStmt = (DefinitionStmt) s;
                Value leftOp = defStmt.getLeftOp();
                if (leftOp instanceof Local) {
                    Local l = (Local) leftOp;

                    Integer count = localToDefCount.get(l);
                    if (count == null) count = 0;
                    count++;
                    localToDefCount.put(l, count);
                }
            }
        }
        return localToDefCount;
    }

    private boolean isRedefined(Iterator<Stmt> pathIt, Stmt stmt, Local m) {
        boolean isRedefined = false;

        while (pathIt.hasNext()) {
            Stmt s = pathIt.next();

            if (stmt == s) {
                // Don't look at the last statement
                // since it is evaluated after the uses
                break;
            }

            if (s instanceof DefinitionStmt) {
                DefinitionStmt defStmt = (DefinitionStmt) s;
                if (defStmt.getLeftOp() == m) {
                    isRedefined = true;
                    break;
                }
            }
        }
        return isRedefined;
    }
}






























