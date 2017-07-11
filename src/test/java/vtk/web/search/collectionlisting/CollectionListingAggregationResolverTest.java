/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.search.collectionlisting;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySetImpl;
import vtk.repository.Repository;
import vtk.repository.resourcetype.BooleanValueFormatter;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;
import vtk.repository.store.DefaultPrincipalMetadataDAO;
import vtk.web.RequestContext;
import vtk.web.display.collection.aggregation.CollectionListingAggregatedResources;
import vtk.web.service.Service;

public class CollectionListingAggregationResolverTest {

    private CollectionListingAggregationResolver aggregationResolver;

    private PropertyTypeDefinition displayAggregationPropDef;
    private String displayAggregationPropDefId = "display-aggregation";
    private PropertyTypeDefinition aggregationPropDef;
    private String aggregationPropDefId = "aggregation";

    private PropertyTypeDefinition displayManuallyApprovedPropDef;
    private String displayManuallyApprovedPropDefId = "display-manually-approved";
    private PropertyTypeDefinition manuallyApprovedPropDef;
    private String manuallyApprovedPropDefId = "manually-approve-from";

    private static Mockery context = new JUnit4Mockery();
    private static Repository mockRepository;
    private static MultiHostSearcher mockMultiHostSearcher;
    private static Service mockViewService;


    @Before
    public void setUp() {

        aggregationResolver = new CollectionListingAggregationResolver();

        displayAggregationPropDef = createPropDef(displayAggregationPropDefId, Type.BOOLEAN,
                new BooleanValueFormatter());
        aggregationResolver.setDisplayAggregationPropDef(displayAggregationPropDef);

        displayManuallyApprovedPropDef = createPropDef(displayManuallyApprovedPropDefId, Type.BOOLEAN,
                new BooleanValueFormatter());
        aggregationResolver.setDisplayManuallyApprovedPropDef(displayManuallyApprovedPropDef);

        aggregationResolver.setViewService(mockViewService);

    }

    @Test
    public void testGetAggregatedResourcesNone() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo");
        RequestContext requestContext = new RequestContext(null, null,
                null, null, null, Path.ROOT, null, false,
                false, true, null, new DefaultPrincipalMetadataDAO());
        RequestContext.setRequestContext(requestContext, request);
        
        PropertySetImpl propSet = new PropertySetImpl();
        propSet.setUri(Path.ROOT);
        propSet.addProperty(createProperty(displayAggregationPropDef, false));
        propSet.addProperty(createProperty(displayManuallyApprovedPropDef, false));
        CollectionListingAggregatedResources result = aggregationResolver
                .getAggregatedResources(request, propSet);

        assertNotNull(result);
        assertNull(result.getAggregationSet());
        assertNull(result.getManuallyApproved());

    }

    @Test
    public void testGetAggregatedResourcesAggregationOnly() {

        PropertySetImpl propSet = new PropertySetImpl();
        propSet.setUri(Path.ROOT);
        propSet.addProperty(createProperty(displayAggregationPropDef, true));
        propSet.addProperty(createProperty(displayManuallyApprovedPropDef, false));
        
//        CollectionListingAggregatedResources result = aggregationResolver.getAggregatedResources(propSet);
//
//        assertNotNull(result);
//        assertNull(result.getManuallyApproved());
//        assertNotNull(result.getAggregationSet());

    }

    private PropertyTypeDefinition createPropDef(final String name, final Type type, final ValueFormatter valueFormatter) {
        PropertyTypeDefinitionImpl propDef = new PropertyTypeDefinitionImpl();
        propDef.setNamespace(Namespace.DEFAULT_NAMESPACE);
        propDef.setName(name);
        propDef.setType(type);
        propDef.setValueFormatter(valueFormatter);
        return propDef;
    }

    private Property createProperty(final PropertyTypeDefinition propDef, final Object valueObj) {
        Property prop = propDef.createProperty();
        Value value = new Value(valueObj.toString(), propDef.getType());
        prop.setValue(value);
        return prop;
    }

}
