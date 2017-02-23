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

import vtk.util.text.Json;

public final class JsonParseResult {
    public final Optional<Json.MapContainer> document;
    public final Optional<Throwable> error;
    
    public static JsonParseResult success(Json.MapContainer document) {
        return new JsonParseResult(Optional.of(document), Optional.empty());
    }
    
    public static JsonParseResult failure(Throwable error) {
        return new JsonParseResult(Optional.empty(), Optional.of(error));
    }

    
    private JsonParseResult(Optional<Json.MapContainer> document, 
            Optional<Throwable> error) {
        if (document.isPresent() && error.isPresent()) {
            throw new IllegalStateException("Both document and error present");
        }
        if (!document.isPresent() && !error.isPresent()) {
            throw new IllegalStateException("Either document or error must be present");
        }
        this.document = document;
        this.error = error;
    }
}

