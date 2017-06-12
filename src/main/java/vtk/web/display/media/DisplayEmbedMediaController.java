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
package vtk.web.display.media;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;

public class DisplayEmbedMediaController implements Controller {

    private String viewName;
    private MediaPlayer mediaPlayer;
    private Map<String, String> readRestrictedHeaders;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Map<String, Object> model = new HashMap<>();

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();
        Resource resource = repository.retrieve(token, uri, true);

        mediaPlayer.addMediaPlayer(model, uri.toString());

        String autoplay = request.getParameter("autoplay");
        if (autoplay != null && autoplay.equals("true")) {
            model.put("autoplay", autoplay);
        }

        if (resource.isReadRestricted() && readRestrictedHeaders != null) {
            readRestrictedHeaders.entrySet().stream().forEach((e) -> {
                response.addHeader(e.getKey(), e.getValue());
            });
        }

        return new ModelAndView(this.viewName, model);
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    @Required
    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }

    public void setReadRestrictedHeaders(Map<String, String> readRestrictedHeaders) {
        this.readRestrictedHeaders = readRestrictedHeaders;
    }

}
