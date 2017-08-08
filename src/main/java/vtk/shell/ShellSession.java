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
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single shell session for some dynamic languge or interpreter.
 *
 * <p>Basically handles IO and evaluation abstractions, delegating to language
 * specific subclasses for actual implementations.
 *
 * <p>Conrete implementations are responsible for any printing of evaluation results and errors
 * to the provided output stream, while this class takes care of input and prompting.
 *
 * <p>Optionally runs continuous REPL-style evaluation in a thread as a {@code Runnable}.
 *
 * <p>Thread safety of a single session instance will depend on implementation, but
 * usually the answer is <em>not thread safe</em>.
 */
public abstract class ShellSession implements Runnable {

    protected final BufferedReader input;
    protected final PrintStream output;
    private final String prompt;

    private boolean terminated = false;
    private Optional<String> clientId = Optional.empty();
    private Optional<Consumer<ShellSession>> terminationCallback = Optional.empty();

    private final Logger logger = LoggerFactory.getLogger(ShellSession.class.getName());

    protected ShellSession(BufferedReader input, PrintStream output) {
        this(input, output, "");
    }

    protected ShellSession(BufferedReader input, PrintStream output, String prompt) {
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.prompt = Objects.requireNonNull(prompt);
    }

    /**
     * Continuously read lines from input, evaluate and print result to output.
     *
     * <p>To be used when running shell session in a dedicated thread.
     *
     * <p>This method will do nothing if I/O channels {@link #close() have been terminated} or input
     * channel signals EOF.
     */
    @Override
    public void run() {
        try {
            while (!terminated) {
                try {
                    String in = readCommand(input, output, Optional.of(prompt));
                    if (in == null) {
                        logger.debug("EOF from input, terminating evaluation loop");
                        close();
                        return;
                    }

                    if (Thread.interrupted()) {
                        logger.debug("Interrupted, terminating evaluation loop");
                        close();
                        return;
                    }

                    try {
                        evaluate(in);
                    } catch (Exception e) {
                        logger.debug("Evaluation error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                } catch (IOException io) {
                    logger.debug("IOException, terminating evaluation loop: " + io.getMessage());
                    close();
                    return;
                }
            }
        } finally {
            if (terminationCallback.isPresent()) {
                terminationCallback.get().accept(this);
            }
        }
    }

    protected String readCommand(BufferedReader in, PrintStream out, Optional<String> prompt) throws IOException {
        if (prompt.isPresent()) {
            out.print(prompt.get());
        }
        final StringBuilder result = new StringBuilder();
        while (true) {
            String line = in.readLine();
            if (line == null) {
                if (result.length() == 0) {
                    return null;
                }

                break;
            }
            if (line.endsWith("\\")) {
                result.append(line.substring(0, line.length() - 1));
                if (prompt.isPresent()) {
                    out.print("> ");
                }
            } else {
                result.append(line);
                break;
            }
        }
        return result.toString();
    }

    /**
     * Used for closing session I/O
     */
    protected void close() {
        terminated = true;
        try {
            input.close();
        } catch (IOException io) {}
        
        output.close();
    }

    /**
     * @return {@code true} if input/output channels have been closed
     */
    public boolean isTerminated() {
        return terminated;
    }

    public void setClientId(String id) {
        this.clientId = Optional.ofNullable(id);
    }

    public Optional<String> clientId() {
        return clientId;
    }

    /**
     * Optional callback invoked whenever the {@link #run run method} returns.
     * @param callback
     */
    public void setTerminationCallback(Consumer<ShellSession> callback) {
        this.terminationCallback = Optional.of(callback);
    }

    /**
     * Bind a named object into the shell session interpreter context.
     * @param name the variable name
     * @param value the value
     */
    public abstract void bind(String name, Object value);

    /**
     * Evaluate an expression and write output to the provided {@©ode PrintStream}.
     * 
     * <p>Any evaluation output is written to the provided {@code PrintStream}.
     * 
     * <p>This is minimally what subclasses need to implement, as the other evaluation
     * variants all delegate in some way to this method.
     * 
     * @param line the expression to evaluate
     * @param output the output stream
     * @return possibly an object result from evaluating an expression (not textual interpreter output), may be {@code null}
     */
    public abstract Object evaluate(String line, PrintStream output);

    /**
     * Evaluate an expression and write output to the session default output stream.
     *
     * <p>This implementation delegates directly to {@link #evaluate(java.lang.String, java.io.PrintStream) }
     * with the shell session default output stream.
     *
     * @param line the expression to evaluate
     * @return possibly an object result from evaluating an expression (not textual interpreter output), may be <code>null</code>
     */
    public Object evaluate(String line)  {
        return evaluate(line, output);
    }

    /**
     * Evaluate a stream of source code, e.g. a script or similar, write output
     * to the session default output stream.
     *
     * <p>This default implementation delegates directly to
     * {@link #evaluate(java.io.Reader, java.io.PrintStream) with the provided reader
     * and shell session default output stream as argument.
     *
     * @param source a reader for the script or expression source
     * @return possibly an object as result from evaluating a script source (not textual interpreter output), may be <code>null</code>
     */
    public Object evaluate(Reader source) {
        return evaluate(source, output);
    }

    /**
     * Evaluate a stream of source code, e.g. a script or similar, write output
     * to the provided {@code PrintStream}.
     *
     * <p>This default implementation just reads source line by line and delegates evaluation
     * to {@link #evaluate(java.lang.String) } until input has been depleted or an unhandled evaluation exception occurs.
     * The result of the last evaluated line from the source will be returned, unless an error
     * occured, then {@code null} will be returned.
     *
     * @param source a reader for the script or expression source
     * @param output the output stream to be used during evaluation
     * @return possibly an object as result from evaluating a script source (not textual interpreter output), may be <code>null</code>
     */
    public Object evaluate(Reader source, PrintStream output) {
        Object returnValue = null;
        try {
            String command;
            BufferedReader inputReader = new BufferedReader(source);
            while ((command = readCommand(inputReader, output, Optional.empty())) != null) {
                returnValue = evaluate(command);
            }
        } catch (Exception e) {
            output.println("Error evaluating source: " + e.getMessage());
            e.printStackTrace(output);
            return null;
        }

        return returnValue;
    }

}
