module ps.ProcelioLauncher {
    requires java.desktop;
    requires javafx.graphics;
    requires javafx.controls;
    requires java.datatransfer;
    requires java.sql;
    requires java.naming;
    requires jdk.crypto.ec;
    //requires static javax.servlet.api;

    uses org.slf4j.spi.SLF4JServiceProvider;
    uses ch.qos.logback.classic.spi.Configurator;
    provides org.slf4j.spi.SLF4JServiceProvider with ch.qos.logback.classic.spi.LogbackServiceProvider;


    exports procul.studios;
}
