module ps.ProcelioDelta {
    requires ch.qos.logback.classic;
    requires gson;
    requires org.slf4j;
    requires jbsdiff;
    requires commons.compress;
    requires ps.ProcelioCommon;
    exports procul.studios.delta;
    exports procul.studios.gson;
}