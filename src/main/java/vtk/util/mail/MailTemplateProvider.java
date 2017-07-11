/* Copyright (c) 2008 University of Oslo, Norway
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
package vtk.util.mail;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.View;

import vtk.web.referencedata.provider.ResourceDetailProvider;
import vtk.web.servlet.BufferedResponse;

public class MailTemplateProvider {

    private View view;
    private ResourceDetailProvider resourceDetailProvider;

    public String generateMailBody(HttpServletRequest request, String title, String url, 
            String mailFrom, String comment, String userAgentViewport, String site) throws Exception {
        return generateMailBody(request, title, url, mailFrom, null, comment, userAgentViewport, site);
    }

    public String generateMailBody(HttpServletRequest request, String title, 
            String url, String mailFrom, String replyTo,
            String comment, String userAgentViewport, String site) throws Exception {

        Map<String, Object> model = new HashMap<>();
        model.put("title", title);
        model.put("mailFrom", mailFrom);
        model.put("comment", comment);
        model.put("userAgentViewport", userAgentViewport);
        model.put("site", site);
        model.put("uri", url);
        model.put("replyTo", replyTo);

        BufferedResponse response = new BufferedResponse();

        if (resourceDetailProvider != null) {
            resourceDetailProvider.referenceData(model, request);
        }

        view.render(model, request, response);
        String mailMessage = response.getContentString();
        return mailMessage;
    }

    @Required
    public void setView(View view) {
        this.view = view;
    }

    public void setResourceDetailProvider(ResourceDetailProvider resourceDetailProvider) {
        this.resourceDetailProvider = resourceDetailProvider;
    }

    public ResourceDetailProvider getResourceDetailProvider() {
        return this.resourceDetailProvider;
    }
}
