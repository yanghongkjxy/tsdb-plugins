var ws = null;
var q = null;

$( document ).ready(function() {
	$(window).resize(function() {
		$("#main").width($(window).width()-$("#leftbar").width() - 50);
		$("#jsonOutput").height($(window).height()-$("#outputQCForm").height() - 70);		
	});
	ws = new WebSocketAPIClient();
	q = QueryContext.newContext();
	$("#goBtn").click(function(e) {
	    goAction();		        
        return false;
	});
	$("#exprfield").focus();
	$("#exprfield").keypress(function(e) {
	    if(e.which == 13) {
		    goAction();
		    return false;
	    }
	    return true;
	});
	$("#exprfield").removeAttr("disabled");
	$("#goBtn").removeAttr("disabled");
	$("#main").width($(window).width()-$("#leftbar").width() - 50);
	$("#jsonOutput").height($(window).height()-$("#outputQCForm").height() - 70);
	console.info("Index.js Loaded");
});

function goAction() {	
	if($('#clearqc').is(':checked')) {
		q = getInputContext();
	} 	
	var selected = $('input[name=goaction]:checked', '#goAction').val();
	console.info("GO!  (Action is [%s])", selected);
	$('#jsonOutput').empty();
	if("json"==selected) doDisplayJson();
	else if("fulltree"==selected) doFullTree();
};




function doDisplayJson() {
	var expr = $('#exprfield').val();
	console.info("Executing fetch for expression [%s]", expr);
	var handle = function(result) {			
		updateOutputContext(result.q);
		if(!result.q.continuous) {
			$('#jsonOutput').empty();
			$('#jsonOutput').append("<pre>" + syntaxHighlight(result.data) + "</pre>");
		} else {
			responseCount++;
			var index = responseCount;
			var app = "<a id='link" + index + "'>Response #" + index + " -- Results:" + result.data.length + "&nbsp;<font size='-2'>(Click to Expand)</font>";
			app += "<div id='data" + index + "'><pre>";
			app += syntaxHighlight(result.data);
			app += "</pre></div></a><br>";
			$('#jsonOutput').append($(app));
			$('#data' + index).toggle();
			$('#link' + index).click(function(){
				
				$('#data' + index).toggle();
			});
		}
	};
	var handleError = function() {
		console.error("resolveTSMetas Failed: [%O]", arguments);
	}
 
	// Retrieve data using expr call.
	// write formatted data to jsonOutput
	var responseCount = 0;
	ws.resolveTSMetas(q, expr).then(
			handle, handleError, handle
	);
};

function updateOutputContext(qctx) {	
	var ctx = QueryContext.readContext(qctx);
	if(ctx.isExpired()|ctx.isExhausted()|ctx.getNextIndex()==null) {
		q = QueryContext.newContext({timeout: 10000});
	}	
	$('#qoutCummulative').val(ctx.getCummulative());
	$('#qoutElapsed').val(ctx.getElapsed());
	$('#qoutNextIndex').val(ctx.getNextIndex());
	$('#qoutExhausted').val(ctx.isExhausted());
	$('#qoutExpired').val(ctx.isExpired());	
};

function getInputContext() {
	return QueryContext.newContext({
		pageSize: parseInt($("#qinPageSize").val()),
		maxSize: parseInt($("#qinMaxSize").val()),
		timeout: parseInt($("#qinTimeout").val()),
		continuous: $('#qinContinuous').is(':checked'),
		format: $("#qinFormat").val()
	});
}

function doFullTree() {
	var expr = $('#exprfield').val();
	console.info("Executing fetch for expression [%s]", expr);
	// Retrieve data using expr call.
	// write formatted data to jsonOutput
	q.format = "D3";
	ws.resolveTSMetas(q, expr).then(
		function(result) {
			$('#jsonOutput').empty();
			updateOutputContext(result.q);
			//$('#jsonOutput').append("<pre>" + syntaxHighlight(result.data) + "</pre>");
			//$('#jsonOutput').json2html(convert('json',result.data,'open'),transforms.object);
			var cluster = d3.layout.cluster().size([$('#jsonOutput').height()*5, $('#jsonOutput').width()*5]);
			var diagonal = d3.svg.diagonal()
		    .projection(function(d) { 
		    	return [d.y, d.x]; 
		    });

			
			var svg = d3.select("div#jsonOutput").append("svg")
		    .attr("width", $('#jsonOutput').width()) // width + margin.right + margin.left)
		    .attr("height", $('#jsonOutput').height()) //height + margin.top + margin.bottom)
		    .append("g")
		    .attr("transform", "translate(10,3)");
			
			
		var nodes = cluster.nodes(result.data),
		links = cluster.links(nodes);
		nodes.forEach(function(d) { d.y = d.depth * 90; });

		  var link = svg.selectAll(".link")
		      .data(links)
		    .enter().append("path")
		      .attr("class", "link")
		      .attr("d", diagonal);

		  var node = svg.selectAll(".node")
		      .data(nodes)
		    .enter().append("g")
		      .attr("class", "node")
//		      .attr("transform", function(d) { return "rotate(" + (d.x - 90) + ")translate(" + d.y + ")"; })
		      .attr("transform", function(d) { 
		      	//console.info("Transform: [%O]", d);
		      	return "translate(" + d.y + "," + d.x + ")"; 
		      })

		  node.append("circle")
		      .attr("r", 4.5);

		  node.append("text")
		      .attr("dx", function(d) { return d.children ? -8 : 8; })
		      .attr("dy", 3)
//		      .attr("text-anchor", function(d) { return d.x < 180 ? "start" : "end"; })
		      .style("text-anchor", function(d) { 
		    	  return d.children ? "end" : "start"; }
		      )
		      .text(function(d) { return d.name; });
			
			
		}	
	).then(function(){
		$('svg g').attr('id', 'viewport');
		$('svg').svgPan('viewport', true, true, true);
	});
};