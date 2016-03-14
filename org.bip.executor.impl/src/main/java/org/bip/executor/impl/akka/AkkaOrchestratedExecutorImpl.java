package org.bip.executor.impl.akka;

import java.util.List;
import java.util.Map;

import org.bip.api.BIPEngine;
import org.bip.api.OrchestratedExecutor;
import org.bip.api.PortBase;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;

public class AkkaOrchestratedExecutorImpl implements AkkaOrchestratedExecutor {

	private OrchestratedExecutor executor;
	private ActorSystem actorSystem;

	public AkkaOrchestratedExecutorImpl(final ActorSystem actorSystem, final OrchestratedExecutor executor) {
		this.executor = executor;
		this.actorSystem = actorSystem;
	}
		
	@Override
	public void step() {
		executor.step();
	}

	@Override
	public void register(BIPEngine bipEngine) {
		executor.register(bipEngine);
	}

	@Override
	public void deregister() {
		executor.deregister();
	}

	@Override
	public BIPEngine engine() {
		return executor.engine();
	}

	@Override
	public void execute(String portID) {
		executor.execute(portID);
	}

	@Override
	public void inform(String portID) {
		executor.inform(portID);
	}

	@Override
	public <T> T getData(String name, Class<T> clazz) {
		return executor.getData(name, clazz);
	}

	@Override
	public List<Boolean> checkEnabledness(PortBase port,
			List<Map<String, Object>> data) {
		return executor.checkEnabledness(port, data);
	}

	@Override
	public void setData(String dataName, Object value) {
		executor.setData(dataName, value);
	}

	@Override
	public String getId() {
		return executor.getId();
	}

	@Override
	public String getType() {
		return executor.getType();
	}

	@Override
	public void init() {
	}

	@Override
	public void destroy() {
		
		executor.deregister();
		if (TypedActor.get(actorSystem).isTypedActor(executor)) {
			TypedActor.get(actorSystem).poisonPill(executor);
		} 

	}

	@Override
	public void inform(String portID, Map<String, Object> data) {
		executor.inform(portID, data);
	}


}