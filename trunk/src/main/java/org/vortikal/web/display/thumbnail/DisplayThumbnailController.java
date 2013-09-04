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
package org.vortikal.web.display.thumbnail;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;
import org.vortikal.repository.ContentStream;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.util.io.StreamUtil;
import org.vortikal.web.RequestContext;

public class DisplayThumbnailController implements Controller, LastModified {

    private static final Logger log = Logger.getLogger(DisplayThumbnailController.class);

    private static final String VIDEO_LOGO = "/web/themes/default/icons/video-icon.png";
    private static final String VIDEO_LOGO_CONTENT_TYPE = "image/png";
    private static final String ADUIO_LOGO = "/web/themes/default/icons/audio-icon.png";
    private static final String AUDIO_LOGO_CONTENT_TYPE = "image/png";

    @Override
    public long getLastModified(HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        try {
            Resource resource = repository.retrieve(token, requestContext.getResourceURI(), true);
            return resource.getLastModified().getTime();
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        Resource image = repository.retrieve(token, uri, true);
        Property thumbnail = image.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.THUMBNAIL_PROP_NAME);

        if (thumbnail == null || StringUtils.isBlank(thumbnail.getBinaryContentType())) {
            String resourceType = image.getResourceType().trim();
            if ("image".equals(resourceType)) {
                if (log.isDebugEnabled()) {
                    String detailedMessage = thumbnail == null ? "no thumbnail found (null)" : "no mimetype set";
                    log.debug("Cannot display thumbnail for image: " + uri + ", " + detailedMessage);
                }
                response.sendRedirect(uri.toString());
            } else if ("audio".equals(resourceType)) {
                InputStream in = this.getClass().getResourceAsStream(ADUIO_LOGO);
                response.setContentType(AUDIO_LOGO_CONTENT_TYPE);
                StreamUtil.pipe(in, response.getOutputStream());
            } else if ("video".equals(resourceType)) {
                InputStream in = this.getClass().getResourceAsStream(VIDEO_LOGO);
                response.setContentType(VIDEO_LOGO_CONTENT_TYPE);
                StreamUtil.pipe(in, response.getOutputStream());
            }
            return null;
        } else {
            ContentStream binaryStream = thumbnail.getBinaryStream();
            String mimetype = thumbnail.getBinaryContentType();
            response.setContentType(mimetype);
            int length = (int) binaryStream.getLength();
            response.setContentLength(length);
            StreamUtil.pipe(binaryStream.getStream(), response.getOutputStream(), length, true);
            return null;
        }
    }
}
