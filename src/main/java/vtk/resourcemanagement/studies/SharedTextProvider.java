package vtk.resourcemanagement.studies;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.util.text.Json;
import vtk.web.referencedata.ReferenceDataProvider;

/**
 * Provides shared text values in model under key 'sharedTextProps' (a map).
 * 
 * Used by editor code.
 * 
 */
public class SharedTextProvider implements ReferenceDataProvider {

    private SharedTextResolver sharedTextResolver;

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void referenceData(Map model, HttpServletRequest request) {
        try {
            Map<String, Map<String, Json.MapContainer>> sharedTextPropsMap = 
                    sharedTextResolver.resolveSharedTexts(request);
            model.put("sharedTextProps", sharedTextPropsMap);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Required
    public void setSharedTextResolver(SharedTextResolver sharedTextResolver) {
        this.sharedTextResolver = sharedTextResolver;
    }
}
