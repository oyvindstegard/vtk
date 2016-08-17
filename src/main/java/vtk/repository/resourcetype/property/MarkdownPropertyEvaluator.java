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
package vtk.repository.resourcetype.property;

import java.util.Optional;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.content.MarkdownInfo;
import vtk.repository.content.MarkdownGFMInfo;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyEvaluator;

public class MarkdownPropertyEvaluator implements LatePropertyEvaluator {
    private static final String SUBTYPE_GFM = "GFM";

	private final String markdownSubtype;
    private final String field;
    private Optional<PropertyEvaluator> fallbackEvaluator = Optional.empty();
    
    public MarkdownPropertyEvaluator(String markdownSubtype, String field) {
        this(markdownSubtype, field, null);
    }
    
    public MarkdownPropertyEvaluator(String markdownSubtype, String field, PropertyEvaluator fallbackEvaluator) {
        this.markdownSubtype = markdownSubtype;    	
        this.field = field;
        this.fallbackEvaluator = Optional.ofNullable(fallbackEvaluator);
    }

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {
        
        boolean exists = ctx.getOriginalResource().getProperty(property.getDefinition()) != null;
        if (fallbackEvaluator.isPresent()) {
            exists = fallbackEvaluator.get().evaluate(property, ctx);
        }
        
        if (ctx.getEvaluationType() == Type.NameChange) {
            if (exists) {
                property.setValue(ctx.getOriginalResource()
                        .getProperty(property.getDefinition()).getValue());
            }
            return exists;
        }
        else if (ctx.getEvaluationType() != Type.ContentChange 
                && ctx.getEvaluationType() != Type.Create) {
            return exists; 
        }
        
        if (ctx.getContent() == null) {
            return exists;
        }
        
        try {
            MarkdownInfo info;
            if (markdownSubtype.equals(SUBTYPE_GFM)) {
            	info = ctx.getContent()
            	        .getContentRepresentation(MarkdownGFMInfo.class);
            }
            else {
                info = ctx.getContent()
                        .getContentRepresentation(MarkdownInfo.class);
            }
            switch (field) {
            case "title":
                if (info.title != null) {
                    property.setStringValue(info.title);
                    return true;
                }
                return exists;
            case "summary":
                if (info.summary != null) {
                    property.setStringValue(info.summary);
                    return true;
                }
                return exists;
            default:
                return exists;
            }            
        }
        catch (Throwable t) {
            return exists;
        }
    }
}
