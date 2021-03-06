//========================================================================
//MetaMetrics Local Relay 
//Whitehead (nwhitehead AT heliosdev DOT org
//2014
//========================================================================
//==============================
//Set up dependencies
//==============================

requirejs.config({
	baseUrl: 'lib',
	paths: {
		scripts: '../scripts'
	}
});

//The mm core namespace
var $mm = $mm || {};


define(['bacon', 'scripts/remote'],
		function (Baconator, Remote) {
	var relay = $mm.relay  = $mm.relay || {};  
	// The central shared event bus
	$mm.bus = new Baconator.Bus();

  // =====================================================================================
  // Local Relay Definition
  // =====================================================================================

	relay.local = function local(props) {
		props = props || {};
		console.info("Local Relay Initializing. Initial Props: [%O]", props);
		var self = this;
		this.remote = null;



		// =======================================
		//  Remoting properties
		// =======================================
		this.remoteProps = {};
		this.remotetype = props.remote || 'websocket';    
		if($mm.remote[this.remotetype]==null) {
			throw new Error(
					"Invalid remote type [" + self.remotetype + "]"
			);
		}

		this.remoteProps.remoteUrl = props.url || null;
		// =======================================
		//  Remoting events
		// =======================================    
		this.remoteProps.onclose = props.onclose;
		this.remoteProps.onopen = props.onopen;
		this.remoteProps.onmessage = props.onmessage;
		this.remoteProps.onerror = props.onerror;

		// ======================================
		// Add new event listeners to remote
		// ======================================
		this.addListener = function(event, listener) {
			this.remote.addListener(event, listener);
		}

		// ======================================
		// Remove registered event listeners
		// ======================================
		this.removeListener = function(event, listener) {
			this.remote.removeListener(event, listener);
		}

	    this.remoteProps.onsession = props.onsession;
	    this.remoteProps.clientid = props.clientid;



		// =======================================
		//  Boot remote instance
		// =======================================    
		this.remoteProps = function _onsession_(r_clientid, r_sessionid) {
			me.clientid = r_clientid;
			me.sessionid = r_sessionid;
			$mm.bus.push({type: 'assignsession', clientid: me.clientid, sessionid: me.sessionid});
		};
		this.remote = new $mm.remote[this.remotetype](this.remoteProps);
		console.info("Remote Acquired: [%s]", this.remote);
		// var events = ['close', 'open', 'message', 'error'];
		this.addListener('message', function(data, remote) {
			console.debug("Relaying Message: [%O]", data);

		});
		// ============================================================
		// Core internal functions
		// ============================================================
		var timeoutCountingStream = function(ms, cancelStream) {  
			var toh = null;
			var errInvoker = null;
			var actual = 0;
			var cb = Baconator.fromCallback(function(callback) {
				errInvoker = callback;
				toh = setTimeout(function() {
					if(cb.hasSubscribers()) {
						callback(new Bacon.Error("Timeout after [" + ms + "] ms."));
					}
				}, ms);
			});
			if(cancelStream && cancelStream.onEnd) {
				cancelStream.endOnError(function(){
					clearTimeout(toh);
					console.info("Timeout [%s] cleared", toh);
				});
			}
			cb.onValue(function(){
				console.warn("Timeout [%s] fired after [%s] ms", toh, ms);
			}
			);
			var ret = {   
					cancel: function() { clearTimeout(toh); console.info("Timeout [%s] cleared", toh); },
					stream: function() { return cb; },
					reset: function() {
						clearTimeout(toh);
						console.info("Timeout [%s] cleared", toh);
						toh = setTimeout(function() {
							if(cb.hasSubscribers()) {
								errInvoker(new Bacon.Error("Timeout after [" + ms + "] ms."));
							}
						}, ms);
					}
			}

			return ret;
		};

  //     routing:
  //   callbacks:
  //     onstart
  //     onend
  //     data
  //     error
  //   response selector 
  //   response end selector
  //   timeout


  // if(opts.timeout==null) opts.timeout = 1000;
  // if(opts.resetting==null) opts.resetting = false;
  // if(opts.expectedReturns==null) opts.expectedReturns = Infinity;
  // if(opts.filter==null) opts.filter = filters.allFilter();
  // if(opts.seperateErrStream==null) opts.seperateErrStream = false;


		this.sendRequest = function _sendRequest_(payload, routing) {
      		routing = routing || {};
      		if(routing.timeout==null) routing.timeout = 1000;
      		if(routing.resetting==null) routing.resetting = false;
      		if(routing.expectedReturns==null) routing.expectedReturns = Infinity;
      		if(routing.filter==null) routing.filter = filters.allFilter();
      		if(routing.seperateErrStream==null) routing.seperateErrStream = false;
		};

		//============================================================================
		//	Register bus listeners
		//============================================================================
		console.info("Registering Bus Listeners");


		return {
			id: -1,
			send: this.sendRequest 
		}

	}

	// ====================
	// end of local ctor
	// ====================


	$mm.relay.relays = [];
	for(var p in $mm.relay) {
		if($mm.relay[p] instanceof Function) {
			$mm.relay.relays.push($mm.relay[p].name);
		}
	}  
	console.info("Available Relays: [%s]", $mm.relay.relays.join(", "));

	return {      
		local : function _local_(props) {
			return new relay.local(props);
		}
	}
});