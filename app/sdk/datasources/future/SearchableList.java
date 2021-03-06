package sdk.datasources.future;

import sdk.list.List;
import sdk.datasources.ListDataSource;
import sdk.list.ListItem;
import sdk.utils.AuthenticationInfo;
import sdk.utils.Parameters;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by alexis on 5/10/16.
 */
public interface SearchableList extends ListDataSource {
    CompletableFuture<List> queryList(String queryText, boolean barcodeSearch, Map<String, Object> searchParameters, AuthenticationInfo authenticationInfo, Parameters params);

    default CompletableFuture<ListItem> fetchItem(String id, AuthenticationInfo authenticationInfo, Parameters parameters) {
        throw new RuntimeException("This source does not support fetching a single item");
    }
}
