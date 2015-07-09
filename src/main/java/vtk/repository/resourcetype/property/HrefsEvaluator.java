package vtk.repository.resourcetype.property;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.text.Json;
import vtk.web.service.URL;

public class HrefsEvaluator implements LatePropertyEvaluator {
    private PropertyTypeDefinition linksPropDef;
    
    public HrefsEvaluator(PropertyTypeDefinition linksPropDef) {
        this.linksPropDef = linksPropDef;
    }
    
    public static enum RelType {
        ABSOLUTE,
        RELATIVE,
        ROOT_RELATIVE,
        PROTOCOL_RELATIVE
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
                Object ref = obj.get("url");
                if (ref != null) {
                    obj.put("reltype", relType(ref.toString()));
                }
                
                values.add(obj);
            }
            Json.MapContainer propVal = new Json.MapContainer();
            propVal.put("links", values);
            propVal.put("size", values.size());
            property.setJSONValue(propVal);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
    
    private String relType(String ref) {
        if (ref.startsWith("//")) return RelType.PROTOCOL_RELATIVE.name();
        if (URL.isRelativeURL(ref)) return RelType.RELATIVE.name();
        if (ref.startsWith("/") && !ref.contains("../") && !ref.contains("./")) 
            return RelType.ROOT_RELATIVE.name();
        return RelType.ABSOLUTE.name();
    }

}
