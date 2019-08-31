-module(benchmarks_server).

%% this file was generated by grpc

-export([decoder/0,
         'Ready'/3,
         'Shutdown'/3,
         'PingPong'/3,
         'NetPingPong'/3,
         'ThroughputPingPong'/3,
         'NetThroughputPingPong'/3,
         'AtomicRegister'/3]).

-type 'PingPongRequest'() ::
    #{number_of_messages => integer()}.

-type 'ThroughputPingPongRequest'() ::
    #{messages_per_pair => integer(),
      pipeline_size => integer(),
      parallelism => integer(),
      static_only => boolean()}.

-type 'AtomicRegisterRequest'() ::
    #{read_workload => float() | infinity | '-infinity' | nan,
      write_workload => float() | infinity | '-infinity' | nan,
      partition_size => integer(),
      number_of_keys => integer()}.

-type 'TestResult'() ::
    #{sealed_value =>
          {success, 'TestSuccess'()} |
          {failure, 'TestFailure'()} |
          {not_implemented, 'NotImplemented'()}}.

-type 'TestSuccess'() ::
    #{number_of_runs => integer(),
      run_results => [float() | infinity | '-infinity' | nan]}.

-type 'TestFailure'() ::
    #{reason => string()}.

-type 'NotImplemented'() ::
    #{}.

-type 'ReadyRequest'() ::
    #{}.

-type 'ReadyResponse'() ::
    #{status => boolean()}.

-type 'ShutdownRequest'() ::
    #{force => boolean()}.

-type 'ShutdownAck'() ::
    #{}.

-spec decoder() -> module().
%% The module (generated by gpb) used to encode and decode protobuf
%% messages.
decoder() -> benchmarks.

%% RPCs for service 'BenchmarkRunner'

-spec 'Ready'(Message::'ReadyRequest'(), Stream::grpc:stream(), State::any()) ->
    {'ReadyResponse'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'Ready'(_Message, Stream, _State) ->
    io:fwrite("Got ready request.~n"),
    {#{status => true}, Stream}.

-spec 'Shutdown'(Message::'ShutdownRequest'(), Stream::grpc:stream(), State::any()) ->
    {'ShutdownAck'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'Shutdown'(_Message, Stream, _State) ->
    io:fwrite("Got shutdown request, but shouldn't handle this!~n"),
    {#{}, Stream}.

-spec 'PingPong'(Message::'PingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'PingPong'(Message, Stream, _State) ->
    io:fwrite("Got PingPong request.~n"),
    {ok, Response} = await_benchmark_result(ping_pong_bench, Message, "PingPong"),
    {Response, Stream}.

-spec 'NetPingPong'(Message::'PingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'NetPingPong'(_Message, Stream, _State) ->
    io:fwrite("Got NetPingPong request.~n"),
    {test_result:not_implemented(), Stream}.

-spec 'ThroughputPingPong'(Message::'ThroughputPingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'ThroughputPingPong'(Message, Stream, _State) ->
    io:fwrite("Got ThroughputPingPong request.~n"),
    {ok, Response} = await_benchmark_result(throughput_ping_pong_bench, Message, "ThroughputPingPong"),
    {Response, Stream}.

-spec 'NetThroughputPingPong'(Message::'ThroughputPingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'NetThroughputPingPong'(_Message, Stream, _State) ->
    io:fwrite("Got NetThroughputPingPong request.~n"),
    {test_result:not_implemented(), Stream}.

-spec 'AtomicRegister'(Message::'AtomicRegisterRequest'(), Stream::grpc:stream(), State::any()) ->
  {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'AtomicRegister'(_Message, Stream, _State) ->
  io:fwrite("Got AtomicRegister request request.~n"),
  {test_result:not_implemented(), Stream}.