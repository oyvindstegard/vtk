/* Copyright (c) 2012 University of Oslo, Norway
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

package vtk.web.actions.publish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.edit.editor.ResourceWrapperManager;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.store.PrincipalMetadata;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.util.mail.MailExecutor;
import vtk.util.mail.MailHelper;
import vtk.util.mail.MailTemplateProvider;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ApprovalViaEmailController implements Controller {
    private String viewName;
    private ResourceWrapperManager resourceManager;
    private MailExecutor mailExecutor;
    private MailTemplateProvider mailTemplateProvider;
    private Service manageService;
    private PropertyTypeDefinition editorialContactsPropDef;
    private PrincipalFactory principalFactory;

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        Principal principal = requestContext.getPrincipal();

        Resource resource = repository.retrieve(token, uri, true);
        if (resource == null) {
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        String method = request.getMethod();

        boolean userEmailFound = true;
        String emailFrom = getUserEmail(principal.getQualifiedName());
        if (StringUtils.isBlank(emailFrom)) {
            model.put("userEmailFrom", true);
            emailFrom = principal.getQualifiedName();
            userEmailFound = false;
        }

        String editorialContacts = getEditorialContactEmails(resource);
        if (editorialContacts != null) {
            model.put("editorialContacts", editorialContacts);
        }

        String title = resource.getTitle();
        URL url = manageService.urlConstructor(URL.create(request))
                .withURI(uri)
                .constructURL();
        
        String[] subjectParams = { getLocalizedMsg(request, "resourcetype.name." + resource.getResourceType(),
                new Object[0]) };
        String subject = getLocalizedMsg(request, "send-to-approval.subject", subjectParams);
        String mailBody = mailTemplateProvider.generateMailBody(request, title, url.toString(), emailFrom, "", "", "");

        if (method.equals("POST")) {
            String emailTo = request.getParameter("emailTo");
            if (!userEmailFound) {
                emailFrom = request.getParameter("emailFrom");
            }
            String yourComment = request.getParameter("yourComment");

            if (StringUtils.isBlank(emailTo) || (!userEmailFound && StringUtils.isBlank(emailFrom))) {
                if (StringUtils.isNotBlank(emailTo)) {
                    model.put("emailSavedTo", emailTo);
                }
                if (StringUtils.isNotBlank(emailFrom) && !userEmailFound) {
                    model.put("emailSavedFrom", emailFrom);
                }
                if (StringUtils.isNotBlank(yourComment)) {
                    model.put("yourSavedComment", yourComment);
                }
                model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_EMPTY_FIELDS);
            } else {
                try {
                    String comment = "";
                    if (StringUtils.isNotBlank(yourComment)) {
                        comment = yourComment;
                    }
                    boolean validAddresses = true;
                    String[] emailMultipleTo = emailTo.split(",");
                    for (String addr : emailMultipleTo) {
                        if (!MailExecutor.isValidEmail(addr)) {
                            validAddresses = false;
                            break;
                        }
                    }
                    if (validAddresses && MailExecutor.isValidEmail(emailFrom)) {
                        mailBody = mailTemplateProvider.generateMailBody(request,
                                title, url.toString(), emailFrom, comment, "", "");

                        MimeMessage mimeMessage = mailExecutor.createMimeMessage(mailBody, emailMultipleTo, emailFrom,
                                true, subject);

                        mailExecutor.enqueue(mimeMessage);

                        model.put("emailSentTo", emailTo);
                        model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_OK);
                    } else {
                        model.put("emailSavedTo", emailTo);
                        model.put("emailSavedFrom", emailFrom);

                        if (!StringUtils.isBlank(yourComment)) {
                            model.put("yourSavedComment", yourComment);
                        }
                        model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_INVALID_EMAILS);
                    }
                } catch (Exception mtex) { // Unreachable because of thread
                    model.put(MailHelper.RESPONSE_MODEL, MailHelper.RESPONSE_GENERAL_FAILURE);
                    model.put(MailHelper.RESPONSE_MODEL + "Msg", ": " + mtex.getMessage());
                }
            }
        }
        model.put("emailSubject", subject);
        model.put("emailBody", mailBody);
        model.put("resource", resourceManager.createResourceWrapper(request));
        return new ModelAndView(viewName, model);
    }

    public String getEditorialContactEmails(Resource resource) {
        Property editorialContactsProp = resource.getProperty(editorialContactsPropDef);
        if (editorialContactsProp != null) {
            Value[] editorialContactsVals = editorialContactsProp.getValues();
            StringBuilder sb = new StringBuilder();
            for (Value editorialContactsVal : editorialContactsVals) {
                sb.append(editorialContactsVal.getStringValue() + ", ");
            }
            String editorialContacts = sb.toString();
            if (editorialContacts.length() > 2) {
                return editorialContacts.substring(0, editorialContacts.length() - 2);
            }
        }
        return null;
    }

    public String getUserEmail(String qualifiedName) {
        if (qualifiedName.endsWith("@uio.no")) {
            Principal principal = principalFactory.getPrincipal(qualifiedName, Principal.Type.USER);
            PrincipalMetadata principalMetaData = principal.getMetadata();
            if (principalMetaData != null) {
                List<Object> emails = principalMetaData.getValues("email");
                if (emails != null && !emails.isEmpty()) {
                    String email = emails.get(0).toString();
                    if (MailExecutor.isValidEmail(email)) {
                        return email;
                    }
                }
            }
        }
        return null;
    }

    private String getLocalizedMsg(HttpServletRequest request, String key, Object[] params) {
        org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                request);
        if (params != null) {
            return springRequestContext.getMessage(key, params);
        }
        return springRequestContext.getMessage(key);
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
    public void setManageService(Service manageService) {
        this.manageService = manageService;
    }

    @Required
    public void setEditorialContactsPropDef(PropertyTypeDefinition editorialContactsPropDef) {
        this.editorialContactsPropDef = editorialContactsPropDef;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

}
