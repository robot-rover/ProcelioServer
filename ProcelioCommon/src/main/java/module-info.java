module ps.ProcelioCommon {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires ch.qos.logback.classic;
    requires gson;
    requires java.xml.bind;
    requires org.slf4j;
    exports procul.studios.pojo;
    exports procul.studios.pojo.response;
    exports procul.studios.pojo.request;
    exports procul.studios.util;
}