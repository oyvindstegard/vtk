package org.vortikal.repository.store.db;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.vortikal.repository.store.BinaryContentDataAccessor;

public class SqlBinaryContentMapDataAccessor extends AbstractSqlMapDataAccessor implements BinaryContentDataAccessor {

	private static final Logger log = Logger.getLogger(SqlBinaryContentMapDataAccessor.class);

	public InputStream getBinaryStream(String binaryName, String binaryRef) {

		try {
			String sqlMap = getSqlMap("selectBinaryPropertyEntry");
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("binaryName", binaryName);
			params.put("binaryRef", binaryRef);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> resultList = getSqlMapClientTemplate().queryForList(sqlMap, params);
			return (InputStream) resultList.get(0).get("binaryStream");
		} catch (Exception e) {
			log.error("An error occured while getting the binary stream for " + binaryName + "/" + binaryRef, e);
		}

		return null;

	}

	public String getBinaryMimeType(String binaryName, String binaryRef) {
		try {
			String sqlMap = getSqlMap("selectBinaryMimeTypeEntry");
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("binaryName", binaryName);
			params.put("binaryRef", binaryRef);
			String binaryMimeType = (String) getSqlMapClientTemplate().queryForObject(sqlMap, params);
			return binaryMimeType;
		} catch (Exception e) {
			log.error("An error occured while getting the binary mimetype for " + binaryName + "/"+ binaryRef, e);
		}
		return null;
	}

}
