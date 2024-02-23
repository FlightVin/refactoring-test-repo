import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.hibernate.engine.jdbc.internal.Formatter;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.jdbc.spi.SqlStatementLogger;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.ConnectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DbOpenHelper {
    private static final Logger log = LoggerFactory.getLogger(DbOpenHelper.class);

    private final ConnectionHelper connectionHelper;
    private final SqlStatementLogger sqlStatementLogger;
    private final List<Exception> exceptions = new ArrayList<>();
    private Formatter formatter;
    private Statement statement;

    public DbOpenHelper(ServiceRegistry serviceRegistry) throws HibernateException {
        final JdbcServices jdbcServices = serviceRegistry.getService(JdbcServices.class);
        connectionHelper = new SuppliedConnectionProviderConnectionHelper(jdbcServices.getConnectionProvider());
        sqlStatementLogger = jdbcServices.getSqlStatementLogger();
        formatter = (sqlStatementLogger.isFormat() ? FormatStyle.DDL : FormatStyle.NONE).getFormatter();
    }

    public void open() {
        log.info("Opening database and executing incremental updates");

        Connection connection = null;
        Writer outputFileWriter = null;

        exceptions.clear();

        try {
            try {
                connectionHelper.prepare(true);
                connection = connectionHelper.getConnection();
            } catch (SQLException sqle) {
                exceptions.add(sqle);
                log.error("Unable to get database metadata", sqle);
                throw sqle;
            }

            // Check if database is already created
            Integer oldVersion = null;
            try {
                statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select c.CFG_VALUE_C from T_CONFIG c where c.CFG_ID_C='DB_VERSION'");
                if (result.next()) {
                    String oldVersionStr = result.getString(1);
                    oldVersion = Integer.parseInt(oldVersionStr);
                }
            } catch (SQLException e) {
                log.error("Error while fetching old version from the database", e);
                exceptions.add(e);
            }
        } catch (Exception ex) {
            log.error("An error occurred during database open", ex);
            exceptions.add(ex);
        } finally {
            closeConnection(connection);
            closeWriter(outputFileWriter);
        }
    }

    private void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("Error closing database connection", e);
            }
        }
    }

    private void closeWriter(Writer writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.error("Error closing output file writer", e);
            }
        }
    }
}