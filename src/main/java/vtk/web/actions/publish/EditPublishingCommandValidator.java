/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.web.actions.publish;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.Errors;

import vtk.repository.Property;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.web.SimpleFormController;

public class EditPublishingCommandValidator implements SimpleFormController.Validator<EditPublishingCommand> {

    private static final String DATE_FORMAT_COMPLETE = "yyyy-MM-dd HH:mm";
    private static final String DATE_FORMAT_HOUR = "yyyy-MM-dd HH";
    private static final String DATE_FORMAT_SIMPLE = "yyyy-MM-dd";
    private static List<String> dateformats;

    static {
        dateformats = new ArrayList<>();
        dateformats.add(DATE_FORMAT_COMPLETE);
        dateformats.add(DATE_FORMAT_HOUR);
        dateformats.add(DATE_FORMAT_SIMPLE);
    }

    private PropertyTypeDefinition publishDatePropDef;
    private PropertyTypeDefinition unpublishDatePropDef;

    @Override
    public void validate(HttpServletRequest request,
            EditPublishingCommand editPublishingCommand, Errors errors) {
        if (editPublishingCommand.getCancelAction() != null) {
            return;
        }
        validatePublishedDate(errors, editPublishingCommand);
        validateUnpublishedDate(errors, editPublishingCommand);
    }

    protected void validateUnpublishedDate(Errors errors, EditPublishingCommand editPublishingCommand) {
        Date date = getValidDate(editPublishingCommand.getUnpublishDate(), "unpublishDate", errors);
        if (date == null) {
            return;
        }
        
        if (editPublishingCommand.getPublishDateValue() != null) {
            Date publishDate = editPublishingCommand.getPublishDateValue().getDateValue();
            if (!publishDate.before(date)) {
                errors.rejectValue("unpublishDate", "publishing.edit.invalid.unpublishDateBefore", "Invalid date");
                return;
            }
            editPublishingCommand.setUnpublishDateValue(new Value(date, false));
            return;
        }
        
        Property publishDateProp = editPublishingCommand.getResource().getProperty(this.publishDatePropDef);
        if (publishDateProp == null) {
            errors.rejectValue("unpublishDate", "publishing.edit.invalid.unpublishDateNonExisting", "Invalid date");
        } else if (date.before(publishDateProp.getDateValue())) {
            errors.rejectValue("unpublishDate", "publishing.edit.invalid.unpublishDateBefore", "Invalid date");
        }

        editPublishingCommand.setUnpublishDateValue(new Value(date, false));

    }

    protected void validatePublishedDate(Errors errors, EditPublishingCommand editPublishingCommand) {
        Date date = getValidDate(editPublishingCommand.getPublishDate(), "publishDate", errors);
        if (date == null) {
            editPublishingCommand.getResource().removeProperty(this.unpublishDatePropDef);
            return;
        }

        Property unpublishDateProp = editPublishingCommand.getResource().getProperty(this.unpublishDatePropDef);
        if (unpublishDateProp != null && unpublishDateProp.getDateValue().before(date)) {
            editPublishingCommand.getResource().removeProperty(this.unpublishDatePropDef);
        }

        editPublishingCommand.setPublishDateValue(new Value(date, false));

    }

    private Date getValidDate(String dateString, String bindName, Errors errors) {

        if (StringUtils.isBlank(dateString)) {
            return null;
        }

        Date validDate = null;

        for (String dateFormat : dateformats) {
            try {
                SimpleDateFormat dateParser = new SimpleDateFormat(dateFormat);
                dateParser.setLenient(false);
                validDate = dateParser.parse(dateString);
                // Break as soon as you get a valid date
                break;
            } catch (ParseException e) {
                // Do nothing, validDate is still null and will be handled
                // later
            }
        }

        if (validDate == null) {
            errors.rejectValue(bindName, "publishing.edit.invalid." + bindName, "Invalid date");
        }

        return validDate;
    }

    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    @Required
    public void setUnpublishDatePropDef(PropertyTypeDefinition unpublishDatePropDef) {
        this.unpublishDatePropDef = unpublishDatePropDef;
    }

}
