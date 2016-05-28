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
package vtk.web.referencedata.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.support.RequestContext;
import vtk.util.io.IO;
import vtk.web.referencedata.ReferenceDataProvider;

public class SpringResourceContentProvider implements ReferenceDataProvider, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private String folderLocation;
    private String repositoryId;
    private String defaultFile;
    private String encoding = "utf-8";
    private String modelKey = "content";

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        List<String> fileLocaltions = new ArrayList<String>();
        RequestContext rc = new RequestContext(request);
        Locale locale = rc.getLocale();
        fileLocaltions.add(this.folderLocation + this.repositoryId + "_" + locale.getLanguage());
        fileLocaltions.add(this.folderLocation + this.repositoryId);
        fileLocaltions.add(this.folderLocation + this.defaultFile + "_" + locale.getLanguage());
        fileLocaltions.add(this.folderLocation + this.defaultFile);
        try {
            Resource resource = getResource(fileLocaltions);
            if (resource != null) {
                String content = IO.readString(resource.getInputStream(), this.encoding).perform();
                model.put(this.modelKey, content);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private Resource getResource(List<String> fileLocaltions) {
        for (String fileLocation : fileLocaltions) {
            try {
                Resource resource = this.applicationContext.getResource(fileLocation);
                if (resource.exists()) {
                    return resource;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public void setFolderLocation(String folderLocation) {
        this.folderLocation = folderLocation;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public void setDefaultFile(String defaultFile) {
        this.defaultFile = defaultFile;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }
}
