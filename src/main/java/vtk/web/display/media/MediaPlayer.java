/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.web.display.media;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.AuthenticationException;
import vtk.util.repository.MimeHelper;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * XXX This class needs complete documentation on model attributes for the two main "provider" methods.
 *
 * <p>
 * Class adds model data required for displaying media files in web pages using an embedded player/placeholder and
 * download link. Used both by component for insertion at arbitrary locations in HTML documents or the web page view of
 * Vortex media resources.
 */
public class MediaPlayer {

    private Service viewService;
    private Service thumbnailService;
    private PropertyTypeDefinition posterImagePropDef;
    private PropertyTypeDefinition thumbnailPropDef;
    private PropertyTypeDefinition hideVideoDownloadLinkPropDef;
    private PropertyTypeDefinition hideVideoFallbackLinkPropDef;

    /**
     * Provided model data:
     * <ul>
     * <li><code>mediaResource</code> - if media reference points to a local resource, then the {@link Resource} will be
     * provided under this key if it could be retrieved from the repository.</li>
     * <li><code>media</code> - a {@link URL} instance pointing to the media.
     * <li>TODO complete me</li>
     * </ul>
     *
     * @param model model to add data to
     * @param mediaRef the media resource URI as a string
     * @param height height of inserted video
     * @param width width of inserted video
     * @param autoplay whether to setup player to start automatically or not.
     * @param contentType
     * @param streamType
     * @param poster
     * @param showDL
     * @throws AuthorizationException
     */
    public void addMediaPlayer(HttpServletRequest request,
            Map<String, Object> model, String mediaRef, String height, String width,
            String autoplay, String contentType, String streamType, String poster, String showDL) {

        if (URL.isEncoded(mediaRef)) {
            mediaRef = urlDecodeMediaRef(mediaRef);
        }

        RequestContext requestContext = RequestContext.getRequestContext(request);
        
        Resource mediaResource = null;
        try {
            mediaResource = getLocalResource(requestContext, mediaRef);
        } catch (AuthorizationException | AuthenticationException e) {
            return; // not able to read local resource - abort
        } catch (Exception e) {
            // ignore
        }

        if (mediaResource != null) {
            model.put("mediaResource", mediaResource);
        }

        if ((height != null && !"".equals(height)) && (width != null && !"".equals(width))) {
            model.put("height", height);
            model.put("width", width);
        }

        if (autoplay != null && !autoplay.isEmpty()) {
            model.put("autoplay", autoplay);
        }

        if (streamType != null && !streamType.isEmpty()) {
            model.put("streamType", streamType);
        }

        model.put("showDL", showDL != null && showDL.equalsIgnoreCase("true") ? "true" : "false");

        if (poster != null && !poster.isEmpty()) {
            model.put("poster", poster);
        } else {
            addPosterUrl(request, mediaResource, model);
        }

        addLinkProperties(mediaResource, model);

        if (contentType != null && !contentType.isEmpty()) {
            model.put("contentType", contentType);
        }
        else if (mediaResource != null) {
            model.put("contentType", mediaResource.getContentType());
        }
        else {
            model.put("contentType", MimeHelper.map(mediaRef));
        }
        model.put("extension", MimeHelper.findExtension(mediaRef));
        model.put("nanoTime", System.nanoTime());

        if (mediaResource != null) {
            addMediaUrl(request, mediaResource, model);
        }
        else {
            addMediaUrl(request, mediaRef, model);
        }
    }

    /**
     * Provided model data:
     * <ul>
     * <li><code>mediaResource</code> - if media reference points to a local resource, then the {@link Resource} will be
     * provided under this key if it could successfully be retrieved from the repository.</li>
     * <li><code>media</code> - a {@link URL} instance pointing to the media.
     * <li>TODO complete me</li>
     * </ul>
     *
     * @param model MVC model
     * @param mediaRef media reference/link as string
     *
     */
    public void addMediaPlayer(HttpServletRequest request, Map<String, Object> model, String mediaRef) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        if (URL.isEncoded(mediaRef)) {
            mediaRef = urlDecodeMediaRef(mediaRef);
        }

        Resource mediaResource = null;
        try {
            mediaResource = getLocalResource(requestContext, mediaRef);
        }
        catch (AuthorizationException | AuthenticationException e) {
            return; // not able to read local resource - abort
        }
        catch (Exception e) {
            // ignore
        }

        if (mediaResource != null) {
            model.put("mediaResource", mediaResource);
        }

        addPosterUrl(request, mediaResource, model);
        addLinkProperties(mediaResource, model);
        model.put("extension", MimeHelper.findExtension(mediaRef));

        if (mediaResource != null) {
            model.put("contentType", mediaResource.getContentType());
        }
        else {
            model.put("contentType", MimeHelper.map(mediaRef));
        }

        model.put("nanoTime", System.nanoTime());

        if (mediaResource != null) {
            addMediaUrl(request, mediaResource, model);
        }
        else {
            addMediaUrl(request, mediaRef, model);
        }
    }

    private Resource getLocalResource(RequestContext requestContext, String resourceRef) throws Exception {
        if (resourceRef != null && resourceRef.startsWith("/")) {
            Repository repository = requestContext.getRepository();
            String token = requestContext.getSecurityToken();
            return repository.retrieve(token, Path.fromString(resourceRef), true);
        }

        return null;
    }

    private void addMediaUrl(HttpServletRequest request, Resource mediaResource, Map<String, Object> model) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        URL mediaURL = viewService.urlConstructor(requestContext.getRequestURL())
                .withURI(mediaResource.getURI())
                .constructURL();
                
        model.put("media", mediaURL);
    }

    // Adds media URL to model, possibly non-local or unreadable local resource. Local resources are resolved to absolute
    // URLs and external URLs are parsed for validity. In case of invalid
    // URL, nothing is added to model.
    private void addMediaUrl(HttpServletRequest request, String resourceRef, Map<String, Object> model) {
        URL url = createUrl(request, resourceRef);
        if (url != null) {
            RequestContext requestContext = RequestContext
                    .getRequestContext(request);
            if (requestContext.isPreviewUnpublished()) {
                url.setParameter("vrtxPreviewUnpublished", "true");
            }
            model.put("media", url);
        }
    }

    private void addPosterUrl(HttpServletRequest request, Resource mediaResource, Map<String, Object> model) {
        if (mediaResource == null) {
            return;
        }
        URL poster = null;
        Property posterImageProp = mediaResource.getProperty(posterImagePropDef);
        Property thumbnail = mediaResource.getProperty(thumbnailPropDef);
        if (posterImageProp != null) {
            poster = createUrl(request, posterImageProp.getStringValue());
        }
        else if (thumbnail != null) {
            RequestContext requestContext = RequestContext.getRequestContext(request);
            poster = thumbnailService.urlConstructor(requestContext.getRequestURL())
                    .withURI(mediaResource.getURI())
                    .constructURL();
            // Work-around for SelectiveProtocolManager URL post-processing which sets
            // URL protocol to "http" for open resources, but this causes mixed-mode
            // in secure page context, since this URL points to an inline element (image).
            if (request.isSecure()) {
                poster.setProtocol("https");
            }
        }

        if (poster != null) {
            model.put("poster", poster);
        }
    }

    private void addLinkProperties(Resource mediaResource, Map<String, Object> model) {
        if (mediaResource == null) {
            return;
        }

        Property hideVideoDownloadLinkProp = mediaResource.getProperty(hideVideoDownloadLinkPropDef);
        Property hideVideoFallbackLinkProp = mediaResource.getProperty(hideVideoFallbackLinkPropDef);

        if (hideVideoDownloadLinkProp != null && hideVideoDownloadLinkProp.getBooleanValue() == true) {
            model.put("hideVideoDownloadLink", true);
        }

        if (hideVideoFallbackLinkProp != null && hideVideoFallbackLinkProp.getBooleanValue() == true) {
            model.put("hideVideoFallbackLink", true);
        }
    }

    /**
     * TODO support references relative to current resource, like "../foo.mp4".
     *
     * @param mediaRef link/reference to a media resource. Must either be root relative path or an absolute URL with
     * protocol.
     *
     * @return a {@link URL} instance, or <code>null</code> if no appropriate URL could be created from reference.
     */
    private URL createUrl(HttpServletRequest request, String mediaRef) {

        if (mediaRef == null) {
            return null;
        }

        if (URL.isEncoded(mediaRef)) {
            mediaRef = urlDecodeMediaRef(mediaRef);
        }

        if (mediaRef.startsWith("/")) {
            URL localURL = null;
            try {
                RequestContext requestContext = RequestContext.getRequestContext(request);

                Path uri = Path.fromString(mediaRef);
                localURL = viewService.urlConstructor(requestContext.getRequestURL())
                        .withURI(uri)
                        .constructURL();
            } catch (Exception e) {
                // ignore
            }
            if (localURL != null) {
                return localURL;
            }

        } else {
            URL externalURL = null;
            try {
                externalURL = URL.parse(mediaRef);
            } catch (Exception e) {
                // ignore
            }
            if (externalURL != null) {
                return externalURL;
            }
        }
        return null;
    }

    private String urlDecodeMediaRef(String mediaRef) {
        // For media file references we don't want '+' chars decoded into spaces.
        return URL.decode(TextUtils.replaceAll(mediaRef, "+", "%2B"));
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setPosterImagePropDef(PropertyTypeDefinition posterImagePropDef) {
        this.posterImagePropDef = posterImagePropDef;
    }

    @Required
    public void setThumbnailService(Service thumbnailService) {
        this.thumbnailService = thumbnailService;
    }

    @Required
    public void setThumbnailPropDef(PropertyTypeDefinition thumbnailPropDef) {
        this.thumbnailPropDef = thumbnailPropDef;
    }

    @Required
    public void setHideVideoDownloadLinkPropDef(PropertyTypeDefinition hideVideoDownloadLinkPropDef) {
        this.hideVideoDownloadLinkPropDef = hideVideoDownloadLinkPropDef;
    }

    @Required
    public void setHideVideoFallbackLinkPropDef(PropertyTypeDefinition hideVideoFallbackLinkPropDef) {
        this.hideVideoFallbackLinkPropDef = hideVideoFallbackLinkPropDef;
    }

}
