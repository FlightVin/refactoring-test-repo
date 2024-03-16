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

    public DbOpenHelper(ServiceRegistry serviceRegistry) throws HibernateException {
        final JdbcServices jdbcServices = serviceRegistry.getService(JdbcServices.class);
        connectionHelper = new SuppliedConnectionProviderConnectionHelper(jdbcServices.getConnectionProvider());
        sqlStatementLogger = jdbcServices.getSqlStatementLogger();
        formatter = (sqlStatementLogger.isFormat() ? FormatStyle.DDL : FormatStyle.NONE).getFormatter();
    }

    public void open() {
        log.info("Opening database and executing incremental updates");

        Connection connection = null;
        exceptions.clear();

        try {
            connectionHelper.prepare(true);
            connection = connectionHelper.getConnection();

            Integer oldVersion = getOldVersion(connection);

            // Continue with other logic
        } catch (SQLException e) {
            exceptions.add(e);
            log.error("Unable to get database metadata", e);
            // Handle the exception
        } finally {
            closeConnection(connection);
        }
    }

    private Integer getOldVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery("select c.CFG_VALUE_C from T_CONFIG c where c.CFG_ID_C='DB_VERSION'");
            if (result.next()) {
                String oldVersionStr = result.getString(1);
                return Integer.parseInt(oldVersionStr);
            }
        }
        return null;
    }

    private void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("Error closing connection", e);
            }
        }
    }
}