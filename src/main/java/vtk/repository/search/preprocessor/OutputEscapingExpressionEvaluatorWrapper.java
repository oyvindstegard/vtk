/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.repository.search.preprocessor;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.search.QueryException;
import vtk.repository.search.preprocessor.QueryStringPreProcessor.ProcessorContext;

/**
 * Wrap another {@link ExpressionEvaluator} and apply escaping to output of
 * {@link ExpressionEvaluator#evaluate(java.lang.String)} according to syntax in 
 * QueryParserImpl.jj. Mostly useful for wrapping evaluators that
 * generate term values for a query.
 *
 */
public class OutputEscapingExpressionEvaluatorWrapper implements
        ExpressionEvaluator {

    private ExpressionEvaluator wrappedEvaluator;
    
    @Override
    public String evaluate(String token, ProcessorContext ctx)
            throws QueryException {
        return escapeStringValue(this.wrappedEvaluator.evaluate(token, ctx));
    }

    @Override
    public boolean matches(String token) {
        return this.wrappedEvaluator.matches(token);
    }
    
    @Required
    public void setWrappedEvaluator(ExpressionEvaluator evaluator) {
        this.wrappedEvaluator = evaluator;
    }
    
    /**
     * Escape syntactically special characters in string value 
     * using back-slash as Occurences of the back-slash in the
     * input values will themselves also be escaped.
     * 
     * Example
     * Input:  "this (is) a \string"
     * Output: "this\ \(is\)\ a\ \\string"
     * 
     * @param value The <code>String</code> to escape.
     * @return A <code>String</code> with also space-characters (' ')
     *         and back-slash-characters ('\') escaped.
     */
    private String escapeStringValue(String value) {
        int length = value.length();
        char[] output = new char[length];
        int p = 0;

        for (int i = 0; i < length; i++) {
            char current = value.charAt(i);
            if (p >= output.length - 1) {
                char[] doubled = new char[output.length * 2];
                System.arraycopy(output, 0, doubled, 0, p);
                output = doubled;
            }
            
            // List of characters to escape was fetched from
            // JavaCC parser defined in QueryParserImpl.jj
            switch (current) {
            case ' ': 
            case '\\':
            case '!':
            case '(':
            case ')':
            case ':':
            case '"':
            case '|':
            case '&':
            case '=':
            case '<':
            case '>':
                output[p++] = '\\';
            default:
                output[p++] = current;
            }
        }

        return new String(output, 0, p);
    }
}
