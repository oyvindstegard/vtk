package vtk.web.search;

import org.junit.Test;
import vtk.repository.Property;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;

import static org.assertj.core.api.Assertions.assertThat;
import vtk.repository.Namespace;

public class ListingUriQueryBuilderTest {
    private static final PropertyTypeDefinitionImpl RECURSIVE_PROP_DEF =
            PropertyTypeDefinitionImpl.createDefault(Namespace.DEFAULT_NAMESPACE, "recursive-listing", false);

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
        return RECURSIVE_PROP_DEF.createProperty(value);
    }
}
