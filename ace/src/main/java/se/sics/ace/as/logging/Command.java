package se.sics.ace.as.logging;

//{"Command":{"value":{"logs":{"message":"error","priority":1,"severity":5,"category":"AS"}}}}
public class Command {

	private OutValue value;

	public Command() {

	}

	public void setValue(OutValue value) {
		this.value = value;
	}

	public OutValue getValue() {
		return value;
	}
}
