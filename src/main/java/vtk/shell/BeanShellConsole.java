/* Copyright (c) 2004, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.shell;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;

import bsh.EvalError;
import bsh.Interpreter;



/**
 * Shell implementation using BeanShell as its interpreter.
 */
@Deprecated
public class BeanShellConsole extends AbstractConsole {

    private Interpreter interpreter = new Interpreter();
    private boolean wrapOutputInBrackets = true;

    protected void init() {
    }
    

    public void setWrapOutputInBrackets(boolean wrapOutputInBrackets) {
        this.wrapOutputInBrackets = wrapOutputInBrackets;
    }
    

    public void bind(String name, Object o) {
        try {
            this.interpreter.set(name, o);
        } catch (Throwable t) {
            this.logger.warn("Unable to set binding: ['" + name + "' = '" + o + "']", t);
        }
    }


    protected void evalInitFile(InputStream inStream, PrintStream out) {
        try {
            this.interpreter.setOut(out);
            Reader reader = new InputStreamReader(inStream);
            out.println(this.interpreter.eval(reader));

        } catch (EvalError e) {
            out.println("Evaluation error: " + e.getMessage());
            e.printStackTrace(out);

        } catch (Throwable t) {
            this.logger.warn("Unable to run init file", t);
        }
    }

    
    
    public void eval(String line, PrintStream out) {
        try {
            if (line != null && !"".equals(line.trim())) {
                this.interpreter.setOut(out);
                Object o = this.interpreter.eval(line);
                if (this.wrapOutputInBrackets) {
                    out.println("[" + o + "]");
                } else {
                    out.println(o);
                }
            }
        } catch (EvalError e) {
            out.println("Evaluation error: " + e.getMessage());
            e.printStackTrace(out);

        } catch (Throwable t) {
            out.println("Error: " + t.getMessage());
            t.printStackTrace(out);
        }
    }
}
