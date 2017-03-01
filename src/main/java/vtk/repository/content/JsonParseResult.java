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
package vtk.repository.content;

import java.util.Optional;

import vtk.util.Result;
import vtk.util.text.Json;

public final class JsonParseResult {
    public final Result<Json.Container> value;
    
    public JsonParseResult(Result<Json.Container> value) {
        if (value == null) throw new NullPointerException("Constructor argument is null");
        this.value = value;
    }
    
    public Optional<Json.MapContainer> asObject() {
        if (value.failure.isPresent()) {
            return Optional.empty();
        }
        Json.Container container = value.result.get();
        if (container.isArray()) {
            return Optional.empty();
        }
        return Optional.of(container.asObject());
    }
    
    public Optional<Json.ListContainer> asArray() {
        if (value.failure.isPresent()) {
            return Optional.empty();
        }
        Json.Container container = value.result.get();
        if (!container.isArray()) {
            return Optional.empty();
        }
        return Optional.of(container.asArray());
    }
}

