/*
 *  Copyright (c) 2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.rssmanager.core.dao.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.rssmanager.common.RSSManagerConstants;
import org.wso2.carbon.rssmanager.core.dao.UserDatabaseEntryDAO;
import org.wso2.carbon.rssmanager.core.dao.exception.RSSDAOException;
import org.wso2.carbon.rssmanager.core.dao.exception.RSSDatabaseConnectionException;
import org.wso2.carbon.rssmanager.core.dao.util.RSSDAOUtil;
import org.wso2.carbon.rssmanager.core.dto.common.UserDatabaseEntry;
import org.wso2.carbon.rssmanager.core.dto.common.UserDatabasePrivilege;
import org.wso2.carbon.rssmanager.core.dto.restricted.DatabaseUser;
import org.wso2.carbon.rssmanager.core.util.RSSManagerUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Database user entry DAO implementation
 */
public class UserDatabaseEntryDAOImpl implements UserDatabaseEntryDAO {

	private static final Log log = LogFactory.getLog(UserDatabaseEntryDAOImpl.class);
	private DataSource dataSource;

	public UserDatabaseEntryDAOImpl() {
		dataSource = RSSManagerUtil.getDataSource();
	}

	/**
	 * @see UserDatabaseEntryDAO#addUserDatabaseEntry(java.sql.Connection, String, UserDatabaseEntry, int)
	 */
	public int addUserDatabaseEntry(Connection conn, String environmentName, UserDatabaseEntry entry,
	                                int tenantId) throws RSSDAOException, RSSDatabaseConnectionException {
		if (entry == null) {
			return -1;
		}
		PreparedStatement userEntryStatement = null;
		PreparedStatement userPrivilegeEntryStatement = null;
		ResultSet resultSet = null;
		int userEntryId = 0;
		try {
			conn = getDataSourceConnection();//acquire data source connection
			//start transaction with setting auto commit value to false
			conn.setAutoCommit(false);
			String userEntrySql = "INSERT INTO RM_USER_DATABASE_ENTRY(DATABASE_USER_ID, DATABASE_ID) VALUES (?,?)";
			userEntryStatement = conn.prepareStatement(userEntrySql, Statement.RETURN_GENERATED_KEYS);
			userEntryStatement.setInt(1, entry.getUserId());
			userEntryStatement.setInt(2, entry.getDatabaseId());
			userEntryStatement.executeUpdate();
			//get the result of the id inserted to the database user entry table which needs to be inserted to
			//user privilege table as a foreign key
			resultSet = userEntryStatement.getGeneratedKeys();
			while (resultSet.next()) {
				userEntryId = resultSet.getInt(1);
				UserDatabasePrivilege privileges = entry.getUserPrivileges();
				String insertTemplateEntryQuery = "INSERT INTO RM_USER_DATABASE_PRIVILEGE(USER_DATABASE_ENTRY_ID, SELECT_PRIV, " +
				                                  "INSERT_PRIV, UPDATE_PRIV, DELETE_PRIV, CREATE_PRIV, DROP_PRIV, GRANT_PRIV, REFERENCES_PRIV, " +
				                                  "INDEX_PRIV, ALTER_PRIV, CREATE_TMP_TABLE_PRIV, LOCK_TABLES_PRIV, CREATE_VIEW_PRIV, SHOW_VIEW_PRIV, " +
				                                  "CREATE_ROUTINE_PRIV, ALTER_ROUTINE_PRIV, EXECUTE_PRIV, EVENT_PRIV, TRIGGER_PRIV) VALUES " +
				                                  "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
				userPrivilegeEntryStatement = conn.prepareStatement(insertTemplateEntryQuery);
				//set data to be inserted
				userPrivilegeEntryStatement.setInt(1, userEntryId);
				userPrivilegeEntryStatement.setString(2, privileges.getSelectPriv());
				userPrivilegeEntryStatement.setString(3, privileges.getInsertPriv());
				userPrivilegeEntryStatement.setString(4, privileges.getUpdatePriv());
				userPrivilegeEntryStatement.setString(5, privileges.getDeletePriv());
				userPrivilegeEntryStatement.setString(6, privileges.getCreatePriv());
				userPrivilegeEntryStatement.setString(7, privileges.getDropPriv());
				userPrivilegeEntryStatement.setString(8, privileges.getGrantPriv());
				userPrivilegeEntryStatement.setString(9, privileges.getReferencesPriv());
				userPrivilegeEntryStatement.setString(10, privileges.getIndexPriv());
				userPrivilegeEntryStatement.setString(11, privileges.getAlterPriv());
				userPrivilegeEntryStatement.setString(12, privileges.getCreateTmpTablePriv());
				userPrivilegeEntryStatement.setString(13, privileges.getLockTablesPriv());
				userPrivilegeEntryStatement.setString(14, privileges.getCreateViewPriv());
				userPrivilegeEntryStatement.setString(15, privileges.getShowViewPriv());
				userPrivilegeEntryStatement.setString(16, privileges.getCreateRoutinePriv());
				userPrivilegeEntryStatement.setString(17, privileges.getAlterRoutinePriv());
				userPrivilegeEntryStatement.setString(18, privileges.getExecutePriv());
				userPrivilegeEntryStatement.setString(19, privileges.getEventPriv());
				userPrivilegeEntryStatement.setString(20, privileges.getTriggerPriv());
				userPrivilegeEntryStatement.executeUpdate();
			}
		} catch (SQLException e) {
			RSSDAOUtil.rollback(conn, RSSManagerConstants.ADD_USER_PRIVILEGE_TEMPLATE_ENTRY);
			String msg = "Failed to add database user entry to meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(null, userPrivilegeEntryStatement, null,
					RSSManagerConstants.ADD_USER_PRIVILEGE_TEMPLATE_ENTRY);
			RSSDAOUtil.cleanupResources(resultSet, userEntryStatement, conn, RSSManagerConstants
					.ADD_USER_PRIVILEGE_TEMPLATE_ENTRY);
		}
		return userEntryId;
	}

	/**
	 * @see UserDatabaseEntryDAO#removeUserDatabaseEntriesByDatabase(Integer)
	 */
	public void removeUserDatabaseEntriesByDatabase(Integer databaseId)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		try {
			conn = getDataSourceConnection();//acquire data source connection
			String removeDBQuery = "DELETE FROM RM_USER_DATABASE_ENTRY WHERE DATABASE_ID = ?";
			statement = conn.prepareStatement(removeDBQuery);
			statement.setInt(1, databaseId);
			statement.executeUpdate();
		} catch (SQLException e) {
			String msg = "Failed to delete database user entry from meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(null, statement, conn, RSSManagerConstants.DELETE_USER_DATABASE_ENTRY);
		}
	}

	/**
	 * @see UserDatabaseEntryDAO#removeUserDatabaseEntry(java.sql.Connection, int, int)
	 */
	public void removeUserDatabaseEntry(Connection conn, int databaseId, int userId)
			throws RSSDAOException, RSSDatabaseConnectionException {
		PreparedStatement removeUserEntryStatement = null;
		try {
			conn = getDataSourceConnection();//acquire data source connection
			conn.setAutoCommit(false);
			//start transaction with setting auto commit value to false
			String removeDBQuery = "DELETE FROM RM_USER_DATABASE_ENTRY WHERE DATABASE_ID = ? AND DATABASE_USER_ID = ?";
			removeUserEntryStatement = conn.prepareStatement(removeDBQuery);
			removeUserEntryStatement.setInt(1, databaseId);
			removeUserEntryStatement.setInt(2, userId);
			//execute remove user statement first from meta repository as native queries not transactional
			removeUserEntryStatement.executeUpdate();
			conn.commit();
		} catch (SQLException e) {
			RSSDAOUtil.rollback(conn, RSSManagerConstants.DELETE_USER_DATABASE_ENTRY);
			String msg = "Failed to delete database user entry from meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(null, removeUserEntryStatement, conn, RSSManagerConstants
					.DELETE_USER_DATABASE_ENTRY);
		}
	}

	/**
	 * @see UserDatabaseEntryDAO#getUserDatabaseEntry(int, int)
	 */
	public UserDatabaseEntry getUserDatabaseEntry(int databaseId, int userId)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement entryIdStatement = null;
		PreparedStatement entryStatement = null;
		ResultSet resultSet = null;
		UserDatabaseEntry databaseEntry = null;
		int userEntryId = 0;
		UserDatabasePrivilege entry = null;
		try {
			conn = getDataSourceConnection();//acquire data source connection
			String getDatabaseEntryIdQuery = "SELECT ID FROM RM_USER_DATABASE_ENTRY WHERE DATABASE_USER_ID=? AND DATABASE_ID=?";
			entryIdStatement = conn.prepareStatement(getDatabaseEntryIdQuery);
			entryIdStatement.setInt(1, userId);
			entryIdStatement.setInt(2, databaseId);
			//select user entry id to get privileges from user database privileges table
			resultSet = entryIdStatement.executeQuery();
			while (resultSet.next()) {
				userEntryId = resultSet.getInt("ID");
			}
			String getPrivilegeEntryQuery = "SELECT * FROM RM_USER_DATABASE_PRIVILEGE WHERE USER_DATABASE_ENTRY_ID = ?";
			entryStatement = conn.prepareStatement(getPrivilegeEntryQuery);
			entryStatement.setInt(1, userEntryId);
			resultSet = entryStatement.executeQuery();
			while (resultSet.next()) {
				entry = new UserDatabasePrivilege();
				//set privileges from result set
				entry.setId(resultSet.getInt("ID"));
				entry.setSelectPriv(resultSet.getString("SELECT_PRIV"));
				entry.setInsertPriv(resultSet.getString("INSERT_PRIV"));
				entry.setUpdatePriv(resultSet.getString("UPDATE_PRIV"));
				entry.setDeletePriv(resultSet.getString("DELETE_PRIV"));
				entry.setCreatePriv(resultSet.getString("CREATE_PRIV"));
				entry.setDropPriv(resultSet.getString("DROP_PRIV"));
				entry.setGrantPriv(resultSet.getString("GRANT_PRIV"));
				entry.setReferencesPriv(resultSet.getString("REFERENCES_PRIV"));
				entry.setIndexPriv(resultSet.getString("INDEX_PRIV"));
				entry.setAlterPriv(resultSet.getString("ALTER_PRIV"));
				entry.setCreateTmpTablePriv(resultSet.getString("CREATE_TMP_TABLE_PRIV"));
				entry.setLockTablesPriv(resultSet.getString("LOCK_TABLES_PRIV"));
				entry.setCreateViewPriv(resultSet.getString("CREATE_VIEW_PRIV"));
				entry.setShowViewPriv(resultSet.getString("SHOW_VIEW_PRIV"));
				entry.setCreateRoutinePriv(resultSet.getString("CREATE_ROUTINE_PRIV"));
				entry.setAlterRoutinePriv(resultSet.getString("ALTER_ROUTINE_PRIV"));
				entry.setExecutePriv(resultSet.getString("EXECUTE_PRIV"));
				entry.setEventPriv(resultSet.getString("EVENT_PRIV"));
				entry.setTriggerPriv(resultSet.getString("TRIGGER_PRIV"));
			}
			databaseEntry = new UserDatabaseEntry();
			databaseEntry.setUserPrivileges(entry);
			databaseEntry.setDatabaseId(databaseId);
			databaseEntry.setUserId(userId);
		} catch (SQLException e) {
			String msg = "Failed to retrieve database user entry information of from meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(null, entryStatement, null, RSSManagerConstants.SELECT_DATABASE_USER_ENTRY);
			RSSDAOUtil.cleanupResources(resultSet, entryIdStatement, conn, RSSManagerConstants.SELECT_DATABASE_USER_ENTRY);
		}
		return databaseEntry;
	}

	/**
	 * @see UserDatabaseEntryDAO#getAssignedDatabaseUsers(String, String, String, int, String)
	 */
	public DatabaseUser[] getAssignedDatabaseUsers(String environmentName, String rssInstanceName,
	                                               String databaseName,
	                                               int tenantId, String instanceType)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		DatabaseUser databaseUser;
		List<DatabaseUser> databaseUsers = new ArrayList<DatabaseUser>();
		int environmentId = getEnvironmentIdByName(environmentName);
		int rssInstanceId = getRSSInstanceIdByName(rssInstanceName, environmentId);
		int databaseId = getDatabaseIdByName(databaseName, rssInstanceId);
		try {
			conn = getDataSourceConnection();//aquire data source connection
			String getDatabaseUserQuery = "SELECT RM_DATABASE_USER.ID, RM_DATABASE_USER.USERNAME, RM_DATABASE_USER.TYPE," +
			                              "RM_DATABASE_USER.TENANT_ID FROM RM_DATABASE_USER , RM_USER_DATABASE_ENTRY " +
			                              "WHERE RM_DATABASE_USER.ID=RM_USER_DATABASE_ENTRY.DATABASE_USER_ID AND RM_DATABASE_USER.ENVIRONMENT_ID=? " +
			                              "AND RM_DATABASE_USER.TYPE=? AND RM_DATABASE_USER.TENANT_ID=? AND RM_USER_DATABASE_ENTRY.DATABASE_ID=?";
			statement = conn.prepareStatement(getDatabaseUserQuery);
			statement.setInt(1, environmentId);
			statement.setString(2, instanceType);
			statement.setInt(3, tenantId);
			statement.setInt(4, databaseId);
			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				//fill data from result set
				databaseUser = new DatabaseUser();
				databaseUser.setId(resultSet.getInt("ID"));
				databaseUser.setName(resultSet.getString("USERNAME"));
				databaseUser.setType(resultSet.getString("TYPE"));
				databaseUser.setTenantId(resultSet.getInt("TENANT_ID"));
				databaseUsers.add(databaseUser);
			}
		} catch (SQLException e) {
			String msg = "Failed to retrieve assigned database users information in environment" + environmentName
			             + "from meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(resultSet, statement, conn, RSSManagerConstants
					.SELECT_ASSIGNED_DATABASE_USER_ENTRIES);
		}
		return databaseUsers.toArray(new DatabaseUser[databaseUsers.size()]);
	}

	/**
	 * @see UserDatabaseEntryDAO#getAvailableDatabaseUsers(String, String, String, int, String)
	 */
	public DatabaseUser[] getAvailableDatabaseUsers(String environmentName, String rssInstanceName,
	                                                String databaseName, int tenantId, String instanceType)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		DatabaseUser databaseUser;
		List<DatabaseUser> databaseUsers = new ArrayList<DatabaseUser>();
		int environmentId = getEnvironmentIdByName(environmentName);
		int rssInstanceId = getRSSInstanceIdByName(rssInstanceName, environmentId);
		int databaseId = getDatabaseIdByName(databaseName, rssInstanceId);
		try {
			conn = getDataSourceConnection();//acquire data source connection
			String getAvailableDatabaseUserQuery = "SELECT RM_DATABASE_USER.ID, RM_DATABASE_USER.USERNAME, RM_DATABASE_USER.TYPE, RM_DATABASE_USER.TENANT_ID " +
			                              "FROM RM_DATABASE_USER  , RM_USER_INSTANCE_ENTRY " +
			                              "WHERE RM_DATABASE_USER.ID=RM_USER_INSTANCE_ENTRY.DATABASE_USER_ID AND RM_DATABASE_USER.ENVIRONMENT_ID=? " +
			                              "AND RM_DATABASE_USER.TYPE=? AND RM_DATABASE_USER.TENANT_ID=? AND RM_USER_INSTANCE_ENTRY.RSS_INSTANCE_ID=? " +
			                              "AND RM_DATABASE_USER.ID NOT IN (SELECT DATABASE_USER_ID FROM RM_USER_DATABASE_ENTRY WHERE DATABASE_ID=?)";
			statement = conn.prepareStatement(getAvailableDatabaseUserQuery);
			statement.setInt(1, environmentId);
			statement.setString(2, instanceType);
			statement.setInt(3, tenantId);
			statement.setInt(4, rssInstanceId);
			statement.setInt(5, databaseId);
			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				//fill available database user list from result set data
				databaseUser = new DatabaseUser();
				databaseUser.setId(resultSet.getInt("ID"));
				databaseUser.setName(resultSet.getString("USERNAME"));
				databaseUser.setType(resultSet.getString("TYPE"));
				databaseUser.setTenantId(resultSet.getInt("TENANT_ID"));
				databaseUsers.add(databaseUser);
			}
		} catch (SQLException e) {
			String msg = "Failed to retrieve database unassigned database users information in environment" + environmentName
			             + "from meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(resultSet, statement, conn, RSSManagerConstants
					.SELECT_UNASSIGNED_DATABASE_USER_ENTRIES);
		}
		return databaseUsers.toArray(new DatabaseUser[databaseUsers.size()]);
	}

	/**
	 * Get environment id by name
	 *
	 * @return environment id
	 */
	private int getEnvironmentIdByName(String environmentName)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		int environmentId = 0;
		try {
			conn = getDataSourceConnection();
			String selectEnvQuery = "SELECT ID FROM RM_ENVIRONMENT WHERE NAME = ?";
			statement = conn.prepareStatement(selectEnvQuery);
			statement.setString(1, environmentName);
			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				environmentId = resultSet.getInt("ID");
			}
		} catch (SQLException e) {
			String msg = "Error while getting environment id by name" + environmentName;
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(resultSet, statement, conn, RSSManagerConstants.SELECT_ENVIRONMENT_ID_BY_NAME);
		}
		return environmentId;
	}


	/**
	 * Get rss instance id by name
	 *
	 * @return rss instance id
	 */
	private int getRSSInstanceIdByName(String rssInstanceName , int environmentId)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		try {
			conn = getDataSourceConnection();
			String selectEnvQuery = "SELECT ID FROM RM_SERVER_INSTANCE WHERE NAME = ? AND ENVIRONMENT_ID=?";
			statement = conn.prepareStatement(selectEnvQuery);
			statement.setString(1, rssInstanceName);
			statement.setInt(2, environmentId);
			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				environmentId = resultSet.getInt("ID");
			}
		} catch (SQLException e) {
			String msg = "Error while getting rss instance id by name" + rssInstanceName;
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(resultSet, statement, conn, RSSManagerConstants.SELECT_RSS_INSTANCE_ID_BY_NAME);
		}
		return environmentId;
	}

	/**
	 * Get rss instance id by name
	 *
	 * @return database id
	 */
	private int getDatabaseIdByName(String databaseName, int rssInstanceId)
			throws RSSDAOException, RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		int databaseId = 0;
		try {
			conn = getDataSourceConnection();
			String getDatabaseQuery = "SELECT ID FROM RM_DATABASE WHERE NAME =? AND RSS_INSTANCE_ID=?";
			statement = conn.prepareStatement(getDatabaseQuery);
			statement.setString(1, databaseName);
			statement.setInt(2, rssInstanceId);
			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				databaseId = resultSet.getInt("ID");
			}
		} catch (SQLException e) {
			String msg = "Failed to retrieve database information of" + databaseName + "from meta repository";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(resultSet, statement, conn, RSSManagerConstants.CHECK_DATABASE_ENTRY_EXIST);
		}
		return databaseId;
	}

	/**
	 * @see  @see UserDatabaseEntryDAO#isDatabaseUserEntriesExist(int)
	 */
	public boolean isDatabaseUserEntriesExist(int userId) throws RSSDAOException,
			RSSDatabaseConnectionException {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		boolean isExist = false;
		try {
			conn = getDataSourceConnection();//acquire data source connection
			String databaseUserEntriesExistenceQuery = "SELECT ID FROM RM_USER_DATABASE_ENTRY WHERE DATABASE_USER_ID=?";
			statement = conn.prepareStatement(databaseUserEntriesExistenceQuery);
			statement.setInt(1, userId);
			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				isExist = true;
				break;
			}
		} catch (SQLException e) {
			String msg = "Failed to check database user entries existence";
			handleException(msg, e);
		} finally {
			RSSDAOUtil.cleanupResources(resultSet, statement, conn, RSSManagerConstants
					.CHECK_DATABASE_USER_ENTRIES_EXISTENCE);
		}
		return isExist;
	}

	/**
	 * Get data source connection
	 *
	 * @return the data source connection
	 */
	private Connection getDataSourceConnection() throws RSSDatabaseConnectionException {
		try{
			return dataSource.getConnection();//acquire data source connection
		} catch (SQLException e) {
			String msg = "Error while acquiring the database connection. Meta Repository Database server may down";
			throw new RSSDatabaseConnectionException(msg, e);
		}
	}

	/**
	 * Log and throw a rss manager data access exception
	 * @param msg high level exception message
	 * @param e error
	 * @throws RSSDAOException throw RSS DAO exception
	 */
	public void handleException(String msg, Exception e) throws RSSDAOException {
		log.error(msg, e);
		throw new RSSDAOException(msg, e);
	}
}
