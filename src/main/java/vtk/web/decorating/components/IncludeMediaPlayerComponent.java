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
package vtk.web.decorating.components;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.display.media.MediaPlayer;

public class IncludeMediaPlayerComponent extends ViewRenderingDecoratorComponent {

    private MediaPlayer mediaPlayer;
    
    private final static String PARAMETER_URL = "url";
    private final static String PARAMETER_URL_DESCRIPTION = "Media file url or uri";
    private final static String PARAMETER_HEIGHT = "height";
    private final static String PARAMETER_HEIGHT_DESCRIPTION = "Height of player";
    private final static String PARAMETER_WIDTH = "width";
    private final static String PARAMETER_WIDTH_DESCRIPTION = "Width of player";
    private final static String PARAMETER_AUTOPLAY = "autoplay";
    private final static String PARAMETER_AUTOPLAY_DESCRIPTION = "Start playing immediately if set to 'true'. Default is 'false'";
    private final static String PARAMETER_CONTENT_TYPE = "content-type";
    private final static String PARAMETER_CONTENT_TYPE_DESCRIPTION = "Content type of media file";
    private final static String PARAMETER_STREAM_TYPE = "stream-type";
    private final static String PARAMETER_STREAM_TYPE_DESC = "Set to live for live stream";
    private final static String PARAMETER_POSTER = "poster";
    private final static String PARAMETER_POSTER_DESC = "Poster image for video";
    private final static String PARAMETER_SHOW_DOWNLOAD_LINK = "show-download-link";
    private final static String PARAMETER_SHOW_DOWNLOAD_LINK_DESC = "Shows download link if set to 'true'. Default is 'false'";

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        
        String url = request.getStringParameter(PARAMETER_URL);
        String height = request.getStringParameter(PARAMETER_HEIGHT);
        String width = request.getStringParameter(PARAMETER_WIDTH);
        String autoplay = request.getStringParameter(PARAMETER_AUTOPLAY);
        String contentType = request.getStringParameter(PARAMETER_CONTENT_TYPE);
        String streamType = request.getStringParameter(PARAMETER_STREAM_TYPE);
        String poster = request.getStringParameter(PARAMETER_POSTER);
        String showDL = request.getStringParameter(PARAMETER_SHOW_DOWNLOAD_LINK);

        mediaPlayer.addMediaPlayer(request.getServletRequest(), model, url, 
                height, width, autoplay, contentType, streamType, poster, showDL);

        super.processModel(model, request, response);
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(PARAMETER_URL, PARAMETER_URL_DESCRIPTION);
        map.put(PARAMETER_HEIGHT, PARAMETER_HEIGHT_DESCRIPTION);
        map.put(PARAMETER_WIDTH, PARAMETER_WIDTH_DESCRIPTION);
        map.put(PARAMETER_AUTOPLAY, PARAMETER_AUTOPLAY_DESCRIPTION);
        map.put(PARAMETER_CONTENT_TYPE, PARAMETER_CONTENT_TYPE_DESCRIPTION);
        map.put(PARAMETER_STREAM_TYPE, PARAMETER_STREAM_TYPE_DESC);
        map.put(PARAMETER_POSTER, PARAMETER_POSTER_DESC);
        map.put(PARAMETER_SHOW_DOWNLOAD_LINK, PARAMETER_SHOW_DOWNLOAD_LINK_DESC);
        return map;
    }

    @Required
    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }
}
