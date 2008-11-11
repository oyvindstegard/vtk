/* Copyright (c) 2005, 2008 University of Oslo, Norway
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

package org.vortikal.web.controller.tipafriend;

import java.util.HashMap;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.edit.editor.ResourceWrapperManager;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.web.EmailUtil;
import org.vortikal.web.RequestContext;

public class TipAFriendController implements Controller {
	
	private Repository repository;
	protected String viewName;
	private ResourceWrapperManager resourceManager;
	
	public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		String token = SecurityContext.getSecurityContext().getToken();
		Path uri = RequestContext.getRequestContext().getResourceURI();
		
		Resource document = this.repository.retrieve(token, uri, true);
		Map<String, Object> m = new HashMap<String, Object>();
		
		if (document == null) {
			return null;
		}
		
		// Checks for userinput
		if (request.getParameter("emailto") == null || request.getParameter("emailfrom") == null
				|| request.getParameter("emailto").equals("") || request.getParameter("emailfrom").equals("")) {
			
			m.put("tipResponse", "FAILURE-NULL-FORM");
			
		} else {
			
			try {
				String emailTo = (String) request.getParameter("emailto");
				String emailFrom = (String) request.getParameter("emailfrom");
				
				String[] emailAddresses = { emailTo, emailFrom };
				
				// Checks for valid userinput
				if (EmailUtil.isValidMultipleEmails(emailAddresses, false)) {
					
					JavaMailSenderImpl sender = new JavaMailSenderImpl();
					
					sender.setHost("smtp.uio.no"); // SMTP mail pusher
					// sender.setProtocol("smtp");
					// sender.setPort(25);
					// SSL
					sender.setProtocol("smtps");
					sender.setPort(465);
					
					sender.setDefaultEncoding("utf-8");
					
					MimeMessage msg = createSimpleMailMessage(sender, document, emailTo, emailFrom);
					
					sender.send(msg);
					
					m.put("emailSentTo", emailTo);
					m.put("tipResponse", "OK");
					
				} else {
					
					m.put("tipResponse", "FAILURE-INVALID-EMAIL");
					
				}
			} catch (Exception mex) {
				m.put("tipResponse", "FAILURE");
			}
		}
		
		m.put("resource", this.resourceManager.createResourceWrapper());
		return new ModelAndView(this.viewName, m);
	}
	
	private MimeMessage createSimpleMailMessage(JavaMailSenderImpl sender, Resource document, String emailTo,
			String emailFrom) throws MessagingException {
		
		// TODO: get hostname from Path object
		String hostname = "http://localhost:9322";
		String hostnameShort = StringUtils.capitalize("localhost");
		
		String mailBody = generateMailBody(document.getTitle(), document.getURI().toString(), emailTo, emailFrom,
				hostname, hostnameShort);
		
		MimeMessage mimeMessage = sender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
		
		helper.setSubject(document.getTitle());
		helper.setFrom(emailFrom);
		helper.setTo(emailTo);
		helper.setText(mailBody);
		
		return mimeMessage;
	}
	
	// TODO: localization and refactor in ex. tipafriend.TipAFriendMailTemplateProvider.java
	private String generateMailBody(String title, String articleURI, String mailTo, String mailFrom, String hostname,
			String hostnameShort) {
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("Hei!\n\n");
		
		sb.append(hostnameShort + " har en artikkel jeg tror kan være interessant for deg:\n");
		
		sb.append(title + "\n\n");
		
		sb.append("Les hele artikkelen her: \n");
		sb.append(hostname + articleURI + " \n\n");
		
		sb.append("Med vennlig hilsen,\n");
		
		sb.append(mailFrom + "\n\n\n\n");
		
		sb.append("Denne meldingen er sendt på oppfordring fra " + mailFrom + "\n\n");
		sb.append("--------------------------------------------\n");
		sb.append("Din e-post adresse blir ikke lagret.\n");
		sb.append("Du vil ikke motta flere meldinger av denne typen,\n");
		sb.append("med mindre noen tipser deg om andre nyheter på " + hostname + "/");
		
		return sb.toString();
	}
	
	public void setRepository(Repository repository) {
		this.repository = repository;
	}
	
	@Required
	public void setViewName(String viewName) {
		this.viewName = viewName;
	}
	
	@Required
	public void setResourceManager(ResourceWrapperManager resourceManager) {
		this.resourceManager = resourceManager;
	}
	
}
