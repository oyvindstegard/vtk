package vtk.repository.resourcetype.property;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.content.JsonParseResult;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.Value;

public class JsonSyntaxErrorsEvaluator implements PropertyEvaluator {

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {
        if (ctx.getContent() == null) {
            return false;
        }
        if (ctx.getEvaluationType() == Type.ContentChange
                || ctx.getEvaluationType() == Type.Create) {

            try {
                JsonParseResult json = ctx.getContent()
                        .getContentRepresentation(JsonParseResult.class);
                if (!json.error.isPresent()) {
                    return false;
                }
                String msg = json.error.get().getMessage();
                if (msg == null) msg = "Syntax error";
                property.setValues(new Value[] {
                        new Value(msg, PropertyType.Type.STRING)
                });
                return true;
            }
            catch (Exception e) {
                return false;
            }
        } 

        return property.isValueInitialized();
    }

}
