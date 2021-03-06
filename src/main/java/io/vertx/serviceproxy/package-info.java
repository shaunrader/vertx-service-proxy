/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * = Vert.x Service Proxy
 * :toc: left
 *
 * When you compose a vert.x application, you may want to isolate a functionality somewhere and make it available to
 * the rest of your application. That's the main purpose of service proxies. It lets you expose a _service_ on the
 * event bus, so, any other vert.x component can consume it, as soon as they know the _address_ on which the service
 * is published.
 *
 * A _service_ is described with a Java interface containing methods following the _async pattern_. Behind the hood,
 * messages are sent on the event bus to invoke the service and get the response back. But to make it more easy to use
 * for you, it generates a _proxy_ that you can invoke directly (using the API  from the service interface).
 *
 *
 * == Using vert.x service proxies
 *
 * To use the Vert.x Service Proxies, add the following dependency to the _dependencies_ section of
 * your build descriptor:
 *
 * * Maven (in your `pom.xml`):
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *   <groupId>${maven.groupId}</groupId>
 *   <artifactId>${maven.artifactId}</artifactId>
 *   <version>${maven.version}</version>
 * </dependency>
 * ----
 *
 * * Gradle (in your `build.gradle` file):
 *
 * [source,groovy,subs="+attributes"]
 * ----
 * compile '${maven.groupId}:${maven.artifactId}:${maven.version}'
 * ----
 *
 * Be aware that as the service proxy mechanism relies on code generation, so modifications to the _service interface_
 * require to re-compile the sources to regenerate the code.
 *
 * To generate the proxies in different languages, you will need to add the _language_ dependency such as
 * `vertx-lang-groovy` for Groovy....
 *
 * == Introduction to service proxies
 *
 * Let's have a look to service proxies and why they can be useful. Let's imagine you have a _database service_
 * exposed on the event bus, you should do something like this:
 *
 * [source,java]
 * ----
 * {@link examples.Examples#example1(io.vertx.core.Vertx)}
 * ----
 *
 * When creating a service there's a certain amount of boilerplate code to listen on the event bus for incoming
 * messages, route them to the appropriate method and return results on the event bus.
 *
 * With Vert.x service proxies, you can avoid writing all that boilerplate code and concentrate on writing your service.
 *
 * You write your service as a Java interface and annotate it with the `@ProxyGen` annotation, for example:
 *
 * [source,java]
 * ----
 * &#64;ProxyGen
 * public interface SomeDatabaseService {
 *
 *   // A couple of factory methods to create an instance and a proxy
 *   static SomeDatabaseService create(Vertx vertx) {
 *     return new SomeDatabaseServiceImpl(vertx);
 *   }
 *
 *   static SomeDatabaseService createProxy(Vertx vertx,
 *     String address) {
 *     return new SomeDatabaseServiceVertxEBProxy(vertx, address);
 *   }
 *
 *  // Actual service operations here...
 *  void save(String collection, JsonObject document,
 *    Handler<AsyncResult<Void>> resultHandler);
 * }
 * ----
 *
 * Given the interface, Vert.x will generate all the boilerplate code required to access your service over the event
 * bus, and it will also generate a *client side proxy* for your service, so your clients can use a rich idiomatic
 * API for your service instead of having to manually craft event bus messages to send. The client side proxy will
 * work irrespective of where your service actually lives on the event bus (potentially on a different machine).
 *
 * That means you can interact with your service like this:
 *
 * [source,java]
 * ----
 * {@link examples.Examples#example2(io.vertx.core.Vertx)}
 * ----
 *
 * You can also combine `@ProxyGen` with language API code generation (`@VertxGen`) in order to create service stubs
 * in any of the languages supported by Vert.x - this means you can write your service once in Java and interact with it
 * through an idiomatic other language API irrespective of whether the service lives locally or is somewhere else on
 * the event bus entirely. For this don't forget to add the dependency on your language in your build descriptor:
 *
 * [source, java]
 * ----
 * &#64;ProxyGen // Generate service proxies
 * &#64;VertxGen // Generate the clients
 * public interface SomeDatabaseService {
 *   // ...
 * }
 * ----
 *
 * == Async Interface
 *
 * To be used by the service-proxy generation, the _service interface_ must comply to a couple of rules. First it
 * should follow the async pattern. To return result, the method should declare a
 * `Handler<AsyncResult<ResultType>>`. `ResultType` can be another proxy (and so a proxies can be factories for other
 * proxies.
 *
 * Let's see an example
 *
 * [source,java]
 * ----
 * &#64;ProxyGen
 * public interface SomeDatabaseService {
 *
 *  // A couple of factory methods to create an instance and a proxy
 *
 *  static SomeDatabaseService create(Vertx vertx) {
 *    return new SomeDatabaseServiceImpl(vertx);
 *  }
 *
 *  static SomeDatabaseService createProxy(Vertx vertx, String address) {
 *    return new SomeDatabaseServiceVertxEBProxy(vertx, address);
 *  }
 *
 *  // A method notifying the completion without a result (void)
 *  void save(String collection, JsonObject document,
 *    Handler<AsyncResult<Void>> result);
 *
 *  // A method providing a result (a json object)
 *  void findOne(String collection, JsonObject query,
 *    Handler<AsyncResult<JsonObject>> result);
 *
 *  // Create a connection
 *  void createConnection(String shoeSize,
 *    Handler<AsyncResult<MyDatabaseConnection>> resultHandler);
 *
 * }
 * ----
 *
 * with:
 *
 * [source,java]
 * ----
 * &#64;ProxyGen
 * &#64;VertxGen
 * public interface MyDatabaseConnection {
 *
 *  void insert(JsonObject someData);
 *
 *  void commit(Handler<AsyncResult<Void>> resultHandler);
 *
 *  &#64;ProxyClose
 *  void close();
 * }
 * ----
 *
 * You can also declare that a particular method unregisters the proxy by annotating it with the `@ProxyClose`
 * annotation. The proxy instance is disposed when this method is called.
 *
 * More constraints on the _service interfaces_ are described below.
 *
 * ## Code generation
 *
 * Service annotated with `@ProxyGen` annotation trigger the generation of the service helper classes:
 *
 * - The service proxy: a compile time generated proxy that uses the `EventBus` to interact with the service via messages
 * - The service handler: a compile time generated `EventBus` handler that reacts to events sent by the proxy
 *
 * Generated proxies and handlers are named after the service class, for example if the service is named `MyService`
 * the handler is called `MyServiceProxyHandler` and the proxy is called `MyServiceEBProxy`.
 *
 * The _codegen_ annotation processor generates these classes at compilation time. It is a feature of the Java
 * compiler so _no extra step_ is required, it is just a matter of configuring correctly the compiler.
 *
 *
 * Here a configuration example for Maven:
 *
 * [source,xml]
 * ----
 * <plugin>
 *   <artifactId>maven-compiler-plugin</artifactId>
 *   <configuration>
 *     <annotationProcessors>
 *       <annotationProcessor>io.vertx.codegen.CodeGenProcessor</annotationProcessor>
 *     </annotationProcessors>
 *     <compilerArgs>
 *       <arg>-AoutputDirectory=${project.basedir}/src/main</arg>
 *     </compilerArgs>
 *   </configuration>
 * </plugin>
 * ----
 *
 * This feature can also be used in Gradle, or even in IDE as they provide usually support for annotation
 * processors.
 *
 * ## Exposing your service
 *
 * Once you have your _service interface_, compile the source to generate the stub and proxies. Then, you need some
 * code to "register" your service on the event bus:
 *
 * [source, java]
 * ----
 * {@link examples.Examples#register(io.vertx.core.Vertx)}
 * ----
 *
 * This can be done in a verticle, or anywhere in your code.
 *
 * Once registered, the service becomes accessible. If you are running your application on a cluster, the service is
 * available from any host.
 *
 * To withdraw your service, use the {@link io.vertx.serviceproxy.ProxyHelper#unregisterService(io.vertx.core.eventbus.MessageConsumer)}
 * method:
 *
 * [source, java]
 * ----
 * {@link examples.Examples#unregister(io.vertx.core.Vertx)}
 * ----
 *
 * ## Proxy creation
 *
 * Now that the service is exposed, you probably want to consume it. For this, you need to create a proxy. The proxy
 * can be created using the `ProxyHelper` class:
 *
 * [source, java]
 * ----
 * {@link examples.Examples#proxyCreation(io.vertx.core.Vertx, io.vertx.core.eventbus.DeliveryOptions)}
 * ----
 *
 * The second method takes an instance of {@link io.vertx.core.eventbus.DeliveryOptions} where you con configure the
 * message delivery (such as the timeout).
 *
 * Alternatively, you can use the generated proxy class. The proxy class name is the _service interface_ class name
 * followed by `VertxEBProxy`. For instance, if your _service interface_ is named `SomeDatabaseService`, the proxy
 * class is named `SomeDatabaseServiceVertxEBProxy`.
 *
 * Generally, _service interface_ contains a `createProxy` static method to create the proxy. But this is not required:
 *
 * [source,java]
 * ----
 * &#64;ProxyGen
 * public interface SomeDatabaseService {
 *
 *  // Method to create the proxy.
 *  static SomeDatabaseService createProxy(Vertx vertx, String address) {
 *    return new SomeDatabaseServiceVertxEBProxy(vertx, address);
 *  }
 *
 *  // ...
 *}
 * ----
 *
 * ## Restrictions for service interface
 *
 * There are restrictions on the types and return values that can be used in a service method so that these are easy to
 * marshall over event bus messages and so they can be used asynchronously. They are:
 *
 * ### Return types
 *
 * Must be one of
 *
 * * `void`
 * * `@Fluent` and return reference to the service (`this`):
 *
 * [source,java]
 * ----
 * &#64;Fluent
 * SomeDatabaseService doSomething();
 * ----
 *
 * This is because methods must not block and it's not possible to return a result immediately without blocking if
 * the service is remote.
 *
 * #### Parameter types
 *
 * Let `JSON` = `JsonObject | JsonArray`
 * Let `PRIMITIVE` = Any primitive type or boxed primitive type
 *
 * Parameters can be any of:
 *
 * * `JSON`
 * * `PRIMITIVE`
 * * `List<JSON>`
 * * `List<PRIMITIVE>`
 * * `Set<JSON>`
 * * `Set<PRIMITIVE>`
 * * `Map<String, JSON>`
 * * `Map<String, PRIMITIVE>`
 * * Any _Enum_ type
 * * Any class annotated with `@DataObject`
 *
 * If an asynchronous result is required a last parameter of type `Handler<AsyncResult<R>>` can be provided.
 *
 * `R` can be any of:
 *
 * * `JSON`
 * * `PRIMITIVE`
 * * `List<JSON>`
 * * `List<PRIMITIVE>`
 * * `Set<JSON>`
 * * `Set<PRIMITIVE>`
 * * Any _Enum_ type
 * * Any class annotated with `@DataObject`
 * * Another proxy
 *
 * ### Overloaded methods
 *
 * There must be no overloaded service methods. (_i.e._ more than one with the same name, regardless the signature).
 *
 * ## Convention for invoking services over the event bus (without proxies)
 *
 * Service Proxies assume that event bus messages follow a certain format so they can be used to invoke services.
 *
 * Of course, you don't *have to* use client proxies to access remote service if you don't want to. It's perfectly acceptable
 * to interact with them by just sending messages over the event bus.
 *
 * In order for services to be interacted with a consistent way the following message formats *must be used* for any
 * Vert.x services.
 *
 * The format is very simple:
 *
 * * There should be a header called `action` which gives the name of the action to perform.
 * * The body of the message should be a `JsonObject`, there should be one field in the object for each argument needed by the action.
 *
 * For example to invoke an action called `save` which expects a String collection and a JsonObject document:
 *
 * ----
 * Headers:
 *     "action": "save"
 * Body:
 *     {
 *         "collection", "mycollection",
 *         "document", {
 *             "name": "tim"
 *         }
 *     }
 * ----
 *
 * The above convention should be used whether or not service proxies are used to create services, as it allows services
 * to be interacted with consistently.
 *
 * In the case where service proxies are used the "action" value should map to the name of an action method in the
 * service interface and each `[key, value]` in the body should map to a `[arg_name, arg_value]` in the action method.
 *
 * For return values the service should use the `message.reply(...)` method to send back a return value - this can be of
 * any type supported by the event bus. To signal a failure the method `message.fail(...)` should be used.
 *
 * If you are using service proxies the generated code will handle this for you automatically.
 *
 */
@ModuleGen(name = "vertx-service-proxy", groupPackage = "io.vertx")
@Document(fileName = "index.adoc")
package io.vertx.serviceproxy;

import io.vertx.codegen.annotations.ModuleGen;
import io.vertx.docgen.Document;