package org.lightj.dal;

import java.util.HashSet;
import java.util.Set;

import org.lightj.initialization.BaseInitializable;
import org.lightj.initialization.BaseModule;
import org.lightj.initialization.InitializationException;
import org.lightj.util.StringUtil;

/**
 * place to intialize your database
 * 
 * @author biyu
 *
 */
public class DatabaseModule {
	
	/** singleton */
	private static DatabaseModuleInner s_Module = null;

	public DatabaseModule() {
		init();
	}
	
	private synchronized void init() {
		if (s_Module == null) s_Module = new DatabaseModuleInner();
	}
	
	private static final void validateInit() {
		if (s_Module == null) {
			throw new InitializationException("DatabaseModule not initialized");
		}
	}
	
	public static BaseDatabaseType getDataSourceOfName(String name) {
		validateInit();
		return s_Module.initializable.getDataSourceOfName(name);
	}

	public DatabaseModule addDatabases(BaseDatabaseType...databases) {
		s_Module.validateForChange();
		s_Module.initializable.addDatabase(databases);
		return this;
	}
	
	public BaseModule getModule() {
		return s_Module;
	}
	
	private class DatabaseModuleInner extends BaseModule {
		
		/** internal initializable */
		private DatabaseInitializable initializable = null;

		/** constructor, initialize */
		private DatabaseModuleInner() {
			super(new BaseModule[] {
			});
			initializable = new DatabaseInitializable();
			addInitializable(initializable);
		}
		
	}

	private class DatabaseInitializable extends BaseInitializable {
		
		/** schedulers need to be initialized */
		private Set<BaseDatabaseType> databases = new HashSet<BaseDatabaseType>();
		
		@Override
		protected void initialize() {
			for (BaseDatabaseType db : databases) {
				db.initialize();
			}
		}

		@Override
		protected void shutdown() {
			for (BaseDatabaseType db : databases) {
				db.shutdown();
			}
		}
		
		/** add a new scheduler type */
		private void addDatabase(BaseDatabaseType...databases) {
			for (BaseDatabaseType db : databases) {
				this.databases.add(db);
			}
		}
		
		/** search by name */
		private BaseDatabaseType getDataSourceOfName(String name) {
			for (BaseDatabaseType db : databases) {
				if (StringUtil.equalIgnoreCase(name, db.getName())) {
					return db;
				}
			}
			return null;
		}
		
	}
}
