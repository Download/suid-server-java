package ws.suid;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/** Stores data about issued Suid ID blocks. */
@Entity
@Table(name="suid")
@NamedQueries(value={
	@NamedQuery(name=SuidRecord.ALL, query="SELECT sr FROM SuidRecord sr"),
	@NamedQuery(name=SuidRecord.DEL, query="DELETE FROM SuidRecord sr WHERE sr.block = :block")
})
public class SuidRecord {
	public static final String ALL = "suid_record_all";
	public static final String DEL = "suid_record_del";
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long block;
	private Byte shard;

	protected SuidRecord() {}
	public SuidRecord(Byte shard) {this.shard = shard;}
	public Long getBlock() {return block;}
	public Byte getShard() {return shard;}
}
