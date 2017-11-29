package procul.studios.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.Message;
import spark.Request;
import spark.Response;

import static procul.studios.ProcelioServer.gson;

public class RestException extends Exception{
    private static Logger LOG = LoggerFactory.getLogger(RestException.class);
    public RestException(String message){
        super(message);
    }

    public RestException(Request req, Response res, String message, int code){
        super(exception(req, res, message, code));
    }

    public static String exception(Request req, Response res, String message, int code) {
        res.status(code);
        LOG.debug("{}: Created Exception {}: `{}`", req.attribute("requestID"), code, message);
        return gson.toJson(new Message("Code " + String.valueOf(code) + ": " + message));
    }
}
