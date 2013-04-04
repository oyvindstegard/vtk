/* Copyright (c) 2013, University of Oslo, Norway
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
package org.vortikal.web.display.feed;

import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.web.service.RequestParameterAssertion;

public class FeedController implements Controller, InitializingBean {

    private FeedGenerator feedGenerator;
    private RequestParameterAssertion feedParameterSetAssertion;
    private Map<String, FeedGenerator> alternateGenerators;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        FeedGenerator generator = feedGenerator;

        if (alternateGenerators != null) {
            String requestedFeedFormat = request.getParameter(feedParameterSetAssertion.getParameterName());
            FeedGenerator requestedFeedFormatGenerator = alternateGenerators.get(requestedFeedFormat);
            if (requestedFeedFormatGenerator != null) {
                generator = requestedFeedFormatGenerator;
            }
        }

        return generator.generateFeed(request, response);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        if (alternateGenerators != null) {

            Set<String> legalParameters = feedParameterSetAssertion.getLegalParameters();
            Set<String> alternateGeneratorsKeySet = alternateGenerators.keySet();

            for (String key : alternateGeneratorsKeySet) {
                if (!legalParameters.contains(key)) {
                    throw new IllegalArgumentException("A feed generator is configured to match on parameter value '"
                            + key + "' that is invalid. Legal values are: " + legalParameters);
                }
            }

        }

    }

    @Required
    public void setFeedGenerator(FeedGenerator feedGenerator) {
        this.feedGenerator = feedGenerator;
    }

    @Required
    public void setFeedParameterSetAssertion(RequestParameterAssertion feedParameterSetAssertion) {
        this.feedParameterSetAssertion = feedParameterSetAssertion;
    }

    public void setAlternateGenerators(Map<String, FeedGenerator> alternateGenerators) {
        this.alternateGenerators = alternateGenerators;
    }

}
