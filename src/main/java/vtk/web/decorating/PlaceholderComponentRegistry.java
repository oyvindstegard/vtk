/* Copyright (c) 2007, 2008, University of Oslo, Norway
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
package vtk.web.decorating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A configuration registry of named template placement identifiers mapped to
 * ordered decorator component lists.
 *
 * <p>
 * This registry is generally not mutable at runtime and is meant to be
 * populated during application initialization.
 */
public class PlaceholderComponentRegistry {

    private final Map<String, List<OrderedDecoratorComponentHolder>> placeholderComponents = new HashMap<>();

    /**
     * Register a component to be invoked at the given placement identifier.
     *
     * @param place the placement id
     * @param order the invocation ordering number for the component under the
     * given placement
     * @param component the decorator component
     */
    public void register(String place, int order, DecoratorComponent component) {
        if (place == null || component == null) {
            throw new IllegalArgumentException("Both place and component must be non-null");
        }

        List<OrderedDecoratorComponentHolder> components = placeholderComponents.computeIfAbsent(place, k -> new ArrayList<>());
        components.add(new OrderedDecoratorComponentHolder(component, order));
        Collections.sort(components);
    }

    /**
     * Get an ordered immutable list of all components registered at the
     * provided placement identifier.
     *
     * @param place the placement identifier
     * @return a list of components, or an empty list if none found for the place
     */
    public List<DecoratorComponent> getComponents(String place) {
        return placeholderComponents.getOrDefault(place, Collections.emptyList())
                .stream().map(h -> h.getComponent()).collect(Collectors.toList());
    }

    private static class OrderedDecoratorComponentHolder implements Comparable<OrderedDecoratorComponentHolder> {

        private final DecoratorComponent c;
        private final int order;

        OrderedDecoratorComponentHolder(DecoratorComponent c, int order) {
            this.c = c;
            this.order = order;
        }

        DecoratorComponent getComponent() {
            return c;
        }

        @Override
        public int compareTo(OrderedDecoratorComponentHolder o) {
            return Integer.compare(order, o.order);
        }
    }
}
