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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Path;
import vtk.repository.search.QueryException;
import vtk.repository.search.preprocessor.QueryStringPreProcessor.ProcessorContext;


/**
 * Expression evaluator the token input to uri depth. 
 * <p>The syntax accepted is '(currentDepth)([+-]\d+)?'.
 * The depth is calculated based on the parents depth, '/' has depth 0.
 *  
 *  <p>Note: The current implementation can return a negative depth value.
 */
public class CurrentDepthExpressionEvaluator implements ExpressionEvaluator {
    private static Logger logger = LoggerFactory.getLogger(CurrentDepthExpressionEvaluator.class);
    private String variableName = "currentDepth";
    private Pattern pattern = compilePattern();
    
    private Pattern compilePattern() {
        return Pattern.compile(
            this.variableName + "(([+-])(\\d+))?");
    }
    
    public void setVariableName(String variableName) {
        this.variableName = variableName;
        this.pattern = compilePattern();
    }
    
    @Override
    public boolean matches(String token) {
        Matcher m = this.pattern.matcher(token);
        return m.matches();
    }
    
    @Override
    public String evaluate(String token, ProcessorContext ctx) throws QueryException {
        Matcher m = this.pattern.matcher(token);
        if (!m.matches()) {
            throw new QueryException("Illegal query token: '" + token + "'");
        }
        
        Path uri = ctx.currentURI;
        int depth = uri.getDepth();

        if (m.group(1) != null) {
            String operator = m.group(2);
            int qty = Integer.parseInt(m.group(3));
            
            if (operator.equals("+")) {
                depth += qty;
            }
            else {
                depth -= qty;
            }
        }
        logger.debug("Evaluated depth variable '{}' to depth {}", token, depth);
        return String.valueOf(depth);
    }

}

