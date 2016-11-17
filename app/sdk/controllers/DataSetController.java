package sdk.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import sdk.AppTree;
import sdk.ValidateRequestAction;
import sdk.data.DataSetItem;
import sdk.data.Event;
import sdk.data.ServiceConfiguration;
import sdk.datasources.AttachmentDataSource_Internal;
import sdk.datasources.DataSource_Internal;
import sdk.utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Created by alexis on 5/3/16.
 */
public class DataSetController extends DataController {

    public DataSetController() {
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> getDataSet(String dataSetName) {
        Http.Request request = request();
        String callbackURL = request.getHeader(Constants.CORE_CALLBACK_URL);

        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());
        if ( callbackURL != null ) {
            generateDataSourceResponse(dataSource, callbackURL, authenticationInfo, parameters);
            return CompletableFuture.completedFuture(ok(JsonUtils.toJson(Response.asyncSuccess())));
        } else {
            return dataSource.getDataSet(authenticationInfo,parameters)
                    .thenApply(dataSet -> ok(dataSet.toJSON()))
                    .exceptionally(ResponseExceptionHandler::handleException);
        }
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> searchDataSet(String dataSetName) {
        Http.Request request = request();
        String callbackURL = request.getHeader(Constants.CORE_CALLBACK_URL);
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());
        return getServiceConfiguration(dataSource, request)
                .thenCompose(configuration -> dataSetItemFromRequest(configuration, request, true))
                .thenCompose(dataSetItem -> {
                    if ( callbackURL != null ) {
                        generateDataSourceSearchResponse(dataSource, dataSetItem, callbackURL, authenticationInfo, parameters);
                        return CompletableFuture.completedFuture(ok(JsonUtils.toJson(Response.asyncSuccess())));
                    } else {
                        return dataSource.queryDataSet(dataSetItem, authenticationInfo, parameters).thenApply(dataSet -> ok(dataSet.toJSON()));
                    }
                })
                .exceptionally(throwable -> ResponseExceptionHandler.handleException(throwable, callbackURL != null));
    }


    private void generateDataSourceResponse(DataSource_Internal dataSource, String callbackURL, AuthenticationInfo authenticationInfo, Parameters parameters) {
                dataSource.getDataSet(authenticationInfo, parameters)
                .whenComplete((dataSet, throwable) -> {
                    if ( throwable != null ) {
                        sendDataSetExceptionCallback(throwable, callbackURL);
                    } else {
                        sendDataSetResponse(dataSet, callbackURL);
                    }
                });
    }

    private void generateDataSourceSearchResponse(DataSource_Internal dataSource, DataSetItem dataSetItem, String callbackURL, AuthenticationInfo authenticationInfo, Parameters parameters) {
             dataSource.queryDataSet(dataSetItem, authenticationInfo, parameters)
                .whenComplete((dataSet, throwable) -> {
                    if ( throwable != null ) {
                        sendDataSetExceptionCallback(throwable, callbackURL);
                    } else {
                        sendDataSetResponse(dataSet, callbackURL);
                    }
                });
    }

    public CompletionStage<Result> getDataConfiguration(String dataSetName) {
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());

        return CompletableFuture
                .supplyAsync(dataSource::getConfiguration)
                .thenApply(response -> ok(JsonUtils.toJson(response)))
                .exceptionally(ResponseExceptionHandler::handleException);
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> createDataSetItem(String dataSetName) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());

        return getServiceConfiguration(dataSource, request)
                .thenCompose(configuration -> dataSetItemFromRequest(configuration, request, false))
                .thenCompose(dataSetItem -> dataSource.createDataSetItem(dataSetItem, authenticationInfo, parameters))
                .thenApply(dataSet -> ok(dataSet.toJSON()))
                .exceptionally(ResponseExceptionHandler::handleException);
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> updateDataSetItem(String dataSetName) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());

        return getServiceConfiguration(dataSource, request)
                .thenCompose(configuration -> dataSetItemFromRequest(configuration, request, false))
                .thenCompose(dataSetItem -> dataSource.updateDataSetItem(dataSetItem, authenticationInfo, parameters))
                .thenApply(dataSet -> ok(dataSet.toJSON()))
                .exceptionally(ResponseExceptionHandler::handleException);
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> bulkUpdate(String dataSetName) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());

        Http.MultipartFormData body = request.body().asMultipartFormData();
        Map<String, String[]> bodyMap = body.asFormUrlEncoded();
        String recordsJSONArray = bodyMap.get("records")[0];
        ArrayNode idsArray = (ArrayNode) Json.parse(recordsJSONArray);
        List<String> ids = new ArrayList<>();
        for ( JsonNode node : idsArray ) {
            ids.add(node.asText());
        }
        return getServiceConfiguration(dataSource, request)
                .thenCompose(configuration -> dataSetItemFromRequest(configuration, request, false))
                .thenCompose(dataSetItem -> dataSource.bulkUpdateDataSetItems(ids, dataSetItem, authenticationInfo, parameters))
                .thenApply(dataSet -> ok(dataSet.toJSON()))
                .exceptionally(ResponseExceptionHandler::handleException);
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> getDataSetItem(String dataSetName, String primaryKey) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());

        return dataSource.getDataSetItem(authenticationInfo, primaryKey, parameters)
                .thenApply(dataSet -> ok(dataSet.toJSON()))
                .exceptionally(ResponseExceptionHandler::handleException);
    }

    public CompletionStage<Result> getAttachment(String attachmentID) {
        AttachmentDataSource_Internal source = AppTree.getAttachmentDataSource_internal();
        if ( source == null ) {
            return CompletableFuture.completedFuture(notFound("No attachment source provided"));
        }
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return source.getAttachment(attachmentID, authenticationInfo, parameters)
                .thenApply(attachmentResponse -> {
                    String mimeType = attachmentResponse.contentType != null ? attachmentResponse.contentType : "application/octet-stream";
                    String filename = attachmentResponse.fileName != null ? attachmentResponse.fileName : UUID.randomUUID().toString();
                    return ok(attachmentResponse.inputStream)
                            .withHeader("Filename", filename )
                            .withHeader("Content-Type", mimeType);
                });
    }

    @With({ValidateRequestAction.class})
    public CompletionStage<Result> postEvent(String dataSetName, String dataSetItemID) {
        JsonNode json = request().body().asJson();
        if (json == null) return CompletableFuture.completedFuture(badRequest("No event information was provided"));
        DataSource_Internal dataSource = AppTree.lookupDataSetHandler(dataSetName);
        if ( dataSource == null ) return CompletableFuture.completedFuture(notFound());

        Event event = JsonUtils.fromJson(json, Event.class);
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return dataSource.updateEventForDataSetItem(dataSetItemID, event, authenticationInfo, parameters)
                .thenApply(response -> ok(JsonUtils.toJson(response)))
                .exceptionally(ResponseExceptionHandler::handleException);
    }

    private CompletionStage<ServiceConfiguration> getServiceConfiguration(DataSource_Internal dataSource, Http.Request request) {
        return CompletableFuture.supplyAsync(dataSource::getConfiguration);
    }
}
