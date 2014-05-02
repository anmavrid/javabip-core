package org.bip.spec;

import org.bip.annotations.*;
import org.bip.api.PortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ports({ @Port(name = "giveY", type = PortType.enforceable), 
		 @Port(name = "giveZ", type = PortType.enforceable), 
		 @Port(name = "returnY", type = PortType.enforceable),
		 @Port(name = "returnZ", type = PortType.enforceable) })
@ComponentType(initial = "zero", name = "org.bip.spec.Feeder")
public class Feeder {
	Logger logger = LoggerFactory.getLogger(Feeder.class);

	private int memoryY = 200;
	private int memoryZ = 300;

	@Transition(name = "giveY", source = "zero", target = "oneY")
	public void changeY() {
		logger.debug("Transition Y has been performed");
	}

	@Transition(name = "returnY", source = "oneY", target = "zero")
	public void returnY() {
		logger.debug("Transition from oneY to zero has been performed");
	}

	@Transition(name = "giveZ", source = "zero", target = "oneZ")
	public void changeZ() {
		logger.debug("Transition Z has been performed");
	}

	@Transition(name = "returnZ", source = "oneZ", target = "zero")
	public void returnZ() {
		logger.debug("Transition from oneZ to zero has been performed");
	}

	@Data(name = "memoryY", accessTypePort = "allowed", ports = { "giveY" })
	public int memoryY() {
		return memoryY;
	}

	@Data(name = "memoryZ", accessTypePort = "allowed", ports = { "giveZ" })
	public int memoryZ() {
		return memoryZ;
	}
}
