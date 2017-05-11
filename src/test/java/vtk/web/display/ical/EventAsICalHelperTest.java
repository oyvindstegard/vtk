package vtk.web.display.ical;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Calendar;

import org.junit.BeforeClass;
import org.junit.Test;

import vtk.context.BaseContext;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.PropertySetImpl;
import vtk.repository.RepositoryImpl;
import vtk.repository.RepositoryResourceSetUpHelper;
import vtk.repository.resourcetype.DateValueFormatter;
import vtk.repository.resourcetype.HtmlValueFormatter;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.StringValueFormatter;
import vtk.repository.store.DefaultPrincipalMetadataDAO;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;

public class EventAsICalHelperTest {

    private static EventAsICalHelper helper;
    private static PropertyTypeDefinition startDatePropDef;
    private static PropertyTypeDefinition endDatePropDef;
    private static PropertyTypeDefinition locationPropDef;
    private static PropertyTypeDefinition introductionPropDef;
    private static PropertyTypeDefinition titlePropDef;

    @BeforeClass
    public static void init() throws Exception {
        helper = new EventAsICalHelper();

        startDatePropDef = RepositoryResourceSetUpHelper.getPropertyTypeDefinition(
                Namespace.DEFAULT_NAMESPACE, "start-date", Type.DATE,
                new DateValueFormatter());
        endDatePropDef = RepositoryResourceSetUpHelper.getPropertyTypeDefinition(
                Namespace.DEFAULT_NAMESPACE, "end-date", Type.DATE, new DateValueFormatter());
        locationPropDef = RepositoryResourceSetUpHelper.getPropertyTypeDefinition(
                Namespace.DEFAULT_NAMESPACE, "location", Type.STRING, new StringValueFormatter());
        introductionPropDef = RepositoryResourceSetUpHelper.getPropertyTypeDefinition(
                Namespace.DEFAULT_NAMESPACE, "introduction", Type.HTML, new HtmlValueFormatter());
        titlePropDef = RepositoryResourceSetUpHelper.getPropertyTypeDefinition(
                Namespace.DEFAULT_NAMESPACE, "title", Type.STRING, new StringValueFormatter());

        helper.setStartDate(startDatePropDef.getName());
        helper.setEndDate(endDatePropDef.getName());
        helper.setLocation(locationPropDef.getName());
        helper.setIntroduction(introductionPropDef.getName());
        helper.setTitle(titlePropDef.getName());

        RepositoryImpl repository = new RepositoryImpl();
        repository.setId("www.testhost.uio.no");
        BaseContext.pushContext();
        SecurityContext securityContext = new SecurityContext(null, null);
        SecurityContext.setSecurityContext(securityContext);
        RequestContext requestContext = new RequestContext(null, securityContext,
                null, null, null, Path.ROOT, null, false,
                false, true, repository, new DefaultPrincipalMetadataDAO());
        RequestContext.setRequestContext(requestContext);
    }

    @Test
    public void getAsICal() throws Exception {
        String iCal = helper.getAsICal(Arrays.asList(
                getEvent(Path.fromString("/events/some-event.html"))));
        assertNotNull(iCal);
    }

    private PropertySet getEvent(Path path) {
        PropertySetImpl event = new PropertySetImpl();
        event.setUri(path);

        Calendar cal = Calendar.getInstance();
        Property startDateProp = startDatePropDef.createProperty(cal.getTime());
        event.addProperty(startDateProp);

        cal.add(Calendar.HOUR, 2);
        Property endDateProp = endDatePropDef.createProperty(cal.getTime());
        event.addProperty(endDateProp);

        // XXX Needs proper setup of HtmlValueMapper
        // Property introductionProp =
        // introductionPropDef.createProperty("Introduction");
        // event.addProperty(introductionProp);

        Property locationProp = locationPropDef.createProperty("UiO");
        event.addProperty(locationProp);

        Property titleProp = titlePropDef.createProperty("ICal for an Event");
        event.addProperty(titleProp);

        return event;
    }

}
