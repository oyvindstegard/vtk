/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.property.PropertyEvaluationException;

/**
 * Maintains a content modification count as property value of type {@code LONG}.
 *
 * <p>Value is set to 1 if property does not exist, and for sub-sequent evaluations on the same resource,
 * the value is incremented each time content is stored, regardless of changes or not.
 *
 * <p>Expects that property is not generally user modifiable, and that
 * property may not exist on resources yet (not mandatory).
 */
public class ContentModificationCountEvaluator implements PropertyEvaluator {

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        if (!property.isValueInitialized()) {
            property.setLongValue(1);
        } else {
            if (ctx.getEvaluationType() == PropertyEvaluationContext.Type.ContentChange) {
                property.setLongValue(Math.max(property.getLongValue(),0) + 1);
            }
        }
        return true;
    }

}
