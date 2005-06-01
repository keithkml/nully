/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Ondrej Lhotak
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

package net.kano.nully.plugin.psiToJimple;
import soot.Body;
import soot.BodyPack;
import soot.PackManager;
import soot.PhaseOptions;
import soot.jimple.JimpleBody;
import soot.options.JJOptions;

import java.util.Map;


/** A wrapper object for a pack of optimizations.
 * Provides chain-like operations, except that the key is the phase name.
 * This is a specific one for the very messy jb phase. */
public class PsiBodyPack extends BodyPack
{
    public PsiBodyPack() {
        super("pj");
    }

    /** Applies the transformations corresponding to the given options. */
    private void applyPhaseOptions(JimpleBody b, Map opts) 
    { 
        JJOptions options = new JJOptions( opts );
        
        if(options.use_original_names()) {
            PhaseOptions.v().setPhaseOptionIfUnset( "jj.lns", "only-stack-locals");
        }

        // local splitter
        PackManager.v().getTransform( "jj.ls" ).apply( b );

        // aggregator
        PackManager.v().getTransform( "jj.a" ).apply( b );
        // unused local eliminator
        PackManager.v().getTransform( "jj.ule" ).apply( b );
        // nop eliminator
        PackManager.v().getTransform( "jj.ne" ).apply( b );
        // type assigner
        PackManager.v().getTransform( "jj.tr" ).apply( b );

        if(options.use_original_names()) {
            // local packer
            PackManager.v().getTransform( "jj.ulp" ).apply( b );
        }
        // local name standardizer
        PackManager.v().getTransform( "jj.lns" ).apply( b );
        // copy propagator
        PackManager.v().getTransform( "jj.cp" ).apply( b );
        // dead assignment eliminator
//        PackManager.v().getTransform( "jj.dae" ).apply( b );
        // unused local eliminator
//        PackManager.v().getTransform( "jj.cp-ule" ).apply( b );
        // local packer
        PackManager.v().getTransform( "jj.lp" ).apply( b );
        //PackManager.v().getTransform( "jb.ne" ).apply( b );
        // unreachable code eliminator
        PackManager.v().getTransform( "jj.uce" ).apply( b );

    }

    protected void internalApply(Body b) {
        String phaseName = getPhaseName();
        if (phaseName != null && !phaseName.equals("pj")) {
            Map options = PhaseOptions.v().getPhaseOptions(phaseName);
            if (options != null) {
                applyPhaseOptions( (JimpleBody) b, options );
            }
        }
    }
}
