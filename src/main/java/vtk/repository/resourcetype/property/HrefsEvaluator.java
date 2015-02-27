package vtk.repository.resourcetype.property;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.text.Json;
import vtk.util.text.JsonBuilder;

public class HrefsEvaluator implements LatePropertyEvaluator {
    private PropertyTypeDefinition linksPropDef;
    
    public HrefsEvaluator(PropertyTypeDefinition linksPropDef) {
        this.linksPropDef = linksPropDef;
    }

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {

        Property linksProp = ctx.getNewResource().getProperty(linksPropDef);
        if (linksProp == null) return false;
        try {
            InputStream stream = linksProp.getBinaryStream().getStream();
            Json.ListContainer arr = Json.parseToContainer(stream).asArray();
            
            List<Object> values = new ArrayList<>();
            for (Object o: arr) {
                if (! (o instanceof Json.MapContainer)) {
                    continue;
                }
                Json.MapContainer obj = (Json.MapContainer) o;
                values.add(obj);
            }
            
            Json.MapContainer propVal = new Json.MapContainer();
            propVal.put("links", values);

            JsonBuilder builder = new JsonBuilder();
            builder.beginObject();
            builder.member("links", values);
            builder.endObject();
            builder.endJson();
            
            byte[] buffer = builder.jsonString().getBytes("utf-8");
            property.setBinaryValue(buffer, "application/json");
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

}
