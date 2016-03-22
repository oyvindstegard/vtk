/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.web.actions.convert;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.web.RequestContext;

public abstract class CopyCommandValidator implements Validator {

    private static Logger logger = LoggerFactory.getLogger(CopyCommandValidator.class);

    @SuppressWarnings("rawtypes")
    protected abstract boolean supportsClass(Class clazz);

    protected abstract Path getCopyToURI(String name);

    protected abstract boolean validateName(String name, Errors errors);

    @SuppressWarnings("rawtypes")
    public boolean supports(Class clazz) {
        return this.supportsClass(clazz);
    }

    public void validate(Object command, Errors errors) {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        
        CopyCommand copyCommand = (CopyCommand) command;
        if (copyCommand.getCancelAction() != null)
            return;

        String name = copyCommand.getName();
        if (StringUtils.isBlank(name)) {
            errors.rejectValue("name", "manage.create.document.missing.name",
                    "A name must be provided for the document");
            return;

        }
        if (!validateName(name, errors)) {
            return;
        }

        Path newURI = getCopyToURI(name);

        try {
            boolean exists = repository.exists(token, newURI);
            if (exists) {
                errors.rejectValue("name", "manage.rename.resource.exists", "A resource with this name already exists");
            }

        } catch (Exception e) {
            logger.warn("Unable to validate resource rename input", e);
        }
    }
}
