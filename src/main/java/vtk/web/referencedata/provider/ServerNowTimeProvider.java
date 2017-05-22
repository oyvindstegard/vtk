package vtk.web.referencedata.provider;

import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.web.referencedata.ReferenceDataProvider;

public class ServerNowTimeProvider implements ReferenceDataProvider {

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        model.put("nowTime", new Date());
    }
}
