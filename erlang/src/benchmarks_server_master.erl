-module(benchmarks_server_master).

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
    case benchmark_master:get_state() of
    	'READY' ->
    		{#{status => true}, Stream};
    	Other ->
    		io:fwrite("Master not ready, yet: ~p.~n", [Other]),
    		{#{status => false}, Stream}
    end.

-spec 'Shutdown'(Message::'ShutdownRequest'(), Stream::grpc:stream(), State::any()) ->
    {'ShutdownAck'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'Shutdown'(Message, Stream, _State) ->
    io:fwrite("Got shutdown request.~n"),
    ok = benchmark_master:shutdown_all(Message),
    {#{}, Stream}.

-spec 'PingPong'(Message::'PingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'PingPong'(Message, Stream, _State) ->
    io:fwrite("Got PingPong request.~n"),
    Response = await_benchmark_result(ping_pong_bench, Message),
    {Response, Stream}.

-spec 'NetPingPong'(Message::'PingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'NetPingPong'(Message, Stream, _State) ->
    io:fwrite("Got NetPingPong request.~n"),
    Response = await_benchmark_result(net_ping_pong_bench, Message),
    {Response, Stream}.

-spec 'ThroughputPingPong'(Message::'ThroughputPingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'ThroughputPingPong'(Message, Stream, _State) ->
    io:fwrite("Got ThroughputPingPong request.~n"),
    Response = await_benchmark_result(throughput_ping_pong_bench, Message),
    {Response, Stream}.

-spec 'NetThroughputPingPong'(Message::'ThroughputPingPongRequest'(), Stream::grpc:stream(), State::any()) ->
    {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'NetThroughputPingPong'(Message, Stream, _State) ->
    io:fwrite("Got NetThroughputPingPong request.~n"),
    Response = await_benchmark_result(net_throughput_ping_pong_bench, Message),
    {Response, Stream}.

-spec 'AtomicRegister'(Message::'AtomicRegisterRequest'(), Stream::grpc:stream(), State::any()) ->
  {'TestResult'(), grpc:stream()} | grpc:error_response().
%% This is a unary RPC
'AtomicRegister'(Message, Stream, _State) ->
    io:fwrite("Got AtomicRegister request.~n"),
    Response = await_benchmark_result(atomic_register_bench, Message),
    {Response, Stream}.

-spec await_benchmark_result(Benchmark :: module(), Params :: term()) -> 'TestResult'().
await_benchmark_result(Benchmark, Params) ->
	try await_benchmark_result_caught(Benchmark, Params) of
		X -> X
	catch
		Error when is_list(Error) ->
			test_result:failure(Error);
		Error ->
			test_result:failure(io_lib:fwrite("Test test threw an exception: ~p.~n", [Error]))
	end.

-spec await_benchmark_result_caught(Benchmark :: module(), Params :: term()) -> 'TestResult'().
await_benchmark_result_caught(Benchmark, Params) ->
	{ok, Request, Future} = benchmark_master:new_bench_request(Benchmark, Params),
	ok = benchmark_master:request_bench(Request),
	{ok, Result} = futures:await(Future),
	case Result of
		{ok, Data} ->
			test_result:success(length(Data), Data);
		{error, Reason} when is_list(Reason) ->
			test_result:failure(Reason);
    	{error, Reason} ->
      		test_result:failure(io_lib:fwrite("Test failed with: ~p.~n", [Reason]));
		Other ->
			test_result:failure(io_lib:fwrite("Unknown test response:~p.~n", [Other]))
    end.
