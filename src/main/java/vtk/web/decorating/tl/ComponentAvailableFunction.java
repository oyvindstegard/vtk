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
package vtk.web.decorating.tl;

import java.util.Objects;

import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.decorating.ComponentResolver;

public class ComponentAvailableFunction extends Function {
    private ComponentResolver resolver;

    public ComponentAvailableFunction(Symbol symbol, ComponentResolver resolver) {
        super(symbol, 1);
        this.resolver = resolver;
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        String component = Objects.requireNonNull(args[0]).toString();
        int idx = component.indexOf(":");
        if (idx == -1 || idx == 0 || idx == component.length() - 1) {
            throw new IllegalArgumentException("Invalid component name: " + component);
        }
        String namespace = component.substring(0, component.indexOf(":"));
        String name = component.substring(idx + 1, component.length());
        
        return resolver.resolveComponent(namespace, name) != null;
    }

}
