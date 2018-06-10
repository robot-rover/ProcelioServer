module ps.ProcelioLauncher {
    requires javafx.graphics;
    requires javafx.controls;
    requires java.datatransfer;
    requires java.sql;
    requires java.naming;
    exports procul.studios;
    uses org.slf4j.spi.SLF4JServiceProvider;
}
