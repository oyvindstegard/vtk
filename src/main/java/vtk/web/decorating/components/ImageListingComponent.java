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
package vtk.web.decorating.components;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.search.Listing;
import vtk.web.search.SearchComponent;

public class ImageListingComponent extends ViewRenderingDecoratorComponent {

    private static final int LIMIT = 5; // default limit
    private static final int MAX_FADE_EFFECT = 999; // ms
    private static final String MAX_HEIGHT_DEFAULT = "4-3";
    private static final String MAX_HEIGHT_WIDESCREEN = "16-9";
    private static final String MAX_HEIGHT_NONE = "none";
    
    private static final String PARAMETER_URI = "uri";
    private static final String PARAMETER_URI_DESC = "URI of the image folder to include pictures from (root-relative or absolute).";

    private static final String PARAMETER_TYPE = "type";
    private static final String PARAMETER_TYPE_DESC = "How to the display the component. Default is 'list'. "
            + "'gallery' will display an embedded gallery.";

    private static final String PARAMETER_LIMIT = "limit";
    private static final String PARAMETER_LIMIT_DESC = "Maximum number of images to show in list. Default is 5.";

    private static final String PARAMETER_FADE_EFFECT = "fade-effect";
    private static final String PARAMETER_FADE_EFFECT_DESC = "Milliseconds of fade effect for choosen image. "
            + "Default is 0 or off, and max is 999 ms.";

    private static final String PARAMETER_HIDE_THUMBNAILS = "hide-thumbnails";
    private static final String PARAMETER_HIDE_THUMBNAILS_DESC = "Optional parameter used when parameter 'type' is set to 'gallery'. "
            + "When set to 'true', will hide thumbnails in gallery view. Default is 'false'.";
    
    private static final String PARAMETER_MAX_HEIGHT = "max-height";
    private static final String PARAMETER_MAX_HEIGHT_DESC = "Optional parameter used when parameter 'type' is set to 'gallery'. "
            + "Default is 4-3 (on). Can be set to '4-3', '16-9' or 'none' (off).";

    private static final String PARAMETER_EXCLUDE_SCRIPTS = "exclude-scripts";
    private static final String PARAMETER_EXCLUDE_SCRIPTS_DESC = "Use to exclude multiple inclusion of scripts for gallery display. "
            + "Set to 'true' when including more than one image gallery on same page. Default is 'false'.";

    private SearchComponent searchComponent;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        String pathUriParameter = request.getStringParameter(PARAMETER_URI);
        if (pathUriParameter == null || "".equals(pathUriParameter.trim())) {
            return;
        }

        Path path = this.getValidPath(pathUriParameter);
        if (path == null) {
            // Invalid path parameter
            return;
        }

        String requestLimit = request.getStringParameter(PARAMETER_LIMIT);
        int searchLimit = getSearchLimit(requestLimit);

        String type = request.getStringParameter(PARAMETER_TYPE);
        if (type != null && type.equals("gallery")) {
            model.put("type", "gallery");
        } else {
            model.put("type", "list");
        }

        String fadeFx = request.getStringParameter(PARAMETER_FADE_EFFECT);
        int fadeEffect = getFadeEffect(fadeFx);

        String hideThumbnails = request.getStringParameter(PARAMETER_HIDE_THUMBNAILS);
        if ("true".equals(hideThumbnails)) {
            model.put("hideThumbnails", true);
        }
        
        String maxHeight = request.getStringParameter(PARAMETER_MAX_HEIGHT);
        model.put("maxHeight", getMaxHeight(maxHeight));

        String excludeScripts = request.getStringParameter("");
        if (excludeScripts != null && "true".equalsIgnoreCase(excludeScripts.trim())) {
            model.put("excludeScripts", excludeScripts);
        }

        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Repository repository = requestContext.getRepository();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460
        Resource requestedResource = repository.retrieve(token, path, false);

        if (!requestedResource.isCollection()) {
            return;
        }

        Listing images = searchComponent.execute(request.getServletRequest(),
                requestedResource, 1, searchLimit, 0);

        model.put("images", images.getEntries());
        model.put("folderTitle", requestedResource.getTitle());
        model.put("folderUrl", pathUriParameter);
        model.put("fadeEffect", fadeEffect);
        
        model.put("unique", System.nanoTime() + "");

    }

    private Path getValidPath(String pathUriParameter) {
        try {
            return Path.fromStringWithTrailingSlash(pathUriParameter);
        } catch (IllegalArgumentException iae) {
            // Invalid pathUriParameter
        }
        return null;
    }

    private int getSearchLimit(String requestLimit) {
        try {
            int lim = Integer.parseInt(requestLimit);
            if (lim > 0) {
                return lim;
            }
        } catch (NumberFormatException nfe) {
        }
        return LIMIT;
    }

    private int getFadeEffect(String fadeEffect) {
        try {
            int fadeFx = Integer.parseInt(fadeEffect);
            if (fadeFx <= MAX_FADE_EFFECT && fadeFx > 0) {
                return fadeFx;
            }
        } catch (NumberFormatException nfe) {
        }
        return 0;
    }
    
    private String getMaxHeight(String maxHeight) {
        String max = MAX_HEIGHT_DEFAULT;
        if(MAX_HEIGHT_NONE.equals(maxHeight)) {
            max = MAX_HEIGHT_NONE;
        } else if(MAX_HEIGHT_WIDESCREEN.equals(maxHeight)) {
            max = MAX_HEIGHT_WIDESCREEN;
        }
        return max;
    }

    @Required
    public void setSearchComponent(SearchComponent searchComponent) {
        this.searchComponent = searchComponent;
    }

    @Override
    protected String getDescriptionInternal() {
        return "Inserts an image list or gallery, depending on parameter setup.";
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(PARAMETER_EXCLUDE_SCRIPTS, PARAMETER_EXCLUDE_SCRIPTS_DESC);
        map.put(PARAMETER_MAX_HEIGHT, PARAMETER_MAX_HEIGHT_DESC);
        map.put(PARAMETER_HIDE_THUMBNAILS, PARAMETER_HIDE_THUMBNAILS_DESC);
        map.put(PARAMETER_FADE_EFFECT, PARAMETER_FADE_EFFECT_DESC);
        map.put(PARAMETER_LIMIT, PARAMETER_LIMIT_DESC);
        map.put(PARAMETER_TYPE, PARAMETER_TYPE_DESC);
        map.put(PARAMETER_URI, PARAMETER_URI_DESC);
        return map;
    }
}
