package se.kth.benchmarks.test

import org.scalatest._
import scala.util.{Failure, Success, Try}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import kompics.benchmarks.benchmarks._
import kompics.benchmarks.messages._
import kompics.benchmarks.distributed._
import io.grpc.{ManagedChannelBuilder, Server, ServerBuilder}
import se.kth.benchmarks._
import com.typesafe.scalalogging.StrictLogging

class DistributedTest(val benchFactory: BenchmarkFactory) extends Matchers with StrictLogging {

  private var implemented: List[String] = Nil;
  private var notImplemented: List[String] = Nil;

  val timeout = 60.seconds;

  def test(): Unit = {
    val runnerPort = 45678;
    val runnerAddrS = s"127.0.0.1:$runnerPort";
    val masterPort = 45679;
    val masterAddrS = s"127.0.0.1:$masterPort";
    val clientPorts = Array(45680, 45681, 45682, 45683);
    val clientAddrs = clientPorts.map(clientPort => s"127.0.0.1:$clientPort");

    val numClients = 4;

    /*
     * Setup
     */

    val masterThread = new Thread("BenchmarkMaster") {
      override def run(): Unit = {
        println("Starting master");
        val runnerAddr = Util.argToAddr(runnerAddrS).get;
        val masterAddr = Util.argToAddr(masterAddrS).get;
        BenchmarkMaster.run(numClients, masterAddr.port, runnerAddr.port, benchFactory);
        println("Finished master");
      }
    };
    masterThread.start();

    val clientThreads = clientAddrs.map { clientAddrS =>
      val clientThread = new Thread(s"BenchmarkClient-$clientAddrS") {
        override def run(): Unit = {
          println(s"Starting client $clientAddrS");
          val masterAddr = Util.argToAddr(masterAddrS).get;
          val clientAddr = Util.argToAddr(clientAddrS).get;
          BenchmarkClient.run(clientAddr.addr, masterAddr.addr, masterAddr.port);
          println(s"Finished client $clientAddrS");
        }
      };
      clientThread.start();
      clientThread
    };

    val runnerAddr = Util.argToAddr(runnerAddrS).get;
    val benchStub = {
      val channel = ManagedChannelBuilder.forAddress(runnerAddr.addr, runnerAddr.port).usePlaintext().build;
      val stub = BenchmarkRunnerGrpc.stub(channel);
      stub
    };

    var attempts = 0;
    var ready = false;
    while (!ready && attempts < 20) {
      attempts += 1;
      println(s"Checking if ready, attempt #${attempts}");
      val readyF = benchStub.ready(ReadyRequest());
      val res = Await.result(readyF, 500.milliseconds);
      if (res.status) {
        println("Was ready.");
        ready = true
      } else {
        println("Wasn't ready, yet.");
        Thread.sleep(500);
      }
    }
    ready should be(true);

    /*
     * Ping Pong
     */
    val ppr = PingPongRequest().withNumberOfMessages(100);
    val pprResF = benchStub.pingPong(ppr);
    val pprRes = Await.result(pprResF, timeout);
    checkResult("PingPong", pprRes);

    val npprResF = benchStub.netPingPong(ppr);
    val npprRes = Await.result(npprResF, timeout);
    checkResult("NetPingPong", npprRes);

    /*
     * Throughput Ping Pong
     */
    val tppr =
      ThroughputPingPongRequest().withMessagesPerPair(100).withParallelism(2).withPipelineSize(20).withStaticOnly(true);
    val tpprResF = benchStub.throughputPingPong(tppr);
    val tpprRes = Await.result(tpprResF, timeout);
    checkResult("ThroughputPingPong (static)", tpprRes);

    val tnpprResF = benchStub.netThroughputPingPong(tppr);
    val tnpprRes = Await.result(tnpprResF, timeout);
    checkResult("NetThroughputPingPong (static)", tnpprRes);

    val tppr2 = ThroughputPingPongRequest()
      .withMessagesPerPair(100)
      .withParallelism(2)
      .withPipelineSize(20)
      .withStaticOnly(false);
    val tpprResF2 = benchStub.throughputPingPong(tppr2);
    val tpprRes2 = Await.result(tpprResF2, timeout);
    checkResult("ThroughputPingPong (gc)", tpprRes2);

    val tnpprResF2 = benchStub.netThroughputPingPong(tppr2);
    val tnpprRes2 = Await.result(tnpprResF2, timeout);
    checkResult("NetThroughputPingPong (gc)", tnpprRes2);

    val nnarr = AtomicRegisterRequest()
      .withReadWorkload(0.5f)
      .withReadWorkload(0.5f)
      .withPartitionSize(3)
      .withNumberOfKeys(500);
    val nnarResF = benchStub.atomicRegister(nnarr);
    val nnarRes = Await.result(nnarResF, timeout);
    checkResult("Atomic Register", nnarRes);

    /*
     * Clean Up
     */
    println("Sending shutdown request to master");
    val sreq = ShutdownRequest().withForce(false);
    val shutdownResF = benchStub.shutdown(sreq);

    println("Waiting for master to finish...");
    masterThread.join();
    println("Master is done.");
    println("Waiting for all clients to finish...");
    clientThreads.foreach(t => t.join());
    println("All clients are done.");

    println(s"""
%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% MASTER-CLIENT SUMMARY %%
%%%%%%%%%%%%%%%%%%%%%%%%%%%
${implemented.size} tests implemented: ${implemented.mkString(",")}
${notImplemented.size} tests not implemented: ${notImplemented.mkString(",")}
""")
  }

  private def checkResult(label: String, tr: TestResult): Unit = {
    tr match {
      case s: TestSuccess => {
        s.runResults.size should equal(s.numberOfRuns);
        implemented ::= label;
      }
      case f: TestFailure => {
        f.reason should include("RSE"); // since tests are short they may not meet RSE requirements
        implemented ::= label;
      }
      case n: NotImplemented => {
        logger.warn(s"Test $label was not implemented");
        notImplemented ::= label;
      }
      case x => fail(s"Unexpected test result: $x")
    }
  }
}
