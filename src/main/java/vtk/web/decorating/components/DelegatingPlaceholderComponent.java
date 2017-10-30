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
package vtk.web.decorating.components;

import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.web.decorating.DecoratorComponent;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.DecoratorResponseImpl;
import vtk.web.decorating.PlaceholderComponentRegistry;

/**
 * A decorator component which invokes all components registered with a given
 * "placement identifier" in a registry, in the order the registry provides.
 *
 * <p>
 * The placement identifier is provided as a {@link #PARAMETER_PLACE parameter}
 * to this component itself.
 *
 * <p>
 * Parameters given to this component, including the place parameter, will be
 * passed on unmodified to all components which it invokes for the given place.
 * Components invoked by this mechanism should generally not base their
 * behaviour on specific parameters, but rather the context of the request in
 * which they are invoked.
 *
 * <p>
 * Can serve as named placeholder in system templates for possible
 * customizations points.
 *
 * <p>
 * TODO can consider adding parameter which controls "override" or "additive"
 * behaviour of customization points. This component currently only implements
 * "ordered additive".
 */
public class DelegatingPlaceholderComponent extends AbstractDecoratorComponent {

    public static final String PARAMETER_PLACE = "place";

    private final PlaceholderComponentRegistry placeholderComponentRegistry;

    private final Logger logger = LoggerFactory.getLogger(DelegatingPlaceholderComponent.class.getName());

    public DelegatingPlaceholderComponent(PlaceholderComponentRegistry registry) {
        this.placeholderComponentRegistry = registry;
    }

    @Override
    protected String getDescriptionInternal() {
        return getClass().getSimpleName();
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        return Collections.singletonMap(PARAMETER_PLACE, "Placement identifier for components to invoke");
    }

    @Override
    public void render(DecoratorRequest request, DecoratorResponse response) throws Exception {
        String place = request.getStringParameter(PARAMETER_PLACE);
        if (place == null) {
            throw new IllegalArgumentException("Missing required parameter: " + PARAMETER_PLACE);
        }

        List<DecoratorComponent> componentsAtPlace = placeholderComponentRegistry.getComponents(place);
        if (componentsAtPlace.isEmpty()) {
            return;
        }

        try (Writer writer = response.getWriter()) {
            componentsAtPlace.forEach(component -> {
                try {
                    DecoratorResponseImpl delegateeResponse = new DecoratorResponseImpl(
                            response.getDoctype(), response.getLocale(), response.getCharacterEncoding());

                    component.render(request, delegateeResponse);

                    // Handles any character set conversions, since components themselves are actually allowed
                    // to change the character set of the DecoratorResponse they write to
                    writer.write(delegateeResponse.getContentAsString());
                } catch (Exception e) {
                    logger.warn("Component invocation failed for component '{}:{}' at place '{}': {}: {}",
                            component.getNamespace(), component.getName(), place, e.getClass().getSimpleName(), e.getMessage());
                }
            });
        }
    }
}
