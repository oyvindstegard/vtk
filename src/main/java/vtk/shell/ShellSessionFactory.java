/* Copyright (c) 2017, University of Oslo, Norway
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 *
 */
public interface ShellSessionFactory {

    /**
     * Create a new Shell session with an empty input channel. May be used
     * purely for
     * {@link ShellSession#evaluate(java.io.Reader) evaluate} {@link ShellSession#evaluate(java.lang.String) calls}.,
     * with no running input loop.
     *
     * <p>
     * By default, calling the {@link ShellSession#run() } method of such sessions will
     * result in the session marking itself as terminated, as no data is
     * available from input channel, and the output will also be closed.
       *
     * @param output output stream which is used for evaluation output (UTF-8 coding)
     * @return a new shell session with no input channel
     * @throws Exception 
     */
    default ShellSession newSession(OutputStream output) throws Exception {
        return newSession(new PrintStream(output, true, "UTF-8"));
    }

    /**
     * Create a new Shell session with an empty input channel. May be used
     * purely for
     * {@link ShellSession#evaluate(java.io.Reader) evaluate} {@link ShellSession#evaluate(java.lang.String) calls}.,
     * with no running input loop.
     *
     * <p>
     * By default, calling the {@link ShellSession#run() } method of such sessions will
     * result in the session marking itself as terminated, as no data is
     * available from input channel, and the output will also be closed.
     *
     * @param output print stream which is used for evaluation output
     * @return a new shell session with no input channel
     * @throws Exception
     */
    default ShellSession newSession(PrintStream output) throws Exception {
        BufferedReader br = new BufferedReader(new StringReader(""));
        return newSession(br, output);
    }

    /**
     * Create a new shell sesion with provided I/O channels.
     *
     * @param input
     * @param output
     * @return
     * @throws java.lang.Exception
     */
    default ShellSession newSession(InputStream input, OutputStream output) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        PrintStream ps = new PrintStream(output, true, "UTF-8");
        return newSession(br, ps);
    }

    /**
     * Create a new shell sesion with provided I/O channels.
     *
     * @param input
     * @param output
     * @return
     * @throws java.lang.Exception
     */
    ShellSession newSession(BufferedReader input, PrintStream output) throws Exception;

}
