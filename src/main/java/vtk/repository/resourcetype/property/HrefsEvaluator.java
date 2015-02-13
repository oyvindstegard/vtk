package vtk.repository.resourcetype.property;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.util.text.Json;

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
            
            List<Value> values = new ArrayList<>();
            for (Object o: arr) {
                if (! (o instanceof Json.MapContainer)) {
                    continue;
                }
                Json.MapContainer obj = (Json.MapContainer) o;
                values.add(new Value(obj));
            }
            
            property.setValues(values.toArray(new Value[values.size()]));
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

}
