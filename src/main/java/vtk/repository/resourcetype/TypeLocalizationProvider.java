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
package vtk.repository.resourcetype;

import java.util.Locale;

public interface TypeLocalizationProvider {

    /**
     * Return a localized name of property type def.
     * <code>Locale</code>.
     * 
     * @param namespace
     * @param name
     * @param locale
     * @return The name of the property type as a localized string.
     *         If the type is unknown or a translation is missing for
     *         the supplied locale, the canonical type name is always
     *         returned.
     */
    public String getLocalizedPropertyName(PropertyTypeDefinition def, Locale locale);

    /**
     *
     * @param def
     * @param locale
     * @return
     */
    public String getPropertyDescription(PropertyTypeDefinition def, Locale locale);

    /**
     * Return a localized name of the resource type.
     * 
     * @param def
     * @param locale
     * 
     * @return The name of the resource type as a localized string.
     *         If the type is unknown or a translation is missing for
     *         the supplied locale, the canonical type name is always
     *         returned.
     */
    public String getLocalizedResourceTypeName(ResourceTypeDefinition def, Locale locale);

    
}
