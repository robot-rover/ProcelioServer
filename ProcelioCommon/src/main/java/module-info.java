module ps.ProcelioCommon {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires ch.qos.logback.classic;
    requires gson;
    requires org.slf4j;
    requires jbsdiff;
    requires commons.compress;
    exports procul.studios.pojo;
    exports procul.studios.pojo.response;
    exports procul.studios.pojo.request;
    exports procul.studios.util;
    exports procul.studios.delta;
}