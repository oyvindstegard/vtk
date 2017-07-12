/* Copyright (c) 2004, University of Oslo, Norway
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

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.Errors;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;

public class RenameCommandValidator implements SimpleFormController.Validator<RenameCommand> {

    private static Logger logger = LoggerFactory.getLogger(RenameCommandValidator.class);
    private Map<String, String> replaceNameChars;

    public void validate(HttpServletRequest request, RenameCommand command, Errors errors) {

        if (command.getCancel() != null)
            return;

        String name = command.getName();

        if (StringUtils.isBlank(name)) {
            errors.rejectValue("name", "manage.rename.resource.missing.name",
                    "The resource needs a name");
            return;

        }
        name = name.trim();
        if (this.isInvalid(name)) {
            errors.rejectValue("name", "manage.create.document.invalid.name", "This is an illegal name");
            return;
        }

        Resource resource = command.getResource();
        Path newUri = command.getRenamePath();

        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();

        try {
            if (repository.exists(token, newUri)) {
                Resource existing = repository.retrieve(token, newUri, false);
                if (command.getOverwrite() == null) {
                    if (resource.isCollection() || existing.isCollection()) {
                        errors.rejectValue("name", "manage.rename.resource.exists",
                                "A resource of this name already exists");
                    } else {
                        errors.rejectValue("name", "manage.rename.resource.overwrite",
                                "A resource of this name already exists, do you want to overwrite it?");
                        command.setConfirmOverwrite(true);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed while validating rename operation", e);
            errors.reject("manage.rename.resource.validation.failed", "Validiation failed");
        }
    }

    private boolean isInvalid(String name) {
        if (name.contains("/") || name.equals(".") || name.equals("..")) {
            return true;
        }
        for (Entry<String, String> entry : this.replaceNameChars.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.isBlank(value)) {
                continue;
            }
            if (Pattern.compile(key).matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    @Required
    public void setReplaceNameChars(Map<String, String> replaceNameChars) {
        this.replaceNameChars = replaceNameChars;
    }

}
