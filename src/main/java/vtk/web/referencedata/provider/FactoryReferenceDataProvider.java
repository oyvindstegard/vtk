/* Copyright (c) 2017 University of Oslo, Norway
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;

import vtk.web.referencedata.ReferenceDataProvider;

public class FactoryReferenceDataProvider<T> implements ReferenceDataProvider {
    private Optional<String> modelName = Optional.empty();
    private String name;
    private Function<HttpServletRequest, T> factory;

    public FactoryReferenceDataProvider(Optional<String> modelName, String key, 
            Function<HttpServletRequest, T> factory) {
        this.modelName = Objects.requireNonNull(modelName);
        this.name = Objects.requireNonNull(key);
        this.factory = Objects.requireNonNull(factory);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void referenceData(Map<String, Object> model,
            HttpServletRequest request) {
        if (modelName.isPresent()) {
            String modelKey = modelName.get();
            Object existing = model.get(modelKey);
            if (existing != null && !(existing instanceof Map<?,?>)) {
                throw new IllegalStateException(
                        "Model already has an attribute for key " + modelKey);
            }
            Map<String, Object> subModel;
            if (existing != null) {
                subModel = ((Map<String,Object>) existing);
            }
            else {
                subModel = new HashMap<>();
                model.put(modelKey, subModel);
            }
            model = subModel;
        }
        model.put(name, factory.apply(request));
    }
}
