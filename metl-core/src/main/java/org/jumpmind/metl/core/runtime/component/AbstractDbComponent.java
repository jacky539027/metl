package org.jumpmind.metl.core.runtime.component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.jumpmind.db.sql.SqlScriptReader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;


abstract public class AbstractDbComponent extends AbstractComponentRuntime {

    protected NamedParameterJdbcTemplate getJdbcTemplate() {
    	if (getResourceRuntime() == null) {
            throw new RuntimeException("The data source resource has not been configured.  Please configure it.");
        }
    	return new NamedParameterJdbcTemplate((DataSource) this.context.getResourceRuntime().reference());
    }
    
    protected List<String> getSqlStatements(String script) {
        List<String> sqlStatements = new ArrayList<String>();
        SqlScriptReader scriptReader = new SqlScriptReader(new StringReader(script));
        try {
            String sql = scriptReader.readSqlStatement();
            while (sql != null) {
                sqlStatements.add(sql);
                sql = scriptReader.readSqlStatement();
            }
            return sqlStatements;
        } finally {
            IOUtils.closeQuietly(scriptReader);
        }
    }

}
