package procul.studios;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.crypto.SecretKeyFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static procul.studios.sqlbindings.Tables.*;

/**
 *
 */
//todo: add class description
public class Database {

    static {System.setProperty("org.jooq.no-logo", "true");}

    public static void main(String[] args){
        new Database();
    }

    DSLContext context;

    public Database() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Can't load SQLite driver", ex);
        }
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ProcelioServer.databasePath);
            context = DSL.using(conn, SQLDialect.SQLITE);
            //todo: uncomment
            // context.truncate(AUTHTABLE).execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public DSLContext getContext() {
        return context;
    }

}
