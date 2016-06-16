package org.bip.executor;

import java.io.IOException;
import java.util.ArrayList;

import org.antlr.v4.runtime.RecognitionException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RoutePolicy;
import org.bip.api.BIPActor;
import org.bip.api.BIPEngine;
import org.bip.api.BIPGlue;
import org.bip.api.ResourceProvider;
import org.bip.engine.factory.EngineFactory;
import org.bip.executor.impl.akka.OrchestratedExecutorFactory;
import org.bip.glue.TwoSynchronGlueBuilder;
import org.bip.resources.AllocatorImpl;
import org.bip.resources.DNetException;
import org.bip.spec.MemoryMonitor;
import org.bip.spec.PComponent;
import org.bip.spec.QComponent;
import org.bip.spec.RComponent;
import org.bip.spec.resources.Bus;
import org.bip.spec.resources.ComponentNeedingResource;
import org.bip.spec.resources.RouteUser;
import org.bip.spec.resources.Memory;
import org.bip.spec.resources.Processor;
import org.bip.spec.resources.RouteManager;
import org.bip.spec.resources.RouteResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;

public class ResourceTest {

	ActorSystem system;
	OrchestratedExecutorFactory factory;
	EngineFactory engineFactory;

	@Before
	public void initialize() {

		system = ActorSystem.create("MySystem");
		factory = new OrchestratedExecutorFactory(system);
		engineFactory = new EngineFactory(system);

	}

	@After
	public void cleanup() {

		system.shutdown();

	}
	
	@SuppressWarnings("unused")
	@Test
	public void procMemBusTest() throws RecognitionException, IOException, DNetException
	{
		BIPGlue bipGlue = new TwoSynchronGlueBuilder() {
			@Override
			public void configure() {

				 synchron(ComponentNeedingResource.class, "getResource").to(AllocatorImpl.class,
						 "request");
				 synchron(ComponentNeedingResource.class, "release").to(AllocatorImpl.class,
						 "release");
				 synchron(ComponentNeedingResource.class, "process").to(AllocatorImpl.class,
						 "provideResource");
				 
				 data(ComponentNeedingResource.class, "utility").to(AllocatorImpl.class, "request");
				 data(ComponentNeedingResource.class, "resourceUnit").to(AllocatorImpl.class, "resourceUnit");
				 data(AllocatorImpl.class, "resources").to(ComponentNeedingResource.class, "resourceArray");
			}

		}.build();

		
		BIPEngine engine = engineFactory.create("myEngine", bipGlue);
		
		String dnetSpec = "src/test/resources/dnet.txt";
		AllocatorImpl alloc = new AllocatorImpl(dnetSpec); 
		
		ComponentNeedingResource aComp = new ComponentNeedingResource(128);
		ComponentNeedingResource bComp = new ComponentNeedingResource(100);


		BIPActor actor1 = engine.register(aComp, "resourceNeeder1", true); 
		BIPActor actor2 = engine.register(bComp, "resourceNeeder2", true); 
		BIPActor allocatorActor = engine.register(alloc, "allocator", true); 
		// aComp.setAllocator(allocatorActor);
		// bComp.setAllocator(allocatorActor); // we do not use the allocator inside, maybe remove the ResourceAware interface
		ResourceProvider memory = new Memory(256);
		ResourceProvider processor = new Processor();
		ResourceProvider bus = new Bus(128);
		
		alloc.addResource(memory);
		alloc.addResource(processor);
		alloc.addResource(bus);

		engine.specifyGlue(bipGlue);

		engine.start();
		engine.execute();

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);
	}
	
	@SuppressWarnings("unused")
	@Test
	public void simpleRouteTest() throws RecognitionException, IOException, DNetException
	{
		BIPGlue bipGlue = new TwoSynchronGlueBuilder() {
			@Override
			public void configure() {

				 synchron(RouteUser.class, "askRoute").to(AllocatorImpl.class,
						 "request");
//				 synchron(RouteUser.class, "release").to(AllocatorImpl.class,
//						 "release");
				 synchron(RouteUser.class, "getRoute").to(AllocatorImpl.class,
						 "provideResource");
				 synchron(RouteUser.class, "initRoute").to(RouteResource.class,
						 "init");
				 synchron(RouteUser.class, "transfer").to(RouteResource.class,
						 "on");
//				 synchron(RouteUser.class, "release").to(RouteResource.class,
//						 "delete");

				port(RouteUser.class, "release").requires(RouteResource.class,
						"delete", AllocatorImpl.class, "release");
				port(AllocatorImpl.class, "release").requires(RouteUser.class, "release");
				port(RouteResource.class, "delete").requires(RouteUser.class, "release");
				port(RouteUser.class, "release").accepts(RouteResource.class,
						"delete", AllocatorImpl.class, "release");
				port(AllocatorImpl.class, "release").accepts(RouteResource.class,
						"delete", RouteUser.class, "release");
				port(RouteResource.class, "delete").accepts(RouteUser.class,
						"release", AllocatorImpl.class, "release"); 
				
				 data(RouteUser.class, "utility").to(AllocatorImpl.class, "request");
				 data(RouteUser.class, "resourceUnit").to(AllocatorImpl.class, "resourceUnit");
				 data(AllocatorImpl.class, "resources").to(RouteUser.class, "resourceArray");
				 data(RouteUser.class, "route").to(RouteResource.class, "id");
				 data(RouteUser.class, "inPath").to(RouteResource.class, "routeIn");
				 data(RouteUser.class, "outPath").to(RouteResource.class, "routeOut");
			}

		}.build();

		bipGlue.toXML(System.out);
		
		BIPEngine engine = engineFactory.create("myEngine", bipGlue);
		
		String dnetSpec = "src/test/resources/simpleRouteDnet.txt";
		AllocatorImpl alloc = new AllocatorImpl(dnetSpec); 
		
		RouteUser routeUser1 = new RouteUser();

		BIPActor actor1 = engine.register(routeUser1, "routeUser1", true); 
		BIPActor allocatorActor = engine.register(alloc, "allocator", true); 

		CamelContext camelContext = new DefaultCamelContext();
		camelContext.setAutoStartup(false);
		ArrayList<RouteResource> routes = new ArrayList<RouteResource>();

		RouteResource route1 = new RouteResource("1", camelContext);
		//RouteResource route2 = new RouteResource("2", camelContext);
		routes.add(route1); //routes.add(route2);
		
		BIPActor route1Actor = engine.register(route1, "route1", true);
		//BIPActor route2Actor = engine.register(route2, "route2", true);
		route1.setExecutor(route1Actor);//route2.setExecutor(route2Actor);
		
		ResourceProvider routeManager = new RouteManager(camelContext, routes);
		
		final RoutePolicy routePolicy1 = createRoutePolicy(route1Actor);
		//final RoutePolicy routePolicy2 = createRoutePolicy(route2Actor);

		RouteBuilder builder = new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("file:inputfolder1?delete=true").routeId("1")
						.routePolicy(routePolicy1).to("file:outputfolder1");

//				from("file:inputfolder2?delete=true").routeId("2")
//						.routePolicy(routePolicy2).to("file:outputfolder2");
			}
			
		};
		
		try {
			camelContext.addRoutes(builder);
			camelContext.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
 
		alloc.addResource(routeManager);

		engine.specifyGlue(bipGlue);

		engine.start();
		engine.execute();

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);
	}
	
	
	
	@Test
	public void sortingTest() throws RecognitionException, IOException, DNetException
	{
		BIPGlue bipGlue = new TwoSynchronGlueBuilder() {
			@Override
			public void configure() {

				// TODO create glue
			}

		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);
		
		//TODO create dnet
		String dnetSpec = "src/test/resources/sortDnet.txt";
		AllocatorImpl alloc = new AllocatorImpl(dnetSpec); 
		
		CamelContext camelContext = new DefaultCamelContext();
		camelContext.setAutoStartup(false);
		ArrayList<RouteResource> routes = new ArrayList<RouteResource>();

		RouteResource route1 = new RouteResource("1", camelContext);
		RouteResource route2 = new RouteResource("2", camelContext);
		RouteResource route3 = new RouteResource("3", camelContext);
		RouteResource route4 = new RouteResource("4", camelContext);
		routes.add(route1); routes.add(route2);
		routes.add(route3); routes.add(route4);
		
		BIPActor route1Actor = engine.register(route1, "1", true);
		BIPActor route2Actor = engine.register(route2, "2", true);
		BIPActor route3Actor = engine.register(route3, "3", true);
		BIPActor route4Actor = engine.register(route4, "4", true);
		
		RouteManager routeManager = new RouteManager(camelContext, routes);
		
		final RoutePolicy routePolicy1 = createRoutePolicy(route1Actor);
		final RoutePolicy routePolicy2 = createRoutePolicy(route2Actor);
		final RoutePolicy routePolicy3 = createRoutePolicy(route3Actor);
		final RoutePolicy routePolicy4 = createRoutePolicy(route4Actor);

		RouteBuilder builder = new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("file:inputfolder1?delete=true").routeId("1")
						.routePolicy(routePolicy1).to("file:outputfolder1");

				from("file:inputfolder2?delete=true").routeId("2")
						.routePolicy(routePolicy2).to("file:outputfolder2");

				from("file:inputfolder3?delete=true").routeId("3")
						.routePolicy(routePolicy3).to("file:outputfolder3");
				from("file:inputfolder4?delete=true").routeId("4")
						.routePolicy(routePolicy4).to("file:outputfolder4");
			}
			
		};
		
		try {
			camelContext.addRoutes(builder);
			camelContext.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		MemoryMonitor routeOnOffMonitor = new MemoryMonitor(200);
		final BIPActor executorM = engine.register(routeOnOffMonitor, "monitor", true);


		BIPActor allocatorActor = engine.register(alloc, "allocator", true); 
		alloc.addResource(routeManager);

		engine.specifyGlue(bipGlue);

		engine.start();
		engine.execute();

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);
	}
	
	private RoutePolicy createRoutePolicy(final BIPActor actor) {

		return new RoutePolicy() {

			public void onInit(Route route) {
			}

			public void onExchangeDone(Route route, Exchange exchange) {
				System.err.println("exchange done!!!");
				actor.inform("end");
			}

			public void onExchangeBegin(Route route, Exchange exchange) {
			}

			@Override
			public void onRemove(Route arg0) {
			}

			@Override
			public void onResume(Route arg0) {
			}

			@Override
			public void onStart(Route arg0) {
			}

			@Override
			public void onStop(Route arg0) {
			}

			@Override
			public void onSuspend(Route arg0) {
			}
		};

	}
}