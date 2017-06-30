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
package vtk.web.display.collection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Comment;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.tags.RepositoryTagElementsDataProvider;
import vtk.web.tags.TagElement;

public class BlogListingController extends CollectionListingController {

    private RepositoryTagElementsDataProvider tagElementsProvider;
    private Service viewService;
    private Service commentingService;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {
        super.runSearch(request, collection, model, pageLimit);
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        List<TagElement> tagElements = tagElementsProvider.getTagElements(collection.getURI(), token, 1, 5, 20, 1);
        model.put("tagElements", tagElements);
        List<Comment> comments = repository.getComments(token, collection, true, 4);

        Map<String, Resource> resourceMap = new HashMap<>();
        Map<String, URL> urlMap = new HashMap<>();

        for (Comment comment : comments) {
            Resource r = repository.retrieve(token, comment.getURI(), true);
            resourceMap.put(r.getURI().toString(), r);
            URL url = viewService.urlConstructor(URL.create(request)).withURI(r.getURI()).constructURL();
            urlMap.put(r.getURI().toString(), url);
        }
        
        URL moreCommentsURL = commentingService.urlConstructor(URL.create(request))
                .withURI(collection.getURI()).constructURL();

        model.put("comments", comments);
        model.put("urlMap", urlMap);
        model.put("resourceMap", resourceMap);
        model.put("moreCommentsUrl", moreCommentsURL);
    }

    @Required
    public void setTagElementsProvider(RepositoryTagElementsDataProvider tagElementsProvider) {
        this.tagElementsProvider = tagElementsProvider;
    }

    @Required
    public RepositoryTagElementsDataProvider getTagElementsProvider() {
        return tagElementsProvider;
    }

    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    public Service getViewService() {
        return viewService;
    }

    public void setCommentingService(Service commentingService) {
        this.commentingService = commentingService;
    }

    public Service getCommentingService() {
        return commentingService;
    }
}
