package org.lightj.session.dal;

import java.util.List;

import org.lightj.dal.BaseDatabaseType;
import org.lightj.dal.DataAccessException;
import org.lightj.dal.DataAccessRuntimeException;

/**
 * session metadata manager interface
 * @author biyu
 *
 * @param <T>
 */
public interface ISessionMetaDataManager<T extends ISessionMetaData, Q> {

	/**
	 * get new instance of session metadata
	 * @return
	 * @throws DataAccessRuntimeException
	 */
	public T newInstance() throws DataAccessRuntimeException;
	
	/**
	 * save metadata
	 * @param data
	 * @throws DataAccessException
	 */
	public void save(T data) throws DataAccessException;
	
	/**
	 * delete metadata
	 * @param data
	 * @throws DataAccessException
	 */
	public void delete(T data) throws DataAccessException;
	
	/**
	 * find all metadata by flow id
	 * @param sessId
	 * @return
	 * @throws DataAccessException
	 */
	public List<T> findByFlowId(long sessId) throws DataAccessException;
	
	/**
	 * associate data store
	 * @param dbEnum
	 */
	public void setDbEnum(BaseDatabaseType dbEnum);
	
}
