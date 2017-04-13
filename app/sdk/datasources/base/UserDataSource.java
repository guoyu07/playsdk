package sdk.datasources.base;

import sdkmodels.data.User;
import sdk.datasources.UserDataSourceBase;
import sdkmodels.utils.AuthenticationInfo;
import sdkmodels.utils.Parameters;

/**
 * Created by matthew on 5/12/16.
 */
public interface UserDataSource extends UserDataSourceBase {
    User getUser(String userID, AuthenticationInfo authenticationInfo, Parameters params);
    User createUser(User user);
    User updateUser(User user);
    Boolean deleteUser(User user);
}
