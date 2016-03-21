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
package vtk.edit.editor;

import java.util.Map;

import javax.servlet.ServletRequest;

import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;

public class ImageEditDataBinder extends ResourceEditDataBinder {

    public ImageEditDataBinder(Object target, String objectName,
            HtmlPageParser htmlParser, HtmlPageFilter htmlPropsFilter,
            Map<PropertyTypeDefinition, PropertyEditPreprocessor> propertyPreprocessors) {
        super(target, objectName, htmlParser, htmlPropsFilter, propertyPreprocessors);
    }

    @Override
    public void bind(ServletRequest request) {
        super.bind(request);
        
        if (getTarget() instanceof ImageResourceEditWrapper) {
            ImageResourceEditWrapper command = (ImageResourceEditWrapper) getTarget();
     
            String cropXStr = request.getParameter("crop-x");
            String cropYStr = request.getParameter("crop-y");
            String cropWidthStr = request.getParameter("crop-width");
            String cropHeightStr = request.getParameter("crop-height");
            String newWidthStr = request.getParameter("new-width");
            String newHeightStr = request.getParameter("new-height");

            if (cropXStr != null) command.setCropX(Integer.parseInt(cropXStr));
            if (cropYStr != null) command.setCropY(Integer.parseInt(cropYStr));
            if (cropWidthStr != null) command.setCropWidth(Integer.parseInt(cropWidthStr));
            if (cropHeightStr != null) command.setCropHeight(Integer.parseInt(cropHeightStr));
            if (newWidthStr != null) command.setNewWidth(Integer.parseInt(newWidthStr));
            if (newHeightStr != null) command.setNewHeight(Integer.parseInt(newHeightStr));
        }
    }
}
