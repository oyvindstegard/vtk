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
package vtk.security.web.clientaddr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import vtk.util.Result;
import vtk.util.text.Json;
import vtk.util.text.Json.Container;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;

/**
 * Configuration implementation for {@link ClientAddressAuthenticationHandler},
 * reading a list of JSON entries in the format:
 * <code>
 *   [
 *     { "net": "ip regexp", "uri": "path", "uid": "principal@domain", 
 *        valid_from: "timestamp", valid_to: "timestamp" },
 *     { ... }
 *   ]
 *   
 * </code>
 * 
 */
public class JsonClientAddrSpec 
    implements Consumer<Result<Json.Container>>,
    Supplier<List<Result<ClientAddrAuthSpec>>> {
    private List<Result<ClientAddrAuthSpec>> config = Collections.emptyList();

    @Override
    public void accept(Result<Json.Container> json) {
        if (json.failure.isPresent()) {
            json.failure.get().printStackTrace();
            return;
        }
        Container container = json.result.get();
        if (!container.isArray()) {
            return;
        }
        ListContainer array = container.asArray();
        List<Result<ClientAddrAuthSpec>> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            MapContainer obj = null;
            try {
                obj = array.objectValue(i);
                
                ClientAddrAuthSpec.Builder builder = ClientAddrAuthSpec.builder();
                if (obj.containsKey("net")) {
                    builder.net(obj.stringValue("net"));
                }
                if (obj.containsKey("uri")) {
                    builder.uri(obj.stringValue("uri"));
                }
                if (obj.containsKey("uid")) {
                    builder.uid(obj.stringValue("uid"));
                }
                if (obj.containsKey("valid_from")) {
                    builder.validFrom(obj.stringValue("valid_from"));
                }
                if (obj.containsKey("valid_to")) {
                    builder.validTo(obj.stringValue("valid_to"));
                }
                result.add(Result.success(builder.build()));
            }
            catch (Throwable t) {
                if (obj != null) {
                    IllegalStateException e = new IllegalStateException(
                            "Error in entry " + obj + ": " + t.getMessage(), t);
                    result.add(Result.failure(e));
                }
                else {
                    result.add(Result.failure(t));
                }
            }
        }
        config = Collections.unmodifiableList(result);
    }

    @Override
    public List<Result<ClientAddrAuthSpec>> get() {
        return config;
    }

}
