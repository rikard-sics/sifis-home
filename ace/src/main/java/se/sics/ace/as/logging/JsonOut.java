package se.sics.ace.as.logging;

// {"Command":{"value":{"logs":{"message":"error","priority":1,"severity":5,"category":"AS"}}}}
public class JsonOut {

	private Command Command;

	public JsonOut() {

	}

	public void setCommand(Command commandIn) {
		this.Command = commandIn;
	}

	public Command getCommand() {
		return Command;
	}

}
