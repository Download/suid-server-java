package ws.suid;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.sql.DataSource;

/**
 * Service that fetches suid ID blocks from the database.
 * 
 * <p>This service looks up the datasource named {@code SuidRWDS} and 
 * fetches suid blocks from it.</p>
 * 
 * <p><b>NOTE</b>: Currently, only MySql is supported.</p>
 * 
 * @author Stijn de Witt [StijnDeWitt@hotmail.com]
 */
@Stateless
@LocalBean
public class SuidService {
	public static final class Generator {
		private Suid block;
		private int id;
		public Generator() {}
		public Suid next(SuidService suidService) {
			if (id >= Suid.IDSIZE) {
				id = 0;
				block = null;
			}
			if (block == null) {
				block = suidService.nextBlocks(1)[0]; 
			}
			return Suid.valueOf(block.getBlock(), id++, block.getShard());
		}
	}
	
	private static final ThreadLocal<Generator> GENERATOR = new ThreadLocal<Generator>() {
		protected Generator initialValue() {
			return new Generator();
		};
	};
	
	public static final int MAX_REQUEST_BLOCKS = 8;
	
	@Resource(name="jdbc/SuidRWDS")
	private DataSource db;

	/**
	 * Initializes this SuidService based on the given {@code shard}.
	 * 
	 * <p>This method only has any effect if the `suid` table is still empty.</p>
	 * 
	 * <p>If there are no records in the {@code suid} table yet, this method will 
	 * insert the first record with the given {@code shard}. Any subsequent calls
	 * to this method will detect that there is a record already and do nothing.</p>
	 * 
	 * @param shard The shard ID when used in clusters. Must be in the range {@code 0 .. 3}.
	 */
	public void init(int shard) {
		assert shard >= 0 && shard <= 3 : "Parameter 'shard' is out of range (0..3): " + shard + ".";
		Connection con = null;
		Statement stm = null;
		try {
			con = db.getConnection();
			stm = con.createStatement();
			stm.execute(
					"CREATE TABLE IF NOT EXISTS suid (\n" +
					"	block BIGINT NOT NULL AUTO_INCREMENT,\n" +
					"	shard TINYINT NOT NULL,\n" +
					"	PRIMARY KEY (block),\n" +
					"	UNIQUE KEY shard (shard)\n" +
					")ENGINE = InnoDB;"
			);
			
			ResultSet results = stm.executeQuery("SELECT block FROM suid");
			if (! results.first()) {
				stm.execute("INSERT INTO suid (shard) VALUES (" + shard + ");");
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		finally {
			release(con, stm);
		}
	}

	/**
	 * Gets the next {@code count} ID blocks.
	 * 
	 * <p>Parameter count must be in the range {@code 1 .. 8}. If count is less than
	 * {@code 1}, it is set to {@code 1}. If {@code count} is greater than 8, it is
	 * set to {@code 8}.</p>
	 * 
	 * @param count The number of blocks to get. Is forced to be in range {@code 1 .. 8}.
	 * @return An array of block {@code Suid}s.
	 */
	public Suid[] nextBlocks(int count) {
		if (count < 1) count = 1;
		if (count > MAX_REQUEST_BLOCKS) count = MAX_REQUEST_BLOCKS;
		Suid[] results = new Suid[count];
		Connection con = null;
		Statement stm = null;
		try {
			con = db.getConnection();
			stm = con.createStatement();
			for (int i=0; i<count; i++)
				results[i] = nextBlock(stm);
			return results;
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}	
		finally {
			release(con, stm);
		}
	}
	
	public Suid next() {
		return GENERATOR.get().next(this);
	}
	
	private Suid nextBlock(Statement stm) throws SQLException {
		stm.execute("REPLACE INTO suid (shard) SELECT shard FROM suid;");
		ResultSet results = stm.executeQuery("SELECT LAST_INSERT_ID() AS block, shard FROM suid;");
		results.first();
		return Suid.valueOf(results.getLong("block"), (byte) 0, results.getByte("shard"));
	}
	
	private void release(Connection con, Statement stm) {
		if (stm != null) {
			try {
				stm.close();
			} catch (SQLException e) {
			}
			stm = null;
		}
		if (con != null) {
			try {
				con.close();
			} catch (SQLException e) {
			}
			con = null;
		}
	}
}
