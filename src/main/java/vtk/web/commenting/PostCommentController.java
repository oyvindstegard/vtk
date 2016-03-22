/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.commenting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.text.html.EnclosingHtmlContent;
import vtk.text.html.HtmlComment;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlElementImpl;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.text.html.HtmlText;
import vtk.text.html.HtmlUtil;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Gets a comment from form input and adds it to the current resource.
 * Optionally stores the binding errors object in the session.
 */
public class PostCommentController extends SimpleFormController<PostCommentCommand> {
    private final Logger logger = LoggerFactory.getLogger(PostCommentController.class);
    
    private String formSessionAttributeName;
    private HtmlPageParser parser;
    private HtmlPageFilter htmlFilter;
    private PolicyFactory htmlSanitizerPolicy;
    private int maxCommentLength = 10000;
    private boolean requireCommentTitle = false;

    @Override
    protected PostCommentCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Service service = requestContext.getService();
        Resource resource = repository.retrieve(token, 
                requestContext.getResourceURI(), false);
        URL url = service.constructURL(resource, requestContext.getPrincipal());
        PostCommentCommand command = new PostCommentCommand(url);
        return command;
    }


    @Override
    protected void onBindAndValidate(HttpServletRequest request, PostCommentCommand command,
            BindException errors) throws Exception {

        if (!"POST".equals(request.getMethod()) && this.formSessionAttributeName != null) {
            if (request.getSession(false) != null) {
                request.getSession().removeAttribute(this.formSessionAttributeName);
            }
        }

        if (command.getCancelAction() != null)
            return;

        if (this.requireCommentTitle && StringUtils.isBlank(command.getTitle())) {
            errors.rejectValue("title", "commenting.post.title.missing",
                            "You must provide a title");
        }

        String commentText = command.getText();
        if (StringUtils.isBlank(commentText)) {
            errors.rejectValue("text", "commenting.post.text.missing",
                    "You must type something in the comment field");
        }

        // Apply first HTML sanitation step if configured (using OWASP policy)
        if (this.htmlSanitizerPolicy != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Input text before OWASP sanitation: " + commentText);
            }
            commentText = this.htmlSanitizerPolicy.sanitize(commentText);
            if (logger.isDebugEnabled()) {
                logger.debug("Input text after OWASP sanitation: " + commentText);
            }
        }

        // Apply second parse/cleanup step
        String parsedText = parseContent(commentText);
        
        // Check cleaned up value and possibly reject
        if (StringUtils.isBlank(parsedText)) {
            errors.rejectValue("text", "commenting.post.text.missing",
                    "You must type something in the comment field");
        }
        else if (parsedText.length() > this.maxCommentLength) {
            errors.rejectValue("text", "commenting.post.text.toolong", new Object[] {
                    parsedText.length(), this.maxCommentLength },
                    "Value too long: maximum length is " + this.maxCommentLength);
        }

        command.setParsedText(parsedText);
        if (this.formSessionAttributeName == null) {
            return;
        }
        if (errors.hasErrors()) {
            Map<String, Object> map = new HashMap<>();
            map.put("form", command);
            map.put("errors", errors);
            request.getSession(true).setAttribute(this.formSessionAttributeName, map);
        }
        else {
            if (request.getSession(false) != null) {
                request.getSession().removeAttribute(this.formSessionAttributeName);
            }
        }
    }

    

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, PostCommentCommand commentCommand,
            BindException errors) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Resource resource = repository.retrieve(token, uri, false);

        if (commentCommand.getCancelAction() != null) {
            commentCommand.setDone(true);
            return new ModelAndView(getSuccessView());
        }
        String title = commentCommand.getTitle();
        String text = commentCommand.getParsedText();
        repository.addComment(token, resource, title, text);
        return new ModelAndView(getSuccessView());
    }
    
    protected String parseContent(String text) throws Exception {
        if (this.parser != null) {
            HtmlFragment fragment = this.parser.parseFragment(text);
            fragment.filter(this.htmlFilter);
            List<HtmlContent> nodes = fragment.getContent();
            boolean empty = true;
            for (HtmlContent c : nodes) {
                if (!isEmptyContent(c)) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                return null;
            }
            nodes = trimNodes(nodes);

            List<HtmlContent> content = new ArrayList<>();
            HtmlElement currentParagraph = new HtmlElementImpl("p", true, false);
            content.add(currentParagraph);
            for (HtmlContent c : nodes) {
                if (c instanceof HtmlElement && "p".equals(((HtmlElement) c).getName())) {
                    content.add(c);
                    currentParagraph = new HtmlElementImpl("p", true, false);
                    content.add(currentParagraph);
                } else {
                    currentParagraph.addContent(c);
                }
            }

            StringBuilder result = new StringBuilder();
            for (HtmlContent c : content) {
                if (c instanceof HtmlElement) {
                    result.append(((HtmlElement) c).getEnclosedContent());
                } else if (c instanceof HtmlText) {
                    result.append(HtmlUtil.encodeBasicEntities(c.getContent()));
                }
            }
            return result.toString();
        }
        return text;
    }


    private List<HtmlContent> trimNodes(List<HtmlContent> nodes) {
        List<HtmlContent> result = new ArrayList<>();
        boolean contentBegun = false;
        for (HtmlContent node : nodes) {
            if (!contentBegun && (node instanceof HtmlText)) {
                String childContent = node.getContent();
                if (childContent.trim().equals("")) {
                    continue;
                } else if (!contentBegun) {
                    contentBegun = true;
                }
            }
            result.add(node);
        }

        for (int i = result.size() - 1; i > 0; i--) {
            HtmlContent c = result.get(i);
            if (c instanceof HtmlText && "".equals(c.getContent())) {
                result.remove(i);
            }
        }

        return result;
    }


    private boolean isEmptyContent(HtmlContent node) {
        if (node instanceof EnclosingHtmlContent) {
            HtmlContent[] children = ((EnclosingHtmlContent) node).getChildNodes();
            for (HtmlContent child : children) {
                if (!isEmptyContent(child)) {
                    return false;
                }
            }
            return true;
        } else if (node instanceof HtmlText) {
            return "".equals(node.getContent().trim());

        } else if (node instanceof HtmlComment) {
            return true;
        }
        return false;
    }
    
    public void setHtmlSanitizerPolicy(PolicyFactory sanitizerPolicy) {
        this.htmlSanitizerPolicy = sanitizerPolicy;
    }
    
    @Required
    public void setHtmlParser(HtmlPageParser parser) {
        this.parser = parser;
    }

    public void setFormSessionAttributeName(String formSessionAttributeName) {
        this.formSessionAttributeName = formSessionAttributeName;
    }

    @Required
    public void setHtmlFilter(HtmlPageFilter htmlFilter) {
        this.htmlFilter = htmlFilter;
    }

    public void setMaxCommentLength(int maxCommentLength) {
        this.maxCommentLength = maxCommentLength;
    }

    public void setRequireCommentTitle(boolean requireCommentTitle) {
        this.requireCommentTitle = requireCommentTitle;
    }

}
