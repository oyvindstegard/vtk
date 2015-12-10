/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.web.referencedata.provider;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Assertion;

/**
 * Provider which insert a single object in model with value either <code>true</code>
 * or <code>false</code> depending on if the configured {@link Assertion} matches
 * the current request.
 * 
 * <p>The value will also be <code>false</code> if errors
 * occur during matching.
 * 
 * <p>Configurable properties:
 * <ul><li>{@code assertion} - the Assertion instance to use for matching against request. Required.
 *     <li>{@code modelKey} - model key to insert Boolean object under. Required.
 * </ul>
 */
public class AssertionMatchProvider implements ReferenceDataProvider {

    private Assertion assertion;
    private String modelKey;
    
    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) throws Exception {
        boolean match = false;

        if (RequestContext.exists()) {
            try {
                RequestContext ctx = RequestContext.getRequestContext();
                Resource resource = ctx.getRepository().retrieve(
                        ctx.getSecurityToken(), ctx.getResourceURI(), true);
                match = this.assertion.matches(request, resource, ctx.getPrincipal());
            } catch (Exception e) {
            }
        }
        
        model.put(this.modelKey, match);
    }

    @Required
    public void setAssertion(Assertion assertion) {
        this.assertion = assertion;
    }

    /**
     * @param modelKey the modelKey to set
     */
    @Required
    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }
    
}
