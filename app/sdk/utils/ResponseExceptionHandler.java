package sdk.utils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.libs.ws.WSRequest;
import play.mvc.Controller;
import play.mvc.Result;
import rx.exceptions.OnErrorThrowable;
import sdk.exceptions.AuthorizationException;
import sdk.exceptions.PrimaryObjectNotFoundException;
import sdkmodels.utils.Constants;
import sdkmodels.utils.JsonUtils;
import sdkmodels.utils.Response;

import static sdkmodels.utils.Constants.CORE_CALLBACK_TYPE_ERROR;
import static sdkmodels.utils.Constants.SDK_ERROR_STATUS_CODE;

/**
 * Created by Matthew Smith on 5/12/16.
 * Copyright AppTree Software, Inc.
 */
public class ResponseExceptionHandler {
    public static Result handleException(Throwable throwable) {
        return handleException(throwable, false);
    }

    public static Result handleException(Throwable throwable, boolean async) {
        Throwable rootCause = findRootCause(throwable, Integer.MAX_VALUE);
        if ( rootCause instanceof OnErrorThrowable.OnNextValue ) {
            throwable = findRootCause(throwable, 1);
        } else {
            throwable = rootCause;
        }
        throwable.printStackTrace();
        if ( throwable instanceof PrimaryObjectNotFoundException) {
            if ( throwable.getMessage() != null ) {
                return Controller.notFound(JsonUtils.toJson(Response.fromException(throwable, async)));
            }
            return Controller.notFound();
        } else if ( throwable instanceof AuthorizationException) {
            return Controller.unauthorized();
        }
        return Controller.status(SDK_ERROR_STATUS_CODE,JsonUtils.toJson(Response.fromException(throwable, async)));
    }

    public static void updateCallbackWithException(WSRequest request, Throwable throwable) {
        String message = "";
        String coreCallbackType = CORE_CALLBACK_TYPE_ERROR;
        if ( throwable instanceof PrimaryObjectNotFoundException ) {
            if ( throwable.getMessage() != null ) {
                message = Response.fromException(throwable, true).getMessage();
            } else {
                message = "The data you are trying to access can not be found";
            }
        } else if ( throwable instanceof AuthorizationException ) {
            coreCallbackType = Constants.CORE_CALLBACK_TYPE_AUTH_FAILURE;
            message = "Authorization Failed";
        } else {
            message = Response.fromException(throwable, true).getMessage();
        }
        request.setHeader(Constants.CORE_CALLBACK_TYPE, coreCallbackType);
        ObjectNode json = Json.newObject();
        json.put(Constants.CORE_CALLBACK_MESSAGE, message);
        request.setBody(json);
    }

    public static Throwable findRootCause(Throwable throwable) {
        return findRootCause(throwable, Integer.MAX_VALUE);
    }

    public static Throwable findRootCause(Throwable throwable, int depth) {
        Throwable childCause = throwable;
        int counter = 0;
        while ( childCause.getCause() != null && counter < depth ) {
            childCause = childCause.getCause();
            counter++;
        }
        return childCause;
    }
}
