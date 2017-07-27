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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import vtk.repository.Comment;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.web.RequestContext;

/**
 * Controller that removes a {@link Comment comment} from a {@link
 * Resource resource}.
 * 
 */
public class DeleteCommentController extends AbstractController implements InitializingBean {

    private String viewName;
    private boolean deleteAllComments = false;
    
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
    
    public void setDeleteAllComments(boolean deleteAllComments) {
        this.deleteAllComments = deleteAllComments;
    }
    
    public void afterPropertiesSet() throws Exception {
        if (this.viewName == null)
            throw new BeanInitializationException("Property 'viewName' must be set");
    }

    protected String getViewName() {
        return this.viewName;
    }

    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        Resource resource = repository.retrieve(token, uri, true);

        if (this.deleteAllComments) {
            repository.deleteAllComments(token, null, resource);
        } else {
            String id = request.getParameter("comment-id");
            if (id == null) throw new ResourceNotFoundException(uri);
            List<Comment> comments = repository.getComments(token, resource);
            Comment comment = findComment(id, comments);
            if (comment == null) {
                throw new ResourceNotFoundException(uri);
            }
            repository.deleteComment(token, null, resource, comment);
        }
        
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("resource", resource);

        return new ModelAndView(this.viewName, model);
    }

    private Comment findComment(String id, List<Comment> comments) {
        for (Comment c: comments) {
            if (c.getID().equals(id)) {
                return c;
            }
        }
        return null;
    }
}
