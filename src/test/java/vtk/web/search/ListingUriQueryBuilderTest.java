package vtk.web.search;

import org.junit.Test;
import vtk.repository.Property;
import vtk.repository.PropertyImpl;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class ListingUriQueryBuilderTest {
    private static final PropertyTypeDefinitionImpl recursivePropertyDef = new PropertyTypeDefinitionImpl();
    static {
        recursivePropertyDef.setMultiple(false);
        recursivePropertyDef.setType(PropertyType.Type.STRING);
    }

    @Test
    public void only_default_values_should_be_SELF() throws Exception {
        ListingUriQueryBuilder queryBuilder = new ListingUriQueryBuilder();
        ListingUriQueryBuilder.RecursionType type = queryBuilder.getRecursionType(null);
        assertThat(type).isEqualTo(ListingUriQueryBuilder.RecursionType.SELF);
    }

    @Test
    public void default_recursion_true_should_be_RECURSION() throws Exception {
        ListingUriQueryBuilder queryBuilder = new ListingUriQueryBuilder();
        queryBuilder.setDefaultRecursive(true);
        ListingUriQueryBuilder.RecursionType type = queryBuilder.getRecursionType(null);
        assertThat(type).isEqualTo(ListingUriQueryBuilder.RecursionType.RECURSION);
    }

    @Test
    public void property_value_of_false_should_be_SELF() throws Exception {
        ListingUriQueryBuilder queryBuilder = new ListingUriQueryBuilder();
        queryBuilder.setDefaultRecursive(true);
        Property property = getRecursiveProperty("false");
        ListingUriQueryBuilder.RecursionType type = queryBuilder.getRecursionType(property);
        assertThat(type).isEqualTo(ListingUriQueryBuilder.RecursionType.SELF);
    }

    @Test
    public void property_value_of_true_should_be_RECURSION() throws Exception {
        ListingUriQueryBuilder queryBuilder = new ListingUriQueryBuilder();
        Property property = getRecursiveProperty("true");
        ListingUriQueryBuilder.RecursionType type = queryBuilder.getRecursionType(property);
        assertThat(type).isEqualTo(ListingUriQueryBuilder.RecursionType.RECURSION);
    }

    @Test
    public void property_value_of_selected_should_be_SELECTED() throws Exception {
        ListingUriQueryBuilder queryBuilder = new ListingUriQueryBuilder();
        Property property = getRecursiveProperty("selected");
        ListingUriQueryBuilder.RecursionType type = queryBuilder.getRecursionType(property);
        assertThat(type).isEqualTo(ListingUriQueryBuilder.RecursionType.SELECTED);
    }

    private Property getRecursiveProperty(String value) {
        PropertyImpl property = new PropertyImpl();
        property.setDefinition(recursivePropertyDef);
        property.setStringValue(value);
        return property;
    }
}
