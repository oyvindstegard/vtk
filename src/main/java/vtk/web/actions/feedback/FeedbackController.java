/* Copyright (c) 2011, University of Oslo, Norway
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

package vtk.web.actions.feedback;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.edit.editor.ResourceWrapperManager;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.mail.MailExecutor;
import vtk.util.mail.MailHelper;
import vtk.util.mail.MailTemplateProvider;
import vtk.web.RequestContext;
import vtk.web.service.Service;

public class FeedbackController implements Controller {

    private String viewName;
    private String siteName;
    private ResourceWrapperManager resourceManager;
    private MailExecutor mailExecutor;
    private MailTemplateProvider mailTemplateProvider;
    private LocaleResolver localeResolver;
    private Service viewService;
    private String displayUpscoping;
    private String[] recipients;
    private String recipientsStr;
    
    private String sender;

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();

        Resource resource = repository.retrieve(token, uri, true);
        if (resource == null) {
            return null;
        }

        String language = resource.getContentLanguage();
        if (language == null) {
            Locale locale = localeResolver.resolveLocale(request);
            language = locale.toString();
        }

        Map<String, Object> model = new HashMap<String, Object>();
        
        model.put("resource", this.resourceManager.createResourceWrapper());

        if(this.displayUpscoping != null) {
          model.put("displayUpscoping", displayUpscoping);
        }

        String method = request.getMethod();

        String title = resource.getTitle();
        String url = this.viewService.constructURL(uri).toString();
        
        String yourComment = request.getParameter("yourComment");
        String userAgentViewport = request.getParameter("userAgentViewport");

        String[] recipients = this.recipients;
        String recipientsStr = this.recipientsStr;
        boolean validAddresses = true;
        
        String mailToParam = request.getParameter("mailto");
        if (!StringUtils.isBlank(mailToParam)) {
            String[] addresses = mailToParam.split(",");
            for (String addr: addresses) {
                if (!MailExecutor.isValidEmail(addr)) {
                    validAddresses = false;
                }
            }
            recipientsStr = mailToParam;
            recipients = addresses;
        }
 
        String contactUrl = request.getParameter("contacturl");
        if (!StringUtils.isBlank(contactUrl)) {
            model.put("contacturl", contactUrl);
        }

        if (!validAddresses) {
            model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_INVALID_EMAILS);
            model.put("yourSavedComment", yourComment);
            return new ModelAndView(this.viewName, model);
        }
        
        model.put("mailto", recipientsStr);
        
        if (!method.equals("POST")) {
            return new ModelAndView(this.viewName, model);
        }    

        if (StringUtils.isBlank(yourComment)) {
            model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_EMPTY_FIELDS);
            return new ModelAndView(this.viewName, model);
        }

        try {
            org.springframework.web.servlet.support.RequestContext springRequestContext = 
                    new org.springframework.web.servlet.support.RequestContext(request);
            
            boolean yourCommentIsMail = request.getParameter("yourcommentismail") != null;
            
            String mailBody = "";
            if(yourCommentIsMail) {
                mailBody = yourComment;
            } else {
                mailBody = mailTemplateProvider.generateMailBody(title, url, this.sender, yourComment, userAgentViewport != null ? userAgentViewport : "", this.siteName);
            }

            MimeMessage mimeMessage = mailExecutor.createMimeMessage(
                    mailBody,
                    recipients,
                    this.sender,
                    false,
                    yourCommentIsMail ? title 
                                      : springRequestContext.getMessage("feedback.mail.subject-header-prefix") + ": " + title
            );

            mailExecutor.enqueue(mimeMessage);

            model.put("emailSentTo", recipientsStr);
            model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_OK);
        } catch (Exception mtex) { // Unreachable because of thread / executor
            model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_GENERAL_FAILURE);
            model.put(MailHelper.RESPONSE_MODEL + "Msg", mtex.getMessage());
        }
        return new ModelAndView(this.viewName, model);
    }
    
    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    @Required
    public void setResourceManager(ResourceWrapperManager resourceManager) {
        this.resourceManager = resourceManager;
    }


    @Required
    public void setMailExecutor(MailExecutor mailExecutor) {
        this.mailExecutor = mailExecutor;
    }

    @Required
    public void setMailTemplateProvider(MailTemplateProvider mailTemplateProvider) {
        this.mailTemplateProvider = mailTemplateProvider;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    @Required
    public void setDisplayUpscoping(String displayUpscoping) {
        this.displayUpscoping = displayUpscoping;
    }

    @Required
    public void setSender(String sender) {
        if (!MailExecutor.isValidEmail(sender)) {
            throw new IllegalArgumentException("Invalid 'sender' field: '" + sender + "'");
        }
        this.sender = sender;
    }

    @Required
    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    public void setRecipients(String str) {
        if (str == null || "".equals(str.trim())) {
            return;
        }
        String[] list = str.split(",");
        for (String addr: list) {
            if (!MailExecutor.isValidEmail(addr)) {
                throw new IllegalArgumentException("Invalid recipient: '" + addr + "'");
            }
        }
        this.recipients = list;
        this.recipientsStr = str;
    }

}
