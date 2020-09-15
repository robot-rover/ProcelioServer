module ps.ProcelioLauncher {
    requires javafx.graphics;
    requires unirest.java;
    requires javafx.controls;
    requires java.naming;
    requires ps.ProcelioCommon;
    requires gson;
    requires org.slf4j;
    requires java.desktop;
    requires jbsdiff;
    requires commons.compress;
    requires ps.ProcelioDelta;
    requires jdk.crypto.ec;
    requires net.harawata.appdirs;
    requires ant;
    uses org.slf4j.spi.SLF4JServiceProvider;
    exports procul.studios;
}