module ps.ProcelioLauncher {
    requires javafx.graphics;
    requires commons.compress;
    requires jbsdiff;
    requires unirest.java;
    requires javafx.controls;
    requires java.naming;
    requires ps.ProcelioCommon;
    requires gson;
    requires org.slf4j;
    requires java.desktop;
    uses org.slf4j.spi.SLF4JServiceProvider;
    exports procul.studios;
}