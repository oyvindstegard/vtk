/* Copyright (c) 2005-2015 University of Oslo, Norway
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
package vtk.web.actions.copymove;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceOverwriteException;
import vtk.web.Message;
import vtk.web.RequestContext;
import vtk.web.actions.convert.CopyAction;

/**
 * A controller that copies (or moves) resources from one folder to another
 * based on a set of resources stored in a session variable
 * 
 * 1. If client has JavaScript enabled ("overwrite"-parameter is sent), the user
 * can decide to skip or overwrite existing resources (VTK-3525).
 * Exceptions:
 * - Copying existing resources to same folder duplicates them
 * - Moving existing resources to same folder gives error-message but files is kept in session
 * 
 * 2. Otherwise gives error-messages for moving existing resources and remove them from session,
 * Copying always duplicates existing resources. Copies and moves the resources that are
 * not giving error-messages.
 * 
 * 
 * <p>
 * Configurable properties:
 * <ul>
 * <li>{@link String viewName} - the view to which to return to
 * <li>{@link CopyAction copyAction} - if specified, invoke this
 * {@link CopyAction} instead of {@link Repository#copy} to copy resources
 * </ul>
 * </p>
 * <p>
 * Model data published:
 * <ul>
 * <li>{@code createErrorMessage}: error message
 * <li>{@code errorItems}: an array of repository items which the error message
 * relates to
 * </ul>
 * </p>
 */
public class CopyMoveToSelectedFolderController implements Controller {

    private static Log logger = LogFactory.getLog(CopyMoveToSelectedFolderController.class);
    public static final String COPYMOVE_SESSION_ATTRIBUTE = "copymovesession";
    private String viewName = "DEFAULT_VIEW_NAME";
    private CopyHelper copyHelper;

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        CopyMoveSessionBean sessionBean = (CopyMoveSessionBean) 
                request.getSession(true).getAttribute(COPYMOVE_SESSION_ATTRIBUTE);
        Map<String, Object> model = new HashMap<>();
        if (sessionBean == null) {
            return new ModelAndView(this.viewName, model);
        }
        if (request.getParameter("cancel-action") != null) {
        	// User declined to confirm the copy/move
            return new ModelAndView(this.viewName, model);
        }
        if (request.getParameter("clear-action") != null) {
        	// Drop out of copy/move state and return to normal state
            request.getSession(true).removeAttribute(COPYMOVE_SESSION_ATTRIBUTE);
            return new ModelAndView(this.viewName, model);
        }
        long before = System.currentTimeMillis();
        ArrayList<String> userProcessExistingFileNames = performAction(sessionBean);
        if (userProcessExistingFileNames == null) {
            // Removing session variable
            request.getSession(true).removeAttribute(COPYMOVE_SESSION_ATTRIBUTE);

            if (logger.isDebugEnabled()) {
                long total = System.currentTimeMillis() - before;
                logger.debug("Milliseconds spent on this copy/move operation: " + total);
            }
            return new ModelAndView(this.viewName, model);
        }
        else {
            if (userProcessExistingFileNames.size() == 0) {
                model.put("moveToSameFolder", "yes");
            }
            else {
                model.put("existingFilenames", userProcessExistingFileNames);
            }
            return new ModelAndView(this.viewName, model);
        }
    }

    private ArrayList<String> performAction(CopyMoveSessionBean sessionBean) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path destinationUri = requestContext.getCurrentCollection();
        Repository repository = requestContext.getRepository();

        String action = sessionBean.getAction();
        boolean moveAction = "move-resources".equals(action);

        List<String> filesToMoveOrCopy = sessionBean.getFilesToBeCopied();

        // Map of files that for some reason failed on copy/move. Separated by a
        // key (String) that specifies type of failure and identifies list of
        // paths to resources that failed.
        Map<String, List<Path>> failures = new HashMap<String, List<Path>>();
        String msgKey = "manage".concat(moveAction ? ".move" : ".copy").concat(".error.");

        boolean shouldOverwriteExisting = requestContext.getServletRequest().getParameter("overwrite") != null;
        String existingSkippedFiles = requestContext.getServletRequest().getParameter("existing-skipped-files");
        String[] existingSkippedFilesArr = null;
        if(existingSkippedFiles != null) {
            existingSkippedFilesArr = existingSkippedFiles.split(",");
        }
        boolean gatherExistingFilenames = false;

        for (String filePath : filesToMoveOrCopy) {

            Path fileUri = Path.fromString(filePath);
            if (!repository.exists(token, fileUri)) {
                // A selected object for copying/moving no longer exists
                this.addToFailures(failures, fileUri, msgKey, "nonExisting");
                continue;
            }

            String name = fileUri.getName();
            Path newResourceUri = destinationUri.extend(name);
            boolean isSameFolder = newResourceUri.toString().equals(fileUri.toString());

            if (isSameFolder && moveAction && shouldOverwriteExisting) { // Give error-message if moving to same folder
                return new ArrayList<String>();
            }

            try {
                if (repository.exists(token, newResourceUri) && !(!moveAction && isSameFolder)) { // Duplicate if copying to same folder
                    if (existingSkippedFilesArr == null) {
                        this.addToFailures(failures, fileUri, msgKey, "namingConflict");
                        gatherExistingFilenames = true;
                        continue;
                    }
                    else {
                        boolean isSkippingFile = false;
                        for (String existingSkippedFile : existingSkippedFilesArr) {
                            if (fileUri.toString().equals(existingSkippedFile)) {
                                isSkippingFile = true;
                                break;
                            }
                        }
                        if (isSkippingFile) { // Skip
                            continue;
                        }
                        else { // Overwrite
                            repository.delete(token, newResourceUri, false);
                        }
                    }
                }
                if (!gatherExistingFilenames || !shouldOverwriteExisting) {
                    if (moveAction) {
                        repository.move(token, fileUri, newResourceUri, false);
                    }
                    else {
                        Path destUri = newResourceUri;
                        Resource src = repository.retrieve(token, fileUri, false);
                        newResourceUri = this.copyHelper.copyResource(fileUri, destUri, repository, token, src, null);
                    }
                }

            }
            catch (AuthorizationException ae) {
                this.addToFailures(failures, fileUri, msgKey, "unAuthorized");
            }
            catch (ResourceLockedException rle) {
                this.addToFailures(failures, fileUri, msgKey, "locked");
            }
            catch (ResourceOverwriteException roe) {
                this.addToFailures(failures, fileUri, msgKey, "namingConflict");
            }
            catch (Exception e) {
                StringBuilder msg = new StringBuilder("Could not perform ");
                msg.append(moveAction ? "move of " : "copy of ").append(filePath);
                msg.append(": ").append(e.getMessage());
                logger.warn(msg);
                this.addToFailures(failures, fileUri, msgKey, "generic");
            }
        }

        ArrayList<String> existingFilenames = new ArrayList<String>();
        for (Entry<String, List<Path>> entry : failures.entrySet()) {
            String key = entry.getKey();
            List<Path> failedResources = entry.getValue();

            if (key.contains("namingConflict") && shouldOverwriteExisting) {
                for (Path p : failedResources) {
                    existingFilenames.add(p.toString());
                }
            }
            else {
                Message msg = new Message(key);
                for (Path p : failedResources) {
                    msg.addMessage(p.getName());
                }
                requestContext.addErrorMessage(msg);
            }
        }
        if (existingFilenames.size() > 0 && requestContext.getErrorMessages().size() == 0) {
            return existingFilenames;
        }
        return null;
    }

    private void addToFailures(Map<String, List<Path>> failures, Path fileUri, String msgKey, String failureType) {
        String key = msgKey.concat(failureType);
        List<Path> failedPaths = failures.get(key);
        if (failedPaths == null) {
            failedPaths = new ArrayList<Path>();
            failures.put(key, failedPaths);
        }
        failedPaths.add(fileUri);
    }

    public void setCopyHelper(CopyHelper copyHelper) {
        this.copyHelper = copyHelper;
    }

}
