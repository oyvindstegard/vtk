package vtk.repository.store.db.ibatis;

import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.ibatis.type.JdbcType;

public class ClobTypeHandler extends org.apache.ibatis.type.ClobTypeHandler {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
				    String parameter, JdbcType jdbcType)
        throws SQLException {
        ps.setClob(i, new StringReader(parameter));
    }
}
