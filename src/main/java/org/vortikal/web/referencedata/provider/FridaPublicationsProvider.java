package org.vortikal.web.referencedata.provider;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.vortikal.repository.ContentStream;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;

public class FridaPublicationsProvider implements ReferenceDataProvider {

    private Repository repository = null;


    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    @SuppressWarnings("unchecked")
    public void referenceData(Map model, HttpServletRequest request) throws Exception {

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        RequestContext requestContext = RequestContext.getRequestContext();

        Resource resource = this.repository
                .retrieve(securityContext.getToken(), requestContext.getResourceURI(), false);

        Property prop = (Property) resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE,
                "externalScientificInformation");

        Property prop2 = (Property) resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE,
                "selectedPublications");

        if (prop != null) {
            ContentStream cs = prop.getBinaryStream();
            InputStream is = cs.getStream();

            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            StringBuilder sb = new StringBuilder();

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            isr.close();
            is.close();

            JSONArray publications = JSONArray.fromObject(sb.toString());

            Iterator it = publications.listIterator();

            // Differantiate
            JSONArray pBooks = new JSONArray();
            JSONArray pSciArtBookChapters = new JSONArray();
            JSONArray pOther = new JSONArray();

            while (it.hasNext()) {
                JSONObject publication = (JSONObject) it.next();
                String mainCategory = (String) publication.get("mainCategoryCode");
                String subCategory = (String) publication.get("subCategoryNO");

                if (mainCategory.equals("BOK")) {
                    pSciArtBookChapters.add(publication);
                } else if (subCategory.equals("Vitenskapelig artikkel")
                        || subCategory.equals("Populærvitenskapelig artikkel") || mainCategory.equals("BOKRAPPORTDEL")) {
                    pBooks.add(publication);
                } else {
                    pOther.add(publication);
                }
            }

            model.put("pBooks", pBooks);
            model.put("pSciArtBookChapters", pSciArtBookChapters);
            model.put("pOther", pOther);

        }

        if (prop2 != null) {
            model.put("selectedPublications", "here comes the selectedPublications");
        } else {
            model.put("selectedPublications", null);
        }

    }
}