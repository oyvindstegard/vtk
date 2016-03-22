/* Copyright (c) 2005, University of Oslo, Norway
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

import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple shell handler thread that reads commands from stdin and
 * writes to stdout by default.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>commandReader</code> - a <code>CommandReader</code> to use for
 *       reading input
 *   <li><code>outputter</code> - a <code>PrintStream</code> to write output to
 * </ul>
 */
public class ConsoleHandlerThread extends ShellHandlerThread {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    private boolean alive = true;
    private CommandReader reader = new ConsoleReader();
    private PrintStream outputter = System.out;
        
    public final void setCommandReader(CommandReader reader) {
        this.reader = reader;
    }
    
    public final void setOutputter(PrintStream out) {
        this.outputter = out;
    }
    
    public void interrupt() {
        this.logger.info("Shutting down thread " + this.getName());
        this.alive = false;
        super.interrupt();
    }
        
    public void run() {
            
        while (this.alive) {
            try {
                String line = this.reader.readLine(this.outputter);
                if (this.alive) {
                    getShell().eval(line, this.outputter);
                }
            } catch (Throwable t) {
                this.outputter.println("Error: " + t.getMessage());
                t.printStackTrace(this.outputter);
            }
        }
        this.outputter.println("Exiting");
    }
}
