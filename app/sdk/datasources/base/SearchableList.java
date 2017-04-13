package sdk.datasources.base;

import sdk.datasources.ListDataSource;
import sdkmodels.list.List;
import sdkmodels.utils.AuthenticationInfo;
import sdkmodels.utils.Parameters;

import java.util.Map;

/**
 * Created by alexis on 5/10/16.
 */
public interface SearchableList extends ListDataSource {
    List queryList(String queryText, boolean barcodeSearch, Map<String, Object> searchParameters, AuthenticationInfo authenticationInfo, Parameters params);
}
