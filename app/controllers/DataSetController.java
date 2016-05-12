package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import sdk.AppTree;
import sdk.data.*;
import sdk.serializers.DataSetModule;
import sdk.utils.AuthenticationInfo;
import sdk.utils.Parameters;
import sdk.utils.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Created by alexis on 5/3/16.
 */
public class DataSetController extends Controller {

    public DataSetController() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new DataSetModule());
        Json.setObjectMapper(objectMapper);
    }

    public CompletionStage<Result> getDataSet(String dataSetName) {
        Http.Request request = request();
        return CompletableFuture.supplyAsync(() -> {
            AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
            Parameters parameters = new Parameters(request.queryString());
            DataSource dataSource = AppTree.lookupDataSetHandler(dataSetName).orElseThrow(() -> new RuntimeException("Invalid Data Set"));
            return dataSource.getDataSet(authenticationInfo, parameters);
        })
        .thenApply(dataSourceResponse -> ok(Json.toJson(dataSourceResponse)))
        .exceptionally(exception -> {
            return ok(Json.toJson(new Response(false, exception.getMessage())));
        });
    }

    public CompletionStage<Result> getDataConfiguration(String dataSetName) {
        Http.Request request = request();
        return CompletableFuture.supplyAsync(() -> {
            DataSource dataSource = AppTree.lookupDataSetHandler(dataSetName).orElseThrow(() -> new RuntimeException("Invalid Data Set"));
            AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
            Parameters parameters = new Parameters(request.queryString());
            return dataSource.getConfiguration(authenticationInfo, parameters);
        })
        .thenApply(response -> ok(Json.toJson(response)))
        .exceptionally(exception -> {
            Response response = new Response(false, exception.getMessage());
            return ok(Json.toJson(response));
        });
    }


    public CompletionStage<Result> createDataSetItem(String dataSetName) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return dataSetItemFromRequest(dataSetName, request)
                .thenApply(dataSetItem -> {
                    DataSource dataSource = AppTree.lookupDataSetHandler(dataSetName).get();
                    return dataSource.createDataSetItem(dataSetItem, authenticationInfo, parameters);
                })
                .thenApply(dataSet -> ok(Json.toJson(dataSet)));
    }

    public CompletionStage<Result> updateDataSetItem(String dataSetName) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return dataSetItemFromRequest(dataSetName, request)
                .thenApply(dataSetItem -> {
                    DataSource dataSource = AppTree.lookupDataSetHandler(dataSetName).get();
                    return dataSource.updateDataSetItem(dataSetItem, authenticationInfo, parameters);
                })
                .thenApply(dataSet -> ok(Json.toJson(dataSet)));
    }

    public CompletionStage<Result> searchDataSet(String dataSetName) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return dataSetItemFromRequest(dataSetName, request)
                .thenApply(dataSetItem -> {
                    DataSource dataSource = AppTree.lookupDataSetHandler(dataSetName).get();
                    return dataSource.queryDataSet(dataSetItem, authenticationInfo, parameters);
                })
                .thenApply(dataSet -> ok(Json.toJson(dataSet)));
    }

    public CompletionStage<Result> getDataSetItem(String dataSetName, String primaryKey) {
        Http.Request request = request();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return CompletableFuture
                .supplyAsync(() -> (DataSource)AppTree.lookupDataSetHandler(dataSetName).orElseThrow(() -> new RuntimeException("Invalid DataSet")))
                .thenApply(dataSource -> dataSource.getDataSetItem(authenticationInfo, primaryKey, parameters))
                .thenApply(dataSet -> ok(Json.toJson(dataSet)));
    }

    private CompletionStage<DataSetItem> dataSetItemFromRequest(String dataSetName, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> (DataSource)AppTree.lookupDataSetHandler(dataSetName).orElseThrow(() -> new RuntimeException("Invalid Data Set")))
                .thenApply(dataSource -> getServiceConfiguration(dataSource,request))
                .thenApply(serviceConfiguration -> new DataSet(serviceConfiguration.getAttributes()))
                .thenApply(dataSet -> {
                    Http.MultipartFormData body = request.body().asMultipartFormData();
                    Map<String, String[]> bodyMap = body.asFormUrlEncoded();
                    List<Http.MultipartFormData.FilePart> files = body.getFiles();
                    String formJSON = bodyMap.get("formJSON")[0];
                    HashMap<String, Http.MultipartFormData.FilePart> attachmentMap = new HashMap<>();
                    for (Http.MultipartFormData.FilePart file : files ) {
                        attachmentMap.put(file.getKey(), file);
                    }
                    ObjectNode json = (ObjectNode) Json.parse(formJSON);
                    return dataSetItemForJSON(json, dataSet, attachmentMap);
                });
    }

    private DataSetItem dataSetItemForJSON(ObjectNode json, DataSet dataSet, HashMap<String, Http.MultipartFormData.FilePart> attachmentMap) {
        DataSetItem dataSetItem = dataSet.addNewDataSetItem();
        dataSetItem.updateFromJSON(json, attachmentMap);
        return dataSetItem;
    }

    private ServiceConfiguration getServiceConfiguration(DataSource dataSource, Http.Request request) {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(request.headers());
        Parameters parameters = new Parameters(request.queryString());
        return dataSource.getConfiguration(authenticationInfo, parameters);
    }
}
