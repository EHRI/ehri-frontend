/**
 * Helper JS for doing a long-running index job and getting progress.
 *
 * NB: The SOCKET_URI, DONE_MSG, and ERR_MSG constant must be initialized locally,
 * and a function getData() must be provided which when called will return the
 * data (as JSON) to be sent to the remote socket.
 */

jQuery(function($) {

  $("#select-all").change(function(event) {
    $("input[name='types[]']").prop("checked", $(this).prop("checked"));
  });

  function appendProgressMessage(msg) {
    var $elem = $("#update-progress");
    var $inner = $("#update-progress > pre");
    if ($inner.length === 0) {
      $inner = $("<pre></pre>");
      $elem.append($inner);
    }
    $inner.append(msg + "<br/>");
    $elem.scrollTop($inner.height());
  }

  var $submit = $("#submit-update");

  $submit.click(function(event) {
    event.preventDefault();
    $submit.attr("disabled", true);
    var $out = $("#update-progress");
    var websocket = new WebSocket(SOCKET_URI);
    websocket.onopen = function() {
      var data = getData();
      console.log("Data: ", data);
      websocket.send(data);
    };
    websocket.onerror = function(e) {
      appendProgressMessage("ERROR. Try refreshing the page.");
      $submit.attr("disabled", false);
      console.log("Socket error!");
    }
    websocket.onmessage = function(e) {
      var msg = JSON.parse(e.data);
      appendProgressMessage(msg);
      if (msg.indexOf(DONE_MSG) != -1 || msg.indexOf(ERR_MSG) != -1) {
        websocket.close();
        $submit.attr("disabled", false);
        console.log("Closed socket")
      }
    };
  });
});
