package se.sics.ace.as.logging;

// {"Command":{"value":{"logs":{"message":"error","priority":1,"severity":5,"category":"AS"}}}}
public class OutValue {

	private Logs logs;

	public OutValue() {

	}

	public void setLogs(Logs logs) {
		this.logs = logs;
	}

	public Logs getLogs() {
		return logs;
	}
}
