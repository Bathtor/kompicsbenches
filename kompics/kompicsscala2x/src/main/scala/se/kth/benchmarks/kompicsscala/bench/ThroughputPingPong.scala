package se.kth.benchmarks.kompicsscala.bench

import se.kth.benchmarks.Benchmark
import se.kth.benchmarks.kompicsscala.{KompicsSystem, KompicsSystemProvider}
import kompics.benchmarks.benchmarks.ThroughputPingPongRequest
import se.sics.kompics.sl._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try
import java.util.concurrent.CountDownLatch
import java.util.UUID

object ThroughputPingPong extends Benchmark {
  import scala.concurrent.ExecutionContext.Implicits.global;

  override type Conf = ThroughputPingPongRequest;

  override def msgToConf(msg: scalapb.GeneratedMessage): Try[Conf] = {
    Try(msg.asInstanceOf[ThroughputPingPongRequest])
  };
  override def newInstance(): Instance = new PingPongI;

  class PingPongI extends Instance {
    private var numMsgs = -1L;
    private var numPairs = -1;
    private var pipeline = -1L;
    private var staticOnly = true;
    private var system: KompicsSystem = null;
    private var pingers: List[UUID] = List.empty;
    private var pongers: List[UUID] = List.empty;
    private var latch: CountDownLatch = null;

    override def setup(c: Conf): Unit = {
      this.numMsgs = c.messagesPerPair;
      this.numPairs = c.parallelism;
      this.pipeline = c.pipelineSize;
      this.staticOnly = c.staticOnly;
      this.system = KompicsSystemProvider.newKompicsSystem(threads = Runtime.getRuntime.availableProcessors());
    }
    override def prepareIteration(): Unit = {
      assert(system != null);
      latch = new CountDownLatch(numPairs);
      val lf = (0 to numPairs).map { _i =>
        for {
          pongerId <- if (staticOnly) {
            system.createNotify[StaticPonger](Init.none[StaticPonger])
          } else {
            system.createNotify[Ponger](Init.none[Ponger])
          };
          pingerId <- if (staticOnly) {
            system.createNotify[StaticPinger](Init[StaticPinger](latch, numMsgs, pipeline))
          } else {
            system.createNotify[Pinger](Init[Pinger](latch, numMsgs, pipeline))
          };
          _ <- system.connectComponents[PingPongPort.type](pingerId, pongerId);
          _ <- system.startNotify(pongerId)
        } yield {
          (pingerId, pongerId)
        }
      }.toList;
      val fl = Future.sequence(lf);
      val l = Await.result(fl, Duration.Inf);
      val (pi, po) = l.unzip;
      pingers = pi;
      pongers = po;
    }
    override def runIteration(): Unit = {
      val startLF = pingers.map(pinger => system.startNotify(pinger));
      val startFL = Future.sequence(startLF);
      Await.result(startFL, Duration.Inf);
      latch.await();
    }
    override def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double): Unit = {
      if (latch != null) {
        latch = null;
      }
      val killPingersLF = pingers.map(pinger => system.killNotify(pinger));
      val killPingersFL = Future.sequence(killPingersLF);
      pingers = List.empty;
      val killPongersLF = pongers.map(ponger => system.killNotify(ponger));
      val killPongersFL = Future.sequence(killPongersLF);
      pongers = List.empty;
      Await.result(killPingersFL, Duration.Inf);
      Await.result(killPongersFL, Duration.Inf);
      if (lastIteration) {
        system.terminate();
        system = null;
      }
    }
  }

  case object StaticPing extends KompicsEvent;
  case object StaticPong extends KompicsEvent;
  case class Ping(index: Long) extends KompicsEvent;
  case class Pong(index: Long) extends KompicsEvent;

  object PingPongPort extends Port {
    request(StaticPing);
    request[Ping];
    indication(StaticPong);
    indication[Pong];
  }

  class StaticPinger(init: Init[StaticPinger]) extends ComponentDefinition {

    val Init(latch: CountDownLatch, count: Long, pipeline: Long) = init;

    val ppp = requires(PingPongPort);

    private var sentCount = 0L;
    private var recvCount = 0L;

    ctrl uponEvent {
      case _: Start => {
        var pipelined = 0L;
        while (pipelined < pipeline && sentCount < count) {
          trigger(StaticPing -> ppp);
          pipelined += 1L;
          sentCount += 1L;
        }
      }
    }

    ppp uponEvent {
      case StaticPong => {
        recvCount += 1L;
        if (recvCount < count) {
          if (sentCount < count) {
            trigger(StaticPing -> ppp);
            sentCount += 1L;
          }
        } else {
          latch.countDown();
        }
      }
    }
  }

  class StaticPonger extends ComponentDefinition {

    val ppp = provides(PingPongPort);

    ppp uponEvent {
      case StaticPing => {
        trigger(StaticPong -> ppp);
      }
    }
  }

  class Pinger(init: Init[Pinger]) extends ComponentDefinition {

    val Init(latch: CountDownLatch, count: Long, pipeline: Long) = init;

    val ppp = requires(PingPongPort);

    private var sentCount = 0L;
    private var recvCount = 0L;

    ctrl uponEvent {
      case _: Start => {
        var pipelined = 0L;
        while (pipelined < pipeline && sentCount < count) {
          trigger(Ping(sentCount) -> ppp);
          pipelined += 1L;
          sentCount += 1L;
        }
      }
    }

    ppp uponEvent {
      case Pong(_) => {
        recvCount += 1L;
        if (recvCount < count) {
          if (sentCount < count) {
            trigger(Ping(sentCount) -> ppp);
            sentCount += 1L;
          }
        } else {
          latch.countDown();
        }
      }
    }
  }

  class Ponger extends ComponentDefinition {

    val ppp = provides(PingPongPort);

    ppp uponEvent {
      case Ping(i) => {
        trigger(Pong(i) -> ppp);
      }
    }
  }
}
