/* Copyright (c) 2013, University of Oslo, Norway
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

package vtk.util.web;

import java.io.IOException;
import java.io.OutputStreamWriter;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;

/**
 * A Spring {@link HttpMessageConverter} which converts
 * HTTP messages to/from instances of {@link Json.Container}.
 */
public class JSONHttpMessageConverter extends AbstractHttpMessageConverter<Json.Container> {

    public JSONHttpMessageConverter() {
        super(MediaType.APPLICATION_JSON);
    }
    
    @Override
    protected boolean supports(Class<?> clazz) {
        return Json.Container.class.isAssignableFrom(clazz);
    }

    @Override
    protected Json.Container readInternal(Class<? extends Json.Container> clazz, HttpInputMessage inputMessage) 
            throws IOException, HttpMessageNotReadableException {
        try {
            return Json.parseToContainer(inputMessage.getBody());
        } catch (Throwable t) {
            throw new HttpMessageNotReadableException(t.getMessage(), t);
        }
    }

    @Override
    protected void writeInternal(Json.Container t, HttpOutputMessage outputMessage) 
            throws IOException, HttpMessageNotWritableException {

        JsonStreamer streamer = new JsonStreamer(new OutputStreamWriter(outputMessage.getBody()), 2, true);
        streamer.value(t);
        outputMessage.getBody().flush();
    }
    
}
