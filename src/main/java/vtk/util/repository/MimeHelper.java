/* Copyright (c) 2004, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.util.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.util.text.TextUtils;


/**
 * Utility class for guessing MIME types based on file
 * names. Reads its properties set from a class path resource
 * specified by the system property
 * <code>vtk.mime.properties.file</code>. The default path
 * (if not specified) is
 * <code>vtk/util/repository/mime.properties</code>.
 *
 * <p>The format of the properties file is
 * <pre>
 * extension1 = MIME type 1
 * ...
 * extensionN = MIME type N
 * </pre>
 * where extensions should not include the leading dot.
 * 
 * <p>There is also a special key used to list file name prefixes which shall always
 * be guessed to {@link #DEFAULT_MIME_TYPE}: <code>unknownTypePrefixes</code>. Its
 * value should be a comma-separated list.
 */
public class MimeHelper {

    /**
     * The default MIME type, returned when no other mapping exists
     * for a given file extension. Its value is
     * <code>application/octet-stream</code>.
     */
    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private static Properties properties;
    private static String[] unknownTypePrefixes;
            
    static {
        load();
    }

    /**
     * Maps a file name to a MIME type based on its file extension.
     *
     * @param file a file name (or path).
     * @return the MIME type, or {@link #DEFAULT_MIME_TYPE} if no
     * mapping exists for the file extension in question.
     */
    public static String map(String file) {
        String fileName = getFilename(file);
        
        if (hasUnknownTypePrefix(fileName)) {
            return DEFAULT_MIME_TYPE;
        }
        
        String extension = findExtension(fileName);

        return properties.getProperty(extension, DEFAULT_MIME_TYPE);
    }
    
    private static boolean hasUnknownTypePrefix(String fileName) {
        for (String prefix: unknownTypePrefixes) {
            if (fileName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtain map from file extension to MIME type.
     *
     * <p>The file extensions do not include the generally expected leading "dot".
     *
     * @return a map of file extensions to MIME type.
     */
    public static Map<String,String> extensionToMimetypeMap() {
        return properties.entrySet().stream()
                .filter(e -> !"unknownTypePrefixes".equals(e.getKey()))
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(), e -> e.getValue().toString()));
    }

    /**
     * Attempts to extract file extension from the given file name or file path.
     * If file name has no extension part, then the empty string is returned.
     * Otherwise a lower cased file extension without the leading dot is returned.
     * 
     * @param file the file name or file path
     * @return lower cased file extension without leading dot, or empty string
     *     if no file extension was found.
     */
    public static String findExtension(String file) {
        if (file.indexOf(".") < 0) {
            return "";
        }

        if (file.lastIndexOf(".") >= (file.length() - 1)) {
            return "";
        }

        return file.substring(file.lastIndexOf(".") + 1,
            file.length()).toLowerCase();
    }
    
    private static String getFilename(String file) {
        int idx = file.lastIndexOf('/');
        if (idx != -1) {
            return file.substring(idx+1);
        }
        
        return file;
    }

    protected static void load() {
        properties = new Properties();

        Logger logger = LoggerFactory.getLogger(MimeHelper.class);

        String fileName = System.getProperty("vtk.mime.properties.file",
                "vtk/util/repository/mime.properties");

        try {
            InputStream inStream = MimeHelper.class.getClassLoader()
                                                   .getResourceAsStream(fileName);
            properties.load(inStream);
            inStream.close();
            logger.info("Loaded MIME type properties from classpath resource: "
                        + fileName);
        } catch (IOException e) {
            logger.warn("Caught IOException while reading MIME " +
                "properties file: '" + fileName + "'.", e);
        }
        
        String unknownTypePrefixConfig = properties.getProperty("unknownTypePrefixes");
        if (unknownTypePrefixConfig != null) {
            unknownTypePrefixes = TextUtils.parseCsv(unknownTypePrefixConfig,',', 
                    TextUtils.DISCARD|TextUtils.TRIM);
        } else {
            unknownTypePrefixes = new String[0];
        }
        
    }
}
