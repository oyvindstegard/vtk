/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.*;
import vtk.repository.store.Revisions;
import vtk.security.Principal;
import vtk.util.io.IO;
import vtk.util.io.SizeLimitException;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;
import vtk.web.filter.UploadLimitInputStreamFilter;
import vtk.web.service.WebAssertion;
import vtk.web.service.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.List;

/**
 * Handler for PUT requests.
 */
public class PutController implements Controller {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final long maxUploadSize;
    private final long maxRevisionFileSize;
    private final List<WebAssertion> revisionAssertions;

    public PutController(
            long maxUploadSize,
            long maxRevisionFileSize,
            List<WebAssertion> revisionAssertions
    ) {
        if (maxUploadSize == 0) {
            throw new IllegalArgumentException(
                    "Invalid upload size: " + maxUploadSize
                            + " (must be a number != 0)");
        }
        this.maxUploadSize = maxUploadSize;
        this.maxRevisionFileSize = maxRevisionFileSize;
        this.revisionAssertions = revisionAssertions;
    }

    public ModelAndView handleRequest(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws Exception {
        if (this.maxUploadSize > 0 && request.getContentLengthLong() > this.maxUploadSize) {
            throw new SizeLimitException(
                    "Exceeds maximum number of bytes: " + this.maxUploadSize);
        }

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        try {
            Resource resource;
            boolean exists = repository.exists(token, uri);

            if (exists) {
                this.logger.debug("Resource '" + uri + "' already exists");

                resource = repository.retrieve(token, uri, false);
                InputStream inStream = request.getInputStream();
                ContentInputSource source;
                if (handleRevisions(request, resource)) {
                    byte[] buffer = IO.read(inStream).perform();
                    List<Revision> revisions = repository.getRevisions(token, uri);
                    Revision prev = revisions.isEmpty() ? null : revisions.get(0);
                    String checksum = Revisions.checksum(buffer);
                    if (prev == null || !checksum.equals(prev.getChecksum())) {
                        // Take snapshot of previous version:
                        repository.createRevision(token, uri, Revision.Type.REGULAR);
                    }
                    source = ContentInputSources.fromBytes(buffer);
                } else {
                    source = ContentInputSources.fromStream(inStream);
                }
                repository.storeContent(token, uri, source);

            } else {
                this.logger.debug("Resource does not exist (creating)");
                /* check for parent: */
                Path parentURI = uri.getParent();
                repository.retrieve(token, parentURI, false);

                InputStream inStream = request.getInputStream();
                resource = repository.createDocument(token, uri, ContentInputSources.fromStream(inStream));
            }

            if (exists) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                response.setStatus(HttpServletResponse.SC_CREATED);
                URL location = URL.create(request);
                location.setPath(resource.getURI());

                response.setHeader("Location", location.toString());
            }

        } catch (ResourceNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (ResourceLockedException e) {
            response.setStatus(HttpUtil.SC_LOCKED);
        } catch (IllegalOperationException | ReadOnlyException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        return null;
    }

    private boolean handleRevisions(HttpServletRequest request, Resource resource) {
        Principal principal = RequestContext.getRequestContext(request).getPrincipal();
        for (WebAssertion a : this.revisionAssertions) {
            if (!a.matches(request, resource, principal)) {
                return false;
            }
        }
        return maxRevisionFileSize <= 0 || request.getContentLengthLong() <= maxRevisionFileSize;
    }
}
