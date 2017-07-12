/* Copyright (c) 2006, University of Oslo, Norway
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

import vtk.repository.search.QueryException;
import vtk.util.text.SimpleTemplate;


public class QueryStringPreProcessorImpl implements QueryStringPreProcessor {

    private String expressionStart = "{$";
    private String expressionEnd = "}";

    private ExpressionEvaluator[] expressionEvaluators;
    
    public void setExpressionEvaluators(ExpressionEvaluator[] expressionEvaluators) {
        this.expressionEvaluators = expressionEvaluators;
    }
    
    @Override
    public String process(String queryString, ProcessorContext ctx) throws QueryException {
        SimpleTemplate template = SimpleTemplate.
                compile(queryString, expressionStart, expressionEnd);
        final StringBuilder result = new StringBuilder();

        template.apply(token -> {
            ExpressionEvaluator e = resolveExpressionEvaluator(token);
            return e != null ? e.evaluate(token, ctx) : expressionStart + token + expressionEnd;
        }, s -> result.append(s));

        return result.toString();
    }
    

    private ExpressionEvaluator resolveExpressionEvaluator(String token) {
        for (int i = 0; i < expressionEvaluators.length; i++) {
            
            if (expressionEvaluators[i].matches(token)) {
                return expressionEvaluators[i];
            }
        }
        return null;
    }

}
