package sdk.datasources.future;

import sdkmodels.data.DataSet;
import sdkmodels.data.DataSetItem;
import sdkmodels.data.Event;
import sdkmodels.utils.RecordActionResponse;
import sdk.datasources.DataSourceBase;
import sdkmodels.utils.AuthenticationInfo;
import sdkmodels.utils.Parameters;
import sdkmodels.utils.Response;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by matthew on 9/6/16.
 */
public interface DataSource extends DataSourceBase {
    /***
     * @param authenticationInfo A HashMap of any authentication information that came through in the request headers from the mobile client
     * @param params   a HashMap of the URL parameters included in the request.
     * @return The data source response that contains the list of data set items you want to return
     */
    CompletableFuture<DataSet> getDataSet(AuthenticationInfo authenticationInfo, Parameters params);

    /***
     *
     * @param id       The ID of the item to fetch
     * @param authenticationInfo a HashMap of any authentication information that came through in the request headers from the mobile client
     * @param parameters   a HashMap of the URL parameters included in the request
     * @return The data source response that contains the data set item with the requested ID
     */

    CompletableFuture<DataSetItem> getRecord(String id, AuthenticationInfo authenticationInfo, Parameters parameters);
    /**
     * @param queryDataItem The data set item containing the values to be searched on
     * @param authenticationInfo      a HashMap of any authentication parameters that came through in the request headers
     * @param params        a HashMap of the URL parameters included in the request
     * @return The data source response that contains the list of data set items which meet the search criteria
     */
    default CompletableFuture<DataSet> queryDataSet(DataSetItem queryDataItem, AuthenticationInfo authenticationInfo, Parameters params) {
        throw new UnsupportedOperationException("Search is not supported on this web service");
    }

    /**
     *
     * @param dataSetItem The data set item to be created
     * @param authenticationInfo    a Hashmap of any authentication parameters that came through the request headers
     * @param params      a HashMap of the URL parameters included in the request
     * @return The data source response that contains the newly created data set item
     */

    default CompletableFuture<RecordActionResponse> createRecord(DataSetItem dataSetItem, AuthenticationInfo authenticationInfo, Parameters params) {
        throw new UnsupportedOperationException("Create is not supported on this web service");
    }

    /**
     *
     * @param dataSetItem The data set item to be updated
     * @param authenticationInfo    a HashMap of any authentication parameters that came from the request headers
     * @param params      a Hashmap of the URL parameters included in the request
     * @return The DataSet that contains a single item that represents the updated item.
     */

    default CompletableFuture<RecordActionResponse> updateRecord(DataSetItem dataSetItem, AuthenticationInfo authenticationInfo, Parameters params) {
        throw new UnsupportedOperationException("Update is not supported on this web service");
    }

    /**
     *
     * @param dataSetItemID the data set item ID that the event is related to
     * @param event the ATEvent object
     * @param authenticationInfo a HashMap of any authentication parameters that came from the request
     * @param params a Parameters object of any URL parameters from the request
     */
    default CompletableFuture<Response> updateEventForDataSetItem(String dataSetItemID, Event event, AuthenticationInfo authenticationInfo, Parameters params) {
        return CompletableFuture.completedFuture(Response.success());
    }

    /**
     * This will update a list of data set items according to the given data set item
     * @param primaryKeys a list of data set item IDs to update
     * @param dataSetItem the data set item values used to update. IMPORTANT: Only the attributes that are getting bulk updated will be included.
     * @param authenticationInfo a HashMap of any authentication parameters sent in the request
     * @param params a Parameters object of any URL parameters from the request
     * @return an DataSourceResponse
     */
    default CompletableFuture<DataSet> bulkUpdateDataSetItems(List<String> primaryKeys, DataSetItem dataSetItem, AuthenticationInfo authenticationInfo, Parameters params) {
        throw new UnsupportedOperationException("Bulk update is not supported by this web service");
    }

    /**
     *
     * @param dataSetItemID the ID of the data set item to delete
     * @param authenticationInfo a HashMap of any authentication parameters sent in the request
     * @param params a Parameters object of any URL parameters from the request
     * @return
     */
    default CompletableFuture<RecordActionResponse> deleteRecord(String dataSetItemID, AuthenticationInfo authenticationInfo, Parameters params) {
        throw new UnsupportedOperationException("Delete is not supported on this web service");
    }
}
