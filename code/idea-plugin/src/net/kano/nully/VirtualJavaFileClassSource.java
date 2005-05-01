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

package net.kano.nully;

import com.intellij.psi.PsiJavaFile;
import polyglot.ast.Node;
import polyglot.frontend.Compiler;
import polyglot.frontend.ExtensionInfo;
import polyglot.frontend.Source;
import polyglot.frontend.SourceJob;
import soot.ClassSource;
import soot.SootClass;
import soot.CompilationDeathException;
import soot.javaToJimple.InitialResolver;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Collections;

public class VirtualJavaFileClassSource extends ClassSource {
    private final PsiJavaFile file;
    private static final String TEMP_FILE_NAME = "test.java";

    public VirtualJavaFileClassSource(String className, PsiJavaFile file) {
        super(className);

        this.file = file;
    }

    public List resolve(SootClass sc) {
        ExtensionInfo extInfo = new NullyExtensionInfo(TEMP_FILE_NAME,
                        new StringReader(file.getText()));
        polyglot.main.Options options = extInfo.getOptions();

//        options.assertions = true;
//
//        options.source_ext = new String []{"java"};
//        options.serialize_type_info = false;
//
//        polyglot.main.Options.global = options;
        polyglot.frontend.Compiler compiler
                = new polyglot.frontend.Compiler(extInfo);

        // build ast
        InitialResolver resolver = InitialResolver.v();
        Node ast;
        try {
            ast = compile(compiler, TEMP_FILE_NAME, extInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        resolver.setAst(ast);

        resolver.resolveAST();

        return resolver.resolveFromJavaFile(sc);
    }

    private Node compile(Compiler compiler, String filename, ExtensionInfo extInfo)
            throws IOException, CompilationDeathException {
        Source source = extInfo.sourceLoader().fileSource(filename);

        ExtensionInfo sourceExtension = compiler.sourceExtension();
        SourceJob job = null;
        if (sourceExtension instanceof soot.javaToJimple.jj.ExtensionInfo){
            soot.javaToJimple.jj.ExtensionInfo jjInfo
                    = (soot.javaToJimple.jj.ExtensionInfo)sourceExtension;
            if (jjInfo.sourceJobMap() != null){
                job = (SourceJob)jjInfo.sourceJobMap().get(source);
            }
        }
        if (job == null) {
            job = sourceExtension.addJob(source);
        }

        boolean result = sourceExtension.runToCompletion();

        if (!result) {
            throw new soot.CompilationDeathException(0, "Could not compile");
        }

        return job.ast();
    }
}
