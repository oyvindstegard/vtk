/* Copyright (c) 2008, University of Oslo, Norway
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Comment;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class RecentCommentsProvider implements ReferenceDataProvider {

    private boolean deepCommentsListing;
    private int maxComments = 100;
    private Service viewService;
    private Service resourceCommentsFeedService;
    private Service recentCommentsService;
    private boolean includeCommentsFromUnpublished;

    public void setDeepCommentsListing(boolean deepCommentsListing) {
        this.deepCommentsListing = deepCommentsListing;
    }

    public void setMaxComments(int maxComments) {
        if (maxComments <= 0) {
            throw new IllegalArgumentException("Number must be a positive integer");
        }
        this.maxComments = maxComments;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    public void setRecentCommentsService(Service recentCommentsService) {
        this.recentCommentsService = recentCommentsService;
    }

    public void setResourceCommentsFeedService(Service resourceCommentsFeedService) {
        this.resourceCommentsFeedService = resourceCommentsFeedService;
    }

    public void setIncludeCommentsFromUnpublished(boolean includeCommentsFromUnpublished) {
        this.includeCommentsFromUnpublished = includeCommentsFromUnpublished;
    }

    @Override
    public void referenceData(final Map<String, Object> model, HttpServletRequest servletRequest) {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Path uri = RequestContext.getRequestContext().getResourceURI();

        // VTK-2460
        if (requestContext.isViewUnauthenticated()) {
            token = null;
            principal = null;
        }

        try {

            Resource resource = repository.retrieve(token, uri, true);
            // If deepCommentListing is specified, always find the nearest
            // collection:
            if (!resource.isCollection() && deepCommentsListing) {
                uri = uri.getParent();
                resource = repository.retrieve(token, uri, true);
            }

            List<Comment> comments = repository.getComments(token, resource, 
                    deepCommentsListing, maxComments);

            Map<String, Resource> resourceMap = new HashMap<>();
            Map<String, URL> commentURLMap = new HashMap<>();
            List<Comment> filteredComments = new ArrayList<>();
            for (Comment comment : comments) {
                try { 
                    Resource r = repository.retrieve(token, comment.getURI(), true);

                    // Don't include comments from resources that are not published
                    Property published = r.getProperty(Namespace.DEFAULT_NAMESPACE, 
                            PropertyType.PUBLISHED_PROP_NAME);
                    if (!published.getBooleanValue()) {
                        published = null;
                    }

                    if (!includeCommentsFromUnpublished && published == null) {
                        continue;
                    }

                    filteredComments.add(comment);
                    resourceMap.put(r.getURI().toString(), r);
                    URL commentURL = viewService.urlConstructor(requestContext.getRequestURL())
                            .withResource(r)
                            .withPrincipal(principal)
                            .constructURL();
                    commentURLMap.put(comment.getID(), commentURL);
                }
                catch (Throwable t) { }
            }

            boolean commentsAllowed =
                    repository.isAuthorized(resource, RepositoryAction.ADD_COMMENT, principal, false);

            URL baseCommentURL = null;
            try {
                baseCommentURL = viewService.urlConstructor(requestContext.getRequestURL())
                        .withResource(resource)
                        .withPrincipal(principal)
                        .constructURL();
            }
            catch (Exception e) { }

            URL feedURL = null;
            if (resourceCommentsFeedService != null) {
                try {
                    feedURL = resourceCommentsFeedService.urlConstructor(requestContext.getRequestURL())
                            .withResource(resource)
                            .withPrincipal(principal)
                            .constructURL();
                }
                catch (Exception e) { }
            }

            URL recentCommentsURL = null;
            if (recentCommentsService != null) {
                try {
                    recentCommentsURL = recentCommentsService.urlConstructor(requestContext.getRequestURL())
                            .withResource(resource)
                            .withPrincipal(principal)
                            .constructURL();
                }
                catch (Exception e) { }
            }

            model.put("commentsEnabled", false);
            Property commentsEnabled = resource.getPropertyByPrefix(null, "commentsEnabled");
            if (commentsEnabled != null) {
                model.put("commentsEnabled", commentsEnabled.getBooleanValue());
            }

            model.put("resource", resource);
            model.put("principal", principal);
            model.put("comments", filteredComments);
            model.put("resourceMap", resourceMap);
            model.put("commentURLMap", commentURLMap);
            model.put("commentsAllowed", commentsAllowed);
            model.put("baseCommentURL", baseCommentURL);
            model.put("feedURL", feedURL);
            model.put("recentCommentsURL", recentCommentsURL);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
