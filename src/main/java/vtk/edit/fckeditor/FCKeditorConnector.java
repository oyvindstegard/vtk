/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.edit.fckeditor;

import java.io.File;
import java.io.InputStream;
import java.text.Collator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.repository.AuthorizationException;
import vtk.repository.ContentInputSources;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyType;
import vtk.util.repository.MimeHelper;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class FCKeditorConnector implements Controller {
    private Service viewService;
    private String browseViewName;
    private String uploadStatusViewName;
    private int maxUploadSize = 1000000;
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));
    private boolean downcaseNames = false;
    private Map<String, String> replaceNameChars;

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setBrowseViewName(String browseViewName) {
        this.browseViewName = browseViewName;
    }

    @Required
    public void setUploadStatusViewName(String uploadStatusViewName) {
        this.uploadStatusViewName = uploadStatusViewName;
    }
    
    public void setTempDir(String tempDir) {
        File tmp = new File(tempDir);
        if (!tmp.exists()) {
            throw new IllegalArgumentException(
                    "Unable to set tempDir: directory does not exist: " + tempDir);
        }
        if (!tmp.isDirectory()) {
            throw new IllegalArgumentException(
                    "Unable to set tempDir: not a directory: " + tempDir);
        }
        this.tempDir = tmp;
    }
    

    public void setMaxUploadSize(int maxUploadSize) {
        if (maxUploadSize <= 0) {
            throw new IllegalArgumentException("Max upload size must be a positive integer");
        }
        this.maxUploadSize = maxUploadSize;
    }

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        FCKeditorFileBrowserCommand command = new FCKeditorFileBrowserCommand(request);

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();

        Locale locale = RequestContextUtils.getLocale(request);

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("currentFolder", ensureTrailingSlash(command.getCurrentFolder()));
        model.put("command", command.getCommand().name());
        model.put("resourceType", command.getResourceType());

        Filter fileFilter = null;

        FCKeditorFileBrowserCommand.ResourceType type = command.getResourceType();
        switch (type) {
        case Image:
            fileFilter = IMAGE_FILTER;
            break;
        case Flash:
            fileFilter = FLASH_FILTER;
            break;
        case Media:
            fileFilter = MEDIA_FILTER;
            break;
        default:
            fileFilter = FILE_FILTER;
            break;
        }

        FCKeditorFileBrowserCommand.Command c = command.getCommand();
        switch (c) {
        case GetFolders:
            try {
                model.put("folders", listResources(token, command, COLLECTION_FILTER, locale));
            } catch (Exception e) {
                model.put("error", 1);
                model.put("customMessage", getErrorMessage(e));
            }
            break;

        case GetFoldersAndFiles:

            try {
                model.put("folders", listResources(token, command, COLLECTION_FILTER, locale));
                model.put("files", listResources(token, command, fileFilter, locale));
            } catch (Exception e) {
                model.put("error", 1);
                model.put("customMessage", getErrorMessage(e));
            }
            break;

        case CreateFolder:
            model.put("error", createFolder(command, requestContext));
            break;

        case FileUpload:
            return uploadFile(command, requestContext);

        default:
            model.put("error", 1);
            model.put("customMessage", "Unknown command");
        }

        return new ModelAndView(this.browseViewName, model);
    }

    private Map<String, Map<String, Object>> listResources(String token, FCKeditorFileBrowserCommand command,
            Filter filter, Locale locale) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();

        Resource[] children = repository.listChildren(token, command.getCurrentFolder(), true);

        Map<String, Map<String, Object>> result = new TreeMap<String, Map<String, Object>>(Collator.getInstance(locale));

        for (Resource r : children) {
            if (!filter.isAccepted(r)) {
                continue;
            }
            Map<String, Object> entry = new HashMap<String, Object>();
            URL url = this.viewService.constructURL(r, null);
            entry.put("resource", r);
            entry.put("url", url);
            if (!r.isCollection()) {
                entry.put("contentLength", r.getContentLength());
            }

            result.put(r.getURI().toString(), entry);
        }
        return result;
    }

    private int createFolder(FCKeditorFileBrowserCommand command, RequestContext requestContext) {

        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path curFolder = command.getCurrentFolder();
        Path newFolderURI = curFolder.extend(fixUploadName(command.getNewFolderName()));
        try {
            if (repository.exists(token, newFolderURI)) {
                return 101;
            }
            repository.createCollection(token, newFolderURI);
            return 0;
        } catch (AuthorizationException e) {
            return 103;
        } catch (Throwable t) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private ModelAndView uploadFile(FCKeditorFileBrowserCommand command, RequestContext requestContext) {
        Map<String, Object> model = new HashMap<String, Object>();

        FileItemFactory factory = new DiskFileItemFactory(maxUploadSize, tempDir);
        ServletFileUpload upload = new ServletFileUpload(factory);
        HttpServletRequest request = requestContext.getServletRequest();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        FileItem uploadItem = null;
        try {
            List<FileItem> fileItems = upload.parseRequest(request);
            for (FileItem item : fileItems) {
                if (!item.isFormField()) {
                    uploadItem = item;
                    break;
                }
            }
            if (uploadItem == null) {
                model.put("error", 1);
                model.put("customMessage", "No file uploaded");
                return new ModelAndView(this.uploadStatusViewName, model);
            }
            String name = cleanupFileName(uploadItem.getName());
            Path uri = command.getCurrentFolder().extend(fixUploadName(name));
            boolean existed = false;
            if (repository.exists(token, uri)) {
                existed = true;
                uri = newFileName(command, requestContext, uploadItem);
            }

            InputStream inStream = uploadItem.getInputStream();
            repository.createDocument(token, uri, ContentInputSources.fromStream(inStream));

            Resource newResource = repository.retrieve(token, uri, true);
            TypeInfo typeInfo = repository.getTypeInfo(newResource);

            String contentType = uploadItem.getContentType();
            if (contentType == null || MimeHelper.DEFAULT_MIME_TYPE.equals(contentType)) {
                contentType = MimeHelper.map(newResource.getName());
            }

            Property prop = typeInfo.createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTTYPE_PROP_NAME);
            prop.setStringValue(contentType);
            newResource.addProperty(prop);
            repository.store(token, newResource);

            URL fileURL = this.viewService.constructURL(uri);

            model.put("existed", existed);
            model.put("fileName", name);
            model.put("newFileName", fileURL.getPath().getName());
            model.put("error", existed ? 201 : 0);

        } catch (AuthorizationException e) {
            model.put("error", 203);

        } catch (Exception e) {
            model.put("error", 1);
            model.put("customMessage", e.getMessage());
        }

        return new ModelAndView(this.uploadStatusViewName, model);
    }

    private String ensureTrailingSlash(Path path) {
        if (path.isRoot())
            return path.toString();
        return path.toString() + "/";
    }

    private Path newFileName(FCKeditorFileBrowserCommand command, RequestContext requestContext, FileItem item)
            throws Exception {

        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        String name = fixUploadName(cleanupFileName(item.getName()));
        Path base = command.getCurrentFolder();

        String extension = "";
        String dot = "";
        int number = 1;

        if (name.endsWith(".")) {
            name = name.substring(0, name.lastIndexOf("."));

        } else if (name.contains(".")) {
            extension = name.substring(name.lastIndexOf(".") + 1, name.length());
            dot = ".";
            name = name.substring(0, name.lastIndexOf("."));
        }
        Path newURI = base.extend(name + "(" + number + ")" + dot + extension);
        number++;
        while (repository.exists(token, newURI)) {
            newURI = base.extend(name + "(" + number + ")" + dot + extension);
            number++;
        }
        return newURI;
    }

    static String cleanupFileName(String fileName) {
        if (fileName == null || fileName.trim().equals("")) {
            return null;
        }
        int pos = fileName.lastIndexOf("\\");
        if (pos > fileName.length() - 2) {
            return fileName;
        } else if (pos >= 0) {
            return fileName.substring(pos + 1, fileName.length());
        }
        return fileName;
    }

    private String getErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getName();
        }
        return message;
    }

    private interface Filter {
        public boolean isAccepted(Resource r);
    }

    private static final Filter FILE_FILTER = new Filter() {
        public boolean isAccepted(Resource resource) {
            return !resource.isCollection();
        }
    };

    private static final Filter COLLECTION_FILTER = new Filter() {
        public boolean isAccepted(Resource resource) {
            return resource.isCollection();
        }
    };

    private static final Filter IMAGE_FILTER = new Filter() {
        public boolean isAccepted(Resource resource) {
            return !resource.isCollection() && resource.getContentType().startsWith("image/");
        }
    };

    private static final Filter MEDIA_FILTER = new Filter() {
        public boolean isAccepted(Resource resource) {
            return !resource.isCollection()
                    && (resource.getContentType().startsWith("audio/") || resource.getContentType()
                            .startsWith("video/"));
        }
    };

    private static final Filter FLASH_FILTER = new Filter() {
        public boolean isAccepted(Resource resource) {
            return !resource.isCollection()
                    && resource.getContentType().equalsIgnoreCase("application/x-shockwave-flash");
        }
    };

    public void setReplaceNameChars(Map<String, String> replaceNameChars) {
        this.replaceNameChars = replaceNameChars;
    }

    public void setDowncaseNames(boolean downcaseNames) {
        this.downcaseNames = downcaseNames;
    }

    private String fixUploadName(String name) {
        if (this.downcaseNames) {
            name = name.toLowerCase();
        }

        if (this.replaceNameChars != null) {
            for (String regex : this.replaceNameChars.keySet()) {
                String replacement = this.replaceNameChars.get(regex);
                name = name.replaceAll(regex, replacement);
            }
        }
        return name;
    }
}
