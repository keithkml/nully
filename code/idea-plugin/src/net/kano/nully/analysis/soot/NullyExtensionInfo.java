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

import net.kano.nully.annotations.NonNull;
import polyglot.frontend.FileSource;
import polyglot.frontend.Job;
import polyglot.frontend.Pass;
import polyglot.frontend.SourceLoader;
import polyglot.frontend.VisitorPass;
import soot.javaToJimple.CastInsertionVisitor;
import soot.javaToJimple.JavaToJimple;
import soot.javaToJimple.SaveASTVisitor;
import soot.javaToJimple.StrictFPPropagator;
import soot.javaToJimple.jj.ExtensionInfo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class NullyExtensionInfo extends ExtensionInfo {
    private final String fileName;
    private final ReaderProvider provider;

    public NullyExtensionInfo(@NonNull String fileName, 
            @NonNull ReaderProvider provider) {
        this.fileName = fileName;
        this.provider = provider;
    }

    public List passes(Job job) {
        List passes = super.passes(job);
        //beforePass(passes, Pass.EXIT_CHECK, new VisitorPass(polyglot.frontend.Pass.FOLD, job, new polyglot.visit.ConstantFolder(ts, nf)));
        beforePass(passes, Pass.EXIT_CHECK,
                new VisitorPass(JavaToJimple.CAST_INSERTION, job,
                        new CastInsertionVisitor(job, ts, nf)));
        beforePass(passes, Pass.EXIT_CHECK,
                new VisitorPass(JavaToJimple.STRICTFP_PROP, job,
                        new StrictFPPropagator(false)));
        afterPass(passes, Pass.PRE_OUTPUT_ALL,
                new SaveASTVisitor(JavaToJimple.SAVE_AST, job, this));
        removePass(passes, Pass.OUTPUT);
        return passes;
    }

    public SourceLoader sourceLoader() {
        return new SingleFileSourceLoader();
    }

    private class SingleFileSourceLoader extends SourceLoader {

        public SingleFileSourceLoader() {
            super(NullyExtensionInfo.this, Collections.EMPTY_LIST);
        }

        public FileSource fileSource(String fileName) throws IOException {
            if (fileName.equals(NullyExtensionInfo.this.fileName)) {
                return new ReaderFileSource(fileName, provider);
            } else {
                throw new FileNotFoundException(fileName);
            }
        }
    }
}
