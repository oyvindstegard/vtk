/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.web.display;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;
import org.springframework.web.servlet.View;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.io.IO;
import vtk.web.RequestContext;

public class DisplayMarkdownView implements View {
    
    private static final int MARKDOWN_EXTENSIONS = Extensions.HARDWRAPS 
            | Extensions.FENCED_CODE_BLOCKS | Extensions.AUTOLINKS | Extensions.TABLES
            | Extensions.DEFINITIONS;

    @Override
    public String getContentType() {
        return "text/html;charset=utf-8";

    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Resource resource = repository.retrieve(token, uri, true);

        try (InputStream inputStream = repository.getInputStream(token, uri, true)) {

            long timeout = Duration.ofMillis(2000).toMillis();
            PegDownProcessor processor = new PegDownProcessor(MARKDOWN_EXTENSIONS, timeout);
            String input = IO.readString(inputStream, "utf-8").perform();
            
            byte[] output = ("<!DOCTYPE html>\n<html>\n<head>\n<title>" 
                    + resource.getTitle() + "</title>\n</head>\n<body>\n"
                    + processor.markdownToHtml(input.toCharArray())
                    + "\n</body>\n</html>\n").getBytes("utf-8");
            
            response.setContentType(getContentType());
            response.setContentLength(output.length);
            
            OutputStream out = response.getOutputStream();
            out.write(output);
            out.flush();
            out.close();
        }
    }

}

