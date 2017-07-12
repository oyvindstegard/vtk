package vtk.repository.resourcetype;

import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.apache.commons.lang.ArrayUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalImpl;

public class ResourceContentTypeRegexpAssertionTest {
    
    private Principal principal;
    
    /* MSOffice 2007+: http://filext.com/faq/office_mime_types.php */
    
    private String[] testWordContentTypes = {
            "application/msword",
            "application/vnd.ms-word",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.ms-word.document.macroEnabled.12",
            "application/vnd.ms-word.template.macroEnabled.12" };

    private String[] testExcelContentTypes = {
            "application/ms-excel",
            "application/x-msexcel",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.ms-excel.sheet.macroEnabled.12",
            "application/vnd.ms-excel.template.macroEnabled.12",
            "application/vnd.ms-excel.addin.macroEnabled.12",
            "application/vnd.ms-excel.sheet.binary.macroEnabled.12" };

    private String[] testPowerpointContentTypes = {
            "application/ms-ppt",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.ms-powerpoint.addin.macroEnabled.12",
            "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
            "application/vnd.ms-powerpoint.template.macroEnabled.12",
            "application/vnd.ms-powerpoint.slideshow.macroEnabled.12" };
    
    // And all together..
    private String[] testOoXmlContentTypes = (String[]) ArrayUtils.addAll(testWordContentTypes, ArrayUtils.addAll(testExcelContentTypes, testPowerpointContentTypes));
    
    @Before
    public void setUp() {
        principal = new PrincipalImpl("user", Type.USER);
    }
    
    @Test
    public void wordContentTypes() {
        String pattern = "application/(msword|vnd\\.(ms-word(|\\.(document|template)\\.macroEnabled\\.12)|openxmlformats-officedocument\\.wordprocessingml\\.(document|template)))";
        assertTrue(matchTestContentTypeWithPattern(testWordContentTypes, pattern));
    }

    @Test
    public void excelContentTypes() {
        String pattern = "application/(ms-excel|x-msexcel|vnd\\.(ms-excel(|\\.(sheet(|\\.binary)|template|addin)\\.macroEnabled\\.12)|openxmlformats-officedocument\\.spreadsheetml\\.(sheet|template)))";
        assertTrue(matchTestContentTypeWithPattern(testExcelContentTypes, pattern));
    }
    
    @Test
    public void powerpointContentTypes() {
        String pattern = "application/(ms-ppt|vnd\\.(ms-powerpoint(|\\.(addin|presentation|template|slideshow)\\.macroEnabled\\.12)|openxmlformats-officedocument\\.presentationml\\.(presentation|template|slideshow)))";
        assertTrue(matchTestContentTypeWithPattern(testPowerpointContentTypes, pattern));
    }
    
    @Test
    public void ooXmlContentTypes() {
        String pattern = "application/(msword|ms-excel|x-msexcel|ms-ppt|vnd\\." +
        		       "((ms-word(|\\.(document|template)\\.macroEnabled\\.12)" +
        		        "|ms-excel(|\\.(sheet(|\\.binary)|template|addin|)\\.macroEnabled\\.12)" +
        		        "|ms-powerpoint(|\\.(addin|presentation|template|slideshow)\\.macroEnabled\\.12))" +
        		        "|(openxmlformats-officedocument\\." +
        		           "(wordprocessingml\\.(document|template)" +
        		           "|spreadsheetml\\.(sheet|template)" +
        		           "|presentationml\\.(presentation|template|slideshow)))))";
        
        assertTrue(matchTestContentTypeWithPattern(testOoXmlContentTypes, pattern));
    }
    
    private boolean matchTestContentTypeWithPattern(String[] testContentTypes, String pattern) {
        for(String testContentType : testContentTypes) {
            if(!matchTestContentTypeWithPattern(testContentType, pattern)) {
                System.out.println(testContentType);
                return false;
            }
        }
        return true;
    }
    
    private boolean matchTestContentTypeWithPattern(final String testContentType, String pattern) {
        ResourceContentTypeRegexpAssertion assertion = new ResourceContentTypeRegexpAssertion(pattern);
        Mockery context = new Mockery();
        final Resource resource = context.mock(Resource.class);
        context.checking(new Expectations(){
            {
                allowing(resource).getContentType();
                will(returnValue(testContentType));
            }
        });
        return assertion.matches(Optional.ofNullable(resource), Optional.ofNullable(principal));
    }

}
